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

  // calls close() at the end
  // see https://github.com/lum-ai/common/blob/b7c0b70c460790088d655a98be178cbef9767a24/src/main/scala/ai/lum/common/TryWithResources.scala
  def usingNewEngine[T](config: Config)(f: ExtractorEngine => T): T = using(newEngine(config))(f)

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

  def loadParentDocByDocumentId(documentId: String, config: Config, engine: ExtractorEngine): OdinsonDocument = {
    val docsDir              = config.apply[File]  ("odinson.docsDir")
    val parentDocFileName    = config.apply[String]("odinson.index.parentDocFieldFileName")
    // lucene doc containing metadata
    val parentDoc: LuceneDocument = engine.getMetadataDoc(documentId)

    val odinsonDocFile = new File(docsDir, parentDoc.getField(parentDocFileName).stringValue)
    println(s"odinsonDocFile:\t${odinsonDocFile.getAbsolutePath()}")
    OdinsonDocument.fromJson(odinsonDocFile)
  }
}
