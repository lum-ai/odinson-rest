package ai.lum.odinson.rest

import ai.lum.common.ConfigUtils._
import ai.lum.odinson.lucene.OdinResults
import ai.lum.odinson.lucene.search.OdinsonScoreDoc
import ai.lum.odinson.{
  Mention,
  NamedCapture,
  OdinsonMatch,
  Document => OdinsonDocument
}
import ai.lum.odinson.rest.utils.ExtractorEngineUtils
import com.typesafe.config.{ Config, ConfigRenderOptions }
import play.api.http.ContentTypes
import play.api.libs.json._
import play.api.mvc._
import play.api.mvc.Results._

// import scala.concurrent.{ ExecutionContext, Future }
// val wordTokenField       = config.apply[String]("odinson.displayField")
  
package object json {

  import ExtractorEngineUtils._

  /** convenience methods for formatting Play 2 Json */
  implicit class JsonOps(json: JsValue) {

    def format(pretty: Option[Boolean]): Result = pretty match {
      case Some(true) => Ok(Json.prettyPrint(json)).as(ContentTypes.JSON)
      case _          => Ok(json).as(ContentTypes.JSON)
    }

  }

    def mkJson(
    odinsonQuery: String,
    metadataQuery: Option[String],
    duration: Float,
    results: OdinResults,
    enriched: Boolean,
    config: Config
  ): JsValue = {

    val scoreDocs: JsValue = enriched match {
      case true  => Json.arr(results.scoreDocs.map{ sd => mkJsonWithEnrichedResponse(sd, config)}: _*)
      case false => Json.arr(results.scoreDocs.map{sd => mkJson(sd, config)}: _*)
    }

    Json.obj(
      // format: off
      "odinsonQuery" -> odinsonQuery,
      "metadataQuery"  -> metadataQuery,
      "duration"     -> duration,
      "totalHits"    -> results.totalHits,
      "scoreDocs"    -> scoreDocs
      // format: on
    )
  }

  /** Process results from executeGrammar */
  def mkJson(
    metadataQuery: Option[String],
    duration: Float,
    allowTriggerOverlaps: Boolean,
    mentions: Seq[Mention],
    config: Config
  ): JsValue = {

    val mentionsJson: JsValue = Json.arr(mentions.map{ m => mkJson(m, config)}: _*)

    Json.obj(
      // format: off
      "metadataQuery"          -> metadataQuery,
      "duration"             -> duration,
      "allowTriggerOverlaps" -> allowTriggerOverlaps,
      "mentions"             -> mentionsJson
      // format: on
    )
  }

  def mkJson(mention: Mention, config: Config): Json.JsValueWrapper = {

    val wordTokenField       = config.apply[String]("odinson.displayField")

    usingNewEngine(config) { extractorEngine =>
      //val doc: LuceneDocument = extractorEngine.indexSearcher.doc(mention.luceneDocId)
      // We want **all** tokens for the sentence
      val tokens = extractorEngine.dataGatherer.getTokens(mention.luceneDocId, wordTokenField)
      // odinsonMatch: OdinsonMatch,

      Json.obj(
        // format: off
        "sentenceId"    -> mention.luceneDocId,
        // "score"         -> odinsonScoreDoc.score,
        "label"         -> mention.label,
        "documentId"    -> getDocId(mention.luceneDocId, config),
        "sentenceIndex" -> getSentenceIndex(mention.luceneDocId, config),
        "words"         -> JsArray(tokens.map(JsString)),
        "foundBy"       -> mention.foundBy,
        "match"         -> Json.arr(mkJson(mention.odinsonMatch, config))
        // format: on
      )
    }
  }

  def mkJson(odinsonScoreDoc: OdinsonScoreDoc, config: Config): Json.JsValueWrapper = {
    val wordTokenField       = config.apply[String]("odinson.displayField")
    usingNewEngine(config) { extractorEngine =>
      //val doc = extractorEngine.indexSearcher.doc(odinsonScoreDoc.doc)
      // we want **all** tokens for the sentence
      val tokens = extractorEngine.dataGatherer.getTokens(odinsonScoreDoc.doc, wordTokenField)

      Json.obj(
        // format: off
        "sentenceId"    -> odinsonScoreDoc.doc,
        "score"         -> odinsonScoreDoc.score,
        "documentId"    -> getDocId(odinsonScoreDoc.doc, config),
        "sentenceIndex" -> getSentenceIndex(odinsonScoreDoc.doc, config),
        "words"         -> JsArray(tokens.map(JsString)),
        "matches"       -> Json.arr(odinsonScoreDoc.matches.map{ om => mkJson(om, config)}: _*)
        // format: on
      )
    }
  }

  def mkJson(m: OdinsonMatch, config: Config): Json.JsValueWrapper = {
    Json.obj(
      "span" -> Json.obj("start" -> m.start, "end" -> m.end),
      "captures" -> Json.arr(m.namedCaptures.map{ nc => mkJson(nc, config)}: _*)
    )
  }

  def mkJson(namedCapture: NamedCapture, config: Config): Json.JsValueWrapper = {
    Json.obj(namedCapture.name -> mkJson(namedCapture.capturedMatch, config))
  }

  def mkJsonWithEnrichedResponse(odinsonScoreDoc: OdinsonScoreDoc, config: Config): Json.JsValueWrapper = {
    Json.obj(
      // format: off
      "sentenceId"    -> odinsonScoreDoc.doc,
      "score"         -> odinsonScoreDoc.score,
      "documentId"    -> getDocId(odinsonScoreDoc.doc, config),
      "sentenceIndex" -> getSentenceIndex(odinsonScoreDoc.doc, config),
      "sentence"      -> mkAbridgedSentence(odinsonScoreDoc.doc, config),
      "matches"       -> Json.arr(odinsonScoreDoc.matches.map{om => mkJson(om, config)}: _*)
      // format: on
    )
  }

  def mkAbridgedSentence(sentenceId: Int, config: Config): JsValue = {
    val sentenceIndex = getSentenceIndex(sentenceId, config)
    val documentId = getDocId(sentenceId, config)
    val unabridgedJson = retrieveSentenceJson(documentId, sentenceIndex, config)
    unabridgedJson
    //unabridgedJson.as[JsObject] - "startOffsets" - "endOffsets" - "raw"
  }

  def retrieveSentenceJson(documentId: String, sentenceIndex: Int, config: Config): JsValue = {
    val odinsonDoc: OdinsonDocument = loadParentDocByDocumentId(documentId, config)
    val docJson: JsValue = Json.parse(odinsonDoc.toJson)
    (docJson \ "sentences")(sentenceIndex)
  }
}