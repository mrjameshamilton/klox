package eu.jameshamilton.klox.interpret

import eu.jameshamilton.klox.compile.SuperConstructorCallCounter
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
import eu.jameshamilton.klox.parse.FunctionFlag.*
import eu.jameshamilton.klox.parse.FunctionStmt
import eu.jameshamilton.klox.parse.GetExpr
import eu.jameshamilton.klox.parse.GroupingExpr
import eu.jameshamilton.klox.parse.IfStmt
import eu.jameshamilton.klox.parse.LiteralExpr
import eu.jameshamilton.klox.parse.LogicalExpr
import eu.jameshamilton.klox.parse.ModifierFlag
import eu.jameshamilton.klox.parse.ModifierFlag.STATIC
import eu.jameshamilton.klox.parse.MultiVarStmt
import eu.jameshamilton.klox.parse.PrintStmt
import eu.jameshamilton.klox.parse.ReturnStmt
import eu.jameshamilton.klox.parse.SetExpr
import eu.jameshamilton.klox.parse.Stmt
import eu.jameshamilton.klox.parse.SuperExpr
import eu.jameshamilton.klox.parse.ThisExpr
import eu.jameshamilton.klox.parse.Token
import eu.jameshamilton.klox.parse.TokenType.*
import eu.jameshamilton.klox.parse.UnaryExpr
import eu.jameshamilton.klox.parse.VarStmt
import eu.jameshamilton.klox.parse.VariableExpr
import eu.jameshamilton.klox.parse.WhileStmt
import eu.jameshamilton.klox.parse.ungroup
import eu.jameshamilton.klox.runtimeError
import java.lang.Double.doubleToRawLongBits
import java.math.BigDecimal
import kotlin.Double.Companion.NEGATIVE_INFINITY
import kotlin.Double.Companion.POSITIVE_INFINITY
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.math.pow
import eu.jameshamilton.klox.parse.Expr.Visitor as ExprVisitor
import eu.jameshamilton.klox.parse.Stmt.Visitor as StmtVisitor

class Interpreter(val args: Array<String> = emptyArray()) : ExprVisitor<Any?>, StmtVisitor<Unit> {

    val globals = Environment()

    val resultClass: LoxClass by lazy { globals.get(Token(IDENTIFIER, "Result")) as LoxClass }
    val errorClass: LoxClass by lazy { globals.get(Token(IDENTIFIER, "Error")) as LoxClass }
    val okClass: LoxClass by lazy { globals.get(Token(IDENTIFIER, "Ok")) as LoxClass }
    val numberClass: LoxClass by lazy { globals.get(Token(IDENTIFIER, "Number")) as LoxClass }
    val characterClass: LoxClass by lazy { globals.get(Token(IDENTIFIER, "Character")) as LoxClass }

    private val locals = mutableMapOf<Expr, Int>()

    private var environment = globals

    fun interpret(stmts: List<Stmt>) {
        try {
            stmts.forEach { execute(it) }
        } catch (e: RuntimeError) {
            runtimeError(e)
        }
    }

    fun resolve(expr: Expr, depth: Int) {
        locals[expr] = depth
    }

    private fun execute(stmt: Stmt) {
        stmt.accept(this)
    }

    fun executeBlock(stmts: List<Stmt>, environment: Environment) {
        val previous = this.environment
        try {
            this.environment = environment
            stmts.forEach { execute(it) }
        } finally {
            this.environment = previous
        }
    }

    private fun evaluate(expr: Expr): Any? = expr.accept(this)

