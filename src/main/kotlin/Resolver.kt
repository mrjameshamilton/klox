import java.util.Stack

class Resolver(private val interpreter: Interpreter) : Stmt.Visitor<Unit>, Expr.Visitor<Unit> {
    private val scopes = Stack<MutableMap<String, Boolean>>()

    fun resolve(stmts: List<Stmt>) {
        stmts.forEach { it.accept(this) }
    }

    private fun resolve(stmt: Stmt) {
        stmt.accept(this)
    }

    private fun resolve(expr: Expr) {
        expr.accept(this)
    }

    private fun beginScope() = scopes.push(mutableMapOf())

    private fun endScope() = scopes.pop()

    private fun declare(name: Token) {
        if (scopes.empty()) return

        if (scopes.peek().size > 254) {
            error(name, "Too many local variables in function.")
        }

        if (scopes.peek()[name.lexeme] != null) {
            error(name, "Already a variable with this name in this scope.")
        }

        scopes.peek()[name.lexeme] = false
    }

    private fun define(name: Token) {
        if (scopes.empty()) return

        scopes.peek()[name.lexeme] = true
    }

    private fun resolveLocal(expr: Expr, name: Token) {
        for (i in (scopes.size - 1) downTo 0) {
            if (scopes[i].containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size - 1 - i)
                return
            }
        }
    }

    private fun resolveFunction(functionStmt: FunctionStmt) {
        beginScope()
        functionStmt.params.forEach {
            declare(it)
            define(it)
        }
        resolve(functionStmt.body)
        endScope()
    }

    override fun visitExprStmt(stmt: ExprStmt) {
        resolve(stmt.expression)
    }

    override fun visitPrintStmt(print: PrintStmt) {
        resolve(print.expression)
    }

    override fun visitVarStmt(`var`: VarStmt) {
        declare(`var`.token)
        `var`.initializer?.let { resolve(it) }
        define(`var`.token)
    }

    override fun visitBlockStmt(block: BlockStmt) {
        beginScope()
        resolve(block.stmts)
        endScope()
    }

    override fun visitIfStmt(ifStmt: IfStmt) {
        resolve(ifStmt.condition)
        resolve(ifStmt.thenBranch)
        ifStmt.elseBranch?.let { resolve(it) }
    }

    override fun visitWhileStmt(whileStmt: WhileStmt) {
        resolve(whileStmt.condition)
        resolve(whileStmt.body)
    }

    override fun visitBreakStmt(breakStmt: BreakStmt) {
    }

    override fun visitContinueStmt(continueStmt: ContinueStmt) {
    }

    override fun visitBinaryExpr(expr: BinaryExpr) {
        resolve(expr.left)
        resolve(expr.right)
    }

    override fun visitUnaryExpr(unaryExpr: UnaryExpr) {
        resolve(unaryExpr.right)
    }

    override fun visitGroupingExpr(groupingExpr: GroupingExpr) {
        resolve(groupingExpr.expression)
    }

    override fun visitLiteralExpr(literalExpr: LiteralExpr) { }

    override fun visitVariableExpr(variableExpr: VariableExpr) {
        if (!scopes.empty() &&
            scopes.peek().containsKey(variableExpr.name.lexeme) &&
            scopes.peek()[variableExpr.name.lexeme] == false
        ) {
            error(variableExpr.name, "Can't read local variable in its own initializer.")
        }

        resolveLocal(variableExpr, variableExpr.name)
    }

    override fun visitAssignExpr(assignExpr: AssignExpr) {
        resolve(assignExpr.value)
        resolveLocal(assignExpr, assignExpr.name)
    }

    override fun visitLogicalExpr(logicalExpr: LogicalExpr) {
        resolve(logicalExpr.left)
        resolve(logicalExpr.right)
    }

    override fun visitCallExpr(callExpr: CallExpr) {
        resolve(callExpr.callee)
        callExpr.arguments.forEach { resolve(it) }
    }

    override fun visitFunctionStmt(functionStmt: FunctionStmt) {
        declare(functionStmt.name)
        define(functionStmt.name)
        resolveFunction(functionStmt)
    }

    override fun visitReturnStmt(returnStmt: ReturnStmt) {
        returnStmt.value?.let { resolve(it) }
    }

    override fun visitClassStmt(classStmt: ClassStmt) {
        declare(classStmt.name)
        define(classStmt.name)

        classStmt.superClass?.let { resolve(it) }

        if (classStmt.superClass != null) {
            beginScope()
            scopes.peek().put("super", true)
        }

        beginScope()
        scopes.peek()["this"] = true

        classStmt.methods.forEach { resolveFunction(it) }

        endScope()

        if (classStmt.superClass != null) endScope()
    }

    override fun visitGetExpr(getExpr: GetExpr) {
        resolve(getExpr.obj)
    }

    override fun visitSetExpr(setExpr: SetExpr) {
        resolve(setExpr.obj)
        resolve(setExpr.value)
    }

    override fun visitSuperExpr(superExpr: SuperExpr) {
        resolveLocal(superExpr, superExpr.keyword)
    }

    override fun visitThisExpr(thisExpr: ThisExpr) {
        resolveLocal(thisExpr, thisExpr.keyword)
    }
}
