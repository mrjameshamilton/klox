package eu.jameshamilton.klox.compile

import eu.jameshamilton.klox.debug
import eu.jameshamilton.klox.error
import eu.jameshamilton.klox.parse.ArrayExpr
import eu.jameshamilton.klox.parse.AssignExpr
import eu.jameshamilton.klox.parse.BinaryExpr
import eu.jameshamilton.klox.parse.BlockStmt
import eu.jameshamilton.klox.parse.BreakStmt
import eu.jameshamilton.klox.parse.CallExpr
import eu.jameshamilton.klox.parse.ClassStmt
import eu.jameshamilton.klox.parse.ContinueStmt
import eu.jameshamilton.klox.parse.DoWhileStmt
import eu.jameshamilton.klox.parse.Expr
import eu.jameshamilton.klox.parse.ExprStmt
import eu.jameshamilton.klox.parse.FunctionExpr
import eu.jameshamilton.klox.parse.FunctionStmt
import eu.jameshamilton.klox.parse.GetExpr
import eu.jameshamilton.klox.parse.GroupingExpr
import eu.jameshamilton.klox.parse.IfStmt
import eu.jameshamilton.klox.parse.LiteralExpr
import eu.jameshamilton.klox.parse.LogicalExpr
import eu.jameshamilton.klox.parse.ModifierFlag.NATIVE
import eu.jameshamilton.klox.parse.MultiVarStmt
import eu.jameshamilton.klox.parse.PrintStmt
import eu.jameshamilton.klox.parse.ReturnStmt
import eu.jameshamilton.klox.parse.SetExpr
import eu.jameshamilton.klox.parse.Stmt
import eu.jameshamilton.klox.parse.SuperExpr
import eu.jameshamilton.klox.parse.ThisExpr
import eu.jameshamilton.klox.parse.Token
import eu.jameshamilton.klox.parse.TokenType.DOT_DOT
import eu.jameshamilton.klox.parse.TokenType.IDENTIFIER
import eu.jameshamilton.klox.parse.UnaryExpr
import eu.jameshamilton.klox.parse.VarAccess
import eu.jameshamilton.klox.parse.VarDef
import eu.jameshamilton.klox.parse.VarStmt
import eu.jameshamilton.klox.parse.VariableExpr
import eu.jameshamilton.klox.parse.WhileStmt
import eu.jameshamilton.klox.parse.visitor.AllFunctionExprVisitor
import eu.jameshamilton.klox.parse.visitor.AllFunctionStmtVisitor
import eu.jameshamilton.klox.parse.visitor.AllVarStmtVisitor
import java.util.Stack
import java.util.WeakHashMap

class Resolver : Expr.Visitor<Unit>, Stmt.Visitor<Unit> {

    private lateinit var mainFunction: FunctionStmt
    private val globalClasses: List<ClassStmt>
        get() = mainFunction.functionExpr.variables.filterIsInstance<ClassStmt>()

    private val scopes = Stack<MutableMap<VarDef, Boolean>>()
    private val unresolved = mutableSetOf<Pair<FunctionExpr, VarAccess>>()
    private val functionStack = Stack<FunctionExpr>()
    private val functionNameStack = Stack<String>()
    private val currentFunction: FunctionExpr get() = functionStack.peek()
    private var lambdaNumber = 0

    // Global scope == 1 because we start with the wrapper main function
    private val isGlobalScope: Boolean get() = scopes.size == 1
    private val FunctionExpr.isMain: Boolean get() = this == mainFunction.functionExpr

