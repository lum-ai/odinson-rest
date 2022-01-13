package ai.lum.odinson.rest.utils

import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.odinson.{
Document => OdinsonDocument,
StringField
}
import com.typesafe.config.Config
import java.io.File
//import org.apache.lucene.document.{ Document => LuceneDocument }
import ai.lum.odinson.lucene.index.OdinsonIndex

object OdinsonIndexUtils {
  def addFileNameMetadata(config: Config, doc: OdinsonDocument): OdinsonDocument = {
    // use config for name field
    val fieldName = config.apply[String]("odinson.index.parentDocFieldFileName")
    // For greater portability, don't include the docs dir as part of the file name.
    // FIXME: any better ways to name?
    val file = new File(s"${doc.toJson.hashCode()}.json")
    val filenameField = StringField(name = fieldName, string = file.getName())
    doc.copy(metadata = doc.metadata ++ Seq(filenameField))
  }

  def writeDoc(config: Config, doc: OdinsonDocument): Unit = {
    val docsDir    = config.apply[File]("odinson.docsDir")
    val fieldName = config.apply[String]("odinson.index.parentDocFieldFileName")
    doc.metadata.find(f => f.name == fieldName) match {
      case Some(sf: StringField) =>
        val f = new File(docsDir, sf.string)
        f.writeString(doc.toJson)
      case _ => ()
    }
  }

  def indexDoc(config: Config, doc: OdinsonDocument, save: Boolean = true): Boolean = {
    val index = OdinsonIndex.fromConfig(config)
    try {
      val od = addFileNameMetadata(config, doc)
      index.indexOdinsonDoc(od)
      // save json file to docs dir
      if (save) { writeDoc(config, od) }
      true
    } catch {
      case error : Throwable =>
        println(s"indexDoc failed:\t${error.getMessage}")
        false
    } finally {
      index.close()
    }
  }

  def indexDocs(config: Config, save: Boolean = false): Unit = {
    val docsDir              = config.apply[File]  ("odinson.docsDir")
    val odinsonDocsWildcards = Seq("*.json", "*.json.gz")

    docsDir.listFilesByWildcards(odinsonDocsWildcards, recursive = true).foreach{ f =>
      val od = OdinsonDocument.fromJson(f)
      indexDoc(config, od, save)
    }
  }

  // FIXME: get DocId

  // FIXME: strip field before returning doc JSON?

}
