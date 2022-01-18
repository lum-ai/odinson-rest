package ai.lum.odinson.rest.utils

import ai.lum.common.ConfigUtils._
import ai.lum.common.TryWithResources.using
import ai.lum.odinson.{ Document => OdinsonDocument, ExtractorEngine }
import ai.lum.odinson.lucene.index.OdinsonIndexWriter
import org.apache.lucene.document.{ Document => LuceneDocument }
import com.typesafe.config.Config
import java.io.File

object ExtractorEngineUtils {

  def getDocId(luceneDocId: Int, engine: ExtractorEngine): String = {
    val doc: LuceneDocument = engine.doc(luceneDocId)
    // FIXME: getOrElse and return Option[String]
    doc.getValues(OdinsonIndexWriter.DOC_ID_FIELD).head
  }

  def getSentenceIndex(luceneDocId: Int, engine: ExtractorEngine): Int = {
    val doc = engine.doc(luceneDocId)
    // FIXME: this isn't safe
    // FIXME: getOrElse and Option[Int]
    doc.getValues(OdinsonIndexWriter.SENT_ID_FIELD).head.toInt
  }

  def loadParentDocByDocumentId(
    documentId: String,
    config: Config,
    engine: ExtractorEngine
  ): OdinsonDocument = {
    val docsDir = config.apply[File]("odinson.docsDir")
    val parentDocFileName = config.apply[String]("odinson.index.parentDocFieldFileName")
    // lucene doc containing metadata
    val parentDoc: LuceneDocument = engine.getMetadataDoc(documentId)

    val odinsonDocFile = new File(docsDir, parentDoc.getField(parentDocFileName).stringValue)
    OdinsonDocument.fromJson(odinsonDocFile)
  }

}
