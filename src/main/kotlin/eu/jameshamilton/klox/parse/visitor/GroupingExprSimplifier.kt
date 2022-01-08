package eu.jameshamilton.klox.parse.visitor

import eu.jameshamilton.klox.parse.ArrayExpr
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

class GroupingExprSimplifier : Expr.Visitor<Expr> {

    override fun visitBinaryExpr(binaryExpr: BinaryExpr): BinaryExpr {
        binaryExpr.left.accept(this)
        binaryExpr.right.accept(this)
        return binaryExpr
    }

    override fun visitUnaryExpr(unaryExpr: UnaryExpr): Expr = unaryExpr.also {
        it.right = ungroup(unaryExpr.right)
    }

    override fun visitGroupingExpr(groupingExpr: GroupingExpr): Expr = groupingExpr.expression.accept(this)

    override fun visitLiteralExpr(literalExpr: LiteralExpr): Expr = literalExpr

    override fun visitVariableExpr(variableExpr: VariableExpr) = variableExpr

    override fun visitAssignExpr(assignExpr: AssignExpr) = assignExpr.value.accept(this)

    override fun visitLogicalExpr(logicalExpr: LogicalExpr): Expr {
        logicalExpr.left.accept(this)
        logicalExpr.right.accept(this)
        return logicalExpr
    }

    override fun visitCallExpr(callExpr: CallExpr): Expr {
        callExpr.callee.accept(this)
        callExpr.arguments.forEach { it.accept(this) }
        return callExpr
    }

    override fun visitGetExpr(getExpr: GetExpr) = getExpr.obj.accept(this)

    override fun visitSetExpr(setExpr: SetExpr): Expr {
        setExpr.obj.accept(this)
        setExpr.value.accept(this)
        return setExpr
    }

    override fun visitThisExpr(thisExpr: ThisExpr) = thisExpr

    override fun visitSuperExpr(superExpr: SuperExpr) = superExpr

    override fun visitFunctionExpr(functionExpr: FunctionExpr): FunctionExpr = functionExpr
    override fun visitArrayExpr(arrayExpr: ArrayExpr): Expr {
        arrayExpr.elements.forEach { it.accept(this) }
        return arrayExpr
    }
}

private fun ungroup(expr: Expr): Expr = when (expr) {
    is GroupingExpr -> ungroup(expr.expression)
    else -> expr
}
