import TokenType.*
import Expr.Visitor as ExprVisitor
import Stmt.Visitor as StmtVisitor

class Interpreter : ExprVisitor<Any?>, StmtVisitor<Unit> {

    private var environment = Environment()

    fun interpret(stmts: List<Stmt>) {
        try {
            stmts.forEach { execute(it) }
        } catch (e: RuntimeError) {
            runtimeError(e)
        }
    }

    private fun execute(stmt: Stmt) {
        stmt.accept(this)
    }

    private fun executeBlock(stmts: List<Stmt>, environment: Environment) {
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
            MINUS, SLASH, STAR, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL, BANG_EQUAL, EQUAL_EQUAL ->
                checkNumberOperands(expr.operator, left, right)
            else -> { }
        }

        return when (expr.operator.type) {
            MINUS -> (left as Double) - (right as Double)
            SLASH -> {
                if (right as Double == 0.0) throw RuntimeError(expr.operator, "Cannot divide ${stringify(left)} by zero")
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
                left is Double -> "${stringify(left)}$right"
                right is Double -> "$left${stringify(right)}"
                else -> throw RuntimeError(expr.operator, "Operands must be two numbers or two strings")
            }
            else -> throw RuntimeError(expr.operator, "Not implemented")
        }
    }

    override fun visitUnaryExpr(unaryExpr: UnaryExpr): Any? {
        val right = evaluate(unaryExpr.right)

        return when (unaryExpr.operator.type) {
            BANG -> !isTruthy(right)
            MINUS -> {
                checkNumberOperand(unaryExpr.operator, unaryExpr.right)
                -(right as Double)
            }
            else -> null
        }
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
            else -> throw RuntimeError(logicalExpr.operator, "Invalid logical operator")
        }

        return evaluate(logicalExpr.right)
    }

    private fun checkNumberOperand(operator: Token, operand: Any?) {
        if (operand !is Double) {
            throw RuntimeError(operator, "Operand must be a number")
        }
    }

    private fun checkNumberOperands(operator: Token, left: Any?, right: Any?) {
        if (!(left is Double && right is Double)) {
            throw RuntimeError(operator, "Operands must be numbers")
        }
    }

    private fun isTruthy(value: Any?): Boolean = when (value) {
        is Boolean -> value
        null -> false
        else -> true
    }

    private fun isEqual(a: Any?, b: Any?): Boolean =
        if (a == null && b == null) true else a?.equals(b) ?: false

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

    override fun visitWhileStmt(whileStmt: WhileStmt) {
        while (isTruthy(evaluate(whileStmt.condition))) {
            try {
                execute(whileStmt.body)
            } catch (ignored: Break) {
                break
            }
        }
    }

    override fun visitBreakStmt(breakStmt: BreakStmt) =
        throw Break()

    override fun visitVarStmt(`var`: VarStmt) {
        environment.define(
            `var`.token.lexeme,
            if (`var`.initializer != null) evaluate(`var`.initializer) else null
        )
    }

    override fun visitVariableExpr(variableExpr: VariableExpr): Any? =
        environment.get(variableExpr.name)

    override fun visitAssignExpr(assignExpr: AssignExpr) {
        environment.assign(assignExpr.name, evaluate(assignExpr.value))
    }

    override fun visitBlockStmt(block: BlockStmt) =
        executeBlock(block.stmts, Environment(environment))

    private class Break : RuntimeException()
}

class RuntimeError(val token: Token, override val message: String) : RuntimeException(message)

class Environment(private val enclosing: Environment? = null) {
    private val values: MutableMap<String, Any?> = mutableMapOf()

    fun define(name: String, value: Any?) {
        values[name] = value
    }

    fun get(name: Token): Any? {
        return if (values.containsKey(name.lexeme)) values[name.lexeme]
        else if (enclosing != null) enclosing.get(name)
        else throw RuntimeError(name, "Undefined variable '${name.lexeme}'")
    }

    fun assign(name: Token, value: Any?) {
        return if (values.containsKey(name.lexeme)) values[name.lexeme] = value
        else if (enclosing != null) enclosing.assign(name, value)
        else throw RuntimeError(name, "Undefined variable '${name.lexeme}'")
    }
}
