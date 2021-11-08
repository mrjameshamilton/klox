package eu.jameshamilton.klox.compile

import eu.jameshamilton.klox.error
import eu.jameshamilton.klox.parse.AssignExpr
import eu.jameshamilton.klox.parse.BinaryExpr
import eu.jameshamilton.klox.parse.BlockStmt
import eu.jameshamilton.klox.parse.BreakStmt
import eu.jameshamilton.klox.parse.CallExpr
import eu.jameshamilton.klox.parse.ClassStmt
import eu.jameshamilton.klox.parse.ContinueStmt
import eu.jameshamilton.klox.parse.Expr
import eu.jameshamilton.klox.parse.ExprStmt
import eu.jameshamilton.klox.parse.FunctionStmt
import eu.jameshamilton.klox.parse.GetExpr
import eu.jameshamilton.klox.parse.GroupingExpr
import eu.jameshamilton.klox.parse.IfStmt
import eu.jameshamilton.klox.parse.LiteralExpr
import eu.jameshamilton.klox.parse.LogicalExpr
import eu.jameshamilton.klox.parse.PrintStmt
import eu.jameshamilton.klox.parse.ReturnStmt
import eu.jameshamilton.klox.parse.SetExpr
import eu.jameshamilton.klox.parse.Stmt
import eu.jameshamilton.klox.parse.SuperExpr
import eu.jameshamilton.klox.parse.ThisExpr
import eu.jameshamilton.klox.parse.Token
import eu.jameshamilton.klox.parse.TokenType.IDENTIFIER
import eu.jameshamilton.klox.parse.UnaryExpr
import eu.jameshamilton.klox.parse.VarAccess
import eu.jameshamilton.klox.parse.VarDef
import eu.jameshamilton.klox.parse.VarStmt
import eu.jameshamilton.klox.parse.VariableExpr
import eu.jameshamilton.klox.parse.WhileStmt
import java.util.Stack
import java.util.WeakHashMap

class Resolver : Expr.Visitor<Unit>, Stmt.Visitor<Unit> {

    private val scopes = Stack<MutableMap<VarDef, Boolean>>()
    private val unresolved = mutableSetOf<Pair<FunctionStmt, VarAccess>>()
    private val functionStack = Stack<FunctionStmt>()
    private val currentFunction: FunctionStmt get() = functionStack.peek()

    // Global scope == 1 because we start with the wrapper main function
    private val isGlobalScope: Boolean get() = scopes.size == 1

    private fun resolve(stmts: List<Stmt>) = stmts.forEach { it.accept(this) }

    private fun resolve(stmt: Stmt) = stmt.accept(this)

    private fun resolve(expr: Expr) = expr.accept(this)

    private fun beginScope(): MutableMap<VarDef, Boolean>? = scopes.push(mutableMapOf())

    private fun endScope() = scopes.pop()

    private fun declare(inVarDef: VarDef) {
        if (scopes.empty()) return

        var varDef = inVarDef

        if (scopes.peek().size > 254) {
            error(varDef.name, "Too many local variables in function.")
        }

        if (scopes.peek().count { it.key.name.lexeme == varDef.name.lexeme } > 0) {
            if (isGlobalScope) {
                val key = scopes.peek().keys.single { it.name.lexeme == varDef.name.lexeme }
                // Special case: global scope allows redefining var in initializer, so replace the varDef with the VarDef of the original `a`.
                /* var a = "foo"; var a = a; */
                scopes.peek().remove(key)
                if (varDef is VarStmt && varDef.initializer is VarAccess) {
                    slotsInFunctions[currentFunction]?.set(varDef, currentFunction.slot(key))
                    varDef = key
                }
            } else error(varDef.name, "Already a variable with this name in this scope.")
        }

        scopes.peek()[varDef] = false

        // Special case: globals used before they're declared
        if (isGlobalScope) unresolved.find { it.second.name.lexeme == varDef.name.lexeme }?.let {
            val (func, varAccess) = it
            varUseMap[varAccess] = varDef
            func.capture(varDef)
            lateInits.add(varDef)
            unresolved.remove(it)
        }

        when (varDef) {
            is FunctionStmt -> classNames[varDef] = functionStack
                .joinToString(
                    separator = "$",
                    postfix = "$${varDef.name.lexeme}"
                ) { it.name.lexeme }
            is ClassStmt -> classNames[varDef] = varDef.name.lexeme
        }

        javaFieldNames[varDef] = "${varDef.name.lexeme}#${varDef.hashCode()}"
        definedIns[varDef] = currentFunction // TODO why does use_local_in_initializer fail without this?
        currentFunction.assign(varDef)
    }