    override fun visitBinaryExpr(binaryExpr: BinaryExpr): Any? {
        val left = evaluate(binaryExpr.left)
        val right = evaluate(binaryExpr.right)

        fun plus(): Any {
            if ((left !is Double && left !is String && left !is LoxInstance) ||
                (right !is Double && right !is String && right !is LoxInstance)
            ) {
                throw RuntimeError(binaryExpr.operator, "Operands must be two numbers or two strings.")
            }

            return if (left is Double && right is Double) left + right
            else return "${stringify(this, left)}${stringify(this, right)}"
        }

        if (left is LoxInstance && binaryExpr.isOverloadable) with(left.get(Token(IDENTIFIER, binaryExpr.overloadMethodName), safeAccess = true)) {
            return when {
                this is LoxFunction -> when (binaryExpr.operator.type) {
                    BANG_EQUAL -> !(this.call(this@Interpreter, listOf(right)) as Boolean)
                    else -> this.call(this@Interpreter, listOf(right))
                }
                binaryExpr.operator.type == PLUS -> plus()
                else -> throw RuntimeError(
                    binaryExpr.operator,
                    "'${left.klass.name}' does not have an operator method '${binaryExpr.overloadMethodName}'."
                )
            }
        }

        when (binaryExpr.operator.type) {
            MINUS, SLASH, STAR, STAR_STAR, GREATER, GREATER_GREATER, LESS_LESS, GREATER_GREATER_GREATER, GREATER_EQUAL, LESS, LESS_EQUAL, PIPE, AMPERSAND, CARET ->
                checkNumberOperands(binaryExpr.operator, left, right)
            else -> { }
        }

        when (binaryExpr.operator.type) {
            PIPE, AMPERSAND, CARET, LESS_LESS, GREATER_GREATER ->
                checkIntegerOperands(binaryExpr.operator, left, right)
            else -> { }
        }

        return when (binaryExpr.operator.type) {
            MINUS -> (left as Double) - (right as Double)
            SLASH -> {
                if (right as Double == 0.0) return Double.NaN
                return left as Double / right
            }
            STAR -> (left as Double) * (right as Double)
            STAR_STAR -> (left as Double).pow(right as Double)
            PERCENT -> (left as Double) % (right as Double)
            GREATER -> (left as Double) > (right as Double)
            GREATER_EQUAL -> (left as Double) >= (right as Double)
            LESS -> (left as Double) < (right as Double)
            LESS_EQUAL -> (left as Double) <= (right as Double)
            BANG_EQUAL -> !isEqual(left, right)
            EQUAL_EQUAL -> isEqual(left, right)
            PLUS -> plus()
            IS -> return when {
                left !is LoxInstance -> false
                right !is LoxClass -> false
                else -> return kloxIsInstance(left, right)
            }
            COMMA -> return right
            PIPE -> ((left as Double).toInt() or (right as Double).toInt()).toDouble()
            AMPERSAND -> ((left as Double).toInt() and (right as Double).toInt()).toDouble()
            CARET -> ((left as Double).toInt() xor (right as Double).toInt()).toDouble()
            LESS_LESS -> ((left as Double).toInt() shl (right as Double).toInt()).toDouble()
            GREATER_GREATER -> ((left as Double).toInt() shr (right as Double).toInt()).toDouble()
            GREATER_GREATER_GREATER -> ((left as Double).toInt() ushr (right as Double).toInt()).toDouble()
            DOT_DOT -> return when {
                // TODO better errors
                // TODO numbers and strings for now call a static method
                left is Double && right is Double -> numberClass.findMethod("rangeTo")?.call(this, listOf(left, right))
                left is String && right is String -> characterClass.findMethod("rangeTo")?.call(this, listOf(left.first().toString(), right.first().toString()))
                // Otherwise, call an instance method "left.rangeTo(right)"
                left is LoxInstance -> (left.get(Token(IDENTIFIER, "rangeTo")) as LoxFunction).call(this, listOf(right))
                else -> throw RuntimeError(binaryExpr.operator, "Left operand must implement 'rangeTo'.")
            }
            else -> throw RuntimeError(binaryExpr.operator, "Not implemented")
        }
    }

    private fun kloxIsInstance(kloxInstance: LoxInstance, kloxClass: LoxClass): Boolean {
        var klass = kloxInstance.klass as LoxClass?
        while (klass != null) {
            if (klass == kloxClass) return true
            else klass = klass.superClass
        }
        return false
    }

