val dottyVersion = "0.3.0-RC2"

lazy val root = (project in file(".")).
  settings(
    name := "dtdays2017",
    version := "0.1",

    scalaVersion := dottyVersion,

    scalacOptions ++= Seq(
      "-explain"
    ),

    initialCommands in console := """
      |import fr.janalyse.ssh._
      |import math._, Math.PI, language._
      |""".stripMargin,

    libraryDependencies ++= Seq(
      "com.novocode" % "junit-interface"   % "0.11" % "test",
      "fr.janalyse"  % "naturalsort_2.11"  % "0.2.0",
      "fr.janalyse"  % "janalyse-ssh_2.11" % "0.10.1"
    )
  )

