package eu.jameshamilton.klox.compile

import eu.jameshamilton.klox.compile.Resolver.Companion.free
import eu.jameshamilton.klox.compile.Resolver.Companion.slot
import eu.jameshamilton.klox.compile.Resolver.Companion.temp
import eu.jameshamilton.klox.parse.FunctionExpr
import eu.jameshamilton.klox.parse.VarDef

interface VariableAllocator {
    fun next(): Int
    fun free(index: Int)
}

// TODO: move variable allocation logic in Resolver to here?
class FunctionVariableAllocator(val function: FunctionExpr) : VariableAllocator {
    private val slots = mutableMapOf<Int, VarDef>()
    override fun next(): Int {
        val temp = function.temp()
        val slot = function.slot(temp)
        slots[slot] = temp
        return slot
    }

    override fun free(index: Int) {
        function.free(slots[index]!!)
    }
}