    override fun visitUnaryExpr(unaryExpr: UnaryExpr): Any? {
        val right = evaluate(unaryExpr.right)

        return when (unaryExpr.operator.type) {
            BANG -> !isTruthy(right)
            MINUS -> {
                checkNumberOperand(unaryExpr.operator, right)
                -(right as Double)
            }
            TILDE -> {
                checkIntegerOperand(unaryExpr.operator, right)
                (right as Double).toInt().inv().toDouble()
            }
            PLUS_PLUS, MINUS_MINUS -> {
                if (right == null) error(
                    unaryExpr.operator,
                    "${unaryExpr.operator.lexeme} operand is 'nil'."
                )

                val varExpr = ungroup(unaryExpr.right) as VariableExpr

                checkNumberOperand(
                    unaryExpr.operator,
                    right,
                    "${unaryExpr.operator.lexeme} operand must be a number."
                )

                with(right as Double) {
                    val newValue = if (unaryExpr.operator.type == PLUS_PLUS) right + 1 else right - 1
                    environment.assign(varExpr.name, newValue)
                    return if (unaryExpr.postfix) right else newValue
                }
            }
            BANG_QUESTION -> {
                if (right !is LoxInstance || !kloxIsInstance(right, resultClass)) {
                    throw RuntimeError(unaryExpr.operator, "!? operator can only be used with functions that return 'Result'.")
                }

                val isError = (right.get(Token(IDENTIFIER, "isError")) as LoxCallable)
                    .call(this, emptyList()) as Boolean

                return if (isError) throw Return(right) else right.get(Token(IDENTIFIER, "value"))
            }
            else -> null
        }
    }

    override fun visitCallExpr(callExpr: CallExpr): Any? {
        val callee = evaluate(callExpr.callee)

        val arguments = callExpr.arguments.map { evaluate(it) }

        if (callee !is LoxCallable) when {
            callExpr.callee is GetExpr && callExpr.callee.safeAccess -> return null
            else -> throw RuntimeError(callExpr.paren, "Can only call functions and classes.")
        }

        if (callee.arity() != arguments.size) {
            throw RuntimeError(callExpr.paren, "Expected ${callee.arity()} arguments but got ${arguments.size}.")
        }

        return callee.call(this, arguments)
    }

    override fun visitGetExpr(getExpr: GetExpr): Any? = when (val obj = evaluate(getExpr.obj)) {
        is LoxInstance, is LoxClass -> {
            val value = when (obj) {
                is LoxInstance -> obj.get(getExpr.name, safeAccess = getExpr.safeAccess)
                is LoxClass -> {
                    val method = obj.findMethod(getExpr.name.lexeme)
                    if (method != null && !method.modifiers.contains(STATIC)) {
                        throw RuntimeError(getExpr.name, "'${method.name}' is not a static class method.")
                    } else method ?: throw RuntimeError(getExpr.name, "Method '${getExpr.name.lexeme}' not found.")
                }
                else -> null
            }

            if (value is LoxFunction && value.declaration.flags.contains(GETTER)) value.call(this) else value
        }
        else -> if (obj == null && getExpr.safeAccess) null else {
            throw RuntimeError(getExpr.name, "Only instances have properties.")
        }
    }

    override fun visitSetExpr(setExpr: SetExpr): Any? {
        val obj = evaluate(setExpr.obj)
        if (obj !is LoxInstance) {
            throw RuntimeError(setExpr.name, "Only instances have fields.")
        }

        obj.set(setExpr.name, evaluate(setExpr.value))

        return evaluate(setExpr.value)
    }

    override fun visitFunctionStmt(functionStmt: FunctionStmt) {
        environment.define(
            functionStmt.name.lexeme,
            LoxFunction(classStmt = null, functionStmt.modifiers, functionStmt.name.lexeme, functionStmt.functionExpr, environment)
        )
    }

    override fun visitFunctionExpr(functionExpr: FunctionExpr): LoxFunction {
        return LoxFunction(classStmt = null, ModifierFlag.empty(), "anon", functionExpr, environment)
    }

    override fun visitGroupingExpr(groupingExpr: GroupingExpr): Any? =
        evaluate(groupingExpr.expression)

    override fun visitLiteralExpr(literalExpr: LiteralExpr): Any? =
        literalExpr.value

    override fun visitLogicalExpr(logicalExpr: LogicalExpr): Any? {
        val left = evaluate(logicalExpr.left)

        when (logicalExpr.operator.type) {
            OR -> if (isTruthy(left)) return left
            AND -> if (!isTruthy(left)) return left
            else -> throw RuntimeError(logicalExpr.operator, "Invalid logical operator.")
        }

        return evaluate(logicalExpr.right)
    }

    private fun checkNumberOperand(operator: Token, operand: Any?, message: String = "Operand must be a number.") {
        if (operand !is Double) {
            throw RuntimeError(operator, message)
        }
    }