    fun execute(mainFunction: FunctionStmt) {
        clearMaps()
        this.mainFunction = mainFunction
        mainFunction.accept(this)

        if (debug == true) {
            println("Unresolved: ${unresolved.map { "$it (${it.second.hashCode()})" }}")
            println("Lateinits: ${lateInits.map { "$it (${it.hashCode()})" }}")
        }

        // Late initialization when capturing in initializer e.g. f in the following:
        // var f = fun (a) { if (a == 0) return 0; else return a + f(a - 1); };
        mainFunction.accept(
            AllVarStmtVisitor(object : VarStmt.Visitor<Unit> {
                override fun visitVarStmt(varStmt: VarStmt) {
                    if (varStmt.isCaptured) varStmt.initializer?.accept(
                        AllFunctionExprVisitor(object : FunctionExpr.Visitor<Unit> {
                            override fun visitFunctionExpr(functionExpr: FunctionExpr) {
                                if (functionExpr.captured.contains(varStmt)) lateInits += varStmt
                            }
                        })
                    )
                }
            })
        )

        val okClass = globalClasses.single { it.name.lexeme == "Ok" }
        val errorClass = globalClasses.single { it.name.lexeme == "Error" }
        mainFunction.accept(
            AllFunctionStmtVisitor(object : FunctionStmt.Visitor<Unit> {
                override fun visitFunctionStmt(functionStmt: FunctionStmt) {
                    if (functionStmt.modifiers.contains(NATIVE)) with(functionStmt.functionExpr) {
                        capture(okClass)
                        capture(errorClass)
                    }
                }
            })
        )
    }

    private fun resolve(stmts: List<Stmt>) = stmts.forEach { it.accept(this) }

    private fun resolve(stmt: Stmt) = stmt.accept(this)

    private fun resolve(expr: Expr) = expr.accept(this)

    private fun beginScope(): MutableMap<VarDef, Boolean>? = scopes.push(mutableMapOf())
    private fun beginScope(functionExpr: FunctionExpr) {
        functionDepths[functionExpr] = scopes.size
        functionStack.push(functionExpr)
        beginScope()
    }

