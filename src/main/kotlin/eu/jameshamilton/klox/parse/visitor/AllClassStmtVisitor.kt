package eu.jameshamilton.klox.parse.visitor

import eu.jameshamilton.klox.parse.BlockStmt
import eu.jameshamilton.klox.parse.BreakStmt
import eu.jameshamilton.klox.parse.ClassStmt
import eu.jameshamilton.klox.parse.ContinueStmt
import eu.jameshamilton.klox.parse.DoWhileStmt
import eu.jameshamilton.klox.parse.ExprStmt
import eu.jameshamilton.klox.parse.FunctionStmt
import eu.jameshamilton.klox.parse.IfStmt
import eu.jameshamilton.klox.parse.MultiVarStmt
import eu.jameshamilton.klox.parse.PrintStmt
import eu.jameshamilton.klox.parse.ReturnStmt
import eu.jameshamilton.klox.parse.Stmt
import eu.jameshamilton.klox.parse.VarStmt
import eu.jameshamilton.klox.parse.WhileStmt

class AllClassStmtVisitor(private val visitor: ClassStmt.Visitor<Unit>) : Stmt.Visitor<Unit> {

    override fun visitExprStmt(exprStmt: ExprStmt) = Unit

    override fun visitPrintStmt(printStmt: PrintStmt) = Unit

    override fun visitVarStmt(varStmt: VarStmt) = Unit

    override fun visitIfStmt(ifStmt: IfStmt) {
        ifStmt.thenBranch.accept(this)
        ifStmt.elseBranch?.accept(this)
    }

    override fun visitBlockStmt(block: BlockStmt) = block.stmts.forEach { it.accept(this) }

    override fun visitWhileStmt(whileStmt: WhileStmt) = whileStmt.body.accept(this)

    override fun visitDoWhileStmt(whileStmt: DoWhileStmt) = whileStmt.body.accept(this)

    override fun visitBreakStmt(breakStmt: BreakStmt) = Unit

    override fun visitContinueStmt(continueStmt: ContinueStmt) = Unit

    override fun visitFunctionStmt(functionStmt: FunctionStmt) =
        functionStmt.functionExpr.body.forEach { it.accept(this) }

    override fun visitReturnStmt(returnStmt: ReturnStmt) = Unit

    override fun visitClassStmt(classStmt: ClassStmt) = visitor.visitClassStmt(classStmt)

    override fun visitMultiVarStmt(multiVarStmt: MultiVarStmt) = multiVarStmt.statements.forEach { it.accept(this) }
}
