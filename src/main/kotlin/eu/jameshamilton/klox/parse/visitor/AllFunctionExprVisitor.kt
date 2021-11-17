package eu.jameshamilton.klox.parse.visitor

import eu.jameshamilton.klox.parse.AssignExpr
import eu.jameshamilton.klox.parse.BinaryExpr
import eu.jameshamilton.klox.parse.CallExpr
import eu.jameshamilton.klox.parse.Expr
import eu.jameshamilton.klox.parse.FunctionExpr
import eu.jameshamilton.klox.parse.GetExpr
import eu.jameshamilton.klox.parse.GroupingExpr
import eu.jameshamilton.klox.parse.LiteralExpr
import eu.jameshamilton.klox.parse.LogicalExpr
import eu.jameshamilton.klox.parse.SetExpr
import eu.jameshamilton.klox.parse.SuperExpr
import eu.jameshamilton.klox.parse.ThisExpr
import eu.jameshamilton.klox.parse.UnaryExpr
import eu.jameshamilton.klox.parse.VariableExpr

class AllFunctionExprVisitor(private val visitor: FunctionExpr.Visitor<Unit>) : Expr.Visitor<Unit> {

    override fun visitBinaryExpr(binaryExpr: BinaryExpr) {
        binaryExpr.left.accept(this)
        binaryExpr.right.accept(this)
    }

    override fun visitUnaryExpr(unaryExpr: UnaryExpr) = unaryExpr.right.accept(this)

    override fun visitGroupingExpr(groupingExpr: GroupingExpr) = groupingExpr.expression.accept(this)

    override fun visitLiteralExpr(literalExpr: LiteralExpr) = Unit

    override fun visitVariableExpr(variableExpr: VariableExpr) = Unit

    override fun visitAssignExpr(assignExpr: AssignExpr) = assignExpr.value.accept(this)

    override fun visitLogicalExpr(logicalExpr: LogicalExpr) {
        logicalExpr.left.accept(this)
        logicalExpr.right.accept(this)
    }

    override fun visitCallExpr(callExpr: CallExpr) {
        callExpr.callee.accept(this)
        callExpr.arguments.forEach { it.accept(this) }
    }

    override fun visitGetExpr(getExpr: GetExpr) = getExpr.obj.accept(this)

    override fun visitSetExpr(setExpr: SetExpr) {
        setExpr.obj.accept(this)
        setExpr.value.accept(this)
    }

    override fun visitThisExpr(thisExpr: ThisExpr) = Unit

    override fun visitSuperExpr(superExpr: SuperExpr) = Unit

    override fun visitFunctionExpr(functionExpr: FunctionExpr) = visitor.visitFunctionExpr(functionExpr)
}
