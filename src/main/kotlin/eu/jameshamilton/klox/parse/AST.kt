package eu.jameshamilton.klox.parse

import eu.jameshamilton.klox.interpret.RuntimeError
import eu.jameshamilton.klox.parse.TokenType.*
import java.util.EnumSet
import java.util.EnumSet.*

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

    operator fun plus(other: Program) = Program(stmts + other.stmts)
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
        SuperExpr.Visitor<R>,
        FunctionExpr.Visitor<R>,
        ArrayExpr.Visitor<R>
}

class BinaryExpr(val left: Expr, val operator: Token, val right: Expr) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitBinaryExpr(this)

    override fun toString(): String = "<${operator.lexeme} $left $right>"

    interface Visitor<R> {
        fun visitBinaryExpr(binaryExpr: BinaryExpr): R
    }

    val isOverloadable: Boolean
        get() = operator.type == PLUS ||
            operator.type == MINUS ||
            operator.type == STAR ||
            operator.type == SLASH ||
            operator.type == PERCENT ||
            operator.type == EQUAL_EQUAL ||
            operator.type == BANG_EQUAL

    val overloadMethodName: String
        get() = when (operator.type) {
            EQUAL_EQUAL -> "equals"
            BANG_EQUAL -> "equals"
            PLUS -> "plus"
            MINUS -> "minus"
            STAR -> "times"
            SLASH -> "div"
            PERCENT -> "rem"
            else -> throw RuntimeError(operator, "Operator '${operator.lexeme}' overloading not supported.")
        }
}

class UnaryExpr(val operator: Token, var right: Expr, val postfix: Boolean = false) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitUnaryExpr(this)

    interface Visitor<R> {
        fun visitUnaryExpr(unaryExpr: UnaryExpr): R
    }

    override fun toString(): String = "<${operator.lexeme} $right>"
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

class GetExpr(val obj: Expr, val name: Token, val safeAccess: Boolean = false) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitGetExpr(this)

    interface Visitor<R> {
        fun visitGetExpr(getExpr: GetExpr): R
    }

    override fun toString(): String = "<get-expr ${obj}${if (safeAccess) "?." else "."}${name.lexeme}>"
}

class SetExpr(val obj: Expr, val name: Token, val value: Expr) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitSetExpr(this)

    interface Visitor<R> {
        fun visitSetExpr(setExpr: SetExpr): R
    }

    override fun toString(): String = "<set-expr $obj.${name.lexeme} = $value>"
}

class ThisExpr(override val name: Token) : Expr, VarAccess {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitThisExpr(this)

    interface Visitor<R> {
        fun visitThisExpr(thisExpr: ThisExpr): R
    }

    override fun toString(): String = "<this-expr ${name.lexeme}>"
}

class SuperExpr(override val name: Token, val method: Token) : Expr, VarAccess {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitSuperExpr(this)

    interface Visitor<R> {
        fun visitSuperExpr(superExpr: SuperExpr): R
    }

    override fun toString(): String = "<super-expr ${name.lexeme}>"
}

class FunctionExpr(
    val params: List<Parameter> = emptyList(),
    val body: List<Stmt> = emptyList()
) : Expr {

    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitFunctionExpr(this)

    interface Visitor<R> {
        fun visitFunctionExpr(functionExpr: FunctionExpr): R
    }

    override fun toString(): String = "<fn-expr>"
}

class ArrayExpr(val elements: List<Expr>) : Expr {
    override fun <R> accept(visitor: Expr.Visitor<R>): R =
        visitor.visitArrayExpr(this)

    interface Visitor<R> {
        fun visitArrayExpr(arrayExpr: ArrayExpr): R
    }

    override fun toString(): String = "<array-expr ${elements.size}>"
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
        DoWhileStmt.Visitor<R>,
        BreakStmt.Visitor<R>,
        ContinueStmt.Visitor<R>,
        FunctionStmt.Visitor<R>,
        ReturnStmt.Visitor<R>,
        ClassStmt.Visitor<R>,
        MultiVarStmt.Visitor<R>
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

    override fun toString(): String = "<print $expression>"
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

    override fun toString(): String = "<if-stmt $condition $thenBranch ${elseBranch?.let { "else $it" }}>"
}

class BlockStmt(val stmts: List<Stmt>) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitBlockStmt(this)

    interface Visitor<R> {
        fun visitBlockStmt(block: BlockStmt): R
    }

    override fun toString(): String = "<block ${stmts.joinToString(", ")}>"
}

class WhileStmt(val condition: Expr, val body: Stmt) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitWhileStmt(this)

    interface Visitor<R> {
        fun visitWhileStmt(whileStmt: WhileStmt): R
    }

    override fun toString(): String = "<while-stmt $condition $body>"
}

class DoWhileStmt(val condition: Expr, val body: Stmt) : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitDoWhileStmt(this)

    interface Visitor<R> {
        fun visitDoWhileStmt(whileStmt: DoWhileStmt): R
    }

    override fun toString(): String = "<do-while-stmt $condition $body>"
}

class BreakStmt : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitBreakStmt(this)

    interface Visitor<R> {
        fun visitBreakStmt(breakStmt: BreakStmt): R
    }

    override fun toString(): String = "<break>"
}

class ContinueStmt : Stmt {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitContinueStmt(this)

    interface Visitor<R> {
        fun visitContinueStmt(continueStmt: ContinueStmt): R
    }

    override fun toString(): String = "<continue>"
}

open class MultiVarStmt(statements: List<VarStmt>) : Stmt {
    val statements: List<VarStmt>

    init {
        this.statements = statements.filterNot { it.name.type == UNDERSCORE }
    }

    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitMultiVarStmt(this)

    interface Visitor<R> {
        fun visitMultiVarStmt(multiVarStmt: MultiVarStmt): R
    }

    override fun toString(): String = "<multi-var ${statements.joinToString(", ")}>"
}

enum class ModifierFlag {
    INITIALIZER,
    STATIC,
    GETTER,
    DATA_CLASS,
    NATIVE;

    companion object {
        fun empty(): EnumSet<ModifierFlag> = noneOf(ModifierFlag::class.java)
    }
}

open class FunctionStmt(
    override val name: Token,
    val modifiers: EnumSet<ModifierFlag>,
    val functionExpr: FunctionExpr,
    var classStmt: ClassStmt? = null
) : Stmt, VarDef {

    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitFunctionStmt(this)

    override fun toString(): String = "<fn ${name.lexeme} $functionExpr>"

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

    override fun toString(): String = "<return ${value?.let { " $it" }}>"
}

class ClassStmt(
    val modifiers: EnumSet<ModifierFlag>,
    override val name: Token,
    val superClass: VariableExpr?,
    val methods: MutableList<FunctionStmt> = mutableListOf()
) :
    Stmt,
    VarDef {
    override fun <R> accept(visitor: Stmt.Visitor<R>): R =
        visitor.visitClassStmt(this)

    override fun toString(): String = "<class[$modifiers] ${name.lexeme}>"

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
