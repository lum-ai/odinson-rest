import sbtassembly.MergeStrategy
import com.typesafe.sbt.packager.docker.{ DockerChmodType }



// use commit hash as the version
// enablePlugins(GitVersioning)
// git.uncommittedSignifier := Some("DIRTY") // with uncommitted changes?
// git.baseVersion := "0.1.0-SNAPSHOT"

// see https://tpolecat.github.io/2017/04/25/scalac-flags.html
lazy val commonScalacOptions = Seq(
  "-feature",
  "-unchecked",
  "-deprecation",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
  "-Ywarn-infer-any", // Warn when a type argument is inferred to be `Any`.
  // some of the "unused" imports *are* used
  //"-Ywarn-unused",
  "-Ywarn-unused:patvars", // Warn if a variable bound in a pattern is unused.
  "-encoding", "utf8"
)


routesGenerator := InjectedRoutesGenerator

ThisBuild / licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

ThisBuild / homepage := Some(url("https://github.com/lum-ai/odinson-rest"))

lazy val commonSettings = Seq(
  organization := "ai.lum",
  scalaVersion := "2.12.17",
  scalacOptions := commonScalacOptions,
  //scalacOptions ++= commonScalacOptions,
  // TODO: anything to filter out here?
  Compile / console / scalacOptions := commonScalacOptions, //--= Seq("-Ywarn-unused:imports", "-Xfatal-warnings")
  // show test duration
  Test / testOptions += Tests.Argument("-oD"),
  // avoid dep. conflict in assembly task for webapp
  excludeDependencies += "commons-logging" % "commons-logging",
  Test / parallelExecution := false
)

lazy val sharedDeps = {
  libraryDependencies ++= {
    val odinsonVersion  = "0.6.1"
    val json4sVersion   = "4.0.6" //"3.2.11" // "3.5.2"
    val luceneVersion   = "6.6.0"
    Seq(
      guice,
      jdbc,
      caffeine,
      "org.scalatest" %% "scalatest" % "3.2.8", //3.2.17",
      "com.typesafe.scala-logging" %%  "scala-logging" % "3.9.5",
      // "com.typesafe" % "config" % "1.4.3",
      // "ch.qos.logback" %  "logback-classic" % "1.4.11",
      "org.json4s" %% "json4s-core" % json4sVersion,
      "ai.lum"        %% "common"               % "0.1.5",
      "ai.lum"        %% "odinson-core"         % odinsonVersion,
      "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test,
    )
  }
}

lazy val assemblySettings = Seq(
  // Trick to use a newer version of json4s with spark (see https://stackoverflow.com/a/49661115/1318989)
  assembly / assemblyShadeRules := Seq(
    ShadeRule.rename("org.json4s.**" -> "shaded_json4s.@1").inAll
  ),
  assembly / assemblyMergeStrategy := {
    case refOverrides if refOverrides.endsWith("reference-overrides.conf") => MergeStrategy.first
    case logback if logback.endsWith("logback.xml") => MergeStrategy.first
    case netty if netty.endsWith("io.netty.versions.properties") => MergeStrategy.first
    case "messages" => MergeStrategy.concat
    case PathList("META-INF", "terracotta", "public-api-types") => MergeStrategy.concat
    case PathList("play", "api", "libs", "ws", xs @ _*) => MergeStrategy.first
    case PathList("org", "apache", "lucene", "analysis", xs @ _ *) => MergeStrategy.first
    case x =>
      val oldStrategy = (assembly / assemblyMergeStrategy).value
      oldStrategy(x)
  }
)

lazy val buildInfoSettings = Seq(
  buildInfoPackage := "ai.lum.odinson.rest",
  buildInfoOptions += BuildInfoOption.BuildTime,
  buildInfoOptions += BuildInfoOption.ToJson,
  buildInfoKeys := Seq[BuildInfoKey](
    "name" -> "odinson-rest",
    version, scalaVersion, sbtVersion, scalacOptions, libraryDependencies,
    "gitCurrentBranch" -> { git.gitCurrentBranch.value },
    "gitHeadCommit" -> { git.gitHeadCommit.value.getOrElse("") },
    "gitHeadCommitDate" -> { git.gitHeadCommitDate.value.getOrElse("") },
    "gitUncommittedChanges" -> { git.gitUncommittedChanges.value }
  )
)

