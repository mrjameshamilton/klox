import FunctionType.*

interface LoxCallable {
    fun arity(): Int = 0
    fun call(interpreter: Interpreter, arguments: List<Any?> = emptyList()): Any?
}

open class LoxFunction(val declaration: FunctionStmt, private val closure: Environment) : LoxCallable {

    override fun arity(): Int = declaration.params.size

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        val environment = Environment(closure)
        if (declaration.params.isNotEmpty()) for (i in 0 until declaration.params.size) {
            environment.define(declaration.params[i].lexeme, arguments[i])
        }
        try {
            interpreter.executeBlock(declaration.body, environment)
        } catch (e: Interpreter.Return) {
            return e.value
        }

        return if (declaration.kind == INITIALIZER) closure.getAt(0, "this"); else null
    }

    fun bind(instance: LoxInstance): LoxFunction {
        val environment = Environment(closure)
        environment.define("this", instance)
        return LoxFunction(declaration, environment)
    }

    override fun toString(): String = "<fn ${declaration.name.lexeme}>"
}

class LoxClass(val name: String, private val methods: MutableMap<String, LoxFunction>) : LoxCallable {

    override fun call(interpreter: Interpreter, arguments: List<Any?>): LoxInstance {
        val instance = LoxInstance(this)
        val initializer = findMethod("init")
        initializer?.bind(instance)?.call(interpreter, arguments)
        return instance
    }

    override fun arity(): Int =
        findMethod("init")?.arity() ?: super.arity()

    fun findMethod(name: String): LoxFunction? = methods[name]

    override fun toString(): String = name
}

class LoxInstance(private val klass: LoxClass, private val fields: MutableMap<String, Any?> = HashMap()) {

    fun get(name: Token): Any {
        val field = fields[name.lexeme]
        if (field != null) return field

        val method = klass.findMethod(name.lexeme)
        if (method != null) return method.bind(this)

        throw RuntimeError(name, "Undefined property '${name.lexeme}'")
    }

    fun set(name: Token, value: Any?) {
        fields[name.lexeme] = value
    }

    override fun toString(): String = "${klass.name} instance"
}
