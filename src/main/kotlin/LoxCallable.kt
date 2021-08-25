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