    private fun define(varDef: VarDef) {
        if (scopes.empty()) return

        val isRedefinedGlobal = isGlobalScope && scopes.peek().count { it.key.name.lexeme == varDef.name.lexeme } > 0
        if (!isRedefinedGlobal) {
            scopes.peek()[varDef] = true
        }

        definedIns[varDef] = currentFunction
    }

    private fun resolveLocal(varAccess: VarAccess) {
        for (i in scopes.size - 1 downTo 0) {
            if (scopes[i].count { it.key.name.lexeme == varAccess.name.lexeme } > 0) {
                scopes[i].map { it.key }.single { it.name.lexeme == varAccess.name.lexeme }.let { varDef ->
                    if (varAccess is SuperExpr || varAccess is ThisExpr) {
                        superThisAccessDepths[varAccess] =
                            functionStack.size - functionStack.indexOf(varDef.definedIn) - 1
                    } else {
                        if (currentFunction != varDef.definedIn) currentFunction.capture(varDef)
                        varUseMap[varAccess] = varDef
                    }
                    return
                }
            }
        }

        unresolved.add(Pair(currentFunction, varAccess))
    }

    private fun resolveFunction(functionStmt: FunctionStmt) {
        functionStack.push(functionStmt)
        beginScope()
        functionStmt.params.forEach {
            declare(it)
            define(it)
        }
        resolve(functionStmt.body)
        endScope()
        functionStack.pop()
    }

    override fun visitExprStmt(exprStmt: ExprStmt) = resolve(exprStmt.expression)

    override fun visitPrintStmt(printStmt: PrintStmt) = resolve(printStmt.expression)

