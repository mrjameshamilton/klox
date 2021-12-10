package eu.jameshamilton.klox.compile

import eu.jameshamilton.klox.parse.ArrayExpr
import eu.jameshamilton.klox.parse.AssignExpr
import eu.jameshamilton.klox.parse.BinaryExpr
import eu.jameshamilton.klox.parse.BlockStmt
import eu.jameshamilton.klox.parse.BreakStmt
import eu.jameshamilton.klox.parse.CallExpr
import eu.jameshamilton.klox.parse.ClassStmt
import eu.jameshamilton.klox.parse.ContinueStmt
import eu.jameshamilton.klox.parse.DoWhileStmt
import eu.jameshamilton.klox.parse.ExprStmt
import eu.jameshamilton.klox.parse.FunctionExpr
import eu.jameshamilton.klox.parse.FunctionStmt
import eu.jameshamilton.klox.parse.GetExpr
import eu.jameshamilton.klox.parse.GroupingExpr
import eu.jameshamilton.klox.parse.IfStmt
import eu.jameshamilton.klox.parse.LiteralExpr
import eu.jameshamilton.klox.parse.LogicalExpr
import eu.jameshamilton.klox.parse.MultiVarStmt
import eu.jameshamilton.klox.parse.PrintStmt
import eu.jameshamilton.klox.parse.ReturnStmt
import eu.jameshamilton.klox.parse.SetExpr
import eu.jameshamilton.klox.parse.SuperExpr
import eu.jameshamilton.klox.parse.ThisExpr
import eu.jameshamilton.klox.parse.UnaryExpr
import eu.jameshamilton.klox.parse.VarStmt
import eu.jameshamilton.klox.parse.VariableExpr
import eu.jameshamilton.klox.parse.WhileStmt
import eu.jameshamilton.klox.parse.Expr.Visitor as ExprVisitor
import eu.jameshamilton.klox.parse.Stmt.Visitor as StmtVisitor

/**
 */
class SuperConstructorCallCounter : ExprVisitor<Int>, StmtVisitor<Int> {

    fun count(functionStmt: FunctionExpr) =
        functionStmt.body.sumOf { it.accept(this) }

    override fun visitBinaryExpr(binaryExpr: BinaryExpr): Int =
        binaryExpr.left.accept(this) + binaryExpr.right.accept(this)

    override fun visitUnaryExpr(unaryExpr: UnaryExpr): Int = unaryExpr.right.accept(this)

    override fun visitGroupingExpr(groupingExpr: GroupingExpr): Int =
        groupingExpr.expression.accept(this)

    override fun visitLiteralExpr(literalExpr: LiteralExpr): Int = 0

    override fun visitVariableExpr(variableExpr: VariableExpr): Int = 0

    override fun visitAssignExpr(assignExpr: AssignExpr): Int = assignExpr.value.accept(this)

    override fun visitLogicalExpr(logicalExpr: LogicalExpr): Int = logicalExpr.left.accept(this) + logicalExpr.right.accept(this)

    override fun visitCallExpr(callExpr: CallExpr): Int = callExpr.arguments.sumOf { it.accept(this) } + callExpr.callee.accept(this)

    override fun visitGetExpr(getExpr: GetExpr): Int = getExpr.obj.accept(this)

    override fun visitSetExpr(setExpr: SetExpr): Int = setExpr.value.accept(this) + setExpr.obj.accept(this)

    override fun visitThisExpr(thisExpr: ThisExpr): Int = 0

    override fun visitSuperExpr(superExpr: SuperExpr): Int = if (superExpr.method.lexeme == "init") 1 else 0

    override fun visitExprStmt(exprStmt: ExprStmt): Int = exprStmt.expression.accept(this)

    override fun visitPrintStmt(printStmt: PrintStmt): Int = printStmt.expression.accept(this)

    override fun visitVarStmt(varStmt: VarStmt): Int = varStmt.initializer?.accept(this) ?: 0

    override fun visitIfStmt(ifStmt: IfStmt): Int = ifStmt.condition.accept(this) + ifStmt.thenBranch.accept(this) + (ifStmt.elseBranch?.accept(this) ?: 0)

    override fun visitBlockStmt(block: BlockStmt): Int = block.stmts.sumOf { it.accept(this) }

    override fun visitWhileStmt(whileStmt: WhileStmt): Int =
        whileStmt.condition.accept(this) + whileStmt.body.accept(this)

    override fun visitDoWhileStmt(whileStmt: DoWhileStmt): Int =
        whileStmt.body.accept(this) + whileStmt.condition.accept(this)

    override fun visitBreakStmt(breakStmt: BreakStmt): Int = 0

    override fun visitContinueStmt(continueStmt: ContinueStmt): Int = 0

    override fun visitFunctionStmt(functionStmt: FunctionStmt): Int = 0

    override fun visitFunctionExpr(functionExpr: FunctionExpr): Int = 0

    override fun visitClassStmt(classStmt: ClassStmt): Int = 0

    override fun visitReturnStmt(returnStmt: ReturnStmt): Int = returnStmt.value?.accept(this) ?: 0

    override fun visitArrayExpr(arrayExpr: ArrayExpr): Int = arrayExpr.elements.sumOf { it.accept(this) }

    override fun visitMultiVarStmt(multiVarStmt: MultiVarStmt): Int = multiVarStmt.statements.sumOf { it.accept(this) }
}
