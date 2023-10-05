package ai.lum.odinson.rest

import ai.lum.common.ConfigUtils._
import ai.lum.odinson.lucene.OdinResults
import ai.lum.odinson.lucene.search.OdinsonScoreDoc
import ai.lum.odinson.{
  Document => OdinsonDocument,
  EventMatch,
  ExtractorEngine,
  Mention,
  NamedCapture,
  NGramMatch,
  OdinsonMatch
}
import ai.lum.odinson.rest.utils.ExtractorEngineUtils
import com.typesafe.config.Config
import play.api.http.ContentTypes
import play.api.libs.json._
import play.api.mvc._
import play.api.mvc.Results._

package object json {

  /** convenience methods for formatting Play 2 Json */
  implicit class JsonOps(json: JsValue) {

    def format(pretty: Option[Boolean]): Result = pretty match {
      case Some(true) => Ok(Json.prettyPrint(json)).as(ContentTypes.JSON)
      case _          => Ok(json).as(ContentTypes.JSON)
    }

  }

  implicit class EngineJsonOps(engine: ExtractorEngine)
      extends ExtractorEngineUtils.EngineOps(engine) {

    def mkJson(
      odinsonQuery: String,
      metadataQuery: Option[String],
      duration: Float,
      results: OdinResults,
      enriched: Boolean,
      config: Config
    ): JsValue = {

      val scoreDocs: JsValue = enriched match {
        case true =>
          Json.arr(results.scoreDocs.map { sd => mkJsonWithEnrichedResponse(sd, config) }: _*)
        case false => Json.arr(results.scoreDocs.map(mkJsonForScoreDoc): _*)
      }

      Json.obj(
        // format: off
        "odinsonQuery"  -> odinsonQuery,
        "metadataQuery" -> metadataQuery,
        "duration"      -> duration,
        "totalHits"     -> results.totalHits,
        "scoreDocs"     -> scoreDocs
        // format: on
      )
    }

    /** Process results from executeGrammar */
    def mkMentionsJson(
      metadataQuery: Option[String],
      duration: Float,
      allowTriggerOverlaps: Boolean,
      mentions: Seq[Mention]
    ): JsValue = {

      val mentionsJson: JsValue = Json.arr(mentions.map(mkJsonForMention): _*)

      Json.obj(
        // format: off
        "metadataQuery"        -> metadataQuery,
        "duration"             -> duration,
        "allowTriggerOverlaps" -> allowTriggerOverlaps,
        "mentions"             -> mentionsJson
        // format: on
      )
    }

    def mkJsonForMention(mention: Mention): Json.JsValueWrapper = {

      val displayField = engine.index.displayField
      // val doc: LuceneDocument = engine.indexSearcher.doc(mention.luceneDocId)
      // We want **all** tokens for the sentence
      val tokens = engine.dataGatherer.getTokens(mention.luceneDocId, displayField)

      Json.obj(
        // format: off
        "sentenceId"    -> mention.luceneDocId,
        // "score"         -> odinsonScoreDoc.score,
        "label"         -> mention.label,
        "documentId"    -> getOdinsonDocId(mention.luceneDocId),
        "sentenceIndex" -> getSentenceIndex(mention.luceneDocId),
        "words"         -> JsArray(tokens.map(JsString)),
        "foundBy"       -> mention.foundBy,
        "match"         -> Json.arr(mkJsonForMatch(mention.odinsonMatch))
        // format: on
      )
    }

    def mkJsonForScoreDoc(odinsonScoreDoc: OdinsonScoreDoc): Json.JsValueWrapper = {
      val displayField = engine.index.displayField
      // val doc = engine.indexSearcher.doc(odinsonScoreDoc.doc)
      // we want **all** tokens for the sentence
      val tokens = engine.dataGatherer.getTokens(odinsonScoreDoc.doc, displayField)

      Json.obj(
        // format: off
        "sentenceId"    -> odinsonScoreDoc.doc,
        "score"         -> odinsonScoreDoc.score,
        "documentId"    -> getOdinsonDocId(odinsonScoreDoc.doc),
        "sentenceIndex" -> getSentenceIndex(odinsonScoreDoc.doc),
        "words"         -> JsArray(tokens.map(JsString)),
        "matches"       -> Json.arr(odinsonScoreDoc.matches.map(mkJsonForMatch): _*)
        // format: on
      )
    }

    def mkJsonForMatch(m: OdinsonMatch): Json.JsValueWrapper = m match {
      case em: EventMatch =>
        Json.obj(
          "start" -> em.trigger.start,
          "end" -> em.trigger.end,
          // FIXME: should we simplify this?
          "trigger" -> mkJsonForMatch(em),
          "namedCaptures" -> Json.arr(em.namedCaptures.map(mkJsonForNamedCapture): _*)
          // ignore argumentMetadata
        )
      case ngram: NGramMatch =>
        Json.obj(
          "start" -> ngram.start,
          "end" -> ngram.end
          // avoid including empty namedCaptures
        )
      case other =>
        Json.obj(
          "start" -> m.start,
          "end" -> m.end,
          "namedCaptures" -> Json.arr(m.namedCaptures.map(mkJsonForNamedCapture): _*)
        )
    }

    def mkJsonForNamedCapture(namedCapture: NamedCapture): Json.JsValueWrapper = {
      Json.obj(
        "name" -> namedCapture.name,
        "label" -> namedCapture.label,
        "capturedMatch" -> mkJsonForMatch(namedCapture.capturedMatch)
      )
    }

    def mkJsonWithEnrichedResponse(
      odinsonScoreDoc: OdinsonScoreDoc,
      config: Config
    ): Json.JsValueWrapper = {
      Json.obj(
        // format: off
        "sentenceId"    -> odinsonScoreDoc.doc,
        "score"         -> odinsonScoreDoc.score,
        "documentId"    -> getOdinsonDocId(odinsonScoreDoc.doc),
        "sentenceIndex" -> getSentenceIndex(odinsonScoreDoc.doc),
        "sentence"      -> mkUnabridgedSentenceJson(odinsonScoreDoc.doc, config),
        "matches"       -> Json.arr(odinsonScoreDoc.matches.map(mkJsonForMatch): _*)
        // format: on
      )
    }

    def mkUnabridgedSentenceJson(
      sentenceLuceneDocId: Int,
      config: Config
    ): JsValue = {
      val sentenceIndex = getSentenceIndex(sentenceLuceneDocId)
      val odinsonDocId = getOdinsonDocId(sentenceLuceneDocId)
      val sentence = getSentence(odinsonDocId, sentenceIndex, config)
      Json.parse(sentence.toJson)
    }

    def mkAbridgedSentenceJson(sentenceLuceneDocId: Int, config: Config): JsValue = {
      val unabridgedJson = mkUnabridgedSentenceJson(sentenceLuceneDocId, config)
      unabridgedJson.as[JsObject] - "startOffsets" - "endOffsets" - "raw"
    }

  }

}