    override fun visitVarStmt(varStmt: VarStmt) {
        declare(varStmt)
        varStmt.initializer?.let { resolve(it) }
        define(varStmt)
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

    override fun visitBreakStmt(breakStmt: BreakStmt) { }

    override fun visitContinueStmt(continueStmt: ContinueStmt) { }

    override fun visitBinaryExpr(binaryExpr: BinaryExpr) {
        resolve(binaryExpr.left)
        resolve(binaryExpr.right)
    }

    override fun visitUnaryExpr(unaryExpr: UnaryExpr) = resolve(unaryExpr.right)

    override fun visitGroupingExpr(groupingExpr: GroupingExpr) = resolve(groupingExpr.expression)

    override fun visitLiteralExpr(literalExpr: LiteralExpr) {}

    override fun visitVariableExpr(variableExpr: VariableExpr) {
        if (!isGlobalScope /* allowed in global scope */ &&
            scopes.peek().count { it.key.name.lexeme == variableExpr.name.lexeme } > 0 &&
            scopes.peek().entries.find { it.key.name.lexeme == variableExpr.name.lexeme }?.value == false
        ) {
            error(variableExpr.name, "Can't read local variable in its own initializer.")
        }

        resolveLocal(variableExpr)
    }

    override fun visitAssignExpr(assignExpr: AssignExpr) {
        resolve(assignExpr.value)
        resolveLocal(assignExpr)
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
        functionDepths[functionStmt] = scopes.size
        declare(functionStmt)
        define(functionStmt)
        resolveFunction(functionStmt)
    }

    override fun visitReturnStmt(returnStmt: ReturnStmt) {
        returnStmt.value?.let { resolve(it) }
    }

    override fun visitClassStmt(classStmt: ClassStmt) {
        declare(classStmt)
        define(classStmt)

        classStmt.superClass?.let { resolve(it) }

        beginScope()

        define(THIS)
        classStmt.superClass?.let { define(SUPER) }

        classStmt.methods.forEach {
            functionDepths[it] = scopes.size
            classNames[it] = "${classStmt.name.lexeme}\$${it.name.lexeme}"
            classes[it] = classStmt
            resolveFunction(it)
        }

        endScope()
    }

    override fun visitGetExpr(getExpr: GetExpr) = resolve(getExpr.obj)

    override fun visitSetExpr(setExpr: SetExpr) {
        resolve(setExpr.obj)
        resolve(setExpr.value)
    }

    override fun visitSuperExpr(superExpr: SuperExpr) = resolveLocal(superExpr)

    override fun visitThisExpr(thisExpr: ThisExpr) = resolveLocal(thisExpr)

    companion object {
        // AST decorations for variable definition and usage.

        private val lateInits = mutableSetOf<VarDef>()
        val VarDef.isGlobalLateInit
            get() = lateInits.contains(this)

        private val superThisAccessDepths = WeakHashMap<VarAccess, Int>()
        val ThisExpr.depth: Int
            get() = superThisAccessDepths[this]!!

        val SuperExpr.depth: Int
            get() = superThisAccessDepths[this]!!

        private val varUseMap = WeakHashMap<VarAccess, VarDef?>()
        val VarAccess.varDef: VarDef?
            get() = varUseMap[this]

        val VarAccess.isDefined: Boolean
            get() = varDef != null

        private val captures = WeakHashMap<FunctionStmt, MutableSet<VarDef>>()
        val VarDef.isCaptured: Boolean
            get() = captures.filterValues { it.contains(this) }.isNotEmpty()

        val FunctionStmt.captured: MutableSet<VarDef>
            get() = captures.getOrPut(this) { HashSet() }

        private fun FunctionStmt.capture(varDef: VarDef) {
            if (!captured.contains(varDef)) {
                assign(varDef)
                captured.add(varDef)
            }
        }

        private val definedIns = WeakHashMap<VarDef, FunctionStmt>()
        val VarDef.definedIn: FunctionStmt
            get() = definedIns[this]!!

        val FunctionStmt.variables
            get() = definedIns.keys.filter { it.definedIn == this }

        private val functionDepths = WeakHashMap<FunctionStmt, Int>()
        val FunctionStmt.depth: Int
            get() = functionDepths[this]!!

        private val slotsInFunctions = WeakHashMap<FunctionStmt, MutableMap<VarDef, Int>>()
        private val FunctionStmt.slots get() = slotsInFunctions.getOrPut(this) { WeakHashMap() }
        private val FunctionStmt.nextSlot get() = (slotsInFunctions[this]?.size ?: 0) + 1

        fun FunctionStmt.slot(varDef: VarDef): Int = slots.getOrDefault(varDef, -1)

        private fun FunctionStmt.assign(varDef: VarDef): Int {
            slots[varDef] = nextSlot
            return this.slot(varDef)
        }

        private val classes = WeakHashMap<FunctionStmt, ClassStmt>()
        val FunctionStmt.classStmt: ClassStmt?
            get() = classes.getOrDefault(this, null)

        private val javaFieldNames = WeakHashMap<VarDef, String>()
        val VarDef.javaName: String
            get() = javaFieldNames.getOrDefault(this, this.name.lexeme)

        private val classNames = WeakHashMap<VarDef, String>()
        val FunctionStmt.javaClassName: String
            get() = classNames.getOrDefault(this, this.name.lexeme)

        val ClassStmt.javaClassName: String
            get() = classNames.getOrDefault(this, this.name.lexeme)

        private val THIS = object : VarDef {
            override val name: Token get() = Token(IDENTIFIER, "this")
        }

        private val SUPER = object : VarDef {
            override val name: Token get() = Token(IDENTIFIER, "super")
        }
    }
}
