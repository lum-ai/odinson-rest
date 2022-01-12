package controllers

import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.odinson.digraph.Vocabulary
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.search.{ OdinsonQuery, OdinsonScoreDoc }
import ai.lum.odinson.{
  ExtractorEngine,
  Mention,
  OdinsonIndexWriter,
  Document => OdinsonDocument
}
import com.typesafe.config.{ Config, ConfigRenderOptions }
import ai.lum.odinson.rest.BuildInfo
import ai.lum.odinson.rest.utils._
import ai.lum.odinson.rest.requests._
import org.apache.lucene.document.{ Document => LuceneDocument }
import org.apache.lucene.store.FSDirectory
import play.api.Configuration
import play.api.http.ContentTypes
import play.api.libs.json._
import play.api.mvc._

import java.io.File
import java.nio.file.Path
import javax.inject._
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class OdinsonController @Inject() (
  config: Config = ConfigFactory.load(),
  playConfig: Configuration,
  cc: ControllerComponents
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) {

  import ai.lum.odinson.rest.json._
  import ExceptionUtils._
  import ExtractorEngineUtils._

  // format: off
  val docsDir              = config.apply[File]  ("odinson.docsDir")
  val pageSize             = config.apply[Int]   ("odinson.pageSize")
  val posTagTokenField     = config.apply[String]("odinson.index.posTagTokenField")
  // format: on

  def indexDocument(): Action[AnyContent] = Action { request =>
    try {
      val jsonStr = request.body.asText.get
      val doc = OdinsonDocument.fromJson(jsonStr)
      OdinsonIndexUtils.indexDoc(config, doc) match {
        case true => Ok
        case false => Status(500)
      }
      // FIXME: return {docId: }
    } catch handleNonFatal
  }

  def buildInfo(pretty: Option[Boolean]) = Action {
    Ok(BuildInfo.toJson.format(pretty)).as(ContentTypes.JSON)
  }

  def configInfo(pretty: Option[Boolean]) = Action {
    val options = ConfigRenderOptions.concise.setJson(true)
    val json = Json.parse(config.root.render(options))
    json.format(pretty)
  }

  def numDocs = Action {
    val extractorEngine: ExtractorEngine = newEngine(config)
    Ok(extractorEngine.numDocs.toString).as(ContentTypes.JSON)
  }

  /** Information about the current corpus. <br>
    * Directory name, num docs, num dependency types, etc.
    */
  def corpusInfo(pretty: Option[Boolean]) = Action.async {
    Future {
      usingNewEngine(config) { extractorEngine =>
        val numDocs = extractorEngine.numDocs()
        val corpusDir = config.apply[File]("odinson.indexDir").getName
        val depsVocabSize = {
          loadVocabulary.terms.toSet.size
        }
        val fields = extractorEngine.index.listFields()
        val fieldNames = fields.iterator.asScala.toList
        val storedFields =
          if (extractorEngine.numDocs < 1) {
            Nil
          } else {
            val firstDoc = extractorEngine.doc(0)
            firstDoc.iterator.asScala.map(_.name).toList
          }
        val tokenFields = extractorEngine.dataGatherer.storedFields
        val allFields = extractorEngine.index.listFields()
        val allFieldNames = allFields.iterator.asScala.toList
        val docFields = allFieldNames diff tokenFields

        val json = Json.obj(
          "numDocs" -> numDocs,
          "corpus" -> corpusDir,
          "distinctDependencyRelations" -> depsVocabSize,
          "tokenFields" -> tokenFields,
          "docFields" -> docFields,
          "storedFields" -> storedFields
        )
        json.format(pretty)
      }
    }
  }

  def loadVocabulary: Vocabulary = {
    val indexPath = config.apply[Path]("odinson.indexDir")
    val indexDir = FSDirectory.open(indexPath)
    Vocabulary.fromDirectory(indexDir)
  }

  /** Retrieves vocabulary of dependencies for the current index.
    */
  def dependenciesVocabulary(pretty: Option[Boolean]) = Action.async {
    Future {
      // NOTE: It's possible the vocabulary could change if the index is updated
      val vocabulary = loadVocabulary
      val vocab = vocabulary.terms.toList.sorted
      val json = Json.toJson(vocab)
      json.format(pretty)
    }
  }

  /** Retrieves JSON for given sentence ID. <br>
    * Used to visualize parse and token attributes.
    */
  def sentenceJsonForSentId(sentenceId: Int, pretty: Option[Boolean]) = Action.async {
    Future {
      // ensure doc id is correct
      val json = mkAbridgedSentence(sentenceId, config)
      json.format(pretty)
    }
  }

  /** Stores query results in state.
    *
    * @param extractorEngine An extractor whose state should be altered.
    * @param odinsonQuery An Odinson pattern.
    * @param metadataQuery A query to filter documents (optional).
    * @param label The label to use when committing matches.
    */
  def commitResults(
    extractorEngine: ExtractorEngine,
    odinsonQuery: String,
    metadataQuery: Option[String],
    label: String = "Mention"
  ): Unit = {
    metadataQuery match {
      case None =>
        val q = extractorEngine.compiler.mkQuery(odinsonQuery)
        extractorEngine.query(q)
      case Some(filter) =>
        val q = extractorEngine.compiler.mkQuery(odinsonQuery, filter)
        extractorEngine.query(q)
    }
  }

  /** Queries the index.
    *
    * @param - extractorEngine - an open instance of ExtractorEngine
    * @param odinsonQuery An OdinsonQuery
    * @param prevDoc The last Document ID seen on the previous page of results (required if retrieving page 2+).
    * @param prevScore The score of the last Document see on the previous page (required if retrieving page 2+).
    * @return JSON of matches
    */
  def retrieveResults(
    extractorEngine: ExtractorEngine,
    odinsonQuery: OdinsonQuery,
    prevDoc: Option[Int],
    prevScore: Option[Float]
  ): OdinResults = {
    (prevDoc, prevScore) match {
      case (Some(doc), Some(score)) =>
        val osd = new OdinsonScoreDoc(doc, score)
        extractorEngine.query(odinsonQuery, pageSize, osd)

      case _ => extractorEngine.query(odinsonQuery, pageSize)
    }
  }

  /** Executes the provided Odinson grammar.
    *
    * @param grammar An Odinson grammar
    * @param pageSize The maximum number of sentences to execute the rules against.
    * @param allowTriggerOverlaps Whether or not event arguments are permitted to overlap with the event's trigger.
    * @return JSON of matches
    */
  def executeGrammar() = Action { request =>
    //println(s"body: ${request.body}")
    //val json: JsValue = request.body.asJson.get
    usingNewEngine(config) { extractorEngine =>
      // FIXME: replace .get with validation check
      val gr = request.body.asJson.get.as[GrammarRequest]
      //println(s"GrammarRequest: ${gr}")
      val grammar = gr.grammar
      val pageSize = gr.pageSize
      val allowTriggerOverlaps = gr.allowTriggerOverlaps.getOrElse(false)
      val pretty = gr.pretty
      try {
        // rules -> OdinsonQuery
        val extractors = extractorEngine.ruleReader.compileRuleString(grammar)

        val start = System.currentTimeMillis()

        val maxSentences: Int = pageSize match {
          case Some(ps) => ps
          case None     => extractorEngine.numDocs()
        }

        val mentions: Seq[Mention] = {
          // FIXME: should deal in iterators to allow for, e.g., pagination...?
          val iterator = extractorEngine.extractMentions(
            extractors,
            numSentences = maxSentences,
            allowTriggerOverlaps = allowTriggerOverlaps,
            disableMatchSelector = false
          )
          iterator.toVector
        }

        val duration = (System.currentTimeMillis() - start) / 1000f // duration in seconds

        val json = Json.toJson(mkJson(None, duration, allowTriggerOverlaps, mentions, config))
        json.format(pretty)
      } catch handleNonFatal
    }
  }

  /** @param odinsonQuery An Odinson pattern
    * @param metadataQuery A Lucene query to filter documents (optional).
    * @param label The label to use when committing matches to the state.
    * @param commit Whether or not results should be committed to the state.
    * @param prevDoc The last Document ID seen on the previous page of results (required if retrieving page 2+).
    * @param prevScore The score of the last Document see on the previous page (required if retrieving page 2+).
    * @return JSON of matches
    */
  def runQuery(
    odinsonQuery: String,
    metadataQuery: Option[String],
    label: Option[String], // FIXME: in the future, this will be decided in the grammar
    commit: Option[Boolean], // FIXME: in the future, this will be decided in the grammar
    prevDoc: Option[Int],
    prevScore: Option[Float],
    enriched: Boolean,
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      usingNewEngine(config) { extractorEngine =>
        try {
          val oq = metadataQuery match {
            case Some(pq) =>
              extractorEngine.compiler.mkQuery(odinsonQuery, pq)
            case None =>
              extractorEngine.compiler.mkQuery(odinsonQuery)
          }
          val start = System.currentTimeMillis()
          val results: OdinResults = retrieveResults(extractorEngine, oq, prevDoc, prevScore)
          val duration = (System.currentTimeMillis() - start) / 1000f // duration in seconds

          // should the results be added to the state?
          if (commit.getOrElse(false)) {
            // FIXME: can this be processed in the background?
            commitResults(
              extractorEngine = extractorEngine,
              odinsonQuery = odinsonQuery,
              metadataQuery = metadataQuery,
              label = label.getOrElse("Mention")
            )
          }

          val json = Json.toJson(mkJson(odinsonQuery, metadataQuery, duration, results, enriched, config))
          json.format(pretty)
        } catch handleNonFatal
      }
    }
  }

  def getMetadataJsonByDocumentId(
    documentId: String,
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      try {
        val odinsonDocument: OdinsonDocument = loadParentDocByDocumentId(documentId, config)
        val json: JsValue = Json.parse(odinsonDocument.toJson)("metadata")
        json.format(pretty)
      } catch mkHandleNullPointer(
        "This search index does not have document filenames saved as stored fields, so metadata cannot be retrieved."
      ).orElse(handleNonFatal)
    }
  }

  def getMetadataJsonBySentenceId(
    sentenceId: Int,
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      usingNewEngine(config) { extractorEngine =>
        try {
          val luceneDoc: LuceneDocument = extractorEngine.doc(sentenceId)
          // FIXME: getOrElse and return Option[]
          val documentId = luceneDoc.getValues(OdinsonIndexWriter.DOC_ID_FIELD).head
          val odinsonDocument: OdinsonDocument = loadParentDocByDocumentId(documentId, config)
          val json: JsValue = Json.parse(odinsonDocument.toJson)("metadata")
          json.format(pretty)
        } catch mkHandleNullPointer(
          "This search index does not have document filenames saved as stored fields, so the parent document cannot be retrieved."
        ).orElse(handleNonFatal)
      }
    }
  }

  def getParentDocJsonBySentenceId(
    sentenceId: Int,
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      usingNewEngine(config) { extractorEngine =>
        try {
          val luceneDoc: LuceneDocument = extractorEngine.doc(sentenceId)
          // FIXME: getOrElse and return Option[String]
          val documentId = luceneDoc.getValues(OdinsonIndexWriter.DOC_ID_FIELD).head
          val odinsonDocument: OdinsonDocument = loadParentDocByDocumentId(documentId, config)
          val json: JsValue = Json.parse(odinsonDocument.toJson)
          json.format(pretty)
        } catch mkHandleNullPointer(
          "This search index does not have document filenames saved as stored fields, so the parent document cannot be retrieved."
        ).orElse(handleNonFatal)
      }
    }
  }

  def getParentDocJsonByDocumentId(
    documentId: String,
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      try {
        val odinsonDoc = loadParentDocByDocumentId(documentId, config)
        val json: JsValue = Json.parse(odinsonDoc.toJson)
        json.format(pretty)
      } catch mkHandleNullPointer(
        "This search index does not have document filenames saved as stored fields, so the parent document cannot be retrieved."
      ).orElse(handleNonFatal)
    }
  }

}
