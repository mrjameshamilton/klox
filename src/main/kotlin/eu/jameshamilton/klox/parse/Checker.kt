package eu.jameshamilton.klox.parse

import eu.jameshamilton.klox.error
import eu.jameshamilton.klox.parse.ClassType.NONE
import eu.jameshamilton.klox.parse.ClassType.SUBCLASS
import eu.jameshamilton.klox.parse.FunctionType.INITIALIZER
import eu.jameshamilton.klox.parse.FunctionType.SCRIPT
import eu.jameshamilton.klox.parse.TokenType.BREAK
import eu.jameshamilton.klox.parse.TokenType.CONTINUE
import java.util.Locale

class Checker : ASTVisitor<Unit> {
    private var inLoop = false
    private var currentFunction: FunctionStmt? = null
    private var currentClassType = NONE

    override fun visitProgram(program: Program) {
        program.stmts.forEach { it.accept(this) }
    }

    override fun visitExprStmt(exprStmt: ExprStmt) {
        exprStmt.expression.accept(this)
    }

    override fun visitPrintStmt(printStmt: PrintStmt) {
        printStmt.expression.accept(this)
    }

    override fun visitVarStmt(varStmt: VarStmt) {
        varStmt.initializer?.accept(this)
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
        whileStmt.condition.accept(this)
        inLoop = true
        whileStmt.body.accept(this)
        inLoop = false
    }

    override fun visitBreakStmt(breakStmt: BreakStmt) {
        if (!inLoop) {
            error(Token(BREAK, "break", null, -1), "break statement is only allowed in loops.")
        }
    }

    override fun visitContinueStmt(continueStmt: ContinueStmt) {
        if (!inLoop) {
            error(Token(CONTINUE, "continue", null, -1), "continue statement is only allowed in loops.")
        }
    }

    override fun visitBinaryExpr(binaryExpr: BinaryExpr) {
        binaryExpr.left.accept(this)
        binaryExpr.right.accept(this)
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

    override fun visitCallExpr(callExpr: CallExpr) {
        callExpr.callee.accept(this)
        callExpr.arguments.forEach { it.accept(this) }
    }

    override fun visitFunctionStmt(functionStmt: FunctionStmt) {
        resolveFunction(functionStmt)
    }

    override fun visitReturnStmt(returnStmt: ReturnStmt) {
        if (returnStmt.value != null) when (currentFunction?.kind) {
            INITIALIZER -> error(returnStmt.keyword, "Can't return a value from an initializer.")
            null /* SCRIPT */ -> error(returnStmt.keyword, "Can't return from top-level code.")
            else -> returnStmt.value.accept(this)
        }
    }

    override fun visitClassStmt(classStmt: ClassStmt) {
        val enclosingClass = currentClassType
        currentClassType = ClassType.CLASS
        if (classStmt.superClass != null) {
            currentClassType = SUBCLASS
            if (classStmt.name.lexeme == classStmt.superClass.name.lexeme) {
                error(
                    classStmt.superClass.name,
                    "A class can't inherit from itself."
                )
            }

            classStmt.superClass.accept(this)
        }
        classStmt.methods.forEach { resolveFunction(it) }
        currentClassType = enclosingClass
    }

    private fun resolveFunction(functionStmt: FunctionStmt) {
        val enclosingFunction = currentFunction
        currentFunction = functionStmt
        functionStmt.body.forEach { it.accept(this) }
        currentFunction = enclosingFunction
    }

    override fun visitGetExpr(getExpr: GetExpr) {
        getExpr.obj.accept(this)
    }

    override fun visitSetExpr(setExpr: SetExpr) {
        setExpr.obj.accept(this)
        setExpr.value.accept(this)
    }

    override fun visitSuperExpr(superExpr: SuperExpr) {
        if (currentClassType == NONE) {
            error(superExpr.name, "Can't use 'super' outside of a class.")
        } else if (currentClassType != SUBCLASS) {
            error(superExpr.name, "Can't use 'super' in a class with no superclass.")
        }
    }

    override fun visitThisExpr(thisExpr: ThisExpr) {
        if (currentClassType == NONE) {
            error(thisExpr.name, "Can't use 'this' outside of a class.")
        } else if (currentFunction?.isStatic == true) {
            error(thisExpr.name, "Can't use 'this' in a static method.")
        }
    }
}

enum class ClassType {
    NONE,
    CLASS,
    SUBCLASS
}

enum class FunctionType {
    SCRIPT,
    METHOD,
    FUNCTION,
    INITIALIZER,
    GETTER,
    NATIVE;

    override fun toString(): String {
        return super.toString().lowercase(Locale.getDefault())
    }
}
