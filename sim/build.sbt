import Tests._

lazy val commonSettings = Seq(
  organization := "berkeley",
  version      := "1.0",
  scalaVersion := "2.12.4",
  traceLevel   := 15,
  scalacOptions ++= Seq("-deprecation","-unchecked","-Xsource:2.11"),
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  libraryDependencies += "org.json4s" %% "json4s-native" % "3.5.3",
  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases"),
    Resolver.mavenLocal)
)

// Fork each scala test for now, to work around persistent mutable state
// in Rocket-Chip based generators when testing the same DESIGN
def isolateAllTests(tests: Seq[TestDefinition]) = tests map { test =>
      val options = ForkOptions()
      new Group(test.name, Seq(test), SubProcess(options))
  } toSeq

testGrouping in Test := isolateAllTests( (definedTests in Test).value )

// NB: FIRRTL dependency is unmanaged (and dropped in sim/lib)
lazy val chisel     = RootProject(file("target-rtl/firechip/rocket-chip/chisel3"))

// Rocket-chip dependencies (subsumes making RC a RootProject)
lazy val hardfloat  = (project in file("target-rtl/firechip/rocket-chip/hardfloat"))
  .settings(commonSettings).dependsOn(chisel)
  .settings(crossScalaVersions := Seq("2.11.12", "2.12.4"))
lazy val macros     = (project in file("target-rtl/firechip/rocket-chip/macros")).settings(commonSettings)
lazy val rocketchip = (project in file("target-rtl/firechip/rocket-chip"))
  .settings(commonSettings)
  .dependsOn(chisel, hardfloat, macros)
  .aggregate(chisel, hardfloat, macros) // <-- means the running task on rocketchip is also run by aggregate tasks

// Target-specific dependencies
lazy val boom       = project in file("target-rtl/firechip/boom") settings commonSettings dependsOn rocketchip
lazy val sifiveip   = project in file("target-rtl/firechip/sifive-blocks") settings commonSettings dependsOn rocketchip
lazy val testchipip = project in file("target-rtl/firechip/testchipip") settings commonSettings dependsOn rocketchip
lazy val icenet     = project in file("target-rtl/firechip/icenet") settings commonSettings dependsOn (rocketchip, testchipip)

// MIDAS-specific dependencies
lazy val mdf        = RootProject(file("barstools/mdf/scalalib"))
lazy val barstools  = project in file("barstools/macros") settings commonSettings dependsOn (mdf, rocketchip)
lazy val midas      = project in file("midas") settings commonSettings dependsOn barstools

lazy val firesim    = project in file(".") settings commonSettings dependsOn (midas, boom, icenet, sifiveip)
