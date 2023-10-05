//ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.16")
addSbtPlugin("com.github.sbt" % "sbt-git" % "2.0.1")
//addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.10")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.3")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")
//addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.2")

//addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.0")

// formatting
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.6")