    @OptIn(ExperimentalContracts::class)
    private fun checkIntegerOperand(operator: Token, operand: Any?) {
        contract {
            returns(true) implies (operand is Double)
        }

        if (!isKloxInteger(operand)) {
            throw RuntimeError(operator, "Operand must be an integer.")
        }
    }

    private fun checkNumberOperands(operator: Token, left: Any?, right: Any?) {
        if (left == null && right == null) return

        if (!(left is Double && right is Double)) {
            throw RuntimeError(operator, "Operands must be numbers.")
        }
    }

    @OptIn(ExperimentalContracts::class)
    private fun checkIntegerOperands(operator: Token, left: Any?, right: Any?) {
        contract {
            returns(true) implies (left is Double && right is Double)
        }

        if (!isKloxInteger(left) && !isKloxInteger(right)) {
            throw RuntimeError(operator, "Operands must be integers.")
        }
    }

    private fun isTruthy(value: Any?): Boolean = when (value) {
        is Boolean -> value
        null -> false
        else -> true
    }

    private fun isEqual(a: Any?, b: Any?): Boolean =
        if (a == null && b == null) true
        else when {
            a is Double && b is Double && a.isNaN() && b.isNaN() -> false
            else -> a?.equals(b) ?: false
        }

    override fun visitExprStmt(exprStmt: ExprStmt) {
        evaluate(exprStmt.expression)
    }

    override fun visitIfStmt(ifStmt: IfStmt) {
        if (isTruthy(evaluate(ifStmt.condition))) {
            execute(ifStmt.thenBranch)
        } else if (ifStmt.elseBranch != null) {
            execute(ifStmt.elseBranch)
        }
    }

    override fun visitPrintStmt(printStmt: PrintStmt) {
        println(stringify(this, evaluate(printStmt.expression)))
    }

    override fun visitReturnStmt(returnStmt: ReturnStmt) {
        throw Return(if (returnStmt.value != null) evaluate(returnStmt.value); else null)
    }

    override fun visitWhileStmt(whileStmt: WhileStmt) {
        while (isTruthy(evaluate(whileStmt.condition))) {
            try {
                execute(whileStmt.body)
            } catch (ignored: Break) {
                break
            } catch (ignored: Continue) {
                continue
            }
        }
    }

    override fun visitDoWhileStmt(whileStmt: DoWhileStmt) {
        do {
            try {
                execute(whileStmt.body)
            } catch (ignored: Break) {
                break
            } catch (ignored: Continue) {
                continue
            }
        } while (isTruthy(evaluate(whileStmt.condition)))
    }

    override fun visitBreakStmt(breakStmt: BreakStmt) =
        throw Break()

    override fun visitContinueStmt(continueStmt: ContinueStmt) =
        throw Continue()

    override fun visitVarStmt(varStmt: VarStmt) {
        environment.define(
            varStmt.name.lexeme,
            if (varStmt.initializer != null) evaluate(varStmt.initializer as Expr) else null
        )
    }

    override fun visitVariableExpr(variableExpr: VariableExpr): Any? =
        lookupVariable(variableExpr.name, variableExpr)

    override fun visitAssignExpr(assignExpr: AssignExpr): Any? {
        val value = evaluate(assignExpr.value)
        val distance = locals[assignExpr]
        if (distance != null) {
            environment.assignAt(distance, assignExpr.name, value)
        } else globals.assign(assignExpr.name, value)
        return value
    }

    override fun visitBlockStmt(block: BlockStmt) =
        executeBlock(block.stmts, Environment(environment))

    override fun visitMultiVarStmt(multiVarStmt: MultiVarStmt) {
        multiVarStmt.statements.forEach { it.accept(this) }
    }

