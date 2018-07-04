package pravda.cli

import java.io.File

import pravda.cli.PravdaConfig.CompileMode
import pravda.common.bytes
import pravda.common.domain.NativeCoin
import pravda.yopt._

object PravdaArgsParser extends CommandLine[PravdaConfig] {

  val model =
    head("pravda")
      .text("Pravda Command Line Interface to Pravda SDK")
      .children(
        cmd("gen")
          .text("Generate auxiliary data for Pravda.")
          .children(
            cmd("address")
              .text("Generate ed25519 key pair. It can be used as regular wallet or validator node identifier.")
              .action(_ => PravdaConfig.GenAddress())
              .children(
                opt[File]('o', "output")
                  .text("Output file")
                  .action {
                    case (file, PravdaConfig.GenAddress(_)) =>
                      PravdaConfig.GenAddress(Some(file.getAbsolutePath))
                    case (_, otherwise) => otherwise
                  }
              )
          ),
        cmd("run")
          .text("Run byte-code on Pravda VM")
          .action(_ => PravdaConfig.RunBytecode())
          .children(
            opt[String]('e', "executor")
              .text("Executor address HEX representation")
              .validate {
                case s if bytes.isHex(s) && s.length == 64 => Right(())
                case s                                     => Left(s"`$s` is not valid address. It should be 32 bytes hex string.")
              }
              .action {
                case (address, config: PravdaConfig.RunBytecode) =>
                  config.copy(executor = address)
                case (_, otherwise) => otherwise
              },
            opt[File]('i', "input")
              .text("Input file")
              .action {
                case (file, config: PravdaConfig.RunBytecode) =>
                  config.copy(input = Some(file.getAbsolutePath))
                case (_, otherwise) => otherwise
              },
            opt[File]("storage")
              .text("Storage name")
              .action {
                case (file, config: PravdaConfig.RunBytecode) =>
                  config.copy(storage = Some(file.getAbsolutePath))
                case (_, otherwise) => otherwise
              }
          ),
        cmd("compile")
          .text("Compile Pravda programs.")
          .action(_ => PravdaConfig.Compile(CompileMode.Nope))
          .children(
            opt[File]('i', "input")
              .text("Input file")
              .action {
                case (file, config: PravdaConfig.Compile) =>
                  config.copy(input = Some(file.getAbsolutePath))
                case (_, otherwise) => otherwise
              },
            opt[File]('o', "output")
              .text("Output file")
              .action {
                case (file, config: PravdaConfig.Compile) =>
                  config.copy(output = Some(file.getAbsolutePath))
                case (_, otherwise) => otherwise
              },
            cmd("asm")
              .text("Assemble Pravda VM bytecode from text representation. " +
                "Input file is a Pravda assembly language text file. " +
                "Output is binary Pravda program. " +
                "By default read from stdin and print to stdout.")
              .action(_ => PravdaConfig.Compile(PravdaConfig.CompileMode.Asm)),
            cmd("disasm")
              .text("Disassemble Pravda VM bytecode to text presentation. " +
                "Input file is a Pravda executable binary. " +
                "Output is a text file with Pravda assembly code. " +
                "By default read from stdin and print to stdout.")
              .action(_ => PravdaConfig.Compile(PravdaConfig.CompileMode.Disasm)),
            cmd("dotnet")
              .text("Compile .exe produced by .NET compiler to Pravda VM bytecode. " +
                "Input file is a .Net PE (portable executable). " +
                "Output is binary Pravdaprogram. " +
                "By default read from stdin and print to stdout")
              .action(_ => PravdaConfig.Compile(PravdaConfig.CompileMode.DotNet))
          ),
        cmd("broadcast")
          .text("Broadcast transactions and programs to the Pravda blockchain.")
          .children(
            cmd("run")
              .text("Send a transaction with Pravda Program address to the blockchain to run it")
              .action(_ => PravdaConfig.Broadcast(PravdaConfig.Broadcast.Mode.Run)),
            cmd("transfer")
              .text("Transfer native coins to a given wallet.")
              .action(_ => PravdaConfig.Broadcast(PravdaConfig.Broadcast.Mode.Transfer(None, None)))
              .children(
                opt[String]('t', "to")
                  .action {
                    case (hex,
                          config @ PravdaConfig.Broadcast(mode: PravdaConfig.Broadcast.Mode.Transfer, _, _, _, _, _)) =>
                      config.copy(mode = mode.copy(to = Some(hex)))
                    case (_, otherwise) => otherwise
                  },
                opt[Long]('a', "amount")
                  .action {
                    case (amount,
                          config @ PravdaConfig.Broadcast(mode: PravdaConfig.Broadcast.Mode.Transfer, _, _, _, _, _)) =>
                      config.copy(mode = mode.copy(amount = Some(amount)))
                    case (_, otherwise) => otherwise
                  }
              ),
            cmd("deploy")
              .text("Deploy Pravda program to the blockchain.")
              .action(_ => PravdaConfig.Broadcast(PravdaConfig.Broadcast.Mode.Deploy)),
            cmd("update")
              .text("Update existing Pravda program in the blockchain.")
              .action(_ => PravdaConfig.Broadcast(PravdaConfig.Broadcast.Mode.Update(None)))
              .children(
                opt[String]('p', "program")
                  .action {
                    case (hex, config: PravdaConfig.Broadcast) =>
                      config.copy(mode = PravdaConfig.Broadcast.Mode.Update(Some(hex)))
                    case (_, otherwise) => otherwise
                  }
              ),
            opt[File]('i', "input")
              .text("Input file.")
              .action {
                case (file, config: PravdaConfig.Broadcast) =>
                  config.copy(input = Some(file.getAbsolutePath))
                case (_, otherwise) => otherwise
              },
            opt[File]('w', "wallet")
              .action {
                case (file, config: PravdaConfig.Broadcast) =>
                  config.copy(wallet = Some(file.getAbsolutePath))
                case (_, otherwise) => otherwise
              },
            opt[Long]('l', "limit")
              .text("Watt limit (300 by default).") // FIXME what to do with default values?
              .action {
                case (limit, config: PravdaConfig.Broadcast) =>
                  config.copy(wattLimit = limit)
                case (_, otherwise) => otherwise
              },
            opt[Long]('p', "price")
              .text("Watt price (1 by default).") // FIXME what to do with default values?
              .action {
                case (price, config: PravdaConfig.Broadcast) =>
                  config.copy(wattPrice = NativeCoin @@ price)
                case (_, otherwise) => otherwise
              },
            opt[String]('e', "endpoint")
              .text("Node endpoint (http://localhost:8080/api/public/broadcast by default).") // FIXME what to do with default values?
              .action {
                case (endpoint, config: PravdaConfig.Broadcast) =>
                  config.copy(endpoint = endpoint)
                case (_, otherwise) => otherwise
              },
          ),
        cmd("node")
          .text("Control Pravda Network Node.")
          .action(_ => PravdaConfig.Node(PravdaConfig.Node.Mode.Nope, None))
          .children(
            cmd("init")
              .text("Create data directory and configuration for a new node.")
              .action(_ => PravdaConfig.Node(PravdaConfig.Node.Mode.Init(PravdaConfig.Node.Network.Local, None), None))
              .children(
                opt[Unit]("local")
                  .action {
                    case (_, config @ PravdaConfig.Node(PravdaConfig.Node.Mode.Init(_, initDistrConf), _)) =>
                      config.copy(mode = PravdaConfig.Node.Mode.Init(PravdaConfig.Node.Network.Local, initDistrConf))
                    case (_, otherwise) => otherwise
                  },
                opt[Unit]("testnet")
                  .action {
                    case (_, config @ PravdaConfig.Node(PravdaConfig.Node.Mode.Init(_, initDistrConf), _)) =>
                      config.copy(mode = PravdaConfig.Node.Mode.Init(PravdaConfig.Node.Network.Testnet, initDistrConf))
                    case (_, otherwise) => otherwise
                  },
                opt[String]("init-distr-conf")
                  .action {
                    case (initDistrConf, config @ PravdaConfig.Node(PravdaConfig.Node.Mode.Init(network, _), _)) =>
                      config.copy(mode = PravdaConfig.Node.Mode.Init(network, Some(initDistrConf)))
                    case (_, otherwise) => otherwise
                  }
              ),
            cmd("run")
              .text("Run initialized node.")
              .action(_ => PravdaConfig.Node(PravdaConfig.Node.Mode.Run, None))
              .children(
                opt[File]('d', "data-dir")
                  .action {
                    case (dataDir, config: PravdaConfig.Node) =>
                      config.copy(dataDir = Some(dataDir.getAbsolutePath))
                    case (_, otherwise) => otherwise
                  },
              )
          )
      )
}
