package eu.jameshamilton.klox.parse

fun ungroup(expr: Expr): Expr = when (expr) {
    is GroupingExpr -> ungroup(expr.expression)
    else -> expr
}
