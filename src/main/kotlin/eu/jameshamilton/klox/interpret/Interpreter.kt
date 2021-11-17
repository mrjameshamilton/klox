package eu.jameshamilton.klox.interpret

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
import eu.jameshamilton.klox.parse.FunctionFlag.*
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
import eu.jameshamilton.klox.parse.TokenType.*
import eu.jameshamilton.klox.parse.UnaryExpr
import eu.jameshamilton.klox.parse.VarStmt
import eu.jameshamilton.klox.parse.VariableExpr
import eu.jameshamilton.klox.parse.WhileStmt
import eu.jameshamilton.klox.runtimeError
import eu.jameshamilton.klox.parse.Expr.Visitor as ExprVisitor
import eu.jameshamilton.klox.parse.Stmt.Visitor as StmtVisitor

class Interpreter(val args: Array<String> = emptyArray()) : ExprVisitor<Any?>, StmtVisitor<Unit> {

    val globals = Environment()

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

        when (binaryExpr.operator.type) {
            MINUS, SLASH, STAR, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL ->
                checkNumberOperands(binaryExpr.operator, left, right)
            else -> { }
        }

        return when (binaryExpr.operator.type) {
            MINUS -> (left as Double) - (right as Double)
            SLASH -> {
                if (right as Double == 0.0) return Double.NaN
                return left as Double / right
            }
            STAR -> (left as Double) * (right as Double)
            GREATER -> (left as Double) > (right as Double)
            GREATER_EQUAL -> (left as Double) >= (right as Double)
            LESS -> (left as Double) < (right as Double)
            LESS_EQUAL -> (left as Double) <= (right as Double)
            BANG_EQUAL -> !isEqual(left, right)
            EQUAL_EQUAL -> isEqual(left, right)
            PLUS -> {
                if ((left !is Double && left !is String && left !is LoxInstance) ||
                    (right !is Double && right !is String && right !is LoxInstance)
                ) {
                    throw RuntimeError(binaryExpr.operator, "Operands must be two numbers or two strings.")
                }

                return if (left is Double && right is Double) left + right
                else return "${stringify(this, left)}${stringify(this, right)}"
            }
            IS -> return when {
                left !is LoxInstance -> false
                right !is LoxClass -> false
                else -> {
                    var klass = left.klass as LoxClass?
                    while (klass != null) {
                        if (klass == right) return true
                        else klass = klass.superClass
                    }
                    return false
                }
            }
            COMMA -> return right
            else -> throw RuntimeError(binaryExpr.operator, "Not implemented")
        }
    }

    override fun visitUnaryExpr(unaryExpr: UnaryExpr): Any? {
        val right = evaluate(unaryExpr.right)

        return when (unaryExpr.operator.type) {
            BANG -> !isTruthy(right)
            MINUS -> {
                checkNumberOperand(unaryExpr.operator, right)
                -(right as Double)
            }
            else -> null
        }
    }

    override fun visitCallExpr(callExpr: CallExpr): Any? {
        val callee = evaluate(callExpr.callee)

        val arguments = callExpr.arguments.map { evaluate(it) }

        if (callee !is LoxCallable) {
            throw RuntimeError(callExpr.paren, "Can only call functions and classes.")
        }

        if (callee.arity() != arguments.size) {
            throw RuntimeError(callExpr.paren, "Expected ${callee.arity()} arguments but got ${arguments.size}.")
        }

        return callee.call(this, arguments)
    }

    override fun visitGetExpr(getExpr: GetExpr): Any? = when (val obj = evaluate(getExpr.obj)) {
        is LoxInstance, is LoxClass -> {
            val value = if (obj is LoxInstance) {
                obj.get(getExpr.name)
            } else if (obj is LoxClass) {
                val method = obj.findMethod(getExpr.name.lexeme)
                if (method != null && !method.declaration.flags.contains(STATIC)) {
                    throw RuntimeError(getExpr.name, "'${method.name}' is not a static class method.")
                } else method ?: throw RuntimeError(getExpr.name, "Method '${getExpr.name.lexeme}' not found.")
            } else null

            if (value is LoxFunction && value.declaration.flags.contains(GETTER)) value.call(this) else value
        }
        else -> throw RuntimeError(getExpr.name, "Only instances have properties.")
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
            LoxFunction(classStmt = null, functionStmt.name.lexeme, functionStmt.functionExpr, environment)
        )
    }

    override fun visitFunctionExpr(functionExpr: FunctionExpr): LoxFunction {
        return LoxFunction(classStmt = null, "anon", functionExpr, environment)
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

    private fun checkNumberOperand(operator: Token, operand: Any?) {
        if (operand !is Double) {
            throw RuntimeError(operator, "Operand must be a number.")
        }
    }

    private fun checkNumberOperands(operator: Token, left: Any?, right: Any?) {
        if (left == null && right == null) return

        if (!(left is Double && right is Double)) {
            throw RuntimeError(operator, "Operands must be numbers.")
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

    override fun visitClassStmt(classStmt: ClassStmt) {
        val superClass = if (classStmt.superClass != null) {
            evaluate(classStmt.superClass)
        } else null

        if (classStmt.superClass != null && superClass !is LoxClass) {
            throw RuntimeError(classStmt.superClass.name, "Superclass must be a class.")
        }

        environment.define(classStmt.name.lexeme)

        if (classStmt.superClass != null) {
            environment = Environment(environment)
            environment.define("super", superClass)
        }

        val methods = mutableMapOf<String, LoxFunction>()
        classStmt.methods.forEach {
            methods[it.name.lexeme] = LoxFunction(classStmt, it.name.lexeme, it.functionExpr, environment)
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
            is Double -> {
                val text = value.toString()
                if (text.endsWith(".0")) text.substring(0, text.length - 2) else text
            }
            is LoxInstance -> {
                try {
                    val toString = value.get(Token(IDENTIFIER, "toString"))
                    if (toString is LoxFunction) toString.call(interpreter, emptyList()) as String
                    else value.toString()
                } catch (e: Exception) {
                    value.toString()
                }
            }
            else -> value.toString()
        }
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