    private fun endScope() = scopes.pop()
    private fun endScope(@Suppress("UNUSED_PARAMETER") functionExpr: FunctionExpr) {
        endScope()
        functionStack.pop()
    }

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
        if (isGlobalScope) unresolved.filter { it.second.name.lexeme == varDef.name.lexeme }.forEach {
            val (func, varAccess) = it
            varUseMap[varAccess] = varDef
            func.capture(varDef)
            lateInits.add(varDef)
            unresolved.remove(it)
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
        if (debug == true) print("resolveLocal($varAccess)")
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
                    if (debug == true) println(" = $varDef")
                    return
                }
            }
        }

        if (debug == true) println(" = unresolved")
        unresolved.add(Pair(currentFunction, varAccess))
    }

    private fun resolveFunction(functionExpr: FunctionExpr, name: String) {
        javaClassNames[functionExpr] = when {
            !functionExpr.isMain -> {
                functionNameStack.push(if (this.isGlobalScope) "${mainFunction.name.lexeme}\$$name" else name)
                functionNameStack.joinToString(separator = "$") { it.toString() }
            }
            else -> mainFunction.name.lexeme
        }

        beginScope(functionExpr)
        functionExpr.params.forEach {
            declare(it)
            define(it)
        }
        resolve(functionExpr.body)
        endScope(functionExpr)

        if (!functionExpr.isMain) functionNameStack.pop()
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

    override fun visitDoWhileStmt(whileStmt: DoWhileStmt) {
        resolve(whileStmt.body)
        resolve(whileStmt.condition)
    }

    override fun visitBreakStmt(breakStmt: BreakStmt) { }

    override fun visitContinueStmt(continueStmt: ContinueStmt) { }

    override fun visitMultiVarStmt(multiVarStmt: MultiVarStmt) =
        multiVarStmt.statements.forEach { it.accept(this) }

    override fun visitBinaryExpr(binaryExpr: BinaryExpr) {
        resolve(binaryExpr.left)
        resolve(binaryExpr.right)

        // TODO do this after resolving?
        if (!currentFunction.isMain) {
            when (binaryExpr.operator.type) {
                DOT_DOT -> {
                    currentFunction.capture(globalClasses.single { it.name.lexeme == "Number" })
                    currentFunction.capture(globalClasses.single { it.name.lexeme == "Character" })
                }
                else -> {}
            }
        }
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
        declare(functionStmt)
        define(functionStmt)
        resolveFunction(functionStmt.functionExpr, functionStmt.name.lexeme)
    }

    override fun visitFunctionExpr(functionExpr: FunctionExpr) {
        resolveFunction(functionExpr, "lambda${lambdaNumber++}")
    }

    override fun visitReturnStmt(returnStmt: ReturnStmt) {
        returnStmt.value?.let { resolve(it) }
    }

    override fun visitClassStmt(classStmt: ClassStmt) {
        declare(classStmt)
        define(classStmt)

        javaClassNames[classStmt] = if (!isGlobalScope) {
            functionNameStack.joinToString(separator = "$") { it } + "\$" + classStmt.name.lexeme
        } else {
            classStmt.name.lexeme
        }

        classStmt.superClass?.let { resolve(it) }

        beginScope()

        define(object : VarDef {
            override val name: Token get() = Token(IDENTIFIER, "this", "this", classStmt.name.line)
        })

        classStmt.superClass?.let {
            define(object : VarDef {
                override val name: Token get() = Token(IDENTIFIER, "super", "super", classStmt.name.line)
            })
        }

        classStmt.methods.forEach {
            resolveFunction(it.functionExpr, "${classStmt.name.lexeme}\$${it.name.lexeme}")
        }

        endScope()
    }

    override fun visitGetExpr(getExpr: GetExpr) = resolve(getExpr.obj)

    override fun visitSetExpr(setExpr: SetExpr) {
        resolve(setExpr.obj)
        resolve(setExpr.value)
    }

    override fun visitArrayExpr(arrayExpr: ArrayExpr) {
        if (!currentFunction.isMain) {
            currentFunction.capture(globalClasses.single { it.name.lexeme == "Array" })
        }

        arrayExpr.elements.forEach { it.accept(this) }
    }

    override fun visitSuperExpr(superExpr: SuperExpr) = resolveLocal(superExpr)

    override fun visitThisExpr(thisExpr: ThisExpr) = resolveLocal(thisExpr)

    private fun clearMaps() {
        lateInits.clear()
        superThisAccessDepths.clear()
        varUseMap.clear()
        captures.clear()
        definedIns.clear()
        functionDepths.clear()
        slotsInFunctions.clear()
        javaFieldNames.clear()
        javaClassNames.clear()
    }

    companion object {
        // TODO move these to AST nodes classes?

        // AST decorations for variable definition and usage.

        private val lateInits = mutableSetOf<VarDef>()
        val VarDef.isLateInit
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

        private val captures = WeakHashMap<FunctionExpr, MutableSet<VarDef>>()
        val VarDef.isCaptured: Boolean
            get() = captures.filterValues { it.contains(this) }.isNotEmpty()

        val FunctionExpr.captured: MutableSet<VarDef>
            get() = captures.getOrPut(this) { HashSet() }

        private fun FunctionExpr.capture(varDef: VarDef) {
            if (!captured.contains(varDef)) {
                assign(varDef)
                captured.add(varDef)
            }
        }

        private val definedIns = WeakHashMap<VarDef, FunctionExpr>()
        val VarDef.definedIn: FunctionExpr
            get() = definedIns[this]!!

        val FunctionExpr.variables
            get() = definedIns.keys.filter { it.definedIn == this }

        private val functionDepths = WeakHashMap<FunctionExpr, Int>()
        val FunctionExpr.depth: Int
            get() = functionDepths[this]!!

        private val slotsInFunctions = WeakHashMap<FunctionExpr, MutableMap<VarDef, Int>>()
        private val FunctionExpr.slots get() = slotsInFunctions.getOrPut(this) { WeakHashMap() }
        val FunctionExpr.nextSlot get() = (slotsInFunctions[this]?.size ?: 0) + 1

        fun FunctionExpr.slot(varDef: VarDef): Int {
            if (!slots.containsKey(varDef))
                throw IllegalStateException("Variable $varDef has no slot assigned in function $this")

            return slots[varDef]!!
        }

        private fun FunctionExpr.assign(varDef: VarDef): Int {
            slots[varDef] = nextSlot
            return this.slot(varDef)
        }

        private val javaFieldNames = WeakHashMap<VarDef, String>()
        val VarDef.javaName: String
            get() = javaFieldNames.getOrDefault(this, this.name.lexeme)

        private val javaClassNames = WeakHashMap<Any, String>()
        val FunctionExpr.javaClassName: String
            get() = javaClassNames[this]!!

        val ClassStmt.javaClassName: String
            get() = javaClassNames.getOrDefault(this, this.name.lexeme)
    }
}
