import TokenType.MINUS
import TokenType.STAR

interface Expr {
    fun <R> accept(visitor: Visitor<R>): R

    interface Visitor<R> {
        fun visitBinaryExpr(expr: BinaryExpr): R
        fun visitUnaryExpr(unaryExpr: UnaryExpr): R
        fun visitGroupingExpr(groupingExpr: GroupingExpr): R
        fun visitLiteralExpr(literalExpr: LiteralExpr): R
        fun visitVariableExpr(variableExpr: VariableExpr): R
        fun visitAssignExpr(assignExpr: AssignExpr): R
        fun visitLogicalExpr(logicalExpr: LogicalExpr): R
        fun visitCallExpr(callExpr: CallExpr): R
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

data class VariableExpr(val name: Token) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitVariableExpr(this)
}

data class AssignExpr(val name: Token, val value: Expr) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitAssignExpr(this)
}

data class LogicalExpr(val left: Expr, val operator: Token, val right: Expr) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitLogicalExpr(this)
}

data class CallExpr(val callee: Expr, val paren: Token, val arguments: List<Expr> = emptyList()) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitCallExpr(this)
}

interface Stmt {
    fun <R> accept(visitor: Visitor<R>): R

    interface Visitor<R> {
        fun visitExprStmt(stmt: ExprStmt): R
        fun visitPrintStmt(print: PrintStmt): R
        fun visitVarStmt(`var`: VarStmt): R
        fun visitBlockStmt(block: BlockStmt): R
        fun visitIfStmt(ifStmt: IfStmt): R
        fun visitWhileStmt(whileStmt: WhileStmt): R
        fun visitBreakStmt(breakStmt: BreakStmt): R
        fun visitContinueStmt(continueStmt: ContinueStmt): R
        fun visitFunctionStmt(functionStmt: FunctionStmt): R
        fun visitReturnStmt(returnStmt: ReturnStmt): R
    }
}

data class ExprStmt(val expression: Expr) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitExprStmt(this)
}

data class PrintStmt(val expression: Expr) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitPrintStmt(this)
}

data class VarStmt(val token: Token, val initializer: Expr? = null) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitVarStmt(this)
}

data class IfStmt(val condition: Expr, val thenBranch: Stmt, val elseBranch: Stmt?) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitIfStmt(this)
}

data class BlockStmt(val stmts: List<Stmt>) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitBlockStmt(this)
}

data class WhileStmt(val condition: Expr, val body: Stmt) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitWhileStmt(this)
}

class BreakStmt : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitBreakStmt(this)
}

class ContinueStmt : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitContinueStmt(this)
}

data class FunctionStmt(val name: Token, val params: List<Token>, val body: List<Stmt>) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitFunctionStmt(this)
}

data class ReturnStmt(val keyword: Token, val value: Expr? = null) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitReturnStmt(this)
}

class AstPrinter : Expr.Visitor<String>, Stmt.Visitor<String> {
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

    override fun visitVariableExpr(variableExpr: VariableExpr): String =
        "var ${variableExpr.name}"

    override fun visitExprStmt(stmt: ExprStmt): String {
        return parenthesize("exprStmt", stmt.expression)
    }

    override fun visitPrintStmt(print: PrintStmt): String {
        return parenthesize("print", print.expression)
    }

    override fun visitVarStmt(`var`: VarStmt): String {
        TODO("Not yet implemented")
    }

    override fun visitAssignExpr(assignExpr: AssignExpr): String {
        TODO("Not yet implemented")
    }

    override fun visitBlockStmt(block: BlockStmt): String {
        TODO("Not yet implemented")
    }

    override fun visitIfStmt(ifStmt: IfStmt): String {
        TODO("Not yet implemented")
    }

    override fun visitLogicalExpr(logicalExpr: LogicalExpr): String {
        TODO("Not yet implemented")
    }

    override fun visitWhileStmt(whileStmt: WhileStmt): String {
        TODO("Not yet implemented")
    }

    override fun visitBreakStmt(breakStmt: BreakStmt): String {
        TODO("Not yet implemented")
    }

    override fun visitContinueStmt(continueStmt: ContinueStmt): String {
        TODO("Not yet implemented")
    }

    override fun visitCallExpr(callExpr: CallExpr): String {
        TODO("Not yet implemented")
    }

    override fun visitFunctionStmt(functionStmt: FunctionStmt): String {
        TODO("Not yet implemented")
    }

    override fun visitReturnStmt(returnStmt: ReturnStmt): String {
        TODO("Not yet implemented")
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
