//Core deps
val amazonV = "2.17.70"
val catsV = "2.6.1"
val catsEffectV = "3.2.8"
val fs2V = "3.1.2"
val log4catsV = "1.2.0"
val refinedV = "0.9.25"
val collectionCompatV = "2.5.0"

val catsCore = "org.typelevel" %% "cats-core" % catsV
val catsEffect = "org.typelevel" %% "cats-effect-std" % catsEffectV
val fs2Core = "co.fs2" %% "fs2-core" % fs2V
val fs2Io = "co.fs2" %% "fs2-io" % fs2V
val fs2ReactiveStreams = "co.fs2" %% "fs2-reactive-streams" % fs2V
val awsSdkCore = "software.amazon.awssdk" % "sdk-core" % amazonV
val awsSQS = "software.amazon.awssdk" % "sqs" % amazonV
val awsS3 = "software.amazon.awssdk" % "s3" % amazonV
val refined = "eu.timepit" %% "refined" % refinedV
val collectionCompat =
  "org.scala-lang.modules" %% "scala-collection-compat" % collectionCompatV

//Test/build deps
val munitV = "0.7.29"
val munitCatsEffectV = "1.0.5"
val scalaCheckV = "1.15.4"
val scalaCheckEffectV = "1.0.2"

val catsEffectLaws =
  "org.typelevel" %% "cats-effect-laws" % catsEffectV % "test"
val log4catsTesting =
  "io.chrisdavenport" %% "log4cats-testing" % log4catsV % "test"
val munitCatsEffect = "org.typelevel" %% "munit-cats-effect-3" % munitCatsEffectV % "test"
val munitScalacheck = "org.scalameta" %% "munit-scalacheck" % munitV
val scalaCheck = "org.scalacheck" %% "scalacheck" % scalaCheckV % "test"
val scalaCheckEffect = "org.typelevel" %% "scalacheck-effect-munit" % scalaCheckEffectV % "test"

//Scala versions supported
val scala213 = "2.13.6"
val scala212 = "2.12.13"
val scala3 = "3.0.2"

// Project setup
inThisBuild(
  List(
    organization := "com.rewardsnetwork",
    developers := List(
      Developer("sloshy", "Ryan Peters", "me@rpeters.dev", url("https://github.com/sloshy"))
    ),
    homepage := Some(url("https://github.com/rewards-network/pure-aws")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    githubWorkflowJavaVersions := Seq("adopt@1.8"),
    githubWorkflowTargetTags ++= Seq("v*"),
    githubWorkflowPublishTargetBranches := Seq(RefPredicate.StartsWith(Ref.Tag("v"))),
    githubWorkflowPublish := Seq(
      WorkflowStep.Sbt(
        List("ci-release"),
        env = Map(
          "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
          "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
          "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
          "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
        )
      )
    ),
    scalaVersion := scala213,
    crossScalaVersions := Seq(scala3, scala213, scala212)
  )
)

lazy val root = (project in file("."))
  .settings(
    publish / skip := true
  )
  .aggregate(core, sqs, sqsRefined, s3, s3Testing)

lazy val core = (project in file("modules/core"))
  .settings(
    name := "pure-aws-core",
    libraryDependencies ++= Seq(
      awsSdkCore,
      catsCore,
      catsEffect,
      collectionCompat,
      fs2Core,
      fs2ReactiveStreams,
      //Test deps
      munitCatsEffect,
      munitScalacheck,
      scalaCheck,
      scalaCheckEffect
    )
  )

lazy val sqs = (project in file("modules/sqs"))
  .settings(
    name := "pure-aws-sqs",
    libraryDependencies ++= Seq(
      //Core deps
      awsSQS,
      //Test deps
      catsEffectLaws
    )
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val sqsRefined = (project in file("modules/sqs-refined"))
  .settings(
    name := "pure-aws-sqs-refined",
    libraryDependencies ++= Seq(
      refined
    ),
    Compile / unmanagedSourceDirectories ++= {
      def dir(s: String) = baseDirectory.value / "src" / "main" / s"scala$s"

      if (scalaVersion.value.startsWith("2.13")) List(dir("-2.13+"))
      else if (scalaVersion.value.startsWith("3")) List(dir("-2.13+"))
      else Nil
    }
  )
  .dependsOn(sqs % "compile->compile;test->test")

lazy val s3 = (project in file("modules/s3"))
  .settings(
    name := "pure-aws-s3",
    libraryDependencies ++= Seq(
      //Core deps
      awsS3,
      fs2Io,
      //Test deps
      catsEffectLaws
    ),
    Compile / doc / sources := {
      if (scalaVersion.value.startsWith("3")) Nil
      else (Compile / doc / sources).value
    }
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val s3Testing = (project in file("modules/s3-testing"))
  .settings(
    name := "pure-aws-s3-testing"
  )
  .dependsOn(s3 % "compile->compile;test->test")
