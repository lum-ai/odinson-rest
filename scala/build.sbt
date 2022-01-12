import sbtassembly.MergeStrategy
import com.typesafe.sbt.packager.docker.DockerChmodType


// use commit hash as the version
// enablePlugins(GitVersioning)
// git.uncommittedSignifier := Some("DIRTY") // with uncommitted changes?
// git.baseVersion := "0.1.0-SNAPSHOT"

lazy val commonScalacOptions = Seq(
  "-feature",
  "-unchecked",
  "-deprecation",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-unused",
  "-encoding", "utf8"
)


routesGenerator := InjectedRoutesGenerator

licenses in ThisBuild := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

homepage in ThisBuild := Some(url("https://github.com/lum-ai/odinson-rest"))

lazy val commonSettings = Seq(
  organization := "ai.lum",
  scalaVersion := "2.12.10",
  // we want to use -Ywarn-unused-import most of the time
  scalacOptions ++= commonScalacOptions,
  scalacOptions += "-Ywarn-unused-import",
  // -Ywarn-unused-import is annoying in the console
  scalacOptions in (Compile, console) := commonScalacOptions,
  // show test duration
  testOptions in Test += Tests.Argument("-oD"),
  // avoid dep. conflict in assembly task for webapp
  excludeDependencies += "commons-logging" % "commons-logging",
  parallelExecution in Test := false
)

// example specifying credentials using ENV variables:
// AWS_ACCESS_KEY_ID="XXXXXX" AWS_SECRET_KEY="XXXXXX"

lazy val sharedDeps = {
  libraryDependencies ++= {
    val odinsonVersion  = "0.6.0"
    val json4sVersion   = "3.2.11" // "3.5.2"
    val luceneVersion   = "6.6.0"
    Seq(
      guice,
      jdbc,
      caffeine,
      "org.scalatest" %% "scalatest" % "3.0.5",
      "com.typesafe.scala-logging" %%  "scala-logging" % "3.5.0",
      "ch.qos.logback" %  "logback-classic" % "1.1.7",
      "org.json4s" %% "json4s-core" % json4sVersion,
      "ai.lum"        %% "common"               % "0.1.2",
      "ai.lum"        %% "odinson-core"         % odinsonVersion,
      "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test,
    )
  }
}

lazy val assemblySettings = Seq(
  // Trick to use a newer version of json4s with spark (see https://stackoverflow.com/a/49661115/1318989)
  assemblyShadeRules in assembly := Seq(
    ShadeRule.rename("org.json4s.**" -> "shaded_json4s.@1").inAll
  ),
  assemblyMergeStrategy in assembly := {
    case refOverrides if refOverrides.endsWith("reference-overrides.conf") => MergeStrategy.first
    case logback if logback.endsWith("logback.xml") => MergeStrategy.first
    case netty if netty.endsWith("io.netty.versions.properties") => MergeStrategy.first
    case "messages" => MergeStrategy.concat
    case PathList("META-INF", "terracotta", "public-api-types") => MergeStrategy.concat
    case PathList("play", "api", "libs", "ws", xs @ _*) => MergeStrategy.first
    case PathList("org", "apache", "lucene", "analysis", xs @ _ *) => MergeStrategy.first
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
)

lazy val buildInfoSettings = Seq(
  buildInfoPackage := "ai.lum.odinson.rest",
  buildInfoOptions += BuildInfoOption.BuildTime,
  buildInfoOptions += BuildInfoOption.ToJson,
  buildInfoKeys := Seq[BuildInfoKey](
    name, version, scalaVersion, sbtVersion, libraryDependencies, scalacOptions,
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
    packageName in Docker := "odinson-rest-api", 
    // "openjdk:11-jre-alpine"
    // "adoptopenjdk:11-jre-hotspot", // arm and amd compat
    dockerBaseImage := "adoptopenjdk/openjdk11", // arm and amd compat
    maintainer in Docker := "Gus Hahn-Powell <ghp@lum.ai>",
    dockerExposedPorts in Docker := Seq(9000),
    javaOptions in Universal ++= Seq(
      "-J-Xmx4G",
      // avoid writing a PID file
      "-Dplay.server.pidfile.path=/dev/null",
      //"-Dplay.server.akka.requestTimeout=20s"
      "-Dlogger.resource=logback.xml"
    )
  )
}

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(BuildInfoPlugin)
  .settings(commonSettings)
  .settings(packagerSettings)
  .settings(sharedDeps)
  .settings(buildInfoSettings)
  .settings(assemblySettings)
  .settings(
    test in assembly := {},
    mainClass in assembly := Some("play.core.server.ProdServerStart"), // FIXME template, is this core subproject?
    fullClasspath in assembly += Attributed.blank(PlayKeys.playPackageAssets.value),
    // these are used by the sbt web task
    PlayKeys.devSettings ++= Seq(
      "play.server.akka.requestTimeout" -> "infinite",
      //"play.server.akka.terminationTimeout" -> "10 seconds",
      "play.server.http.idleTimeout" -> "2400 seconds"
    )
  )


lazy val cp = taskKey[Unit]("Copies api directories from target to docs")

cp := {
  println{"Copying api documentation..."}
  def copyDocs(): Unit = {
    val projects = Seq("reader", "rest")
    for (s <- projects) {
      println(s"$s docs...")
      val source = baseDirectory.value / s / "target" / "scala-2.12" / "api"
      val target = baseDirectory.value / "docs" / "api" / s
      IO.copyDirectory(source, target, overwrite = true)
    }
  }
  copyDocs()
}
lazy val web = taskKey[Unit]("Launches the webapp in dev mode.")
web := (run in Compile in root).toTask("").value

addCommandAlias("dockerize", ";docker:publishLocal")

addCommandAlias("documentize", ";clean;doc;cp")
