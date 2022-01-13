
package controllers

import java.io.{ File, IOException }
import java.nio.file.Files
import ai.lum.odinson.rest.utils.OdinsonIndexUtils
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
import play.api.test._

import scala.reflect.io.Directory

class OdinsonControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

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
  OdinsonIndexUtils.indexDocs(testConfig, save = false)

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
    new OdinsonController(
      testConfig,
      fakeApp.configuration,
      cc = Helpers.stubControllerComponents()
    )

  "OdinsonController" should {

    "access the /api/buildinfo endpoint from a new instance of controller" in {

      val buildinfo = controller.buildInfo(pretty = None).apply(FakeRequest(GET, "/buildinfo"))

      status(buildinfo) mustBe OK
      contentType(buildinfo) mustBe Some("application/json")
      (contentAsJson(buildinfo) \ "name").as[String] mustBe "odinson-core"

    }

    "process a pattern query using the runQuery method without a metadataQuery" in {

      val res = controller.runQuery(
        odinsonQuery = "[lemma=be] []",
        metadataQuery = None,
        label = None,
        commit = None,
        prevDoc = None,
        prevScore = None,
        enriched = false,
        pretty = None
      ).apply(FakeRequest(GET, "/pattern"))

      status(res) mustBe OK
      contentType(res) mustBe Some("application/json")
      Helpers.contentAsString(res) must include("core")

    }

    "process a pattern query using the runQuery method with a metadataQuery" in {

      val res1 = controller.runQuery(
        odinsonQuery = "[lemma=pie]",
        metadataQuery = Some("character contains '/Maj.*/'"),
        label = None,
        commit = None,
        prevDoc = None,
        prevScore = None,
        enriched = false,
        pretty = None
      ).apply(FakeRequest(GET, "/pattern"))

      status(res1) mustBe OK
      contentType(res1) mustBe Some("application/json")
      //println(contentAsJson(res1))
      noResults(contentAsJson(res1)) mustBe true

      val res2 = controller.runQuery(
        odinsonQuery = "[lemma=pie]",
        metadataQuery = Some("character contains 'Special Agent'"),
        label = None,
        commit = None,
        prevDoc = None,
        prevScore = None,
        enriched = false,
        pretty = None
      ).apply(FakeRequest(GET, "/pattern"))

      status(res2) mustBe OK
      contentType(res2) mustBe Some("application/json")
      hasResults(contentAsJson(res2)) mustBe true
    }

    "process a pattern query by accessing the /api/execute/pattern endpoint" in {
      // the pattern used in this test: "[lemma=be] []"
      val result = route(
        app,
        FakeRequest(GET, "/api/execute/pattern?odinsonQuery=%5Blemma%3Dbe%5D%20%5B%5D")
      ).get

      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      Helpers.contentAsString(result) must include("core")

    }

    "execute a grammar using the executeGrammar method" in {

      val ruleString =
        s"""
           |rules:
           | - name: "example"
           |   label: GrammaticalSubject
           |   type: event
           |   pattern: |
           |     trigger  = [tag=/VB.*/]
           |     subject  = >nsubj []
           |
        """.stripMargin

      val body = Json.obj(
        // format: off
        "grammar"              -> ruleString,
        "pageSize"             -> 10,
        "allowTriggerOverlaps" -> false
        // format: on
      )

      val response =
        controller.executeGrammar().apply(FakeRequest(POST, "/grammar").withJsonBody(body))
      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      Helpers.contentAsString(response) must include("vision")

    }

    "execute a grammar using the /api/execute/grammar endpoint" in {

      val ruleString =
        s"""
           |rules:
           | - name: "example"
           |   label: GrammaticalSubject
           |   type: event
           |   pattern: |
           |     trigger  = [tag=/VB.*/]
           |     subject  = >nsubj []
           |
        """.stripMargin

      val body = Json.obj(
        // format: off
        "grammar"              -> ruleString,
        "pageSize"             -> 10,
        "allowTriggerOverlaps" -> false
        // format: on
      )

      val response = route(app, FakeRequest(POST, "/api/execute/grammar").withJsonBody(body)).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      Helpers.contentAsString(response) must include("vision")
    }

    "not persist state across uses of the /api/execute/grammar endpoint" in {

      val ruleString1 =
        s"""
           |rules:
           | - name: "example1"
           |   label: GrammaticalSubject
           |   type: event
           |   pattern: |
           |       trigger = [lemma=have]
           |       subject  = >nsubj []
           |
        """.stripMargin

      val body1 =
        Json.obj("grammar" -> ruleString1, "pageSize" -> 10, "allowTriggerOverlaps" -> false)

      val response1 = route(app, FakeRequest(POST, "/api/execute/grammar").withJsonBody(body1)).get

      status(response1) mustBe OK
      contentType(response1) mustBe Some("application/json")
      Helpers.contentAsString(response1) must include("example1")

      val ruleString2 =
        s"""
           |rules:
           | - name: "example2"
           |   label: GrammaticalObject
           |   type: event
           |   pattern: |
           |       trigger = [lemma=have]
           |       subject  = >dobj []
           |
        """.stripMargin

      val body2 =
        Json.obj("grammar" -> ruleString2, "pageSize" -> 10, "allowTriggerOverlaps" -> false)

      val response2 = route(app, FakeRequest(POST, "/api/execute/grammar").withJsonBody(body2)).get

      status(response2) mustBe OK
      contentType(response2) mustBe Some("application/json")
      val response2Content = Helpers.contentAsString(response2)
      response2Content must include("example2")
      response2Content must include("GrammaticalObject")
      response2Content must not include ("example1")
      response2Content must not include ("GrammaticalSubject")

    }

    "retrieve metadata using the /api/metadata/by-sentence-id endpoint" in {
      val response = route(app, FakeRequest(GET, "/api/metadata/by-sentence-id?sentenceId=2")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")

      Helpers.contentAsString(response) must include("ai.lum.odinson.TokensField")
      Helpers.contentAsString(response) must include("Garland")
    }

    "retrieve metadata using the /api/metadata/by-document-id endpoint" in {
      val response =
        route(app, FakeRequest(GET, "/api/metadata/by-document-id?documentId=tp-pies")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")

      Helpers.contentAsString(response) must include("ai.lum.odinson.TokensField")
      Helpers.contentAsString(response) must include("Cooper")
    }

    "retrieve the parent doc using the /api/parent/by-sentence-id endpoint" in {
      val response = route(app, FakeRequest(GET, "/api/parent/by-sentence-id?sentenceId=2")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")

      Helpers.contentAsString(response) must include("Briggs") // metadata
      Helpers.contentAsString(response) must include("subconscious") // this sentence
      Helpers.contentAsString(response) must include("veranda") // other sentences in parent
    }

    "retrieve the parent doc using the /api/parent/by-document-id endpoint" in {
      val response =
        route(app, FakeRequest(GET, "/api/parent/by-document-id?documentId=tp-pies")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")

      Helpers.contentAsString(response) must include("MacLachlan") // metadata
      Helpers.contentAsString(response) must include("pies") // sentence info
    }

    "respond with corpus information using the /corpus endpoint" in {
      val response = route(app, FakeRequest(GET, "/api/corpus")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      // println(Helpers.contentAsString(response))
      val responseString = Helpers.contentAsString(response)
      responseString must include("numDocs")
      responseString must include("corpus")
      responseString must include("distinctDependencyRelations")
      responseString must include("tokenFields")
      responseString must include("docFields")
      responseString must include("storedFields")
    }

    "respond with dependencies list using the /dependencies-vocabulary endpoint" in {
      val response = route(app, FakeRequest(GET, "/api/dependencies-vocabulary")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      // println(Helpers.contentAsString(response))
      val json = Helpers.contentAsJson(response)
      val deps = json.as[Array[String]]
      deps must contain("nsubj")
      deps must contain("nmod_from")
    }

    "respond with dependencies list using the /tags-vocabulary endpoint" in {
      val response = route(app, FakeRequest(GET, "/api/tags-vocabulary")).get

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      // println(Helpers.contentAsString(response))
      val json = Helpers.contentAsJson(response)
      val tags = json.as[Array[String]]
      tags must contain("VBG")
      tags must contain("WRB")
    }

  }

}
