package eu.jameshamilton.klox.parse

import eu.jameshamilton.klox.error
import eu.jameshamilton.klox.interpret.isKloxInteger
import eu.jameshamilton.klox.parse.Checker.ClassType.CLASS
import eu.jameshamilton.klox.parse.Checker.ClassType.NONE
import eu.jameshamilton.klox.parse.Checker.ClassType.SUBCLASS
import eu.jameshamilton.klox.parse.FunctionFlag.*
import eu.jameshamilton.klox.parse.ModifierFlag.STATIC
import eu.jameshamilton.klox.parse.TokenType.BANG_QUESTION
import eu.jameshamilton.klox.parse.TokenType.BREAK
import eu.jameshamilton.klox.parse.TokenType.CONTINUE
import eu.jameshamilton.klox.parse.TokenType.MINUS_MINUS
import eu.jameshamilton.klox.parse.TokenType.PLUS_PLUS
import eu.jameshamilton.klox.parse.TokenType.TILDE
import kotlin.contracts.ExperimentalContracts

class Checker : ASTVisitor<Unit> {
    private var inLoop = false
    private var currentFunction: FunctionExpr? = null
    private var currentFunctionStmt: FunctionStmt? = null
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

    override fun visitDoWhileStmt(whileStmt: DoWhileStmt) {
        inLoop = true
        whileStmt.body.accept(this)
        inLoop = false
        whileStmt.condition.accept(this)
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

    @OptIn(ExperimentalContracts::class)
    override fun visitUnaryExpr(unaryExpr: UnaryExpr) {
        unaryExpr.right.accept(this)

        when (unaryExpr.operator.type) {
            PLUS_PLUS, MINUS_MINUS -> if (ungroup(unaryExpr.right) !is VariableExpr) error(
                unaryExpr.operator,
                "${unaryExpr.operator.lexeme} operand must be a variable."
            )
            BANG_QUESTION -> if (currentFunction == null) {
                error(unaryExpr.operator, "Can't use !? in top-level code.")
            } else if (currentFunction?.flags?.contains(INITIALIZER) == true) {
                error(unaryExpr.operator, "Can't use !? in an initializer.")
            } else unaryExpr.right.accept(this)
            TILDE -> if (unaryExpr.right is LiteralExpr && !isKloxInteger(unaryExpr.right.value)) {
                error(unaryExpr.operator, "Can't use ~ on a non-integer value.")
            }
            else -> {}
        }
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
        val enclosingFunctionStmt = currentFunctionStmt
        currentFunctionStmt = functionStmt
        resolveFunction(functionStmt.functionExpr)
        currentFunctionStmt = enclosingFunctionStmt
    }

    override fun visitFunctionExpr(functionExpr: FunctionExpr) {
        resolveFunction(functionExpr)
    }

    override fun visitReturnStmt(returnStmt: ReturnStmt) {
        if (returnStmt.value != null) {
            if (currentFunction == null) {
                error(returnStmt.keyword, "Can't return from top-level code.")
            } else if (currentFunction?.flags?.contains(INITIALIZER) == true) {
                error(returnStmt.keyword, "Can't return a value from an initializer.")
            } else returnStmt.value.accept(this)
        }
    }

    override fun visitClassStmt(classStmt: ClassStmt) {
        val enclosingClass = currentClassType
        currentClassType = CLASS
        if (classStmt.superClass == null) {
            if (classStmt.name.lexeme != "Object") {
                error(classStmt.name, "Only Object has no super class.")
            }
        } else {
            currentClassType = SUBCLASS
            if (classStmt.name.lexeme == classStmt.superClass.name.lexeme) {
                error(
                    classStmt.superClass.name,
                    "A class can't inherit from itself."
                )
            }

            classStmt.superClass.accept(this)
        }
        classStmt.methods.forEach { it.accept(this) }
        currentClassType = enclosingClass
    }

    private fun resolveFunction(functionExpr: FunctionExpr) {
        val enclosingFunction = currentFunction
        currentFunction = functionExpr
        functionExpr.body.forEach { it.accept(this) }
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
        } else if (currentFunctionStmt?.modifiers?.contains(STATIC) == true) {
            error(superExpr.name, "Can't use 'super' in a static method.")
        }
    }

    override fun visitThisExpr(thisExpr: ThisExpr) {
        if (currentClassType == NONE) {
            error(thisExpr.name, "Can't use 'this' outside of a class.")
        } else if (currentFunctionStmt?.modifiers?.contains(STATIC) == true) {
            error(thisExpr.name, "Can't use 'this' in a static method.")
        }
    }

    private enum class ClassType {
        NONE,
        CLASS,
        SUBCLASS
    }

    override fun visitArrayExpr(arrayExpr: ArrayExpr) = arrayExpr.elements.forEach { it.accept(this) }

    override fun visitMultiVarStmt(multiVarStmt: MultiVarStmt) = multiVarStmt.statements.forEach { it.accept(this) }
}
