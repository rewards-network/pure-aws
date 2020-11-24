//Core deps
val amazonV = "2.15.35"
val catsV = "2.2.0"
val catsEffectV = "2.2.0"
val fs2V = "2.4.6"
val log4catsV = "1.1.1"
val refinedV = "0.9.18"
val monixV = "3.3.0"
val collectionCompatV = "2.3.0"

val catsCore = "org.typelevel" %% "cats-core" % catsV
val catsEffect = "org.typelevel" %% "cats-effect" % catsEffectV
val fs2Core = "co.fs2" %% "fs2-core" % fs2V
val fs2Io = "co.fs2" %% "fs2-io" % fs2V
val log4catsCore = "io.chrisdavenport" %% "log4cats-core" % log4catsV
val awsSQS = "software.amazon.awssdk" % "sqs" % amazonV
val awsS3 = "software.amazon.awssdk" % "s3" % amazonV
val refined = "eu.timepit" %% "refined" % refinedV
val monixCatnap = "io.monix" %% "monix-catnap" % monixV
val collectionCompat =
  "org.scala-lang.modules" %% "scala-collection-compat" % collectionCompatV

//Test/build deps
val scalaTestV = "3.2.3"
val scalaCheckV = "1.15.1"
val scalaTestScalacheckV = "3.2.2.0"
val betterMonadicForV = "0.3.1"
val flexmarkV = "0.35.10" // scala-steward:off

val catsEffectLaws =
  "org.typelevel" %% "cats-effect-laws" % catsEffectV % "test"
val log4catsTesting =
  "io.chrisdavenport" %% "log4cats-testing" % log4catsV % "test"
val scalatest = "org.scalatest" %% "scalatest" % scalaTestV % "test"
val scalacheck = "org.scalacheck" %% "scalacheck" % scalaCheckV % "test"
val scalatestPlusScalacheck =
  "org.scalatestplus" %% "scalacheck-1-14" % scalaTestScalacheckV % "test"
val flexmark = "com.vladsch.flexmark" % "flexmark-all" % flexmarkV % "test"

//Scala versions supported
val scala213 = "2.13.4"
val scala212 = "2.12.12"

// Project setup
inThisBuild(
  List(
    organization := "com.rewardsnetwork",
    developers := List(
      Developer("sloshy", "Ryan Peters", "rpeters@rewardsnetwork.com", url("https://github.com/sloshy"))
    ),
    homepage := Some(url("https://github.com/rewards-network/pure-aws")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    githubWorkflowJavaVersions := Seq("adopt@1.11"),
    githubWorkflowTargetTags ++= Seq("v*"),
    githubWorkflowPublishTargetBranches := Seq(RefPredicate.StartsWith(Ref.Tag("v"))),
    githubWorkflowPublish := Seq(WorkflowStep.Sbt(List("ci-release"))),
    scalaVersion := scala213,
    crossScalaVersions := Seq(scala213, scala212)
  )
)

val commonSettings = Seq(
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % betterMonadicForV)
)

lazy val root = (project in file("."))
  .settings(
    commonSettings,
    publish / skip := true
  )
  .aggregate(core, sqs, sqsRefined, s3, s3Testing)

lazy val core = (project in file("modules/core"))
  .settings(
    commonSettings,
    name := "pure-aws-core",
    libraryDependencies ++= Seq(
      collectionCompat
    )
  )

lazy val sqs = (project in file("modules/sqs"))
  .settings(
    commonSettings,
    name := "pure-aws-sqs",
    libraryDependencies ++= Seq(
      //Core deps
      catsCore,
      catsEffect,
      fs2Core,
      log4catsCore,
      awsSQS,
      monixCatnap,
      //Test deps
      catsEffectLaws,
      log4catsTesting,
      scalatest,
      scalacheck,
      scalatestPlusScalacheck,
      flexmark
    )
  )
  .dependsOn(core)

lazy val sqsRefined = (project in file("modules/sqs-refined"))
  .settings(
    commonSettings,
    name := "pure-aws-sqs-refined",
    libraryDependencies ++= Seq(
      refined
    )
  )
  .dependsOn(sqs)

lazy val s3 = (project in file("modules/s3"))
  .settings(
    commonSettings,
    name := "pure-aws-s3",
    libraryDependencies ++= Seq(
      //Core deps
      catsCore,
      catsEffect,
      fs2Io,
      log4catsCore,
      awsS3,
      monixCatnap,
      //Test deps
      catsEffectLaws,
      log4catsTesting,
      scalatest,
      scalacheck,
      scalatestPlusScalacheck,
      flexmark
    )
  )
  .dependsOn(core)

lazy val s3Testing = (project in file("modules/s3-testing"))
  .settings(
    commonSettings,
    name := "pure-aws-s3-testing"
  )
  .dependsOn(s3 % "compile->compile;test->test")
