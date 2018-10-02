package pravda.dotnet

import java.io.File

import pravda.plaintest._

object ParserSuiteData {
  final case class Input(exe: String)
  final case class Output(methods: String, signatures: String)
}

import ParserSuiteData._

object ParserSuite extends Plaintest[Input, Output] {

  lazy val dir = new File("dotnet/src/test/resources/parser")
  override lazy val ext = "prs"
  override lazy val allowOverwrite = true

  def produce(input: Input): Either[String, Output] =
    for {
      pe <- parsePeFile(input.exe)
      (_, cilData, ms, ss) = pe
    } yield
      Output(pprint.apply(ms, height = Int.MaxValue).plainText,
             pprint.apply(ss.toList.sortBy(_._1), height = Int.MaxValue).plainText)
}
