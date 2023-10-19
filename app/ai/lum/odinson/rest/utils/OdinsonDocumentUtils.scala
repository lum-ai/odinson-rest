package ai.lum.odinson.rest.utils

import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.odinson.{ Document => OdinsonDocument, StringField => OdinsonStringField }
import ai.lum.odinson.utils.exceptions.OdinsonException
import com.typesafe.config.Config
import java.io.File

object OdinsonDocumentUtils {

  implicit class OdinsonDocumentOps(doc: OdinsonDocument) {

    def addFileNameMetadata(config: Config): OdinsonDocument = {
      // use config for name field
      val fieldName = config.apply[String]("odinson.index.parentDocFieldFileName")
      // For greater portability, don't include the docs dir as part of the file name.
      // FIXME: any better ways to name?
      val file = new File(s"${doc.toJson.hashCode()}.json")
      val filenameField = OdinsonStringField(name = fieldName, string = file.getName())
      doc.copy(metadata = doc.metadata ++ Seq(filenameField))
    }

    def writeDoc(config: Config): Unit = {
      val docsDir = config.apply[File]("odinson.docsDir")
      val fieldName = config.apply[String]("odinson.index.parentDocFieldFileName")
      doc.metadata.find(f => f.name == fieldName) match {
        case Some(sf: OdinsonStringField) =>
          val f = new File(docsDir, sf.string)
          f.writeString(doc.toJson)
        case _ => ()
      }
    }

    def deleteDoc(config: Config): Unit = {
      val docsDir = config.apply[File]("odinson.docsDir")
      val fieldName = config.apply[String]("odinson.index.parentDocFieldFileName")
      doc.metadata.find(f => f.name == fieldName) match {
        case Some(sf: OdinsonStringField) =>
          val f = new File(docsDir, sf.string)
          f.delete()
        case None =>
          throw OdinsonException(s"No parentDocFieldFileName found for ${doc.id}")
      }
    }

  }

}
