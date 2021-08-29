package eu.jameshamilton.klox

import eu.jameshamilton.klox.TokenType.MINUS
import eu.jameshamilton.klox.TokenType.STAR

interface Expr {
    fun <R> accept(visitor: Visitor<R>): R

    interface Visitor<R> {
        fun visitBinaryExpr(binaryExpr: BinaryExpr): R
        fun visitUnaryExpr(unaryExpr: UnaryExpr): R
        fun visitGroupingExpr(groupingExpr: GroupingExpr): R
        fun visitLiteralExpr(literalExpr: LiteralExpr): R
        fun visitVariableExpr(variableExpr: VariableExpr): R
        fun visitAssignExpr(assignExpr: AssignExpr): R
        fun visitLogicalExpr(logicalExpr: LogicalExpr): R
        fun visitCallExpr(callExpr: CallExpr): R
        fun visitGetExpr(getExpr: GetExpr): R
        fun visitSetExpr(setExpr: SetExpr): R
        fun visitThisExpr(thisExpr: ThisExpr): R
        fun visitSuperExpr(superExpr: SuperExpr): R
    }
}

class BinaryExpr(val left: Expr, val operator: Token, val right: Expr) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitBinaryExpr(this)
}

class UnaryExpr(val operator: Token, val right: Expr) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitUnaryExpr(this)
}

class GroupingExpr(val expression: Expr) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>) =
        visitor.visitGroupingExpr(this)
}

class LiteralExpr(val value: Any?) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitLiteralExpr(this)
}

class VariableExpr(val name: Token) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitVariableExpr(this)
}

class AssignExpr(val name: Token, val value: Expr) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitAssignExpr(this)
}

class LogicalExpr(val left: Expr, val operator: Token, val right: Expr) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitLogicalExpr(this)
}

class CallExpr(val callee: Expr, val paren: Token, val arguments: List<Expr> = emptyList()) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitCallExpr(this)
}

class GetExpr(val obj: Expr, val name: Token) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitGetExpr(this)
}

class SetExpr(val obj: Expr, val name: Token, val value: Expr) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitSetExpr(this)
}

class ThisExpr(val keyword: Token) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitThisExpr(this)
}

class SuperExpr(val keyword: Token, val method: Token) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitSuperExpr(this)
}

interface Stmt {
    fun <R> accept(visitor: Visitor<R>): R

    interface Visitor<R> {
        fun visitExprStmt(exprStmt: ExprStmt): R
        fun visitPrintStmt(printStmt: PrintStmt): R
        fun visitVarStmt(varStmt: VarStmt): R
        fun visitBlockStmt(block: BlockStmt): R
        fun visitIfStmt(ifStmt: IfStmt): R
        fun visitWhileStmt(whileStmt: WhileStmt): R
        fun visitBreakStmt(breakStmt: BreakStmt): R
        fun visitContinueStmt(continueStmt: ContinueStmt): R
        fun visitFunctionStmt(functionStmt: FunctionStmt): R
        fun visitReturnStmt(returnStmt: ReturnStmt): R
        fun visitClassStmt(classStmt: ClassStmt): R
    }
}

class ExprStmt(val expression: Expr) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitExprStmt(this)
}

class PrintStmt(val expression: Expr) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitPrintStmt(this)
}

class VarStmt(val token: Token, val initializer: Expr? = null) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitVarStmt(this)
}

class IfStmt(val condition: Expr, val thenBranch: Stmt, val elseBranch: Stmt?) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitIfStmt(this)
}

class BlockStmt(val stmts: List<Stmt>) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitBlockStmt(this)
}

class WhileStmt(val condition: Expr, val body: Stmt) : Stmt {
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

class FunctionStmt(val name: Token, val kind: FunctionType, val params: List<Token>, val body: List<Stmt>) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitFunctionStmt(this)
}

class ReturnStmt(val keyword: Token, val value: Expr? = null) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitReturnStmt(this)
}

class ClassStmt(val name: Token, val superClass: VariableExpr?, val methods: List<FunctionStmt> = emptyList()) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitClassStmt(this)
}

class AstPrinter : Expr.Visitor<String>, Stmt.Visitor<String> {
    fun print(expr: Expr) = expr.accept(this)

    override fun visitBinaryExpr(binaryExpr: BinaryExpr): String =
        parenthesize(binaryExpr.operator.lexeme, binaryExpr.left, binaryExpr.right)

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

    override fun visitExprStmt(exprStmt: ExprStmt): String {
        return parenthesize("exprStmt", exprStmt.expression)
    }

    override fun visitPrintStmt(printStmt: PrintStmt): String {
        return parenthesize("print", printStmt.expression)
    }

    override fun visitVarStmt(varStmt: VarStmt): String {
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

    override fun visitClassStmt(classStmt: ClassStmt): String {
        TODO("Not yet implemented")
    }

    override fun visitGetExpr(getExpr: GetExpr): String {
        TODO("Not yet implemented")
    }

    override fun visitSetExpr(setExpr: SetExpr): String {
        TODO("Not yet implemented")
    }

    override fun visitThisExpr(thisExpr: ThisExpr): String {
        TODO("Not yet implemented")
    }

    override fun visitSuperExpr(superExpr: SuperExpr): String {
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
