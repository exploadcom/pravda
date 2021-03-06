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

package pravda.dotnet.translation.opcode

import java.nio.charset.StandardCharsets

import pravda.common.vm.{Data, Opcodes}
import pravda.dotnet.data.TablesData._
import pravda.dotnet.parser.CIL
import pravda.dotnet.parser.CIL._
import pravda.dotnet.parser.Signatures._
import pravda.dotnet.translation.{FieldExtractors, NamesBuilder, TypeDetectors}
import pravda.dotnet.translation.data._
import pravda.vm.asm

/** Translator that handles fields operations */
case object FieldsTranslation extends OneToManyTranslatorOnlyAsm {

  override def asmOpsOne(op: CIL.Op,
                         stackOffsetO: Option[Int],
                         ctx: MethodTranslationCtx): Either[InnerTranslationError, List[asm.Operation]] = {

    // search for TypeDefData and FieldData, these structures have full description of the field
    // MemberRefData has only basic info: name of parent, name of the field, signature
    def refToDef(m: MemberRefData): Option[(TypeDefData, FieldData)] = m match {
      case MemberRefData(TypeRefData(_, name, namespace), fieldName, signatureIdx) =>
        val key = s"${NamesBuilder.fullType(namespace, name)}.$fieldName"
        ctx.tctx.fieldIndex.byName(key)
      case _ => None
    }

    def loadField(p: TypeDefData, fd: FieldData, sigIdx: Long): Either[InnerTranslationError, List[asm.Operation]] = {

      // load from program's storage
      lazy val defaultLoad = Right(
        List(
          pushString(s"p_${fd.name}"), // p for program fields
          asm.Operation(Opcodes.SGET)
        ))

      if (p == ctx.tctx.mainProgramClass) {
        ctx.tctx.signatures.get(sigIdx) match {
          case Some(FieldSig(tpe)) =>
            tpe match {
              case SigType.Generic(TypeDetectors.Mapping(), _) =>
                // if we want to load Mapping we just load the name of the Mapping
                // when we want to access this Mapping we just prepend the name of Mapping to the key
                Right(List(pushBytes(fd.name.getBytes(StandardCharsets.UTF_8))))
              case _ =>
                defaultLoad
            }
          case _ => defaultLoad
        }
      } else if (ctx.tctx.programClasses.contains(p)) {
        Left(InternalError("Reading fields from other [Program] classes is forbidden"))
      } else {
        if (FieldExtractors.isStatic(fd.flags)) {
          Right(
            List(
              // s for static
              // static fields of all user-defined structures lay in the storage
              pushString(s"s_${NamesBuilder.fullTypeDef(p)}_${fd.name}"),
              asm.Operation(Opcodes.SGET)
            ))
        } else {
          // load from structure
          Right(List(asm.Operation.StructGet(Some(Data.Primitive.Utf8(fd.name)))))
        }
      }
    }

    def storeField(p: TypeDefData, fd: FieldData, sigIdx: Long): Either[InnerTranslationError, List[asm.Operation]] = {

      // store to program's storage
      lazy val defaultStore = Right(
        List(
          pushString(s"p_${fd.name}"), // p for program fields
          asm.Operation(Opcodes.SPUT)
        ))

      if (p == ctx.tctx.mainProgramClass) {
        ctx.tctx.signatures.get(sigIdx) match {
          case Some(FieldSig(tpe)) =>
            tpe match {
              case SigType.Generic(TypeDetectors.Mapping(), _) =>
                Right(
                  List(
                    // remove mapping name and key from the stack
                    asm.Operation(Opcodes.POP),
                    asm.Operation(Opcodes.POP),
                    pushString("Mapping modification is forbidden"),
                    asm.Operation(Opcodes.THROW)
                  ))
              case _ => defaultStore
            }
          case _ => defaultStore
        }
      } else if (ctx.tctx.programClasses.contains(p)) {
        Left(InternalError("Writing fields from other [Program] classes is forbidden"))
      } else {
        if (FieldExtractors.isStatic(fd.flags)) {
          Right(
            List(
              // s for static
              // static fields of all user-defined structures lay in the storage
              pushString(s"s_${NamesBuilder.fullTypeDef(p)}_${fd.name}"),
              asm.Operation(Opcodes.SPUT)
            ))
        } else {
          // store to structure
          Right(List(asm.Operation.StructMut(Some(Data.Primitive.Utf8(fd.name)))))
        }
      }
    }

    op match {
      case LdSFld(fd: FieldData) =>
        ctx.tctx.fieldIndex
          .parent(fd.id)
          .map(p => loadField(p, fd, fd.signatureIdx))
          .getOrElse(Left(UnknownOpcode))
      case LdFld(fd: FieldData) =>
        ctx.tctx.fieldIndex
          .parent(fd.id)
          .map(p => loadField(p, fd, fd.signatureIdx))
          .getOrElse(Left(UnknownOpcode))
      case StSFld(fd: FieldData) =>
        ctx.tctx.fieldIndex
          .parent(fd.id)
          .map(p => storeField(p, fd, fd.signatureIdx))
          .getOrElse(Left(UnknownOpcode))
      case StFld(fd: FieldData) =>
        ctx.tctx.fieldIndex
          .parent(fd.id)
          .map(p => storeField(p, fd, fd.signatureIdx))
          .getOrElse(Left(UnknownOpcode))

      case LdSFld(m: MemberRefData) =>
        refToDef(m).map { case (p, fd) => loadField(p, fd, m.signatureIdx) }.getOrElse(Left(UnknownOpcode))
      case LdFld(m: MemberRefData) =>
        refToDef(m).map { case (p, fd) => loadField(p, fd, m.signatureIdx) }.getOrElse(Left(UnknownOpcode))
      case StSFld(m: MemberRefData) =>
        refToDef(m).map { case (p, fd) => storeField(p, fd, m.signatureIdx) }.getOrElse(Left(UnknownOpcode))
      case StFld(m: MemberRefData) =>
        refToDef(m).map { case (p, fd) => storeField(p, fd, m.signatureIdx) }.getOrElse(Left(UnknownOpcode))

      case _ => Left(UnknownOpcode)
    }
  }
}
