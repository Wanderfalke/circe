import sbtunidoc.Plugin.UnidocKeys._
import ReleaseTransformations._

lazy val buildSettings = Seq(
  organization := "io.circe",
  scalaVersion := "2.11.7",
  crossScalaVersions := Seq("2.10.5", "2.11.7")
)

lazy val compilerOptions = Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-unchecked",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Xfuture"
)

val catsVersion = "0.1.3-SNAPSHOT"

lazy val baseSettings = Seq(
  scalacOptions ++= compilerOptions ++ (
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 11)) => Seq("-Ywarn-unused-import")
      case _ => Nil
    }
  ),
  scalacOptions in (Compile, console) := compilerOptions,
  scalacOptions in (Compile, test) := compilerOptions,
  libraryDependencies ++= Seq(
    "org.typelevel" %% "export-hook" % "1.0.1-SNAPSHOT",
    compilerPlugin("org.scalamacros" % "paradise" % "2.1.0-M5" cross CrossVersion.full)
  ),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots")
  ),
  ScoverageSbtPlugin.ScoverageKeys.coverageHighlighting := (
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 10)) => false
      case _ => true
    }
  )
)

lazy val allSettings = buildSettings ++ baseSettings ++ publishSettings

lazy val commonJsSettings = Seq(
  postLinkJSEnv := NodeJSEnv().value,
  scalaJSStage in Global := FastOptStage
)

lazy val docSettings = site.settings ++ ghpages.settings ++ unidocSettings ++ Seq(
  site.addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), "api"),
  scalacOptions in (ScalaUnidoc, unidoc) ++= Seq("-groups", "-implicits"),
  git.remoteRepo := "git@github.com:travisbrown/circe.git",
  unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(benchmark, coreJS, genericJS, parseJS)
)

lazy val root = project.in(file("."))
  .settings(allSettings)
  .settings(docSettings)
  .settings(noPublishSettings)
  .settings(
    initialCommands in console :=
      """
        |import io.circe._
        |import io.circe.generic.auto._
        |import io.circe.parse._
        |import io.circe.syntax._
        |import cats.data.Xor
      """.stripMargin
  )
  .aggregate(core, coreJS, generic, genericJS, parse, parseJS, tests, testsJS, jawn, async, benchmark)
  .dependsOn(core, generic, parse)

lazy val coreBase = crossProject.in(file("core"))
  .settings(moduleName := "circe-core")
  .settings(allSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.spire-math" %%% "cats-core" % catsVersion
    ),
    sourceGenerators in Compile <+= (sourceManaged in Compile).map(Boilerplate.gen)
  )
  .jsSettings(commonJsSettings: _*)
  .jvmConfigure(_.copy(id = "core"))
  .jsConfigure(_.copy(id = "coreJS"))

lazy val core = coreBase.jvm
lazy val coreJS = coreBase.js

lazy val genericBase = crossProject.in(file("generic"))
  .settings(moduleName := "circe-generic")
  .settings(allSettings: _*)
  .settings(
    libraryDependencies += "com.chuusai" %%% "shapeless" % "2.3.0-SNAPSHOT"
  )
  .jsSettings(commonJsSettings: _*)
  .jvmConfigure(_.copy(id = "generic"))
  .jsConfigure(_.copy(id = "genericJS"))
  .dependsOn(coreBase)

lazy val generic = genericBase.jvm
lazy val genericJS = genericBase.js

lazy val parseBase = crossProject.in(file("parse"))
  .settings(moduleName := "circe-parse")
  .settings(allSettings: _*)
  .jsSettings(commonJsSettings: _*)
  .jvmConfigure(_.copy(id = "parse").dependsOn(jawn))
  .jsConfigure(_.copy(id = "parseJS"))
  .dependsOn(coreBase)

lazy val parse = parseBase.jvm
lazy val parseJS = parseBase.js

lazy val testsBase = crossProject.in(file("tests"))
  .settings(moduleName := "circe-tests")
  .settings(allSettings: _*)
  .settings(noPublishSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.scalacheck" %%% "scalacheck" % "1.12.5-SNAPSHOT",
      "org.scalatest" %%% "scalatest" % "3.0.0-M7",
      "org.spire-math" %%% "cats-laws" % catsVersion,
      "org.typelevel" %%% "discipline" % "0.4"
    ),
    unmanagedResourceDirectories in Compile +=
      file("tests") / "shared" / "src" / "main" / "resources"
  )
  .settings(
    ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := "io\\.circe\\.tests\\..*"
  )
  .jsSettings(commonJsSettings: _*)
  .jvmConfigure(_.copy(id = "tests").dependsOn(jawn, async))
  .jsConfigure(_.copy(id = "testsJS"))
  .dependsOn(coreBase, genericBase, parseBase)

lazy val tests = testsBase.jvm
lazy val testsJS = testsBase.js

lazy val jawn = project
  .settings(moduleName := "circe-jawn")
  .settings(allSettings)
  .settings(
    libraryDependencies += "org.spire-math" %% "jawn-parser" % "0.8.3"
  )
  .dependsOn(core)

lazy val async = project
  .settings(moduleName := "circe-async")
  .settings(allSettings)
  .settings(noPublishSettings)
  .settings(
    libraryDependencies += "com.twitter" %% "util-core" % "6.26.0"
  )
  .dependsOn(core, jawn)

lazy val benchmark = project
  .settings(moduleName := "circe-benchmark")
  .settings(allSettings)
  .settings(noPublishSettings)
  .settings(
    libraryDependencies += "io.argonaut" %% "argonaut" % "6.1"
  )
  .enablePlugins(JmhPlugin)
  .dependsOn(core, generic, jawn)

lazy val publishSettings = Seq(
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  homepage := Some(url("https://github.com/travisbrown/circe")),
  licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  autoAPIMappings := true,
  apiURL := Some(url("https://travisbrown.github.io/circe/api/")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/travisbrown/circe"),
      "scm:git:git@github.com:travisbrown/circe.git"
    )
  ),
  pomExtra := (
    <developers>
      <developer>
        <id>travisbrown</id>
        <name>Travis Brown</name>
        <url>https://twitter.com/travisbrown</url>
      </developer>
    </developers>
  )
)

lazy val noPublishSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)

lazy val sharedReleaseProcess = Seq(
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    publishArtifacts,
    setNextVersion,
    commitNextVersion,
    ReleaseStep(action = Command.process("sonatypeReleaseAll", _)),
    pushChanges
  )
)

credentials ++= (
  for {
    username <- Option(System.getenv().get("SONATYPE_USERNAME"))
    password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
  } yield Credentials(
    "Sonatype Nexus Repository Manager",
    "oss.sonatype.org",
    username,
    password
  )
).toSeq

val jvmProjects = Seq(
  "core",
  "generic",
  "parse",
  "tests",
  "jawn",
  "async",
  "benchmark"
)

val jsProjects = Seq(
  "coreJS",
  "genericJS",
  "parseJS",
  "testsJS"
)

addCommandAlias("buildJVM", jvmProjects.map(";" + _ + "/compile").mkString)
addCommandAlias("validateJVM", ";buildJVM;tests/test;scalastyle")
addCommandAlias("buildJS", jsProjects.map(";" + _ + "/compile").mkString)
addCommandAlias("validateJS", ";buildJS;testsJS/test;scalastyle")
addCommandAlias("validate", ";validateJVM;validateJS")