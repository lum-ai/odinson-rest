package controllers

import ai.lum.common.ConfigFactory
import ai.lum.common.ConfigUtils._
import ai.lum.odinson.digraph.Vocabulary
import ai.lum.odinson.lucene._
import ai.lum.odinson.lucene.search.{ OdinsonQuery, OdinsonScoreDoc }
import ai.lum.odinson.{ Document => OdinsonDocument, ExtractorEngine, Mention }
import ai.lum.odinson.lucene.index.OdinsonIndexWriter
import com.typesafe.config.{ Config, ConfigRenderOptions, ConfigValueFactory }
import ai.lum.odinson.rest.BuildInfo
import ai.lum.odinson.rest.requests._
import org.apache.lucene.document.{ Document => LuceneDocument }
import org.apache.lucene.store.FSDirectory
import play.api.Configuration
import play.api.http.ContentTypes
import play.api.libs.json._
import play.api.mvc._

import java.io.File
import java.nio.file.{ Files, Path }
import javax.inject._
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class OdinsonController @Inject() (
  config: Config = ConfigFactory.load(),
  // playConfig: Configuration,
  cc: ControllerComponents
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) {

  import ai.lum.odinson.rest.json._
  import ai.lum.odinson.rest.utils.ExceptionUtils._
  import ai.lum.odinson.rest.utils.OdinsonDocumentUtils._

  // format: off
  val docsDir              = config.apply[File]  ("odinson.docsDir")
  val pageSize             = config.apply[Int]   ("odinson.pageSize")
  val posTagTokenField     = config.apply[String]("odinson.index.posTagTokenField")
  val defaultMaxTokens     = config.apply[Int]("odinson.index.maxNumberOfTokensPerSentence")
  // format: on

  /** Initializes index directory structure if the app is started with an empty index.
   *
   */
  def initializeIndex(): Unit = {
    // def setPermissions(f: File): Unit = {
    //   f.setReadable(true, false)
    //   f.setWritable(true, false)
    // }
    if (!docsDir.exists()) {
      println(f"creating empty docs directory:\t${docsDir.getAbsolutePath}")
      docsDir.mkdirs()
    }
    val indexDir = config.apply[File]("odinson.indexDir")
    if (!indexDir.exists()) {
      println(f"creating empty index directory:\t${indexDir.getAbsolutePath}")
      indexDir.mkdirs()
    }
    // Files.setPosixFilePermissions(docsDir.toPath(), permissions)
    // Files.setPosixFilePermissions(indexDir.toPath(), permissions)
    ExtractorEngine.usingEngine(config) { engine =>
    // initialize empty index
    }
  }
  initializeIndex()

  /** Inspects JSON to see if it is valid OdinsonDocument, and throws an exception for any error
    * encountered.
    */
  def validateOdinsonDocument(json: JsValue, strict: Boolean = false): Unit = {
    val doc = OdinsonDocument.fromJson(json.toString)
    // FIXME: do we want to complain about any metadata fields not supported by the metadata query language?
    (strict, doc.metadata.find(field => false)) match {
      case (true, Some(badField)) =>
        throw DocumentValidationError(
          s"field ${badField} not supported by metadata query language."
        )
      case _ => ()
    }
    // FIXME: in strict mode, ensure that all fields are known to compiler.
  }

  /** Returns 200 if the Json body can be turned into an Odinson Document.
    * @return
    *   A status code indicating validity
    */
  def validateOdinsonDocumentRelaxedMode(): Action[AnyContent] = Action { request =>
    try {
      val json = request.body.asJson.get
      val validated = json match {
        case jsObject: JsObject => validateOdinsonDocument(jsObject, false)
        // case jsArray: JsArray
        case _ => BadRequest("Malformed JSON.  Send a single OdinsonDocument.")
      }
      Status(OK)
    } catch handleNonFatal
  }

  /** Returns 200 if the Json body can be turned into an Odinson Document.
    * @return
    *   A status code indicating validity
    */
  def validateOdinsonDocumentStrictMode(): Action[AnyContent] = Action { request =>
    try {
      val json = request.body.asJson.get
      val validated = json match {
        case jsObject: JsObject => validateOdinsonDocument(jsObject, false)
        // case jsArray: JsArray
        case _ => BadRequest("Malformed JSON.  Send a single OdinsonDocument.")
      }
      Status(OK)
    } catch handleNonFatal
  }

  // def indexOdinsonDoc(): Action[AnyContent] = Action { request =>
  //   try {
  //     request.body.asJson match {
  //       case Some(json) =>
  //         // Add file field
  //         val doc = {
  //           OdinsonDocument.fromJson(json.toString).addFileNameMetadata(config)
  //         }
  //         ExtractorEngine.usingEngine(config) { engine =>
  //           engine.index.indexOdinsonDoc(doc)
  //           // write JSON to disk
  //           doc.writeDoc(config)
  //           Ok
  //         }
  //       // FIXME: better error
  //       case None => Status(500)
  //     }
  //   } catch handleNonFatal
  // }

  def deleteOdinsonDoc(odinsonDocId: String) = Action.async {
    Future {
      try {
        ExtractorEngine.usingEngine(config) { engine =>
          // Delete doc's JSON file
          val oldDocFile = engine.getDocJsonFile(odinsonDocId, config)
          oldDocFile.delete()
          // Delete doc from index
          engine.index.deleteOdinsonDoc(odinsonDocId)
          Ok
        }
      } catch {
        case e: Throwable =>
          println(e)
          // FIXME: make BadRequest from OdinsonException
          println(s"failed to delete Doc: ${e}")
          BadRequest(s"Failed to delete Document ${odinsonDocId}")
      }
    }
  }

  def updateOdinsonDoc(maxTokens: Int = -1): Action[AnyContent] = Action { request =>
    try {
      request.body.asJson match {
        case Some(json) =>
          val doc = {
            OdinsonDocument.fromJson(json.toString).addFileNameMetadata(config)
          }

          val maxTokensPerSentence: Int = maxTokens.max(defaultMaxTokens)
          val tempConfig = config.withValue("odinson.index.maxNumberOfTokensPerSentence", ConfigValueFactory.fromAnyRef(maxTokensPerSentence))
          println(s"maxTokensPerSentence: ${maxTokensPerSentence}")
          ExtractorEngine.usingEngine(tempConfig) { engine =>
            // Delete old JSON file (if exists)
            try {
              val oldDocFile = engine.getDocJsonFile(doc.id, tempConfig)
              oldDocFile.delete()
            } catch {case e: Throwable => { () } }
            // Update index & write JSON file
            engine.index.updateOdinsonDoc(doc)
            doc.writeDoc(config)
            Ok
          }
        // FIXME: better error
        case None => Status(500)
      }
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

  def numDocs = Action.async {
    Future {
      ExtractorEngine.usingEngine(config) { engine =>
        Ok(engine.numDocs.toString).as(ContentTypes.JSON)
      }
    }
  }

  /** Information about the current corpus. <br> Directory name, num docs, num dependency types,
    * etc.
    */
  def corpusInfo(pretty: Option[Boolean]) = Action.async {
    Future {
      ExtractorEngine.usingEngine(config) { engine =>
        val numDocs = engine.numDocs()
        val corpusDir = config.apply[File]("odinson.indexDir").getName
        val depsVocabSize = {
          loadVocabulary.terms.toSet.size
        }
        val fields = engine.index.listFields()
        val fieldNames = fields.iterator.asScala.toList
        val storedFields =
          if (engine.numDocs < 1) {
            Nil
          } else {
            val firstDoc = engine.doc(0)
            firstDoc.iterator.asScala.map(_.name).toList
          }
        val tokenFields = engine.dataGatherer.storedFields
        val allFields = engine.index.listFields()
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

  /** Retrieves JSON for given Odinson Document ID.
    */
  def odinsonDocumentJsonForId(odinsonDocId: String, pretty: Option[Boolean]) = Action.async {
    Future {
      ExtractorEngine.usingEngine(config) { engine =>
        try {
          val doc = engine.odinsonDoc(odinsonDocId, config)
          val json: JsValue = Json.parse(doc.toJson)
          json.format(pretty)
        } catch {
          case _: NullPointerException =>
            BadRequest(
              "This search index does not have document filenames saved as stored fields, so the parent document cannot be retrieved."
            )
          case _: Throwable =>
            BadRequest(s"odinsonDocId '${odinsonDocId}' not found")
        }
      }
    }
  }

  /** Retrieves JSON for given sentence ID. <br> Used to visualize parse and token attributes.
    */
  def sentenceJsonForSentId(sentenceId: Int, pretty: Option[Boolean]) = Action.async {
    Future {
      try {
        ExtractorEngine.usingEngine(config) { engine =>
          // ensure doc id is correct
          val json = engine.mkUnabridgedSentenceJson(sentenceId, config)
          json.format(pretty)
        }
      } catch {
        case _: NullPointerException =>
          // FIXME: should we check that this field is actually missing/not stored?
          BadRequest(
            "This search index does not have document filenames saved as stored fields, so the parent document cannot be retrieved."
          )
        case _: Throwable =>
          BadRequest(s"sentenceId ${sentenceId} not found")
      }
    }
  }

  /** Stores query results in state.
    *
    * @param engine
    *   An extractor whose state should be altered.
    * @param odinsonQuery
    *   An Odinson pattern.
    * @param metadataQuery
    *   A query to filter documents (optional).
    * @param label
    *   The label to use when committing matches.
    */
  def commitResults(
    engine: ExtractorEngine,
    odinsonQuery: String,
    metadataQuery: Option[String],
    label: String = "Mention"
  ): Unit = {
    metadataQuery match {
      case None =>
        val q = engine.compiler.mkQuery(odinsonQuery)
        engine.query(q)
      case Some(filter) =>
        val q = engine.compiler.mkQuery(odinsonQuery, filter)
        engine.query(q)
    }
  }

  /** Queries the index.
    *
    * @param -
    *   engine - an open instance of Engine
    * @param odinsonQuery
    *   An OdinsonQuery
    * @param prevDoc
    *   The last Document ID seen on the previous page of results (required if retrieving page 2+).
    * @param prevScore
    *   The score of the last Document see on the previous page (required if retrieving page 2+).
    * @return
    *   JSON of matches
    */
  def retrieveResults(
    engine: ExtractorEngine,
    odinsonQuery: OdinsonQuery,
    prevDoc: Option[Int],
    prevScore: Option[Float]
  ): OdinResults = {
    (prevDoc, prevScore) match {
      case (Some(doc), Some(score)) =>
        val osd = new OdinsonScoreDoc(doc, score)
        engine.query(odinsonQuery, pageSize, osd)

      case _ => engine.query(odinsonQuery, pageSize)
    }
  }

  /** Executes the provided Odinson grammar.
    *
    * @param grammar
    *   An Odinson grammar
    * @param pageSize
    *   The maximum number of sentences to execute the rules against.
    * @param allowTriggerOverlaps
    *   Whether or not event arguments are permitted to overlap with the event's trigger.
    * @return
    *   JSON of matches
    */
  def executeGrammar() = Action { request =>
    ExtractorEngine.usingEngine(config) { engine =>
      // FIXME: replace .get with validation check
      val gr = request.body.asJson.get.as[GrammarRequest]
      val grammar = gr.grammar
      val pageSize = gr.pageSize
      val allowTriggerOverlaps = gr.allowTriggerOverlaps.getOrElse(false)
      val pretty = gr.pretty
      try {
        // rules -> OdinsonQuery
        val extractors = engine.ruleReader.compileRuleString(grammar)

        val start = System.currentTimeMillis()

        val maxSentences: Int = pageSize match {
          case Some(ps) => ps
          case None     => engine.numDocs()
        }

        val mentions: Seq[Mention] = {
          // FIXME: should deal in iterators to better support pagination...?
          val iterator = engine.extractMentions(
            extractors,
            numSentences = maxSentences,
            allowTriggerOverlaps = allowTriggerOverlaps,
            disableMatchSelector = false
          )
          iterator.toVector
        }

        val duration = (System.currentTimeMillis() - start) / 1000f // duration in seconds

        val json = Json.toJson(engine.mkMentionsJson(None, duration, allowTriggerOverlaps, mentions))
        json.format(pretty)
      } catch handleNonFatal
    }
  }

  /** @param odinsonQuery
    *   An Odinson pattern
    * @param metadataQuery
    *   A Lucene query to filter documents (optional).
    * @param label
    *   The label to use when committing matches to the state.
    * @param commit
    *   Whether or not results should be committed to the state.
    * @param prevDoc
    *   The last Document ID seen on the previous page of results (required if retrieving page 2+).
    * @param prevScore
    *   The score of the last Document see on the previous page (required if retrieving page 2+).
    * @return
    *   JSON of matches
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
      ExtractorEngine.usingEngine(config) { engine =>
        try {
          val oq = metadataQuery match {
            case Some(pq) =>
              engine.compiler.mkQuery(odinsonQuery, pq)
            case None =>
              engine.compiler.mkQuery(odinsonQuery)
          }
          val start = System.currentTimeMillis()
          val results: OdinResults = retrieveResults(engine, oq, prevDoc, prevScore)
          val duration = (System.currentTimeMillis() - start) / 1000f // duration in seconds

          // should the results be added to the state?
          if (commit.getOrElse(false)) {
            // FIXME: can this be processed in the background?
            commitResults(
              engine = engine,
              odinsonQuery = odinsonQuery,
              metadataQuery = metadataQuery,
              label = label.getOrElse("Mention")
            )
          }

          val json = Json.toJson(engine.mkJson(
            odinsonQuery,
            metadataQuery,
            duration,
            results,
            enriched,
            config
          ))
          json.format(pretty)
        } catch handleNonFatal
      }
    }
  }

  def getMetadataJsonByDocumentId(
    odinsonDocId: String,
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      ExtractorEngine.usingEngine(config) { engine =>
        try {
          val doc: OdinsonDocument =
            engine.odinsonDoc(odinsonDocId, config)
          val json: JsValue = Json.parse(doc.toJson)("metadata")
          json.format(pretty)
        } catch {
          case _: NullPointerException =>
            BadRequest(
              "This search index does not have document filenames saved as stored fields, so the parent document cannot be retrieved."
            )
          case _: Throwable =>
            BadRequest(s"odinsonDocId '${odinsonDocId}' not found")
        }
      }
    }
  }

  def getMetadataJsonBySentenceId(
    sentenceId: Int,
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      ExtractorEngine.usingEngine(config) { engine =>
        try {
          val odinsonDocId = engine.getOdinsonDocId(sentenceId)
          val doc: OdinsonDocument =
            engine.odinsonDoc(odinsonDocId, config)
          val json: JsValue = Json.parse(doc.toJson)("metadata")
          json.format(pretty)
        } catch {
          case _: NullPointerException =>
            BadRequest(
              "This search index does not have document filenames saved as stored fields, so the parent document cannot be retrieved."
            )
          case _: Throwable =>
            BadRequest(s"sentenceId '${sentenceId}' not found")
        }
      }
    }
  }

  def getParentDocJsonBySentenceId(
    sentenceId: Int,
    pretty: Option[Boolean]
  ) = Action.async {
    Future {
      ExtractorEngine.usingEngine(config) { engine =>
        try {
          val odinsonDocId = engine.getOdinsonDocId(sentenceId)
          val doc: OdinsonDocument =
            engine.odinsonDoc(odinsonDocId, config)
          val json: JsValue = Json.parse(doc.toJson)
          json.format(pretty)
        } catch {
          case _: NullPointerException =>
            BadRequest(
              "This search index does not have document filenames saved as stored fields, so the parent document cannot be retrieved."
            )
          case _: Throwable =>
            BadRequest(s"sentenceId '${sentenceId}' not found")
        }
      }
    }
  }

}
