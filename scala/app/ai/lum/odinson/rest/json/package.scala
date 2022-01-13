package ai.lum.odinson.rest

import ai.lum.common.ConfigUtils._
import ai.lum.odinson.lucene.OdinResults
import ai.lum.odinson.lucene.search.OdinsonScoreDoc
import ai.lum.odinson.{
  Mention,
  NamedCapture,
  OdinsonMatch,
  ExtractorEngine,
  Document => OdinsonDocument
}
import ai.lum.odinson.rest.utils.ExtractorEngineUtils
import com.typesafe.config.Config
import play.api.http.ContentTypes
import play.api.libs.json._
import play.api.mvc._
import play.api.mvc.Results._


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
    config: Config,
    engine: ExtractorEngine
  ): JsValue = {

    val scoreDocs: JsValue = enriched match {
      case true  => Json.arr(results.scoreDocs.map{ sd => mkJsonWithEnrichedResponse(sd, config, engine)}: _*)
      case false => Json.arr(results.scoreDocs.map{sd => mkJson(sd, engine)}: _*)
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
    engine: ExtractorEngine
  ): JsValue = {

    val mentionsJson: JsValue = Json.arr(mentions.map{ m => mkJson(m, engine)}: _*)

    Json.obj(
      // format: off
      "metadataQuery"          -> metadataQuery,
      "duration"             -> duration,
      "allowTriggerOverlaps" -> allowTriggerOverlaps,
      "mentions"             -> mentionsJson
      // format: on
    )
  }

  def mkJson(mention: Mention, engine: ExtractorEngine): Json.JsValueWrapper = {

    val displayField = engine.index.displayField
    //val doc: LuceneDocument = engine.indexSearcher.doc(mention.luceneDocId)
    // We want **all** tokens for the sentence
    val tokens = engine.dataGatherer.getTokens(mention.luceneDocId, displayField)

    Json.obj(
      // format: off
      "sentenceId"    -> mention.luceneDocId,
      // "score"         -> odinsonScoreDoc.score,
      "label"         -> mention.label,
      "documentId"    -> getDocId(mention.luceneDocId, engine),
      "sentenceIndex" -> getSentenceIndex(mention.luceneDocId, engine),
      "words"         -> JsArray(tokens.map(JsString)),
      "foundBy"       -> mention.foundBy,
      "match"         -> Json.arr(mkJson(mention.odinsonMatch, engine))
      // format: on
    )
  }

  def mkJson(odinsonScoreDoc: OdinsonScoreDoc, engine: ExtractorEngine): Json.JsValueWrapper = {
    val displayField = engine.index.displayField
    //val doc = engine.indexSearcher.doc(odinsonScoreDoc.doc)
    // we want **all** tokens for the sentence
    val tokens = engine.dataGatherer.getTokens(odinsonScoreDoc.doc, displayField)

    Json.obj(
      // format: off
      "sentenceId"    -> odinsonScoreDoc.doc,
      "score"         -> odinsonScoreDoc.score,
      "documentId"    -> getDocId(odinsonScoreDoc.doc, engine),
      "sentenceIndex" -> getSentenceIndex(odinsonScoreDoc.doc, engine),
      "words"         -> JsArray(tokens.map(JsString)),
      "matches"       -> Json.arr(odinsonScoreDoc.matches.map{ om => mkJson(om, engine)}: _*)
      // format: on
    )
  }

  def mkJson(m: OdinsonMatch, engine: ExtractorEngine): Json.JsValueWrapper = {
    Json.obj(
      "span" -> Json.obj("start" -> m.start, "end" -> m.end),
      "captures" -> Json.arr(m.namedCaptures.map{ nc => mkJson(nc, engine)}: _*)
    )
  }

  def mkJson(namedCapture: NamedCapture, engine: ExtractorEngine): Json.JsValueWrapper = {
    Json.obj(namedCapture.name -> mkJson(namedCapture.capturedMatch, engine))
  }

  def mkJsonWithEnrichedResponse(odinsonScoreDoc: OdinsonScoreDoc, config: Config, engine: ExtractorEngine): Json.JsValueWrapper = {
    Json.obj(
      // format: off
      "sentenceId"    -> odinsonScoreDoc.doc,
      "score"         -> odinsonScoreDoc.score,
      "documentId"    -> getDocId(odinsonScoreDoc.doc, engine),
      "sentenceIndex" -> getSentenceIndex(odinsonScoreDoc.doc, engine),
      "sentence"      -> mkAbridgedSentence(odinsonScoreDoc.doc, config, engine),
      "matches"       -> Json.arr(odinsonScoreDoc.matches.map{om => mkJson(om, engine)}: _*)
      // format: on
    )
  }

  def mkAbridgedSentence(sentenceId: Int, config: Config, engine: ExtractorEngine): JsValue = {
    val sentenceIndex = getSentenceIndex(sentenceId, engine)
    val documentId = getDocId(sentenceId, engine)
    val unabridgedJson = retrieveSentenceJson(documentId, sentenceIndex, config, engine)
    unabridgedJson
    //unabridgedJson.as[JsObject] - "startOffsets" - "endOffsets" - "raw"
  }

  def retrieveSentenceJson(documentId: String, sentenceIndex: Int, config: Config, engine: ExtractorEngine): JsValue = {
    val odinsonDoc: OdinsonDocument = loadParentDocByDocumentId(documentId, config, engine)
    val docJson: JsValue = Json.parse(odinsonDoc.toJson)
    (docJson \ "sentences")(sentenceIndex)
  }
}