    override fun visitClassStmt(classStmt: ClassStmt) {
        val superClass = if (classStmt.superClass != null) {
            evaluate(classStmt.superClass)
        } else null

        if (classStmt.superClass != null && superClass !is LoxClass) {
            throw RuntimeError(classStmt.superClass.name, "Superclass must be a class.")
        } else if (superClass is LoxClass) {
            classStmt.methods.singleOrNull { it.name.lexeme == "init" }?.let {
                superClass.findMethod("init")?.run {
                    if (arity() > 0 && SuperConstructorCallCounter().count(it.functionExpr) == 0) {
                        throw RuntimeError(
                            classStmt.name,
                            "'${classStmt.name.lexeme}' does not call superclass '${superClass.name}' constructor."
                        )
                    }
                }
            }
        }

        environment.define(classStmt.name.lexeme)

        if (classStmt.superClass != null) {
            environment = Environment(environment)
            environment.define("super", superClass)
        }

        val methods = mutableMapOf<String, LoxFunction>()
        classStmt.methods.forEach {
            methods[it.name.lexeme] = LoxFunction(classStmt, it.modifiers, it.name.lexeme, it.functionExpr, environment)
        }

        val klass = LoxClass(classStmt.name.lexeme, superClass as LoxClass?, methods)

        if (superClass != null) {
            environment = environment.enclosing!!
        }

        environment.assign(classStmt.name, klass)
    }

    override fun visitSuperExpr(superExpr: SuperExpr): Any {
        val distance = locals.getOrDefault(superExpr, 0)
        val superClass = environment.getAt(distance, "super") as LoxClass

        // Look up this in the inner environment
        val obj = environment.getAt(distance - 1, "this") as LoxInstance

        val method = superClass.findMethod(superExpr.method.lexeme)
            ?: throw RuntimeError(superExpr.method, "Undefined property '${superExpr.method.lexeme}'.")

        return method.bind(obj)
    }

    override fun visitThisExpr(thisExpr: ThisExpr): Any? =
        lookupVariable(thisExpr.name, thisExpr)

    private fun lookupVariable(name: Token, expr: Expr): Any? {
        val distance = locals[expr]
        return if (distance != null) environment.getAt(distance, name.lexeme); else globals.get(name)
    }

    private class Break : RuntimeException()
    private class Continue : RuntimeException()
    class Return(val value: Any?) : RuntimeException()

    companion object {

        fun stringify(interpreter: Interpreter, value: Any?): String = when (value) {
            null -> "nil"
            is Double ->
                when {
                    value == 0 && doubleToRawLongBits(value) == Long.MIN_VALUE -> "-0"
                    value == NEGATIVE_INFINITY -> "-Infinity"
                    value == POSITIVE_INFINITY -> "+Infinity"
                    value.isNaN() -> "NaN"
                    else -> BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
                }
            is LoxInstance -> try {
                val toString = value.get(Token(IDENTIFIER, "toString"))
                if (toString is LoxFunction) stringify(interpreter, toString.call(interpreter, emptyList()))
                else value.toString()
            } catch (e: Exception) {
                value.toString()
            }
            else -> value.toString()
        }
    }

    override fun visitArrayExpr(arrayExpr: ArrayExpr): Any {
        val array = evaluate(
            CallExpr(
                VariableExpr(Token(IDENTIFIER, "Array")),
                Token(LEFT_PAREN, "("),
                listOf(LiteralExpr(arrayExpr.elements.size.toDouble()))
            )
        ) as LoxInstance

        @Suppress("UNCHECKED_CAST")
        val underlyingArray =
            array.get(Token(IDENTIFIER, "\$array")) as Array<Any?>

        for ((index, element) in arrayExpr.elements.withIndex())
            underlyingArray[index] = evaluate(element)

        return array
    }
}

class RuntimeError(val token: Token, override val message: String) : RuntimeException(message)

class Environment(val enclosing: Environment? = null) {
    private val values: MutableMap<String, Any?> = mutableMapOf()

    fun define(name: String, value: Any? = null) {
        values[name] = value
    }

    fun get(name: Token): Any? {
        return if (values.containsKey(name.lexeme)) values[name.lexeme]
        else if (enclosing != null) enclosing.get(name)
        else {
            throw RuntimeError(name, "Undefined variable '${name.lexeme}'.")
        }
    }

    fun getAt(distance: Int, name: String): Any? {
        return ancestor(distance).values[name]
    }

    fun assign(name: Token, value: Any?) {
        return if (values.containsKey(name.lexeme)) values[name.lexeme] = value
        else if (enclosing != null) enclosing.assign(name, value)
        else throw RuntimeError(name, "Undefined variable '${name.lexeme}'.")
    }

    fun assignAt(distance: Int, name: Token, value: Any?) {
        ancestor(distance).values[name.lexeme] = value
    }

    private fun ancestor(distance: Int): Environment {
        var environment = this
        for (i in 0 until distance) {
            environment = environment.enclosing!!
        }
        return environment
    }
}
