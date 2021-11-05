package eu.jameshamilton.klox.parse

import eu.jameshamilton.klox.parse.TokenType.*

interface ASTVisitor<R> : Program.Visitor<R>, Expr.Visitor<R>, Stmt.Visitor<R>

class Program(val stmts: List<Stmt>) {
    fun <R> accept(visitor: Visitor<R>) =
        visitor.visitProgram(this)

    fun <R> statementAccept(visitor: Stmt.Visitor<R>): List<R> {
        return stmts.map { it.accept(visitor) }
    }

    interface Visitor<R> {
        fun visitProgram(program: Program): R
    }
}

interface Expr {
    fun <R> accept(visitor: Visitor<R>): R

    interface Visitor<R> :
        BinaryExpr.Visitor<R>,
        UnaryExpr.Visitor<R>,
        GroupingExpr.Visitor<R>,
        LiteralExpr.Visitor<R>,
        VariableExpr.Visitor<R>,
        AssignExpr.Visitor<R>,
        LogicalExpr.Visitor<R>,
        CallExpr.Visitor<R>,
        GetExpr.Visitor<R>,
        SetExpr.Visitor<R>,
        ThisExpr.Visitor<R>,
        SuperExpr.Visitor<R>
}

class BinaryExpr(val left: Expr, val operator: Token, val right: Expr) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitBinaryExpr(this)

    override fun toString(): String = "<${operator.lexeme} $left $right>"

    interface Visitor<R> {
        fun visitBinaryExpr(binaryExpr: BinaryExpr): R
    }
}

class UnaryExpr(val operator: Token, val right: Expr) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitUnaryExpr(this)

    interface Visitor<R> {
        fun visitUnaryExpr(unaryExpr: UnaryExpr): R
    }
}

class GroupingExpr(val expression: Expr) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>) =
        visitor.visitGroupingExpr(this)

    interface Visitor<R> {
        fun visitGroupingExpr(groupingExpr: GroupingExpr): R
    }

    override fun toString(): String = "<grouping $expression>"
}

class LiteralExpr(val value: Any?) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitLiteralExpr(this)

    interface Visitor<R> {
        fun visitLiteralExpr(literalExpr: LiteralExpr): R
    }

    override fun toString(): String = "<$value>"
}

class VariableExpr(override val name: Token) : Expr, VarAccess {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitVariableExpr(this)

    interface Visitor<R> {
        fun visitVariableExpr(variableExpr: VariableExpr): R
    }

    override fun toString(): String = "<use-var ${name.lexeme}>"
}

class AssignExpr(override val name: Token, val value: Expr) : Expr, VarAccess {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitAssignExpr(this)

    interface Visitor<R> {
        fun visitAssignExpr(assignExpr: AssignExpr): R
    }

    override fun toString(): String = "<${name.lexeme} = $value>"
}

class LogicalExpr(val left: Expr, val operator: Token, val right: Expr) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitLogicalExpr(this)

    interface Visitor<R> {
        fun visitLogicalExpr(logicalExpr: LogicalExpr): R
    }

    override fun toString(): String =
        "<${operator.lexeme} $left $right>"
}

class CallExpr(val callee: Expr, val paren: Token, val arguments: List<Expr> = emptyList()) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitCallExpr(this)

    interface Visitor<R> {
        fun visitCallExpr(callExpr: CallExpr): R
    }

    override fun toString(): String =
        "<call $callee(${arguments.joinToString(",")})>"
}

class GetExpr(val obj: Expr, val name: Token) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitGetExpr(this)

    interface Visitor<R> {
        fun visitGetExpr(getExpr: GetExpr): R
    }
}

class SetExpr(val obj: Expr, val name: Token, val value: Expr) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitSetExpr(this)

    interface Visitor<R> {
        fun visitSetExpr(setExpr: SetExpr): R
    }
}

class ThisExpr(override val name: Token) : Expr, VarAccess {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitThisExpr(this)

