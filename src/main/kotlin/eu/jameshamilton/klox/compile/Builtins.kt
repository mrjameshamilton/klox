package eu.jameshamilton.klox.compile

import eu.jameshamilton.klox.parse.FunctionStmt
import eu.jameshamilton.klox.parse.FunctionType.*
import eu.jameshamilton.klox.parse.Parameter
import eu.jameshamilton.klox.parse.Token
import eu.jameshamilton.klox.parse.TokenType.*
import proguard.classfile.editor.CompactCodeAttributeComposer as Composer

// Built-in Klox functions

val builtIns = listOf(
    NativeFunctionStmt(Token(IDENTIFIER, "clock"), emptyList()) {
        invokestatic("java/lang/System", "currentTimeMillis", "()J")
        l2d()
        pushDouble(1000.0)
        ddiv()
        box("java/lang/Double")
        areturn()
    }
)

class NativeFunctionStmt(override val name: Token, override val params: List<Parameter>, val code: Composer.() -> Composer) :
    FunctionStmt(name, NATIVE, params, emptyList()) {

    override fun toString(): String = "<native fn>"
}
