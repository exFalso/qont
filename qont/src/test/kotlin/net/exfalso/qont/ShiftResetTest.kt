package net.exfalso.qont

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.SuspendableCallable
import org.junit.Test

sealed class ShiftResetTree {
    data class Reset(val inside: List<ShiftResetTree>) : ShiftResetTree()
    data class Shift(val inside: List<ShiftResetTree>) : ShiftResetTree()
    data class Cont(val shiftIndex: Int) : ShiftResetTree()
}

data class PrettyPrintLine(val indentationLevel: Int, val line: String)

private fun prettyShiftResetTreeMutable(tree: ShiftResetTree, indentationLevel: Int, contCounter: Int, result: ArrayList<PrettyPrintLine>) {
    when (tree) {
        is ShiftResetTree.Reset -> {
            result.add(PrettyPrintLine(indentationLevel, "reset(${SuspendableCallable::class.simpleName} @${Suspendable::class.simpleName} {"))
            for (inside in tree.inside) {
                prettyShiftResetTreeMutable(inside, indentationLevel + 1, contCounter, result)
            }
            result.add(PrettyPrintLine(indentationLevel, "})"))
        }
        is ShiftResetTree.Shift -> {
            result.add(PrettyPrintLine(indentationLevel, "shift(${SuspendableFunction::class.simpleName} @${Suspendable::class.simpleName} { cont$contCounter ->"))
            for (inside in tree.inside) {
                prettyShiftResetTreeMutable(inside, indentationLevel + 1, contCounter, result)
            }
            result.add(PrettyPrintLine(indentationLevel, "})"))
        }
        is ShiftResetTree.Cont -> {
            result.add(PrettyPrintLine(indentationLevel, "cont${tree.shiftIndex}(Unit)"))
        }
    }
}

fun prettyShiftResetTree(tree: ShiftResetTree, indentationUnit: String = "    "): String {
    val lines = ArrayList<PrettyPrintLine>()
    prettyShiftResetTreeMutable(tree, 0, 0, lines)
    val resultBuilder = StringBuilder()
    for ((indentation, line) in lines) {
        for (i in 1 .. indentation) {
            resultBuilder.append(indentationUnit)
        }
        resultBuilder.appendln(line)
    }
    return resultBuilder.toString()
}

fun main(args: Array<String>) {
    println(prettyShiftResetTree(
            ShiftResetTree.Reset(listOf(
                    ShiftResetTree.Shift(listOf(
                            ShiftResetTree.Cont(0)
                    ))
            ))
    ))
}

class ShiftResetTest {
    @Test
    fun test() {

    }
}