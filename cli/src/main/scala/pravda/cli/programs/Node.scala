/*
 * Copyright (C) 2018  Expload.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pravda.cli.programs

import cats._
import cats.data.EitherT
import cats.implicits._
import com.google.protobuf.ByteString
import pravda.cli.PravdaConfig
import pravda.cli.PravdaConfig.Node.{Mode, Network}
import pravda.node.client.{IoLanguage, NodeLanguage, RandomLanguage}
import pravda.common.data.blockchain._
import pravda.common.{bytes, cryptography}
import pravda.node.data.PravdaConfig.Validator
import pravda.common.data.blockchain.CoinDistributionMember
import pravda.common.serialization._
import pravda.node.data.serialization.json._

import scala.language.higherKinds
import scala.util.Try

final class Node[F[_]: Monad](io: IoLanguage[F], random: RandomLanguage[F], node: NodeLanguage[F]) {

  private def applicationConfig(isValidator: Boolean,
                                chainId: String,
                                dataDir: String,
                                paymentWallet: Validator,
                                validators: Seq[String],
                                coinDistribution: Seq[CoinDistributionMember],
                                seeds: Seq[(String, Int)],
                                createEmptyBlocks: Boolean,
                                createEmptyBlocksInterval: Int) =
    s"""pravda {
       |  network-address-cache {
       |    ttl = 60
       |    negative-ttl = 20
       |  }
       |  http {
       |    host = "127.0.0.1"
       |    port = 8080
       |  }
       |  tendermint {
       |    peer-port = 46656
       |    rpc-port = 46657
       |    proxy-app-port = 46658
       |    use-unix-domain-socket = false
       |    create-empty-blocks = ${createEmptyBlocks.toString}
       |    create-empty-blocks-interval = ${createEmptyBlocksInterval.toString}
       |  }
       |  data-directory = "${dataDir.replace("\\", "\\\\")}"
       |  coin-distribution = "${coinDistribution
         .map(d => s"${bytes.byteString2hex(d.address)}:${d.amount}")
         .mkString(",")}"
       |  seeds = "${seeds.map { case (host, port) => s"$host:$port" }.mkString(",")}"
       |  genesis {
       |    time = "0001-01-01T00:00:00Z"
       |    chain-id = "$chainId"
       |    validators = "${validators.mkString(",")}"
       |    app-hash = ""
       |  }
       |${if (isValidator) {
         s"""  validator {
                |    private-key = "${bytes.byteString2hex(paymentWallet.privateKey)}"
                |    address = "${bytes.byteString2hex(paymentWallet.address)}"
                |  }
              """.stripMargin
       } else ""}
       |}
       |akka {
       |  http {
       |    host-connection-pool {
       |      // default value is 32
       |      max-open-requests = 1024
       |    }
       |  }
       |}
       |""".stripMargin

  private def mkConfigPath(dataDir: String): F[String] =
    io.concatPath(dataDir, "node.conf")

  private val readFromFile: String => F[Either[String, ByteString]] = (path: String) =>
    io.readFromFile(path)
      .map(_.toRight(s"`$path` is not found."))

  // FIXME remove bulldozer code

  private def init(dataDir: String, network: Network, initDistrConf: Option[String]): F[Either[String, Unit]] = {

    val result = for {
      configPath <- EitherT[F, String, String](io.concatPath(dataDir, "node.conf").map(Right.apply))
      randomBytes <- EitherT[F, String, ByteString](random.secureBytes64().map(Right.apply))
      (pub, sec) = cryptography.generateKeyPair(randomBytes)
      paymentWallet = Validator(sec, pub)
      initialDistribution <- initDistrConf
        .map { path =>
          EitherT[F, String, ByteString](readFromFile(path)).flatMap { bs =>
            EitherT[F, String, Seq[CoinDistributionMember]](
              Monad[F].pure(
                Try(transcode(Json @@ bs.toStringUtf8).to[Seq[CoinDistributionMember]])
                  .fold(e => Left(e.getMessage), Right(_))
              )
            )
          }
        }
        .getOrElse(
          EitherT[F, String, Seq[CoinDistributionMember]](
            Monad[F].pure(
              Right(List(CoinDistributionMember(pub, NativeCoin.amount(50000))))
            )
          )
        )

      config = network match {
        case Network.Local(_, createEmptyBlocks, createEmptyBlocksInterval) =>
          applicationConfig(
            isValidator = true,
            chainId = "local",
            dataDir = dataDir,
            paymentWallet = paymentWallet,
            coinDistribution = initialDistribution,
            validators = List(s"me:10:${bytes.byteString2hex(pub)}"),
            seeds = Nil,
            createEmptyBlocks = createEmptyBlocks,
            createEmptyBlocksInterval = createEmptyBlocksInterval
          )
        case Network.Testnet =>
          val pkey = "c77f81ae0c37ea3742e16b5cf15563ca6cc063bc5e88ff55a74dc0e52bd7d632"
          applicationConfig(
            isValidator = false,
            "testnet",
            dataDir,
            paymentWallet,
            Seq(s"bob:10:$pkey"),
            Seq(
              CoinDistributionMember(
                Address @@ bytes.hex2byteString(pkey),
                NativeCoin @@ 1000000000L
              )
            ),
            Seq("35.234.141.154" -> 30001),
            // For non-validator nodes these settings doesn't make sense.
            createEmptyBlocks = false,
            createEmptyBlocksInterval = 0
          )
      }
      _ <- EitherT[F, String, Unit](io.writeToFile(configPath, ByteString.copyFromUtf8(config)).map(Right.apply))
    } yield ()
    result.value
  }

  def apply(config: PravdaConfig.Node): F[Unit] = {
    val errorOrOk =
      for {
        dataDir <- EitherT.liftF {
          config.dataDir.map(Monad[F].pure).getOrElse {
            for {
              pwd <- io.pwd()
              dataDir <- io.concatPath(pwd, "pravda-data")
              _ <- io.mkdirs(dataDir)
            } yield dataDir
          }
        }
        _ <- EitherT[F, String, Unit] {
          io.isDirectory(dataDir).flatMap {
            case None        => io.mkdirs(dataDir).map(Right.apply)
            case Some(true)  => Monad[F].pure(Right(()))
            case Some(false) => Monad[F].pure(Left(s"'$dataDir' is not a directory."))
          }
        }
        _ <- EitherT[F, String, Unit] {
          config.mode match {
            case Mode.Nope                                    => Monad[F].pure(Left(s"[init|run] subcommand required."))
            case Mode.Init(network @ Network.Local(cd, _, _)) => init(dataDir, network, cd)
            case Mode.Init(network)                           => init(dataDir, network, None)
            case Mode.Run                                     => mkConfigPath(dataDir).flatMap(node.launch).map(Right.apply)
          }
        }
      } yield ()

    errorOrOk.value.flatMap {
      case Left(error) => io.writeStringToStderrAndExit(s"$error\n")
      case Right(_)    => Monad[F].unit
    }
  }
}
