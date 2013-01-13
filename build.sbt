name := "csvtools"

version := "1.0"

scalaVersion := "2.9.2"

libraryDependencies += "org.scalatest" %% "scalatest" % "1.8" % "test"

libraryDependencies += "junit" % "junit" % "4.10" % "test"

libraryDependencies += "net.sf.supercsv" % "super-csv" % "2.0.1"

libraryDependencies += "com.github.scopt" %% "scopt" % "2.1.0"

seq(com.github.retronym.SbtOneJar.oneJarSettings: _*)