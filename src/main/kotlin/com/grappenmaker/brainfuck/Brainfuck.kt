@file:JvmName("Brainfuck")

package com.grappenmaker.brainfuck

import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // Find the file to read from and read it
    val program = args.getOrNull(0)?.let { File(it).readText() }
        ?: System.`in`.bufferedReader().readText()

    // Parse the program
    val parsed = try {
        parse(program)
    } catch (e: ParseException) {
        println("Error while parsing the brainfuck program: ${e.message}")
        exitProcess(1)
    }

    // Execute the program
    println("-- Executing program --")
    if (!intepret(parsed)) {
        println("-- Program execution failed --")
    }

    println("-- Program finished --")
}

// Parse the program
fun parse(program: String): List<Instruction> {
    // Keep track of the current index and the loop stack
    val loopStack = ArrayDeque<MutableList<Instruction>>()
    var index = 0

    val result: List<Instruction> = buildList {
        while (index in program.indices) {
            val toAdd = loopStack.lastOrNull() ?: this
            when (program[index]) {
                '>' -> Instruction.MoveRight
                '<' -> Instruction.MoveLeft
                '+' -> Instruction.Increment
                '-' -> Instruction.Decrement
                ',' -> Instruction.Read
                '.' -> Instruction.Write
                '[' -> {
                    loopStack.add(mutableListOf())
                    null
                }
                ']' -> {
                    val currentLoop =
                        loopStack.removeLastOrNull() ?: parseError("Loop end without loop start at $index")
                    (loopStack.lastOrNull() ?: this).add(Instruction.Loop(currentLoop))
                    null
                }
                else -> null // Invalid instruction, should be considered a comment
            }?.let { toAdd.add(it) }

            index++
        }
    }

    if (loopStack.isNotEmpty()) parseError("Unmatched loop end to loop start at $index")
    return result
}

// Utility to throw a parse error
fun parseError(message: String): Nothing = throw ParseException(message)
class ParseException(message: String) : Exception(message)

// Interpret the program (execute it)
fun intepret(
    instructions: List<Instruction>,
    memory: Memory = Memory()
): Boolean {
    for (instruction in instructions) {
        when (instruction) {
            is Instruction.MoveRight -> memory.pointer++
            is Instruction.MoveLeft -> memory.pointer--
            is Instruction.Increment -> memory.current++
            is Instruction.Decrement -> memory.current--
            is Instruction.Read -> memory.current = System.`in`.read().toUByte()
            is Instruction.Write -> print(memory.current.toInt().toChar())
            is Instruction.Loop -> {
                while (memory.current != 0.toUByte()) {
                    if (!intepret(instruction.instructions, memory)) return false
                }
            }
        }

        if (memory.pointer !in 0..30000) {
            println("Memory pointer overflow! Pointer is at ${memory.pointer}!")
            return false
        }
    }

    return true
}

// The instruction set of brainfuck
sealed class Instruction {
    object MoveLeft : Instruction()
    object MoveRight : Instruction()
    object Increment : Instruction()
    object Decrement : Instruction()
    object Read : Instruction()
    object Write : Instruction()
    data class Loop(val instructions: List<Instruction>) : Instruction()

    override fun toString(): String = javaClass.simpleName
}

// Utility to check for a ubyte inside a range
operator fun IntRange.contains(b: UByte) = contains(b.toInt())

// The memory of the brainfuck turing machine
class Memory(private val delegate: MutableMap<Int, UByte> = hashMapOf()) : MutableMap<Int, UByte> by delegate {
    var pointer: Int = 0
    var current: UByte
        get() = delegate.getOrPut(pointer) { 0.toUByte() }
        set(value) {
            delegate[pointer] = value
        }

    override fun toString() = delegate.toString()
}