import TokenType.*

class Interpreter : Visitor<Any?> {

    fun interpret(expr: Expr): String? {
        return try {
            val value = evaluate(expr)
            stringify(value)
        } catch (e: RuntimeError) {
            runtimeError(e)
            return null
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

    private fun checkNumberOperand(operator: Token, operand: Any?) {
        if (operand !is Double)
            throw RuntimeError(operator, "Operand must be a number")
    }

    private fun checkNumberOperands(operator: Token, left: Any?, right: Any?) {
        if (!(left is Double && right is Double))
            throw RuntimeError(operator, "Operands must be numbers")
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
}

class RuntimeError(val token: Token, override val message: String) : RuntimeException(message)