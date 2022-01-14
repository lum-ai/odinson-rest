package ai.lum.odinson.index

import ai.lum.common.ConfigUtils._
import ai.lum.common.FileUtils._
import ai.lum.odinson.digraph.Vocabulary
import ai.lum.odinson.{
Document => OdinsonDocument,
OdinsonIndexWriter,
StringField
}
import ai.lum.odinson.lucene.index.IncrementalOdinsonIndex
import ai.lum.odinson.utils.exceptions.OdinsonException
import ai.lum.odinson.utils.IndexSettings

import com.typesafe.config.Config
import java.io.File
import java.nio.file.Paths
import org.apache.lucene.document.{ Document => LuceneDocument }
import org.apache.lucene.index.Term
import org.apache.lucene.search.{
  TermQuery,
  BooleanClause => LuceneBooleanClause,
  BooleanQuery => LuceneBooleanQuery
}
import org.apache.lucene.store.{ Directory, FSDirectory, RAMDirectory }


case class CustomOdinsonIndex(
  override val directory: Directory,
  override val settings: IndexSettings,
  override val computeTotalHits: Boolean,
  override val displayField: String,
  override val normalizedTokenField: String,
  override val addToNormalizedField: Set[String],
  override val incomingTokenField: String,
  override val outgoingTokenField: String,
  override val maxNumberOfTokensPerSentence: Int,
  override val invalidCharacterReplacement: String,
  override val refreshMs: Int = -1
) extends IncrementalOdinsonIndex(
  directory: Directory,
  settings: IndexSettings,
  computeTotalHits: Boolean,
  displayField: String,
  normalizedTokenField: String,
  addToNormalizedField: Set[String],
  incomingTokenField: String,
  outgoingTokenField: String,
  maxNumberOfTokensPerSentence: Int,
  invalidCharacterReplacement: String
) {

}

object CustomOdinsonIndex {
  def fromConfig(config: Config): CustomOdinsonIndex = {

    val indexDir = config.apply[String]("odinson.indexDir")
    val (directory, vocabulary) = indexDir match {
      case ":memory:" =>
        // memory index is supported in the configuration file
        val dir = new RAMDirectory
        val vocab = Vocabulary.empty
        (dir, vocab)
      case path =>
        val dir = FSDirectory.open(Paths.get(path))
        val vocab = Vocabulary.fromDirectory(dir)
        (dir, vocab)
    }

    val storedFields = config.apply[List[String]]("odinson.index.storedFields")
    val displayField = config.apply[String]("odinson.displayField")
    // Always store the display field, also store these additional fields
    if (!storedFields.contains(displayField)) {
      throw new OdinsonException("`odinson.index.storedFields` must contain `odinson.displayField`")
    }

    val computeTotalHits = config.apply[Boolean]("odinson.computeTotalHits")

    val settings = IndexSettings(storedFields)
    val normalizedTokenField = config.apply[String]("odinson.index.normalizedTokenField")
    val addToNormalizedField =
      config.apply[List[String]]("odinson.index.addToNormalizedField").toSet
    val incomingTokenField = config.apply[String]("odinson.index.incomingTokenField")
    val outgoingTokenField = config.apply[String]("odinson.index.outgoingTokenField")
    val maxNumberOfTokensPerSentence =
      config.apply[Int]("odinson.index.maxNumberOfTokensPerSentence")
    val invalidCharacterReplacement =
      config.apply[String]("odinson.index.invalidCharacterReplacement")
    val refreshMs = {
      if (config.apply[Boolean]("odinson.index.incremental"))
        config.apply[Int]("odinson.index.refreshMs")
      else -1
    }

    CustomOdinsonIndex(
      directory,
      settings,
      computeTotalHits,
      displayField,
      normalizedTokenField,
      addToNormalizedField,
      incomingTokenField,
      outgoingTokenField,
      maxNumberOfTokensPerSentence,
      invalidCharacterReplacement,
      refreshMs
    )

  }

  def addFileNameMetadata(config: Config, doc: OdinsonDocument): OdinsonDocument = {
    // use config for name field
    val fieldName = config.apply[String]("odinson.index.parentDocFieldFileName")
    // For greater portability, don't include the docs dir as part of the file name.
    // FIXME: any better ways to name?
    val file = new File(s"${doc.toJson.hashCode()}.json")
    val filenameField = StringField(name = fieldName, string = file.getName())
    doc.copy(metadata = doc.metadata ++ Seq(filenameField))
  }

  def deleteDoc(config: Config, documentId: String): Unit = {
    // see https://lucene.apache.org/core/6_6_6/core/org/apache/lucene/index/IndexWriter.html#deleteDocuments-org.apache.lucene.index.Term...-
    // 1. build term or query from documentId
    // 2. writer.deleteDocuments()
    // 3. writer.commit()
    // 4. writer.close()
    // 5. try to delete JSON file as well
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

  def indexOdinsonDoc(config: Config, doc: OdinsonDocument, save: Boolean = true): Boolean = {
    val index = CustomOdinsonIndex.fromConfig(config)
    try {
      val od = addFileNameMetadata(config, doc)
      index.indexOdinsonDoc(od)
      // save json file to docs dir
      if (save) { writeDoc(config, od) }
      true
    } catch {
      case error : Throwable =>
        println(s"indexOdinsonDoc failed:\t${error.getMessage}")
        false
    } finally {
      index.close()
    }
  }

  def indexOdinsonDocs(config: Config, save: Boolean = false): Unit = {
    val docsDir              = config.apply[File]  ("odinson.docsDir")
    val odinsonDocsWildcards = Seq("*.json", "*.json.gz")

    docsDir.listFilesByWildcards(odinsonDocsWildcards, recursive = true).foreach{ f =>
      val od = OdinsonDocument.fromJson(f)
      indexOdinsonDoc(config, od, save)
    }
  }

  // FIXME: this seems to just return the doc ID for the metadata?
  /** Retrieves the Lucene doc ID for all sentences belonging to an OdinsonDocument ID */
  def childrenForOdinsonDocumentId(config: Config, documentId: String): Seq[Int] = {
    val index = CustomOdinsonIndex.fromConfig(config)
    try {
      val queryBuilder = new LuceneBooleanQuery.Builder()

      queryBuilder.add(
        new LuceneBooleanClause(
          new TermQuery(new Term(OdinsonIndexWriter.DOC_ID_FIELD, documentId)),
          LuceneBooleanClause.Occur.MUST
        )
      )
      val query = queryBuilder.build()
      index.search(query).scoreDocs.map(sd => sd.doc)
    } catch {
      case error : Throwable =>
        println(s"childrenForOdinsonDocumentId(${documentId}) failed:\t${error.getMessage}")
        Nil
    } finally {
      index.close()
    }
  }

}
