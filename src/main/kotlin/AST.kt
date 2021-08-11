import TokenType.MINUS
import TokenType.STAR

interface Expr {
    fun <R> accept(visitor: Visitor<R>): R

    interface Visitor<R> {
        fun visitBinaryExpr(expr: BinaryExpr): R
        fun visitUnaryExpr(unaryExpr: UnaryExpr): R
        fun visitGroupingExpr(groupingExpr: GroupingExpr): R
        fun visitLiteralExpr(literalExpr: LiteralExpr): R
    }
}

data class BinaryExpr(val left: Expr, val operator: Token, val right: Expr) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitBinaryExpr(this)
}

data class UnaryExpr(val operator: Token, val right: Expr) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitUnaryExpr(this)
}

data class GroupingExpr(val expression: Expr) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>) =
        visitor.visitGroupingExpr(this)
}

data class LiteralExpr(val value: Any?) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitLiteralExpr(this)
}


class AstPrinter : Expr.Visitor<String> {
    fun print(expr: Expr) = expr.accept(this)

    override fun visitBinaryExpr(expr: BinaryExpr): String =
        parenthesize(expr.operator.lexeme, expr.left, expr.right)

    override fun visitUnaryExpr(unaryExpr: UnaryExpr): String =
        parenthesize(unaryExpr.operator.lexeme, unaryExpr.right)

    override fun visitGroupingExpr(groupingExpr: GroupingExpr): String =
        parenthesize("group", groupingExpr.expression)

    override fun visitLiteralExpr(literalExpr: LiteralExpr): String =
        if (literalExpr.value == null) "nil" else literalExpr.value.toString()

    private fun parenthesize(name: String, vararg exprs: Expr): String {
        val sb = StringBuilder()
        sb.append("(").append(name)
        exprs.forEach {
            sb.append(" ").append(it.accept(this))
        }
        sb.append(")")
        return sb.toString()
    }
}

fun main() {
    val expression = BinaryExpr(
        UnaryExpr(Token(MINUS, "-", null, 1), LiteralExpr(123)),
        Token(STAR, "*", null, 1),
        GroupingExpr(LiteralExpr(45.67))
    )
    println(AstPrinter().print(expression))
}