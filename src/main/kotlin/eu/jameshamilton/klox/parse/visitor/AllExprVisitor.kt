package eu.jameshamilton.klox.parse.visitor

import eu.jameshamilton.klox.parse.ArrayExpr
import eu.jameshamilton.klox.parse.AssignExpr
import eu.jameshamilton.klox.parse.BinaryExpr
import eu.jameshamilton.klox.parse.BlockStmt
import eu.jameshamilton.klox.parse.BreakStmt
import eu.jameshamilton.klox.parse.CallExpr
import eu.jameshamilton.klox.parse.ClassStmt
import eu.jameshamilton.klox.parse.ContinueStmt
import eu.jameshamilton.klox.parse.DoWhileStmt
import eu.jameshamilton.klox.parse.Expr
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
import eu.jameshamilton.klox.parse.Stmt
import eu.jameshamilton.klox.parse.SuperExpr
import eu.jameshamilton.klox.parse.ThisExpr
import eu.jameshamilton.klox.parse.UnaryExpr
import eu.jameshamilton.klox.parse.VarStmt
import eu.jameshamilton.klox.parse.VariableExpr
import eu.jameshamilton.klox.parse.WhileStmt

class AllExprVisitor<T>(private val visitor: Expr.Visitor<T>) : Stmt.Visitor<Unit>, Expr.Visitor<Unit> {

    override fun visitBinaryExpr(binaryExpr: BinaryExpr) {
        binaryExpr.left.accept(visitor)
        binaryExpr.right.accept(visitor)
    }

    override fun visitUnaryExpr(unaryExpr: UnaryExpr) {
        unaryExpr.right.accept(visitor)
    }

    override fun visitGroupingExpr(groupingExpr: GroupingExpr) {
        groupingExpr.expression.accept(visitor)
    }

    override fun visitLiteralExpr(literalExpr: LiteralExpr) {
        literalExpr.accept(visitor)
    }

    override fun visitVariableExpr(variableExpr: VariableExpr) {
        variableExpr.accept(visitor)
    }

    override fun visitAssignExpr(assignExpr: AssignExpr) {
        assignExpr.value.accept(visitor)
    }

    override fun visitLogicalExpr(logicalExpr: LogicalExpr) {
        logicalExpr.left.accept(visitor)
        logicalExpr.right.accept(visitor)
    }

    override fun visitCallExpr(callExpr: CallExpr) {
        callExpr.callee.accept(visitor)
        callExpr.arguments.forEach { it.accept(visitor) }
    }

    override fun visitGetExpr(getExpr: GetExpr) {
        getExpr.obj.accept(visitor)
    }

    override fun visitSetExpr(setExpr: SetExpr) {
        setExpr.obj.accept(visitor)
        setExpr.value.accept(visitor)
    }

    override fun visitThisExpr(thisExpr: ThisExpr) {
        thisExpr.accept(visitor)
    }

    override fun visitSuperExpr(superExpr: SuperExpr) {
        superExpr.accept(visitor)
    }

    override fun visitFunctionExpr(functionExpr: FunctionExpr) {
        functionExpr.body.forEach { it.accept(this) }
    }
    override fun visitArrayExpr(arrayExpr: ArrayExpr) {
        arrayExpr.elements.forEach { it.accept(visitor) }
    }

    override fun visitExprStmt(exprStmt: ExprStmt) {
        exprStmt.expression.accept(this)
    }

    override fun visitPrintStmt(printStmt: PrintStmt) {
        printStmt.expression.accept(visitor)
    }

    override fun visitVarStmt(varStmt: VarStmt) {
        varStmt.initializer?.accept(visitor)
        varStmt.initializer?.accept(this)
    }

    override fun visitIfStmt(ifStmt: IfStmt) {
        ifStmt.condition.accept(visitor)
        ifStmt.condition.accept(this)
        ifStmt.thenBranch.accept(this)
        ifStmt.elseBranch?.accept(this)
    }

    override fun visitBlockStmt(block: BlockStmt) {
        block.stmts.forEach { it.accept(this) }
    }

    override fun visitWhileStmt(whileStmt: WhileStmt) {
        whileStmt.condition.accept(this)
        whileStmt.body.accept(this)
    }

    override fun visitDoWhileStmt(whileStmt: DoWhileStmt) {
        whileStmt.condition.accept(this)
        whileStmt.body.accept(this)
    }

    override fun visitBreakStmt(breakStmt: BreakStmt) {
    }

    override fun visitContinueStmt(continueStmt: ContinueStmt) {
    }

    override fun visitMultiVarStmt(multiVarStmt: MultiVarStmt) {
        multiVarStmt.statements.forEach { it.accept(this) }
    }

    override fun visitFunctionStmt(functionStmt: FunctionStmt) {
        functionStmt.functionExpr.body.forEach { it.accept(this) }
    }

    override fun visitReturnStmt(returnStmt: ReturnStmt) {
        returnStmt.value?.accept(this)
    }

    override fun visitClassStmt(classStmt: ClassStmt) {
        classStmt.methods.forEach { it.accept(this) }
    }
}
