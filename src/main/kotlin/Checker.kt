import TokenType.*

class Checker : Stmt.Visitor<Unit>, Expr.Visitor<Unit> {
    private var inLoop = false

    fun check(stmts: List<Stmt>) {
        stmts.forEach { it.accept(this) }
    }

    override fun visitExprStmt(stmt: ExprStmt) {
        stmt.expression.accept(this)
    }

    override fun visitPrintStmt(print: PrintStmt) {
        print.expression.accept(this)
    }

    override fun visitVarStmt(`var`: VarStmt) {
        `var`.initializer?.accept(this)
    }

    override fun visitBlockStmt(block: BlockStmt) {
        block.stmts.forEach { it.accept(this) }
    }

    override fun visitIfStmt(ifStmt: IfStmt) {
        ifStmt.condition.accept(this)
        ifStmt.thenBranch.accept(this)
        ifStmt.elseBranch?.accept(this)
    }

    override fun visitWhileStmt(whileStmt: WhileStmt) {
        inLoop = true
        whileStmt.condition.accept(this)
        whileStmt.body.accept(this)
        inLoop = false
    }

    override fun visitBreakStmt(breakStmt: BreakStmt) {
        if (!inLoop) {
            error(Token(BREAK, "break", null, -1),"break statement is only allowed in loops")
        }
    }
    override fun visitBinaryExpr(expr: BinaryExpr) {
        expr.left.accept(this)
        expr.right.accept(this)
    }

    override fun visitUnaryExpr(unaryExpr: UnaryExpr) {
        unaryExpr.right.accept(this)
    }

    override fun visitGroupingExpr(groupingExpr: GroupingExpr) {
        groupingExpr.expression.accept(this)
    }

    override fun visitLiteralExpr(literalExpr: LiteralExpr) { }

    override fun visitVariableExpr(variableExpr: VariableExpr) { }

    override fun visitAssignExpr(assignExpr: AssignExpr) {
        assignExpr.value.accept(this)
    }

    override fun visitLogicalExpr(logicalExpr: LogicalExpr) {
        logicalExpr.left.accept(this)
        logicalExpr.right.accept(this)
    }
}