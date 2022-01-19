package ai.lum.odinson.rest.utils

import ai.lum.common.ConfigUtils._
import ai.lum.odinson.{ Document => OdinsonDocument, ExtractorEngine, Sentence => OdinsonSentence }
import ai.lum.odinson.utils.exceptions.OdinsonException
import ai.lum.odinson.lucene.index.OdinsonIndexWriter
import org.apache.lucene.document.{ Document => LuceneDocument }
import com.typesafe.config.Config
import java.io.File

object ExtractorEngineUtils {

  /** Additional convenience methods for an [[ai.lum.odinson.ExtractorEngine]].
    */
  implicit class EngineOps(engine: ExtractorEngine) {

    def getOdinsonDocId(luceneDocId: Int): String = {
      val doc: LuceneDocument = engine.doc(luceneDocId)
      try {
        doc.getValues(OdinsonIndexWriter.DOC_ID_FIELD).head
      } catch {
        case e: Throwable => throw OdinsonException(
            s"Lucene document for sentence did not have stored field '${OdinsonIndexWriter.DOC_ID_FIELD}'"
          )
      }

    }

    def getSentenceIndex(luceneDocId: Int): Int =
      try {
        val doc = engine.doc(luceneDocId)
        doc.getValues(OdinsonIndexWriter.SENT_ID_FIELD).head.toInt
      } catch {
        case e: Throwable => throw OdinsonException(
            s"Lucene doc ${luceneDocId} has no field '{OdinsonIndexWriter.SENT_ID_FIELD}'"
          )
      }

    def getDocJsonFile(odinsonDocId: String, config: Config): File = {
      val docsDir = config.apply[File]("odinson.docsDir")
      val parentDocFileName = config.apply[String]("odinson.index.parentDocFieldFileName")
      try {
        // lucene doc containing metadata
        val parentDoc: LuceneDocument = engine.getMetadataDoc(odinsonDocId)
        val fname = parentDoc.getField(parentDocFileName).stringValue
        new File(docsDir, fname)
      } catch {
        case e: Throwable =>
          throw OdinsonException(
            s"'${parentDocFileName}' field missing from Odinson Document Metadata for ${odinsonDocId}"
          )
      }
    }

    def odinsonDoc(odinsonDocId: String, config: Config): OdinsonDocument = {
      val f = getDocJsonFile(odinsonDocId, config)
      OdinsonDocument.fromJson(f)
    }

    def getSentence(
      odinsonDocId: String,
      sentenceIndex: Int,
      config: Config
    ): OdinsonSentence = {
      val doc = odinsonDoc(odinsonDocId, config)
      try {
        doc.sentences(sentenceIndex)
      } catch {
        case _: Throwable =>
          throw OdinsonException(
            s"sentence index '${sentenceIndex}' out of range for doc '${odinsonDocId}'"
          )
      }
    }

  }

}
