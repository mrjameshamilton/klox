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
import eu.jameshamilton.klox.parse.MultiStmt
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

class StackSizeComputer : ExprVisitor<Int>, StmtVisitor<Int> {

    override fun visitBinaryExpr(binaryExpr: BinaryExpr): Int = compute(
        before = binaryExpr.left.accept(this) + binaryExpr.right.accept(this),
        consumes = 2,
        produces = 1
    )

    override fun visitUnaryExpr(unaryExpr: UnaryExpr): Int = compute(
        before = unaryExpr.right.accept(this),
        consumes = 1,
        produces = 1
    )

    override fun visitGroupingExpr(groupingExpr: GroupingExpr): Int = compute(
        before = groupingExpr.expression.accept(this),
        consumes = 0,
        produces = 0
    )

    override fun visitLiteralExpr(literalExpr: LiteralExpr): Int = compute(
        produces = 1
    )

    override fun visitVariableExpr(variableExpr: VariableExpr): Int = compute(
        produces = 1
    )

    override fun visitAssignExpr(assignExpr: AssignExpr): Int = compute(
        before = assignExpr.value.accept(this),
        consumes = 1,
        produces = 1
    )

    override fun visitLogicalExpr(logicalExpr: LogicalExpr): Int = compute(
        before = logicalExpr.left.accept(this) + logicalExpr.right.accept(this),
        consumes = 2,
        produces = 1
    )

    override fun visitCallExpr(callExpr: CallExpr): Int = compute(
        before = callExpr.arguments.sumOf { it.accept(this) } + callExpr.callee.accept(this),
        consumes = callExpr.arguments.size + 1,
        produces = 1
    )

    override fun visitGetExpr(getExpr: GetExpr): Int = compute(
        before = getExpr.obj.accept(this),
        consumes = 1,
        produces = 1
    )

    override fun visitSetExpr(setExpr: SetExpr): Int = compute(
        before = setExpr.value.accept(this),
        consumes = setExpr.obj.accept(this),
        produces = 1
    )

    override fun visitThisExpr(thisExpr: ThisExpr): Int = compute(
        consumes = 0,
        produces = 1
    )

    override fun visitSuperExpr(superExpr: SuperExpr): Int = compute(
        consumes = 0,
        produces = 1
    )

    override fun visitExprStmt(exprStmt: ExprStmt): Int = compute(
        before = exprStmt.expression.accept(this),
        consumes = 0,
        produces = 0
    )

    override fun visitPrintStmt(printStmt: PrintStmt): Int = compute(
        before = printStmt.expression.accept(this),
        consumes = 1,
        produces = 0
    )

    override fun visitVarStmt(varStmt: VarStmt): Int = compute(
        before = varStmt.initializer?.accept(this) ?: 0,
        consumes = if (varStmt.initializer != null) 1 else 0,
        produces = 0
    )

    override fun visitIfStmt(ifStmt: IfStmt): Int = compute(
        before = ifStmt.condition.accept(this) + ifStmt.thenBranch.accept(this) + (ifStmt.elseBranch?.accept(this) ?: 0),
        consumes = 1,
        produces = 0
    )

    override fun visitBlockStmt(block: BlockStmt): Int = compute(
        before = block.stmts.sumOf { it.accept(this) },
        consumes = 0,
        produces = 0
    )

    override fun visitWhileStmt(whileStmt: WhileStmt): Int = compute(
        before = whileStmt.condition.accept(this) + whileStmt.body.accept(this),
        consumes = 0,
        produces = 0
    )

    override fun visitDoWhileStmt(whileStmt: DoWhileStmt): Int = compute(
        before = whileStmt.body.accept(this) + whileStmt.condition.accept(this),
        consumes = 0,
        produces = 0
    )

    override fun visitBreakStmt(breakStmt: BreakStmt): Int = compute(
        consumes = 0,
        produces = 0
    )

    override fun visitContinueStmt(continueStmt: ContinueStmt): Int = compute(
        consumes = 0,
        produces = 0
    )

    override fun visitFunctionStmt(functionStmt: FunctionStmt): Int = compute(
        consumes = 0,
        produces = 0
    )

    override fun visitFunctionExpr(functionExpr: FunctionExpr): Int = compute(
        consumes = 0,
        produces = 1
    )

    override fun visitClassStmt(classStmt: ClassStmt): Int = compute(
        consumes = 0,
        produces = 0
    )

    override fun visitReturnStmt(returnStmt: ReturnStmt): Int = compute(
        before = returnStmt.value?.accept(this) ?: 0,
        consumes = 0,
        produces = 0
    )

    private fun compute(before: Int = 0, consumes: Int = 0, produces: Int = 0) =
        before - consumes + produces

    override fun visitArrayExpr(arrayExpr: ArrayExpr): Int = compute(
        before = 0, // arrayExpr.elements.sumOf { it?.accept(this) ?: 0},
        consumes = arrayExpr.elements.size,
        produces = 1
    )

    override fun visitMultiStmt(multiStmt: MultiStmt): Int = compute(
        before = multiStmt.statements.sumOf { it.accept(this) },
        consumes = 0,
        produces = 0
    )
}
