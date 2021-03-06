package eu.jameshamilton.klox.interpret

import eu.jameshamilton.klox.parse.ClassStmt
import eu.jameshamilton.klox.parse.FunctionExpr
import eu.jameshamilton.klox.parse.ModifierFlag
import eu.jameshamilton.klox.parse.ModifierFlag.NATIVE
import eu.jameshamilton.klox.parse.Token
import java.util.EnumSet

interface LoxCallable {
    fun arity(): Int = 0
    fun call(interpreter: Interpreter, arguments: List<Any?> = emptyList()): Any?
}

open class LoxFunction(val classStmt: ClassStmt? = null, val modifiers: EnumSet<ModifierFlag>, val name: String, val declaration: FunctionExpr, private val closure: Environment) : LoxCallable {

    override fun arity(): Int = declaration.params.size

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        val environment = Environment(closure)
        if (declaration.params.isNotEmpty()) for (i in 0 until declaration.params.size) {
            environment.define(declaration.params[i].name.lexeme, arguments[i])
        }

        if (modifiers.contains(NATIVE)) {
            return findNative(interpreter, classStmt?.name?.lexeme, name)(environment, arguments)
        } else {
            try {
                interpreter.executeBlock(declaration.body, environment)
            } catch (e: Interpreter.Return) {
                return if (modifiers.contains(ModifierFlag.INITIALIZER)) closure.getAt(0, "this"); else e.value
            }
        }

        return if (name == "init") closure.getAt(0, "this"); else null
    }

    fun bind(instance: LoxInstance): LoxFunction {
        val environment = Environment(closure)
        environment.define("this", instance)
        return LoxFunction(classStmt, modifiers, name, declaration, environment)
    }

    override fun toString(): String = "<fn $name>"
}

class LoxClass(val name: String, val superClass: LoxClass? = null, private val methods: MutableMap<String, LoxFunction>) :
    LoxCallable {

    override fun call(interpreter: Interpreter, arguments: List<Any?>): LoxInstance {
        val instance = LoxInstance(this)
        val initializer = findMethod("init")
        initializer?.bind(instance)?.call(interpreter, arguments)
        return instance
    }

    override fun arity(): Int =
        findMethod("init")?.arity() ?: super.arity()

    fun findMethod(name: String): LoxFunction? = when {
        methods.containsKey(name) -> methods[name]
        superClass != null -> superClass.findMethod(name)
        else -> null
    }

    override fun toString(): String = name
}

class LoxInstance(val klass: LoxClass, private val fields: MutableMap<String, Any?> = HashMap()) {

    fun get(name: Token, safeAccess: Boolean = false): Any? {
        if (fields.containsKey(name.lexeme)) return fields[name.lexeme]

        val method = klass.findMethod(name.lexeme)
        if (method != null) return method.bind(this)

        if (safeAccess) return null else throw RuntimeError(name, "Undefined property '${name.lexeme}'.")
    }

    fun set(name: Token, value: Any?) {
        fields[name.lexeme] = value
    }

    fun remove(name: Token) {
        if (fields.containsKey(name.lexeme)) fields.remove(name.lexeme)
    }

    fun hasField(name: Token): Boolean = fields.containsKey(name.lexeme)

    override fun toString(): String = "${klass.name} instance"
}
