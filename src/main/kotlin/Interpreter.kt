import FunctionType.*
import TokenType.*
import Expr.Visitor as ExprVisitor
import Stmt.Visitor as StmtVisitor

class Interpreter : ExprVisitor<Any?>, StmtVisitor<Unit> {

    private val globals = Environment().apply {
        define(
            "clock",
            object : LoxCallable {
                override fun call(interpreter: Interpreter, arguments: List<Any?>): Any =
                    System.currentTimeMillis() / 1000.0

                override fun toString(): String = "<native fn>"
            }
        )
    }

    private val locals = mutableMapOf<Expr, Int>()

    private var environment = globals

    fun interpret(stmts: List<Stmt>) {
        try {
            stmts.forEach { execute(it) }
        } catch (e: RuntimeError) {
            runtimeError(e)
        }
    }

    fun resolve(expr: Expr, depth: Int) {
        locals[expr] = depth
    }

    private fun execute(stmt: Stmt) {
        stmt.accept(this)
    }

    fun executeBlock(stmts: List<Stmt>, environment: Environment) {
        val previous = this.environment
        try {
            this.environment = environment
            stmts.forEach { execute(it) }
        } finally {
            this.environment = previous
        }
    }

    private fun evaluate(expr: Expr): Any? = expr.accept(this)

    override fun visitBinaryExpr(expr: BinaryExpr): Any {
        val right = evaluate(expr.right)
        val left = evaluate(expr.left)

        when (expr.operator.type) {
            MINUS, SLASH, STAR, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL ->
                checkNumberOperands(expr.operator, left, right)
            else -> { }
        }

        return when (expr.operator.type) {
            MINUS -> (left as Double) - (right as Double)
            SLASH -> {
                if (right as Double == 0.0) return Double.NaN
                return left as Double / right
            }
            STAR -> (left as Double) * (right as Double)
            GREATER -> (left as Double) > (right as Double)
            GREATER_EQUAL -> (left as Double) >= (right as Double)
            LESS -> (left as Double) < (right as Double)
            LESS_EQUAL -> (left as Double) <= (right as Double)
            BANG_EQUAL -> !isEqual(left, right)
            EQUAL_EQUAL -> isEqual(left, right)
            PLUS -> return when {
                left is Double && right is Double -> left + right
                left is String && right is String -> "$left$right"
                left is Double && right is String -> "${stringify(left)}$right"
                right is Double && left is String-> "$left${stringify(right)}"
                else -> throw RuntimeError(expr.operator, "Operands must be two numbers or two strings.")
            }
            else -> throw RuntimeError(expr.operator, "Not implemented")
        }
    }

    override fun visitUnaryExpr(unaryExpr: UnaryExpr): Any? {
        val right = evaluate(unaryExpr.right)

        return when (unaryExpr.operator.type) {
            BANG -> !isTruthy(right)
            MINUS -> {
                checkNumberOperand(unaryExpr.operator, right)
                -(right as Double)
            }
            else -> null
        }
    }

    override fun visitCallExpr(callExpr: CallExpr): Any? {
        val callee = evaluate(callExpr.callee)

        val arguments = callExpr.arguments.map { evaluate(it) }

        if (callee !is LoxCallable) {
            throw RuntimeError(callExpr.paren, "Can only call functions and classes.")
        }

        if (callee.arity() != arguments.size) {
            throw RuntimeError(callExpr.paren, "Expected ${callee.arity()} arguments but got ${arguments.size}.")
        }

        return callee.call(this, arguments)
    }

    override fun visitGetExpr(getExpr: GetExpr): Any? = when (val obj = evaluate(getExpr.obj)) {
        is LoxInstance -> {
            val value = obj.get(getExpr.name)
            if (value is LoxFunction && value.declaration.kind == GETTER) value.call(this) else value
        }
        is LoxClass -> obj.findMethod(getExpr.name.lexeme)
        else -> throw RuntimeError(getExpr.name, "Only instances have properties.")
    }

    override fun visitSetExpr(setExpr: SetExpr): Any? {
        val obj = evaluate(setExpr.obj)
        if (obj !is LoxInstance) {
            throw RuntimeError(setExpr.name, "Only instances have fields.")
        }

        obj.set(setExpr.name, evaluate(setExpr.value))

        return evaluate(setExpr.value)
    }

    override fun visitFunctionStmt(functionStmt: FunctionStmt) {
        val function = LoxFunction(functionStmt, environment)
        environment.define(functionStmt.name.lexeme, function)
    }

    override fun visitGroupingExpr(groupingExpr: GroupingExpr): Any? =
        evaluate(groupingExpr.expression)

    override fun visitLiteralExpr(literalExpr: LiteralExpr): Any? =
        literalExpr.value

    override fun visitLogicalExpr(logicalExpr: LogicalExpr): Any? {
        val left = evaluate(logicalExpr.left)

        when (logicalExpr.operator.type) {
            OR -> if (isTruthy(left)) return left
            AND -> if (!isTruthy(left)) return left
            else -> throw RuntimeError(logicalExpr.operator, "Invalid logical operator.")
        }

        return evaluate(logicalExpr.right)
    }

    private fun checkNumberOperand(operator: Token, operand: Any?) {
        if (operand !is Double) {
            throw RuntimeError(operator, "Operand must be a number.")
        }
    }

    private fun checkNumberOperands(operator: Token, left: Any?, right: Any?) {
        if (left == null && right == null) return

        if (!(left is Double && right is Double)) {
            throw RuntimeError(operator, "Operands must be numbers.")
        }
    }

