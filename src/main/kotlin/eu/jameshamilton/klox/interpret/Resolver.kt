package eu.jameshamilton.klox.interpret

import eu.jameshamilton.klox.parse.ASTVisitor
import eu.jameshamilton.klox.parse.ArrayExpr
import eu.jameshamilton.klox.parse.AssignExpr
import eu.jameshamilton.klox.parse.BinaryExpr
import eu.jameshamilton.klox.parse.BlockStmt
import eu.jameshamilton.klox.parse.BreakStmt
import eu.jameshamilton.klox.parse.CallExpr
import eu.jameshamilton.klox.parse.ClassStmt
import eu.jameshamilton.klox.parse.ContinueStmt
import eu.jameshamilton.klox.parse.Expr
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
import eu.jameshamilton.klox.parse.Program
import eu.jameshamilton.klox.parse.ReturnStmt
import eu.jameshamilton.klox.parse.SetExpr
import eu.jameshamilton.klox.parse.Stmt
import eu.jameshamilton.klox.parse.SuperExpr
import eu.jameshamilton.klox.parse.ThisExpr
import eu.jameshamilton.klox.parse.Token
import eu.jameshamilton.klox.parse.UnaryExpr
import eu.jameshamilton.klox.parse.VarStmt
import eu.jameshamilton.klox.parse.VariableExpr
import eu.jameshamilton.klox.parse.WhileStmt
import java.util.Stack

class Resolver(private val interpreter: Interpreter) : ASTVisitor<Unit> {
    private val scopes = Stack<MutableMap<String, Boolean>>()

    override fun visitProgram(program: Program) {
        resolve(program.stmts)
    }

    private fun resolve(stmts: List<Stmt>) {
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
            eu.jameshamilton.klox.error(name, "Too many local variables in function.")
        }

        if (scopes.peek()[name.lexeme] != null) {
            eu.jameshamilton.klox.error(name, "Already a variable with this name in this scope.")
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

    private fun resolveFunction(functionStmt: FunctionExpr) {
        beginScope()
        functionStmt.params.forEach {
            declare(it.name)
            define(it.name)
        }
        resolve(functionStmt.body)
        endScope()
    }

    override fun visitExprStmt(exprStmt: ExprStmt) {
        resolve(exprStmt.expression)
    }

    override fun visitPrintStmt(printStmt: PrintStmt) {
        resolve(printStmt.expression)
    }

    override fun visitVarStmt(varStmt: VarStmt) {
        declare(varStmt.name)
        varStmt.initializer?.let { resolve(it) }
        define(varStmt.name)
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

    override fun visitMultiStmt(multiStmt: MultiStmt) {
        multiStmt.statements.forEach { it.accept(this) }
    }

    override fun visitBinaryExpr(binaryExpr: BinaryExpr) {
        resolve(binaryExpr.left)
        resolve(binaryExpr.right)
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
            eu.jameshamilton.klox.error(variableExpr.name, "Can't read local variable in its own initializer.")
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
        resolveFunction(functionStmt.functionExpr)
    }

    override fun visitFunctionExpr(functionExpr: FunctionExpr) {
        resolveFunction(functionExpr)
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
            scopes.peek()["super"] = true
        }

        beginScope()
        scopes.peek()["this"] = true

        classStmt.methods.forEach { resolveFunction(it.functionExpr) }

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
        resolveLocal(superExpr, superExpr.name)
    }

    override fun visitThisExpr(thisExpr: ThisExpr) {
        resolveLocal(thisExpr, thisExpr.name)
    }

    override fun visitArrayExpr(arrayExpr: ArrayExpr) {
        arrayExpr.elements.forEach { resolve(it) }
    }
}
