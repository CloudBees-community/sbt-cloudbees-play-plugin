
sbtPlugin := true

organization := "com.cloudbees.deploy.play"

name := "sbt-cloudbees-play-plugin"

version := "0.1-SNAPSHOT"


// maven repositories
resolvers ++= Seq(
  "specs.repo" at "http://specs.googlecode.com/svn/maven2/",
  "sonatype.repo" at "https://oss.sonatype.org/content/groups/public",
  "web-plugin.repo" at "http://siasia.github.com/maven2",
  "typesafe releases" at "http://repo.typesafe.com/typesafe/releases"
)

scalacOptions += "-deprecation"

libraryDependencies ++= Seq(
  "com.cloudbees" % "cloudbees-api-client-nodeps" % "1.2.1" % "compile",
  "edu.stanford.ejalbert" % "BrowserLauncher2" % "1.3" % "compile",
  "org.scala-tools.testing" % "specs" % "1.6.1" % "test"
)

libraryDependencies <++= (scalaVersion, sbtVersion)((scalaVersion, sbtVersion) =>
	Seq("play" % "sbt-plugin" % "2.0.3" % "provided->default(compile)" extra ("scalaVersion" -> scalaVersion, "sbtVersion" -> sbtVersion))
)

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials.sonatype")

publishTo <<= version { (v: String) => 
  val nexus = "https://oss.sonatype.org/" 
  if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots") 
  else Some("releases" at nexus + "service/local/staging/deploy/maven2") 
}


publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { repo => false }

releaseSettings

pomExtra := (
  <url>https://github.com/cloudbees-cummunity/sbt-cloudbees-play-plugin</url>
  <licenses>
    <license>
      <name>Apache 2.0 License</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:cloudbees-community/sbt-cloudbees-play-plugin.git</url>
    <connection>scm:git:git@github.com:cloudbees-community/sbt-cloudbees-play-plugin.git</connection>
  </scm>
  <developers>
    <developer>
      <id>timperrett</id>
      <name>Timothy Perrett</name>
      <url>http://timperrett.com</url>
    </developer>
  </developers>)
