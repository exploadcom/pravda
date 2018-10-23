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

package pravda.evm.translate

import pravda.evm.EVM._
import pravda.vm.asm
import cats.instances.list._
import cats.instances.either._
import cats.syntax.traverse._
import pravda.evm.EVM
import pravda.evm.translate.opcode.{JumpDestinationPrepare, SimpleTranslation}
import pravda.vm.asm.Operation

object Translator {

  private val startLabelName = "__start_evm_program"

  def apply(ops: List[EVM.Op]): Either[String, List[asm.Operation]] = {
    ops.map(SimpleTranslation.evmOpToOps).sequence.map(_.flatten)
  }

  def translateActualContract(ops: List[(Int, EVM.Op)]): Either[String, List[asm.Operation]] = {
    ops
      .takeWhile({
        case (_, CodeCopy) => false
        case _             => true
      })
      .reverse
      .tail
      .headOption match {
      case Some((_, Push(address))) =>
        val offset = BigInt(1, address.toArray).intValue()

        val filteredOps = ops
          .map({ case (ind, op) => ind - offset -> op })
          .filterNot(_._1 < 0)
          .map({
            case (ind, JumpDest) => JumpDest(ind)
            case (_, op)         => op
          })

        import JumpDestinationPrepare._
        val jumpDests = filteredOps.collect({ case j @ JumpDest(x) => j }).zipWithIndex
        val prepare = jumpDests.flatMap(jumpDestToOps)
        Translator(filteredOps).map(
          opcodes =>
            Operation.Jump(Some(startLabelName))
              :: addLastBranch(prepare, jumpDests.size) ++
              (asm.Operation.Label(startLabelName) :: opcodes))

      case _ => Left("Parse error")
    }

  }

}
