package ai.lum.odinson.rest

import ai.lum.common.ConfigUtils._
import ai.lum.odinson.lucene.OdinResults
import ai.lum.odinson.lucene.search.OdinsonScoreDoc
import ai.lum.odinson.{
  //Document => OdinsonDocument,
  EventMatch,
  ExtractorEngine,
  Mention,
  NamedCapture,
  NGramMatch,
  OdinsonMatch
}
import ai.lum.odinson.rest.utils.{ExtractorEngineUtils,StartEnd}
import com.typesafe.config.Config
import play.api.http.ContentTypes
import play.api.libs.json._
import play.api.mvc._
import play.api.mvc.Results._
import scala.annotation.tailrec

package object json {
  
  @tailrec
  def getStartEnd(
    m: OdinsonMatch, 
    start: Int, 
    end: Int, 
    remaining: List[NamedCapture]
  ): StartEnd = m match {
    case finished if remaining.isEmpty =>
      StartEnd(
        start = List(start, finished.start).min, 
        end = List(end, finished.end).max
      )
    case _ =>
      val next: OdinsonMatch = remaining.head.capturedMatch
      getStartEnd(
        next,
        start = List(start, next.start).min,
        end = List(end, next.end).max,
        remaining = remaining.tail ::: m.namedCaptures.toList
      )
  }

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

    def getTokens(luceneDocId: Int): Seq[String] = {
      val displayField = engine.index.displayField
      // val doc: LuceneDocument = engine.indexSearcher.doc(mention.luceneDocId)
      // We want **all** tokens for the sentence
      engine.dataGatherer.getTokens(luceneDocId, displayField)
    }
    
    def mkJsonForMention(mention: Mention): Json.JsValueWrapper = {
      // We want **all** tokens for the sentence
      val tokens = getTokens(mention.luceneDocId)
      //println(s"""(${mention.start} - ${mention.end} w/ label ${mention.label.getOrElse("???")}): ${tokens.slice(mention.start, mention.end).mkString(" ")}""")
      mention.odinsonMatch match {
          case em: EventMatch =>
            Json.obj(
              // format: off
              "sentenceId"    -> mention.luceneDocId,
              // "score"         -> odinsonScoreDoc.score,
              "label"         -> mention.label,
              "documentId"    -> getOdinsonDocId(mention.luceneDocId),
              "sentenceIndex" -> getSentenceIndex(mention.luceneDocId),
              "words"         -> JsArray(tokens.map(JsString)),
              "foundBy"       -> mention.foundBy,
              "trigger"       -> Json.obj(
                "start" -> em.trigger.start,
                "end" -> em.trigger.end
              ),
              "match"         -> mkJsonForMatch(m=em, luceneDocId=mention.luceneDocId)
              // format: on
            )
          case om =>
            Json.obj(
              // format: off
              "sentenceId"    -> mention.luceneDocId,
              // "score"         -> odinsonScoreDoc.score,
              "label"         -> mention.label,
              "documentId"    -> getOdinsonDocId(mention.luceneDocId),
              "sentenceIndex" -> getSentenceIndex(mention.luceneDocId),
              "words"         -> JsArray(tokens.map(JsString)),
              "foundBy"       -> mention.foundBy,
              "match"         -> mkJsonForMatch(m=om, luceneDocId=mention.luceneDocId)
              // format: on
            )
      }

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
        "matches"       -> Json.arr(odinsonScoreDoc.matches.map{ m => mkJsonForMatch(m=m, luceneDocId=odinsonScoreDoc.doc)}:_*)
        //"matches"       -> Json.arr(odinsonScoreDoc.matches.map(mkJsonForMatch): _*)
        // format: on
      )
    }

    def mkJsonForMatch(m: OdinsonMatch, luceneDocId: Int): Json.JsValueWrapper = {
      val se: StartEnd = getStartEnd(
        m = m, 
        start = m.start, 
        end = m.end, 
        remaining = m.namedCaptures.toList
      )
      val tokens = getTokens(luceneDocId)
      val text = tokens.slice(se.start, se.end).mkString(" ")
      m match {
      case em: EventMatch =>
        Json.obj(
          "start" -> se.start,
          "end" -> se.end,
          "text" -> text,
          "trigger" -> Json.obj(
            "start" -> em.trigger.start,
            "end" -> em.trigger.end,
            "text" -> tokens.slice(em.trigger.start, em.trigger.end).mkString(" "),
          ),
          "namedCaptures" -> {
              em.namedCaptures match {
              case nothing if nothing.size == 0 => JsNull
              case captures => 
                Json.arr(captures.map{c => mkJsonForNamedCapture(c, luceneDocId)}:_*)
            }
          }
          // ignore argumentMetadata
        )
      case _: NGramMatch =>
        Json.obj(
          "start" -> se.start,
          "end" -> se.end,
          "text" -> text,
          // avoid including empty namedCaptures
        )
      case other@_ =>
        m.namedCaptures match {
          case nothing if nothing.size == 0 =>
            Json.obj(
              "start" -> se.start,
              "end" -> se.end,
              "text" -> text
            )
          case captures => 
            Json.obj(
              "start" -> se.start,
              "end" -> se.end,
              "text" -> text,
              "trigger" -> Json.obj(
                "start" -> m.start,
                "end" -> m.end,
                "text" -> tokens.slice(m.start, m.end).mkString(" ")
              ),
              "namedCaptures" -> Json.arr(captures.map{nc => mkJsonForNamedCapture(nc, luceneDocId)}:_*)
            )
        }
      }
    }

    def mkJsonForNamedCapture(namedCapture: NamedCapture, luceneDocId: Int): Json.JsValueWrapper = {
      //val tokens = getTokens(luceneDocId)
      Json.obj(
        "name" -> namedCapture.name,
        "label" -> namedCapture.label,
        //"text" -> tokens.slice(namedCapture.start, namedCapture.end).mkString(" "),
        //"capturedMatch" 
        "match" -> mkJsonForMatch(namedCapture.capturedMatch, luceneDocId)
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
        "matches"       -> Json.arr(odinsonScoreDoc.matches.map{m => mkJsonForMatch(m=m, luceneDocId=odinsonScoreDoc.doc)}:_*)
        //matches.map{mkJsonForMatch): _*)  
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
