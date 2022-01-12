package ai.lum.odinson.rest.utils

import ai.lum.common.ConfigUtils._
import ai.lum.common.TryWithResources.using
import ai.lum.odinson.{ 
Document => OdinsonDocument,
ExtractorEngine, OdinsonIndexWriter
}
import com.typesafe.config.Config
import java.io.File
import org.apache.lucene.document.{ Document => LuceneDocument }

object ExtractorEngineUtils {

  def newEngine(config: Config): ExtractorEngine = ExtractorEngine.fromConfig(config)

  def usingNewEngine[T](config: Config)(f: ExtractorEngine => T): T = using(newEngine(config))(f)

  def getDocId(luceneDocId: Int, config: Config): String = {
    usingNewEngine(config) { extractorEngine =>
      val doc: LuceneDocument = extractorEngine.doc(luceneDocId)
      // FIXME: getOrElse and return Option[String]
      doc.getValues(OdinsonIndexWriter.DOC_ID_FIELD).head
    }
  }

  def getSentenceIndex(luceneDocId: Int, config: Config): Int = {
    usingNewEngine(config) { extractorEngine =>
      val doc = extractorEngine.doc(luceneDocId)
      // FIXME: this isn't safe
      // FIXME: getOrElse and Option[Int]
      doc.getValues(OdinsonIndexWriter.SENT_ID_FIELD).head.toInt
    }
  }

  def loadParentDocByDocumentId(documentId: String, config: Config): OdinsonDocument = {
    val docsDir              = config.apply[File]  ("odinson.docsDir")
    val parentDocFileName    = config.apply[String]("odinson.index.parentDocFieldFileName")

    usingNewEngine(config) { extractorEngine =>
      // lucene doc containing metadata
      val parentDoc: LuceneDocument = extractorEngine.getMetadataDoc(documentId)

      val odinsonDocFile = new File(docsDir, parentDoc.getField(parentDocFileName).stringValue)
      OdinsonDocument.fromJson(odinsonDocFile)
    }
  }
}