    private fun isTruthy(value: Any?): Boolean = when (value) {
        is Boolean -> value
        null -> false
        else -> true
    }

    private fun isEqual(a: Any?, b: Any?): Boolean =
        if (a == null && b == null) true
        else when {
            a is Double && b is Double && a.isNaN() && b.isNaN() -> false
            else -> a?.equals(b) ?: false
        }

    private fun stringify(value: Any?): String = when (value) {
        null -> "nil"
        is Double -> {
            val text = value.toString()
            if (text.endsWith(".0")) text.substring(0, text.length - 2) else text
        }
        else -> value.toString()
    }

    override fun visitExprStmt(stmt: ExprStmt) {
        evaluate(stmt.expression)
    }

    override fun visitIfStmt(ifStmt: IfStmt) {
        if (isTruthy(evaluate(ifStmt.condition))) {
            execute(ifStmt.thenBranch)
        } else if (ifStmt.elseBranch != null) {
            execute(ifStmt.elseBranch)
        }
    }

    override fun visitPrintStmt(print: PrintStmt) {
        println(stringify(evaluate(print.expression)))
    }

    override fun visitReturnStmt(returnStmt: ReturnStmt) {
        throw Return(if (returnStmt.value != null) evaluate(returnStmt.value); else null)
    }

    override fun visitWhileStmt(whileStmt: WhileStmt) {
        while (isTruthy(evaluate(whileStmt.condition))) {
            try {
                execute(whileStmt.body)
            } catch (ignored: Break) {
                break
            } catch (ignored: Continue) {
                continue
            }
        }
    }

    override fun visitBreakStmt(breakStmt: BreakStmt) =
        throw Break()

    override fun visitContinueStmt(continueStmt: ContinueStmt) =
        throw Continue()

    override fun visitVarStmt(`var`: VarStmt) {
        environment.define(
            `var`.token.lexeme,
            if (`var`.initializer != null) evaluate(`var`.initializer) else null
        )
    }

    override fun visitVariableExpr(variableExpr: VariableExpr): Any? =
        lookupVariable(variableExpr.name, variableExpr)

    override fun visitAssignExpr(assignExpr: AssignExpr): Any? {
        val value = evaluate(assignExpr.value)
        val distance = locals[assignExpr]
        if (distance != null) {
            environment.assignAt(distance, assignExpr.name, value)
        } else globals.assign(assignExpr.name, value)
        return value
    }

    override fun visitBlockStmt(block: BlockStmt) =
        executeBlock(block.stmts, Environment(environment))

    override fun visitClassStmt(classStmt: ClassStmt) {
        val superClass = if (classStmt.superClass != null) {
            evaluate(classStmt.superClass)
        } else null

        if (classStmt.superClass != null && superClass !is LoxClass) {
            throw RuntimeError(classStmt.superClass.name, "Superclass must be a class.")
        }

        environment.define(classStmt.name.lexeme)

        if (classStmt.superClass != null) {
            environment = Environment(environment)
            environment.define("super", superClass)
        }

        val methods = mutableMapOf<String, LoxFunction>()
        classStmt.methods.forEach {
            methods[it.name.lexeme] = LoxFunction(it, environment)
        }

        val klass = LoxClass(classStmt.name.lexeme, superClass as LoxClass?, methods)

        if (superClass != null) {
            environment = environment.enclosing!!
        }

        environment.assign(classStmt.name, klass)
    }

    override fun visitSuperExpr(superExpr: SuperExpr): Any {
        val distance = locals.getOrDefault(superExpr, 0)
        val superClass = environment.getAt(distance, "super") as LoxClass

        // Look up this in the inner environment
        val obj = environment.getAt(distance - 1, "this") as LoxInstance

        val method = superClass.findMethod(superExpr.method.lexeme)
            ?: throw RuntimeError(superExpr.method, "Undefined property '${superExpr.method.lexeme}'.")

        return method.bind(obj)
    }

    override fun visitThisExpr(thisExpr: ThisExpr): Any? =
        lookupVariable(thisExpr.keyword, thisExpr)

    private fun lookupVariable(name: Token, expr: Expr): Any? {
        val distance = locals[expr]
        return if (distance != null) environment.getAt(distance, name.lexeme); else globals.get(name)
    }

    private class Break : RuntimeException()
    private class Continue : RuntimeException()
    class Return(val value: Any?) : RuntimeException()
}

class RuntimeError(val token: Token, override val message: String) : RuntimeException(message)

class Environment(val enclosing: Environment? = null) {
    private val values: MutableMap<String, Any?> = mutableMapOf()

    fun define(name: String, value: Any? = null) {
        values[name] = value
    }

    fun get(name: Token): Any? {
        return if (values.containsKey(name.lexeme)) values[name.lexeme]
        else if (enclosing != null) enclosing.get(name)
        else {
            throw RuntimeError(name, "Undefined variable '${name.lexeme}'.")
        }
    }

    fun getAt(distance: Int, name: String): Any? {
        return ancestor(distance).values[name]
    }

    fun assign(name: Token, value: Any?) {
        return if (values.containsKey(name.lexeme)) values[name.lexeme] = value
        else if (enclosing != null) enclosing.assign(name, value)
        else throw RuntimeError(name, "Undefined variable '${name.lexeme}'.")
    }

    fun assignAt(distance: Int, name: Token, value: Any?) {
        ancestor(distance).values[name.lexeme] = value
    }

    private fun ancestor(distance: Int): Environment {
        var environment = this
        for (i in 0 until distance) {
            environment = environment.enclosing!!
        }
        return environment
    }
}