    interface Visitor<R> {
        fun visitThisExpr(thisExpr: ThisExpr): R
    }
}

class SuperExpr(override val name: Token, val method: Token) : Expr, VarAccess {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitSuperExpr(this)

    interface Visitor<R> {
        fun visitSuperExpr(superExpr: SuperExpr): R
    }
}

interface Stmt {
    fun <R> accept(visitor: Visitor<R>): R

    interface Visitor<R> :
        ExprStmt.Visitor<R>,
        PrintStmt.Visitor<R>,
        VarStmt.Visitor<R>,
        BlockStmt.Visitor<R>,
        IfStmt.Visitor<R>,
        WhileStmt.Visitor<R>,
        BreakStmt.Visitor<R>,
        ContinueStmt.Visitor<R>,
        FunctionStmt.Visitor<R>,
        ReturnStmt.Visitor<R>,
        ClassStmt.Visitor<R>
}

class ExprStmt(val expression: Expr) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitExprStmt(this)

    interface Visitor<R> {
        fun visitExprStmt(exprStmt: ExprStmt): R
    }

    override fun toString(): String = "<expr-stmt $expression>"
}

class PrintStmt(val expression: Expr) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitPrintStmt(this)

    interface Visitor<R> {
        fun visitPrintStmt(printStmt: PrintStmt): R
    }
}

class VarStmt(override val name: Token, var initializer: Expr? = null) : Stmt, VarDef {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitVarStmt(this)

    interface Visitor<R> {
        fun visitVarStmt(varStmt: VarStmt): R
    }

    override fun toString(): String = "<var ${name.lexeme}${if (initializer != null) " $initializer" else ""}>"
}

class IfStmt(val condition: Expr, val thenBranch: Stmt, val elseBranch: Stmt?) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitIfStmt(this)

    interface Visitor<R> {
        fun visitIfStmt(ifStmt: IfStmt): R
    }
}

class BlockStmt(val stmts: List<Stmt>) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitBlockStmt(this)

    interface Visitor<R> {
        fun visitBlockStmt(block: BlockStmt): R
    }
}

class WhileStmt(val condition: Expr, val body: Stmt) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitWhileStmt(this)

    interface Visitor<R> {
        fun visitWhileStmt(whileStmt: WhileStmt): R
    }
}

class BreakStmt : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitBreakStmt(this)

    interface Visitor<R> {
        fun visitBreakStmt(breakStmt: BreakStmt): R
    }
}

class ContinueStmt : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitContinueStmt(this)

    interface Visitor<R> {
        fun visitContinueStmt(continueStmt: ContinueStmt): R
    }
}

open class FunctionStmt(override val name: Token, open val kind: FunctionType, val isStatic: Boolean = false, open val params: List<Parameter>, val body: List<Stmt>) :
    Stmt,
    VarDef {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitFunctionStmt(this)

    override fun toString(): String = "<fn ${name.lexeme}>"

    interface Visitor<R> {
        fun visitFunctionStmt(functionStmt: FunctionStmt): R
    }
}

class ReturnStmt(val keyword: Token, val value: Expr? = null) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitReturnStmt(this)

    interface Visitor<R> {
        fun visitReturnStmt(returnStmt: ReturnStmt): R
    }
}

class ClassStmt(override val name: Token, val superClass: VariableExpr?, val methods: List<FunctionStmt> = emptyList()) :
    Stmt,
    VarDef {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitClassStmt(this)

    override fun toString(): String = "<class ${name.lexeme}>"

    interface Visitor<R> {
        fun visitClassStmt(classStmt: ClassStmt): R
    }
}

interface VarDef {
    val name: Token
}

interface VarAccess {
    val name: Token
}

class Parameter(override val name: Token) : VarDef {
    constructor(name: String) : this(Token(IDENTIFIER, name))

    override fun toString(): String = "<param ${name.lexeme}>"
}
