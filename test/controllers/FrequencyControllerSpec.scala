
package controllers

import java.io.{ File, IOException }
import java.nio.file.Files
import ai.lum.odinson.extra.IndexDocuments
import ai.lum.odinson.utils.exceptions.OdinsonException
import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }
import org.scalatestplus.play.guice._
import play.api.test.Helpers._
import org.apache.commons.io.FileUtils
import org.scalatest.TestData
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import org.scalatestplus.play._
import play.api.cache.AsyncCacheApi
import play.api.test._

import scala.reflect.io.Directory

class FrequencyControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {
  // for testing `term-freq` endpoint
  case class SingletonRow(term: String, frequency: Double)
  type SingletonRows = Seq[SingletonRow]
  implicit val singletonRowFormat: Format[SingletonRow] = Json.format[SingletonRow]
  implicit val readSingletonRows: Reads[Seq[SingletonRow]] = Reads.seq(singletonRowFormat)

  // for testing `term-freq` endpoint
  case class GroupedRow(term: String, group: String, frequency: Double)
  type GroupedRows = Seq[GroupedRow]
  implicit val groupedRowFormat: Format[GroupedRow] = Json.format[GroupedRow]
  implicit val readGroupedRows: Reads[Seq[GroupedRow]] = Reads.seq(groupedRowFormat)

  val defaultConfig: Config = ConfigFactory.load()

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val tmpFolder: File = Files.createTempDirectory("odinson-test").toFile
  val srcDir: File = new File(getClass.getResource("/").getFile)

  try {
    FileUtils.copyDirectory(srcDir, tmpFolder)
  } catch {
    case e: IOException =>
      throw new OdinsonException(s"Can't copy resources directory ${srcDir}")
  }

  val dataDir = tmpFolder.getAbsolutePath
  val indexDir = new File(tmpFolder, "index")
  val docsDir = new File(tmpFolder, "docs").getAbsolutePath

  val testConfig: Config = {
    defaultConfig
      .withValue("odinson.dataDir", ConfigValueFactory.fromAnyRef(dataDir))
      // re-compute the index and docs path's
      .withValue(
        "odinson.indexDir",
        ConfigValueFactory.fromAnyRef(indexDir.getAbsolutePath)
      )
      .withValue(
        "odinson.docsDir",
        ConfigValueFactory.fromAnyRef(docsDir)
      )

  }

  def hasResults(resp: JsValue): Boolean = (resp \ "scoreDocs") match {
    // scoreDocs exists, but what is its type?
    case JsDefined(jsval) => jsval match {
        // if our query matched, we should have a non-empty array here
        case JsArray(array) => array.nonEmpty
        case _              => false
      }
    // scoreDocs not found! :(
    case _ => false
  }

  def noResults(resp: JsValue): Boolean = hasResults(resp) == false

  def deleteIndex = {
    val dir = new Directory(indexDir)
    dir.deleteRecursively()
  }

  deleteIndex
  // create index
  // FIXME: replace this
  IndexDocuments.main(Array(tmpFolder.getAbsolutePath))

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .configure(
      Map(
        "odinson.dataDir" -> ConfigValueFactory.fromAnyRef(dataDir),
        "odinson.indexDir" -> ConfigValueFactory.fromAnyRef(indexDir.getAbsolutePath),
        "odinson.docsDir" -> ConfigValueFactory.fromAnyRef(docsDir)
      )
    )
    .build()

  implicit override def newAppForTest(testData: TestData): Application = fakeApplication()

  val fakeApp: Application = fakeApplication()

  val controller =
    new FrequencyController(
      testConfig,
      fakeApp.configuration,
      fakeApp.injector.instanceOf[AsyncCacheApi],
      cc = Helpers.stubControllerComponents()
    )

