interface LoxCallable {
    fun arity(): Int = 0
    fun call(interpreter: Interpreter, arguments: List<Any?>): Any?
}

class LoxFunction(private val declaration: FunctionStmt, private val closure: Environment) : LoxCallable {
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
        return null
    }

    override fun toString(): String = "<fn ${declaration.name.lexeme}>"
}

class LoxClass(val name: String) : LoxCallable {
    override fun call(interpreter: Interpreter, arguments: List<Any?>): LoxInstance = LoxInstance(this)

    override fun toString(): String = name
}

class LoxInstance(private val klass: LoxClass, private val fields: MutableMap<String, Any?> = HashMap()) {

    fun get(name: Token): Any? {
        if (!fields.containsKey(name.lexeme)) {
            throw RuntimeError(name, "Undefined property '${name.lexeme}'")
        }

        return fields[name.lexeme]
    }

    fun set(name: Token, value: Any?) {
        fields[name.lexeme] = value
    }

    override fun toString(): String = "${klass.name} instance"
}