val gitDockerTag = settingKey[String]("Git commit-based tag for docker")
ThisBuild / gitDockerTag := {
  val shortHash: String = git.gitHeadCommit.value.get.take(7)
  val uncommittedChanges: Boolean = (git.gitUncommittedChanges).value
  s"""${shortHash}${if (uncommittedChanges) "-DIRTY" else ""}"""
}

lazy val packagerSettings = {
  Seq(
    // see https://www.scala-sbt.org/sbt-native-packager/formats/docker.html
    dockerUsername := Some("lumai"),
    dockerAliases ++= Seq(
      dockerAlias.value.withTag(Option("latest")),
      dockerAlias.value.withTag(Option(gitDockerTag.value)),
      // see https://github.com/sbt/sbt-native-packager/blob/master/src/main/scala/com/typesafe/sbt/packager/docker/DockerAlias.scala
    ),
    Docker / daemonUser  := "odinson",
    Docker / packageName := "odinson-rest-api",
    dockerBaseImage := "eclipse-temurin:11-jre-focal", // arm46 and amd64 compat
    Docker / maintainer := "Gus Hahn-Powell <ghp@lum.ai>",
    Docker / dockerExposedPorts := Seq(9000),
    Universal / javaOptions ++= Seq(
      "-J-Xmx4G",
      // avoid writing a PID file
      "-Dplay.server.pidfile.path=/dev/null",
      //"-Dlogger.resource=logback.xml"
      "-Dplay.secret.key=odinson-rest-api-is-not-production-ready",
      // NOTE: bind mount odison dir to /data/odinson
      //"-Dodinson.dataDir=/app/data/odinson",
      // timeouts
      "-Dplay.server.akka.requestTimeout=infinite",
      //"play.server.akka.terminationTimeout=10s",
      //"-Dplay.server.http.idleTimeout=2400s"
    ),
    dockerEnvVars ++= Map(
      "APP_VERSION" -> scala.util.Properties.envOrElse("APP_VERSION", "???"),
      "APPLICATION_SECRET" -> "this-is-not-a-secure-key-please-change-me",
      // NOTE: bind mount odinson dir to /data/odinson
      "ODINSON_DATA_DIR" -> "/app/data/odinson",
      // token attributes that should be searchable
      "ODINSON_TOKEN_ATTRIBUTES" -> "raw,word,norm,lemma,tag,chunk,entity,incoming,outgoing",
      // NOTE: the expected min. RAM requirements
      "_JAVA_OPTIONS" -> "-Xmx2g -Dfile.encoding=UTF-8 -Dplay.server.pidfile.path=/dev/null"
    )
  )
}

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(DockerPlugin)
  .settings(commonSettings)
  .settings(packagerSettings)
  .settings(sharedDeps)
  .settings(buildInfoSettings)
  .settings(assemblySettings)
  .settings(
    assembly / test := {},
    assembly / mainClass := Some("play.core.server.ProdServerStart"),
    assembly / fullClasspath += Attributed.blank(PlayKeys.playPackageAssets.value),
    // these are used by the sbt web task
    PlayKeys.devSettings ++= Seq(
      "play.secret.key" -> "odinson-rest-api-is-not-production-ready",
      "play.server.akka.requestTimeout" -> "infinite",
      //"play.server.akka.terminationTimeout" -> "10 seconds",
      "play.server.http.idleTimeout" -> "2400 seconds"
    )
  )


lazy val cp = taskKey[Unit]("Copies api directories from target to docs")

cp := {
  println{"Copying api documentation..."}
  def copyDocs(): Unit = {
    val source = baseDirectory.value / "target" / "scala-2.12" / "api"
    val target = baseDirectory.value / "docs" / "api" / "odinson-rest"
    IO.copyDirectory(source, target, overwrite = true)
  }
  copyDocs()
}
lazy val web = taskKey[Unit]("Launches the webapp in dev mode.")
web := (root / Compile / run).toTask("").value

addCommandAlias("dockerfile", ";docker:stage")
addCommandAlias("dockerize", ";docker:publishLocal")
addCommandAlias("documentize", ";clean;doc;cp")