  "FrequencyController" should {

    "respond with token-based frequencies using the /term-freq endpoint" in {
      val response = route(app, FakeRequest(GET, "/api/term-freq?field=word")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")

      Helpers.contentAsString(response) must include("term")
      Helpers.contentAsString(response) must include("frequency")

      val json = Helpers.contentAsJson(response)

      // check that the response is valid in form
      val rowsResult = json.validate(readSingletonRows)
      rowsResult.isSuccess must be(true)

      // check that 10 results are returned by default
      val rows: Seq[SingletonRow] = rowsResult match {
        case r: JsResult[SingletonRows] => r.get
        case _                          => Nil
      }

      rows must have length (10)

      // check for ordering (greatest to least)
      val freqs = rows.map(_.frequency)
      freqs.zip(freqs.tail).foreach { case (freq1, freq2) =>
        freq1 must be >= freq2
      }
    }

    "respond with grouped token-based frequencies using the /term-freq endpoint" in {
      val response =
        route(app, FakeRequest(GET, "/api/term-freq?field=tag&group=raw&min=0&max=4")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")

      Helpers.contentAsString(response) must include("term")
      Helpers.contentAsString(response) must include("group")
      Helpers.contentAsString(response) must include("frequency")

      val json = Helpers.contentAsJson(response)

      // check that the response is of a valid form
      val rowsResult = json.validate(readGroupedRows)
      rowsResult.isSuccess must be(true)

      // check that we have the right number of results
      val rows: Seq[GroupedRow] = rowsResult match {
        case r: JsResult[GroupedRows] => r.get
        case _                        => Nil
      }
      // important to save ordering
      val termOrder = rows.map(_.term).distinct.zipWithIndex.toMap
      val rowsForTerm = rows.groupBy(_.term).toSeq.sortBy { case (term, frequencies) =>
        termOrder(term)
      }
      rowsForTerm must have length (5)

      // check for ordering among `term`s (greatest to least)
      val termFreqs = rowsForTerm.map { case (term, rows) => rows.map(_.frequency).sum }
      termFreqs.zip(termFreqs.tail).foreach { case (freq1, freq2) =>
        freq1 must be >= freq2
      }
    }

    "respond to order and reverse variables in /term-freq endpoint" in {
      val response =
        route(app, FakeRequest(GET, "/api/term-freq?field=word&order=alpha&reverse=true")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      // println(Helpers.contentAsString(response))
      Helpers.contentAsString(response) must include("term")
      Helpers.contentAsString(response) must include("frequency")

      val json = Helpers.contentAsJson(response)

      // check that the response is valid in form
      val rowsResult = json.validate(readSingletonRows)
      rowsResult.isSuccess must be(true)

      // check that 10 results are returned by default
      val rows: Seq[SingletonRow] = rowsResult match {
        case r: JsResult[SingletonRows] => r.get
        case _                          => Nil
      }

      // check for ordering (reverse Unicode sort)
      val terms = rows.map(_.term)
      terms.zip(terms.tail).foreach { case (term1, term2) =>
        // no overlap because terms must be distinct
        term1 must be > term2
      }
    }

    "filter terms in /term-freq endpoint" in {
      // filter: `th.*`
      val response = route(app, FakeRequest(GET, "/api/term-freq?field=lemma&filter=th.*")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      // println(Helpers.contentAsString(response))
      Helpers.contentAsString(response) must include("term")
      Helpers.contentAsString(response) must include("frequency")

      val json = Helpers.contentAsJson(response)

      // check that the response is valid in form
      val rowsResult = json.validate(readSingletonRows)
      rowsResult.isSuccess must be(true)

      val rows: Seq[SingletonRow] = rowsResult match {
        case r: JsResult[SingletonRows] => r.get
        case _                          => Nil
      }

      // regex is `^t` and is meant to be unanchored (on the right) and case sensitive
      // check that all terms begin with lowercase t, and that there's at least one term that isn't
      // just `t`
      val terms = rows.map(_.term)
      terms.foreach { term =>
        term.startsWith("th") mustBe (true)
      }
      terms.exists { term =>
        term.length > 1
      } mustBe (true)
    }

    val expandedRules =
      s"""
         |rules:
         | - name: "agent-active"
         |   label: Agent
         |   type: event
         |   pattern: |
         |       trigger = [tag=/V.*/]
         |       agent  = >nsubj []
         |
         | - name: "patient-active"
         |   label: Patient
         |   type: event
         |   pattern: |
         |       trigger = [tag=/V.*/]
         |       patient  = >dobj []
         |
         | - name: "agent-passive"
         |   label: Agent
         |   type: event
         |   pattern: |
         |       trigger = [tag=/V.*/]
         |       agent  = >nmod_by []
         |
         | - name: "patient-passive"
         |   label: Patient
         |   type: event
         |   pattern: |
         |       trigger = [tag=/V.*/]
         |       patient  = >nsubjpass []
         |
        """.stripMargin

    "respond with rule-based frequencies using the /rule-freq endpoint" in {
      val body = Json.obj(
        "grammar" -> expandedRules,
        "allowTriggerOverlaps" -> true,
        "order" -> "freq",
        "min" -> 0,
        "max" -> 1,
        "scale" -> "log10",
        "reverse" -> true,
        "pretty" -> true
      )

      val response =
        controller.ruleFreq().apply(FakeRequest(POST, "/api/rule-freq").withJsonBody(body))
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      // println(Helpers.contentAsString(response))
      Helpers.contentAsString(response) must include("term")
      Helpers.contentAsString(response) must include("frequency")
    }

    "respond with frequency table using the simplest possible /term-hist endpoint" in {
      val response = route(app, FakeRequest(GET, "/api/term-hist?field=lemma")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      // println(Helpers.contentAsString(response))
      Helpers.contentAsString(response) must include("w")
      Helpers.contentAsString(response) must include("x")
      Helpers.contentAsString(response) must include("y")
    }

    "respond with frequency table using the maximal /term-hist endpoint" in {
      val response = route(
        app,
        FakeRequest(
          GET,
          "/api/term-hist?field=tag&bins=5&equalProbability=true&xLogScale=true&pretty=true"
        )
      ).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      // println(Helpers.contentAsString(response))
      Helpers.contentAsString(response) must include("w")
      Helpers.contentAsString(response) must include("x")
      Helpers.contentAsString(response) must include("y")
    }

    "respond with frequency table using the simplest possible /rule-hist endpoint" in {
      val body = Json.obj("grammar" -> expandedRules)

      val response =
        controller.ruleHist().apply(FakeRequest(POST, "/api/rule-hist").withJsonBody(body))
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      // println(Helpers.contentAsString(response))
      Helpers.contentAsString(response) must include("w")
      Helpers.contentAsString(response) must include("x")
      Helpers.contentAsString(response) must include("y")
    }

    "respond with frequency table using the maximal /rule-hist endpoint" in {
      val body = Json.obj(
        "grammar" -> expandedRules,
        "allowTriggerOverlaps" -> true,
        "bins" -> 2,
        "equalProbability" -> true,
        "xLogScale" -> true,
        "pretty" -> true
      )

      val response =
        controller.ruleHist().apply(FakeRequest(POST, "/api/rule-hist").withJsonBody(body))
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      //println(Helpers.contentAsString(response))
      // println(Helpers.contentAsString(response))
      Helpers.contentAsString(response) must include("w")
      Helpers.contentAsString(response) must include("x")
      Helpers.contentAsString(response) must include("y")
    }

  }

}
