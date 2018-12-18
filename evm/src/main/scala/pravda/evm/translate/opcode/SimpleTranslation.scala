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

package pravda.evm.translate.opcode

import pravda.evm.EVM
import pravda.evm.translate.Translator.Converted
import pravda.vm.asm
import pravda.vm._
import pravda.vm.asm.Operation

object SimpleTranslation {

  import pravda.evm.EVM._

  val pow2_256 = scala.BigInt(2).pow(256) - 1

  private val translate: PartialFunction[EVM.Op, List[asm.Operation]] = {
    case Push(bytes) => pushBigInt(BigInt(1, bytes.toArray)) :: Nil

    case Pop => codeToOps(Opcodes.POP)

    case Add => codeToOps(Opcodes.ADD) //FIXME result % 2^256
    case Mul => codeToOps(Opcodes.MUL)
    case Div => codeToOps(Opcodes.DIV) //FIXME 0 if stack[1] == 0 othervise s[0] / s[1]
    case Mod => codeToOps(Opcodes.MOD) //FIXME 0 if stack[1] == 0 othervise s[0] % s[1]
    case Sub => sub //FIXME result & (2^256 - 1)
    case AddMod =>
      dupn(3) ::: codeToOps(Opcodes.SWAP, Opcodes.MOD, Opcodes.SWAP) ::: dupn(3) :::
        codeToOps(Opcodes.SWAP, Opcodes.MOD, Opcodes.ADD, Opcodes.MOD)
    case MulMod =>
      dupn(3) ::: codeToOps(Opcodes.SWAP, Opcodes.MOD, Opcodes.SWAP) ::: dupn(3) :::
        codeToOps(Opcodes.SWAP, Opcodes.MOD, Opcodes.MUL, Opcodes.MOD)

    //  case Not => codeToOps(Opcodes.NOT) //TODO (2^256 - 1) - s[0]

    case And => codeToOps(Opcodes.AND)
    case Or  => codeToOps(Opcodes.OR)
    case Xor => codeToOps(Opcodes.XOR)

    case Byte =>
      List(
        pushBigInt(31) :: Nil,
        sub,
        pushBigInt(8) :: Nil,
        codeToOps(Opcodes.MUL),
        pushBigInt(2) :: Nil,
        callExp,
        codeToOps(Opcodes.SWAP),
        codeToOps(Opcodes.DIV),
        pushBigInt(0xff) :: Nil,
        codeToOps(Opcodes.AND)
      ).flatten

    case IsZero => pushBigInt(BigInt(0)) :: codeToOps(Opcodes.EQ) ::: cast(Data.Type.BigInt)
    case Lt     => codeToOps(Opcodes.LT) ::: cast(Data.Type.BigInt)
    case Gt     => codeToOps(Opcodes.GT) ::: cast(Data.Type.BigInt)
    case Eq     => codeToOps(Opcodes.EQ) ::: cast(Data.Type.BigInt)

    case Jump(_, dest)  => codeToOps(Opcodes.POP) ::: Operation.Jump(Some(nameByAddress(dest))) :: Nil
    case JumpI(_, dest) => jumpi(dest)

    case Stop => codeToOps(Opcodes.STOP)

    case Dup(n)  => if (n > 1) dupn(n) else codeToOps(Opcodes.DUP)
    case Swap(n) => if (n > 1) swapn(n + 1) else codeToOps(Opcodes.SWAP)

    case Balance => codeToOps(Opcodes.BALANCE)
    case Address => codeToOps(Opcodes.PADDR)

    case JumpDest(address) => asm.Operation.Label(nameByAddress(address)) :: Nil

    case SStore => codeToOps(Opcodes.SPUT)
    case SLoad  => codeToOps(Opcodes.SGET)

    case MLoad(size) =>
      pushInt(size + 1) :: codeToOps(Opcodes.DUPN, Opcodes.SWAP) ::: Operation.Call(Some("read_word")) :: Nil

    case MStore(size) =>
      pushInt(size + 1) ::
        codeToOps(Opcodes.DUPN) ::: pushInt(3) :: codeToOps(Opcodes.SWAPN, Opcodes.SWAP) :::
        Operation.Call(Some("write_word")) ::
        pushInt(size) ::
        codeToOps(Opcodes.SWAPN, Opcodes.POP)

    //TODO DELETE ME
    case Not    => pushBigInt(pow2_256) :: sub ::: Nil
    case Revert => codeToOps(Opcodes.STOP)
    case Return => codeToOps(Opcodes.RET)
    case Return(size) =>
      pushInt(size + 1) ::
        codeToOps(Opcodes.DUPN) ::: pushInt(3) :: codeToOps(Opcodes.SWAPN, Opcodes.SWAP) :::
        Operation.Call(Some("read_bytes")) :: codeToOps(Opcodes.STOP) ::: Nil

    case CallValue    => pushBigInt(scala.BigInt(0)) :: Nil
    case CallDataSize => pushBigInt(scala.BigInt(1000)) :: Nil
    case CallDataLoad => codeToOps(Opcodes.POP) ::: pushBigInt(scala.BigInt(1000)) :: Nil
    case Invalid      => codeToOps(Opcodes.STOP)
    case Sha3(size) =>
      pushInt(size + 1) ::
        codeToOps(Opcodes.DUPN) ::: pushInt(3) :: codeToOps(Opcodes.SWAPN, Opcodes.SWAP) :::
        Operation.Call(Some("read_bytes")) :: Nil

    case Caller => codeToOps(Opcodes.FROM)

  }

  def evmOpToOps(op: EVM.Op): Converted =
    translate.lift(op).toRight(op)
}
