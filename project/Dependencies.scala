import sbt._

object Dependencies {
  lazy val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.1.7"
  lazy val akkaActor = "com.typesafe.akka" %% "akka-actor" % "2.5.19"
  lazy val akkaStream = "com.typesafe.akka" %% "akka-stream" % "2.5.19"
  lazy val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.14.0"
  lazy val catsCore = "org.typelevel" %% "cats-core" % "1.5.0"
  lazy val superTagged = "org.rudogma" %% "supertagged" % "1.4"
  lazy val uTest = "com.lihaoyi" %% "utest" % "0.6.3"
  lazy val tethys = "com.tethys-json" %% "tethys" % "0.7.0.2"
  lazy val tethysDerivation = "com.tethys-json" %% "tethys-derivation" % "0.7.0.2"
  lazy val tethysJson4s = "com.tethys-json" %% "tethys-json4s" % "0.7.0.2"
  lazy val json4sAst = "org.json4s" %% "json4s-ast" % "3.6.1"
  lazy val json4sNative = "org.json4s" %% "json4s-native" % "3.6.1"
  lazy val fastParse = "com.lihaoyi" %% "fastparse" % "1.0.0"
  lazy val fastParseByte = "com.lihaoyi" %% "fastparse-byte" % "1.0.0"
  lazy val protobufJava = "com.google.protobuf" % "protobuf-java" % "3.5.0"
  lazy val pprint = "com.lihaoyi" %% "pprint" % "0.5.3"
  lazy val commonsIo = "commons-io" % "commons-io" % "2.6"
  lazy val snakeYml = "org.yaml" % "snakeyaml" % "1.23"
  lazy val scopt = "com.github.scopt" %% "scopt" % "3.7.0"
  lazy val javaCompiler = "com.github.spullara.mustache.java" % "compiler" % "0.9.5"
  lazy val contextual = "com.propensive" %% "contextual" % "1.1.0"
  lazy val levelDb = "org.iq80.leveldb" % "leveldb" % "0.10"
  lazy val korolevServerAkkaHttp = "com.github.fomkin" %% "korolev-server-akkahttp" % "0.10.0"
  lazy val pureConfig = "com.github.pureconfig" %% "pureconfig" % "0.10.0"
  lazy val akkaStreamUnixDomainSocket = "com.lightbend.akka" %% "akka-stream-alpakka-unix-domain-socket" % "0.17"
  lazy val zhukov = "com.github.fomkin" %% "zhukov-derivation" % "0.3.2"
  lazy val bcprov = "org.bouncycastle" % "bcprov-jdk15on" % "1.62"

  lazy val exploadAbciServer = "com.expload" %% "scala-abci-server" % "0.13.0"
}
