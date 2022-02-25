@file:JvmName("Bf2Jvm")

package com.grappenmaker.brainfuck

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import java.io.File
import java.io.InputStream
import java.io.PrintStream
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KProperty
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // Just like the intepreter, find the file to read from and read it
    val filename = args.getOrNull(0) ?: error("No file specified")
    val file = File(filename)
    val program = file.readText()

    // Parse the program
    val parsed = try {
        parse(program)
    } catch (e: ParseException) {
        println("Error while parsing the brainfuck program: ${e.message}")
        exitProcess(1)
    }

    // Define classname
    val className = file.nameWithoutExtension

    // Generate the class
    val classFile = with(ClassWriter(ClassWriter.COMPUTE_FRAMES)) {
        visit(V1_8, ACC_PUBLIC or ACC_SUPER, className, null, "java/lang/Object", null)

        // Define main method
        visitMethod(ACC_PUBLIC or ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null).apply {
            visitCode()

            // Define memory array
            visitLdcInsn(30000)
            visitIntInsn(NEWARRAY, T_BYTE)
            visitVarInsn(ASTORE, 0)

            // Define current pointer
            visitInsn(ICONST_0)
            visitVarInsn(ISTORE, 1)

            val getCurrent = {
                visitVarInsn(ALOAD, 0)
                visitVarInsn(ILOAD, 1)
                visitInsn(BALOAD)
            }

            val increment = { opcode: Int ->
                visitVarInsn(ALOAD, 0)
                visitVarInsn(ILOAD, 1)
                getCurrent()
                visitInsn(opcode)
                visitInsn(IADD)
                visitInsn(BASTORE)
            }

            // Compile the program
            fun compile(insns: List<Instruction>) {
                insns.forEach {
                    when (it) {
                        is Instruction.MoveRight -> visitIincInsn(1, 1)
                        is Instruction.MoveLeft -> visitIincInsn(1, -1)
                        is Instruction.Increment -> increment(ICONST_1)
                        is Instruction.Decrement -> increment(ICONST_M1)
                        is Instruction.Read -> {
                            visitVarInsn(ALOAD, 0)
                            visitVarInsn(ILOAD, 1)
                            getField(System::class.java.getField("in"))
                            invokeMethod(InputStream::class.java.getMethod("read"))
                            visitInsn(I2B)
                            visitInsn(BASTORE)
                        }
                        is Instruction.Write -> {
                            getField(System::class.java.getField("out"))
                            getCurrent()
                            visitInsn(I2C)
                            invokeMethod(PrintStream::class.java.getMethod("print", Char::class.javaPrimitiveType))
                        }
                        is Instruction.Loop -> {
                            val start = Label()
                            val end = Label()
                            visitLabel(start)

                            getCurrent()
                            visitJumpInsn(IFEQ, end)
                            compile(it.instructions)
                            visitJumpInsn(GOTO, start)

                            visitLabel(end)
                        }
                    }
                }
            }

            compile(parsed)

            visitInsn(RETURN)
            visitMaxs(-1, -1)
            visitEnd()
        }

        visitEnd()
        toByteArray()
    }

    // Write classfile to file
    File(file.parentFile, "$className.class").writeBytes(classFile)
}

fun MethodVisitor.invokeMethod(method: Method, opcode: Int = INVOKEVIRTUAL) = visitMethodInsn(
    opcode,
    Type.getInternalName(method.declaringClass),
    method.name,
    Type.getMethodDescriptor(method),
    false
)

fun MethodVisitor.getField(field: Field) = visitFieldInsn(
    if (Modifier.isStatic(field.modifiers)) GETSTATIC else GETFIELD,
    Type.getInternalName(field.declaringClass),
    field.name,
    Type.getDescriptor(field.type)
)