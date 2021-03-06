package eu.jameshamilton.klox.compile

import eu.jameshamilton.klox.compile.Compiler.Companion.KLOX_CALLABLE
import eu.jameshamilton.klox.compile.Compiler.Companion.KLOX_CAPTURED_VAR
import eu.jameshamilton.klox.compile.Compiler.Companion.KLOX_CLASS
import eu.jameshamilton.klox.compile.Compiler.Companion.KLOX_EXCEPTION
import eu.jameshamilton.klox.compile.Compiler.Companion.KLOX_FUNCTION
import eu.jameshamilton.klox.compile.Compiler.Companion.KLOX_INSTANCE
import eu.jameshamilton.klox.compile.Compiler.Companion.KLOX_MAIN_CLASS
import eu.jameshamilton.klox.compile.Resolver.Companion.isCaptured
import eu.jameshamilton.klox.compile.Resolver.Companion.isGlobal
import eu.jameshamilton.klox.compile.Resolver.Companion.isLateInit
import eu.jameshamilton.klox.compile.Resolver.Companion.javaName
import eu.jameshamilton.klox.compile.Resolver.Companion.slot
import eu.jameshamilton.klox.compile.composer.helper
import eu.jameshamilton.klox.compile.composer.instanceof_
import eu.jameshamilton.klox.compile.composer.labels
import eu.jameshamilton.klox.compile.composer.packarray
import eu.jameshamilton.klox.compile.composer.unbox
import eu.jameshamilton.klox.parse.ClassStmt
import eu.jameshamilton.klox.parse.FunctionExpr
import eu.jameshamilton.klox.parse.ModifierFlag
import eu.jameshamilton.klox.parse.Token
import eu.jameshamilton.klox.parse.VarDef
import proguard.classfile.editor.CompactCodeAttributeComposer as Composer

// Custom Klox instructions

fun Composer.kloxthrow(message: String): Composer = kloxthrow(null) { ldc(message) }

fun Composer.kloxthrow(message: Composer.() -> Composer): Composer = kloxthrow(null, message)

fun Composer.kloxthrow(token: Token?, message: String): Composer = kloxthrow(token) { ldc(message) }

fun Composer.kloxthrow(token: Token?, message: Composer.() -> Composer): Composer {
    message()
    new_(KLOX_EXCEPTION)
    dup_x1()
    swap()
    if (token != null) {
        iconst(token.line)
        invokespecial(KLOX_EXCEPTION, "<init>", "(Ljava/lang/String;I)V")
    } else {
        invokespecial(KLOX_EXCEPTION, "<init>", "(Ljava/lang/String;)V")
    }
    athrow()
    return this
}

/**
 * Checks the type of the object at the top of the stack and throws an exception,
 * with the specified message, if it doesn't match.
 *
 * Takes into account that klox represents all numbers as java/lang/Double;
 * checking for java/lang/Integer will check that the double is an actual integer.
 */
fun Composer.checktype(expectedType: String, errorMessage: String): Composer =
    checktype(null, expectedType) { ldc(errorMessage) }

fun Composer.checktype(token: Token?, expectedType: String, errorMessage: String): Composer =
    checktype(token, expectedType) { ldc(errorMessage) }

/**
 * Checks the type of the object at the top of the stack and throws an exception,
 * with the specified message built by the composer, if it doesn't match.
 *
 * Takes into account that klox represents all numbers as java/lang/Double;
 * checking for java/lang/Integer will check that the double is an actual integer.
 */
fun Composer.checktype(token: Token?, expectedType: String, errorMessageComposer: Composer.() -> Composer): Composer {
    val (notInstance, end) = labels(2)
    dup()
    if (expectedType == "java/lang/Integer") {
        // All numbers are represented as Double,
        // so check that the number is an integer.
        instanceof_("java/lang/Double")
        ifeq(notInstance)
        dup()
        unbox("java/lang/Double")
        dconst_1()
        drem()
        dconst_0()
        dcmpg()
        ifeq(end)
    } else {
        instanceof_(expectedType)
        ifne(end)
    }

    label(notInstance)
    pop()
    kloxthrow(token, errorMessageComposer)

    label(end)
    checkcast(if (expectedType == "java/lang/Integer") "java/lang/Double" else expectedType)
    return this
}

fun Composer.checknonnull(message: String): Composer = checknonnull(null, message)

fun Composer.checknonnull(token: Token?, message: String): Composer = checknonnull(token) { ldc(message) }

fun Composer.checknonnull(token: Token?, message: Composer.() -> Composer): Composer {
    val (notNull) = labels(1)
    dup()
    ifnonnull(notNull)
    pop()
    kloxthrow(token, message)

    label(notNull)
    return this
}

/**
 * Klox truthiness instruction.
 */
fun Composer.iftruthy(label: Composer.Label): Composer {
    val (end) = labels(1)
    ifnontruthy(end)
    goto_(label)
    label(end)
    return this
}

/** klox non-truthiness instruction.
 */
fun Composer.ifnontruthy(jumpTo: Composer.Label): Composer {
    val (label0, label1, endLabel) = labels(3)
    dup()
    instanceof_("java/lang/Boolean")
    ifeq(label0)
    unbox("java/lang/Boolean")
    goto_(endLabel)

    label(label0)
    ifnonnull(label1)
    iconst_0()
    goto_(endLabel)

    label(label1)
    iconst_1()

    label(endLabel)
    ifeq(jumpTo)
    return this
}

/**
 * Klox stringification instruction.
 *     null -> "nil"
 *     1.0 -> "1"
 *     1.1 -> "1.1"
 *     "string" -> "string"
 *     object -> object.toString()
 */
fun Composer.stringify(): Composer = helper("Main", "stringify", stackInputSize = 1, stackResultSize = 1) {
    val (isNull, nonString, returnBigDecimal, nonNumeric) = labels(4)
    val (maybePositiveInfinity, maybeNegativeInfinity, maybeNaN) = labels(3)

    aload_0()
    dup()
    ifnull(isNull)

    instanceof_("java/lang/String")
    ifeq(nonString)
    aload_0()
    checkcast("java/lang/String")
    areturn()

    label(nonString)
    aload_0()
    dup()
    instanceof_("java/lang/Double")
    ifeq(nonNumeric)
    unbox("java/lang/Double")
    dup2() // unboxed, so dealing with category 2 value now, so dup2
    dup2()
    ldc2_w(0.0)
    dcmpg()
    ifne(maybePositiveInfinity)
    // value is zero but is it negative zero?
    dup2()
    invokestatic("java/lang/Double", "doubleToRawLongBits", "(D)J")
    getstatic("java/lang/Long", "MIN_VALUE", "J")
    lcmp()
    ifne(maybePositiveInfinity)
    pop2()
    ldc("-0")
    areturn()

    label(maybePositiveInfinity)
    dup2()
    getstatic("java/lang/Double", "POSITIVE_INFINITY", "D")
    dcmpg()
    ifne(maybeNegativeInfinity)
    pop2()
    ldc("+Infinity")
    areturn()

    label(maybeNegativeInfinity)
    dup2()
    getstatic("java/lang/Double", "NEGATIVE_INFINITY", "D")
    dcmpg()
    ifne(maybeNaN)
    pop2()
    ldc("-Infinity")
    areturn()

    label(maybeNaN)
    dup2()
    invokestatic("java/lang/Double", "isNaN", "(D)Z")
    ifeq(returnBigDecimal)
    pop2()
    ldc("NaN")
    areturn()

    label(returnBigDecimal)
    invokestatic("java/math/BigDecimal", "valueOf", "(D)Ljava/math/BigDecimal;")
    invokevirtual("java/math/BigDecimal", "stripTrailingZeros", "()Ljava/math/BigDecimal;")
    invokevirtual("java/math/BigDecimal", "toPlainString", "()Ljava/lang/String;")
    areturn()

    label(nonNumeric)
    invokevirtual("java/lang/Object", "toString", "()Ljava/lang/String;")
    dup()
    ifnull(isNull)
    areturn()

    label(isNull)
    pop()
    ldc("nil")
    areturn()
}

/**
 * Declare a Klox local variable - takes into account if the variable is global, late init, captured or not.
 *
 * The initial value should be on the stack.
 *
 * Globals are stored as static fields in the main class.
 * Captured variables are stored as non-static fields in the capturing class.
 * Late init variables are captured variables that are used before they are declared e.g. using a global from a function before it's declared.
 *    -> A CapturedVar with a null value is put in the field in the function's constructor.
 */
fun Composer.declare(func: FunctionExpr, varDef: VarDef): Composer = when {
    varDef.isGlobal -> when {
        // Declaring a global variable so access directly the static field.
        varDef.isCaptured && varDef.isLateInit -> {
            getstatic(KLOX_MAIN_CLASS, varDef.javaName, "L$KLOX_CAPTURED_VAR;")
            swap()
            invokevirtual(KLOX_CAPTURED_VAR, "setValue", "(Ljava/lang/Object;)V")
        }
        varDef.isCaptured -> box(varDef).putstatic(KLOX_MAIN_CLASS, varDef.javaName, "L$KLOX_CAPTURED_VAR;")
        else -> putstatic(KLOX_MAIN_CLASS, varDef.javaName, "Ljava/lang/Object;")
    }
    varDef.isCaptured && varDef.isLateInit -> {
        aload_0()
        getfield(targetClass.name, varDef.javaName, "L$KLOX_CAPTURED_VAR;")
        dup_x1()
        swap()
        invokevirtual(KLOX_CAPTURED_VAR, "setValue", "(Ljava/lang/Object;)V")
        astore(func.slot(varDef))
    }
    varDef.isCaptured -> {
        box(varDef)
        dup()
        aload_0()
        swap()
        putfield(targetClass.name, varDef.javaName, "L$KLOX_CAPTURED_VAR;")
        astore(func.slot(varDef))
    }
    // normal locals
    else -> astore(func.slot(varDef))
}

/**
 * Creates a new klox class instance. The ClassStmt variable will be loaded
 * from the given function.
 *
 * The constructor parameters should be placed on the stack before calling this method.
 * After this method is called, the stack will contain the new instance.
 */
fun Composer.new_(function: FunctionExpr, klass: ClassStmt): Composer {
    val init = klass.methods.singleOrNull { it.modifiers.contains(ModifierFlag.INITIALIZER) }
    packarray(init?.functionExpr?.params?.size ?: 0)
    load(function, klass)
    checkcast(KLOX_CLASS)
    swap()
    invokeinterface(KLOX_CALLABLE, "invoke", "([Ljava/lang/Object;)Ljava/lang/Object;")
    checkcast(KLOX_INSTANCE)
    return this
}

/**
 * Box a Klox captured variable in a CapturedVar container.
 */
fun Composer.box(varDef: VarDef): Composer {
    if (!varDef.isCaptured) throw RuntimeException("Cannot box a non-captured variable.")

    new_(KLOX_CAPTURED_VAR)
    dup_x1()
    swap()
    invokespecial(KLOX_CAPTURED_VAR, "<init>", "(Ljava/lang/Object;)V")
    return this
}

/**
 * Unbox a captured Klox variable.
 */
fun Composer.unbox(varDef: VarDef): Composer {
    if (!varDef.isCaptured) throw RuntimeException("Cannot unbox a non-captured variable.")

    checkcast(KLOX_CAPTURED_VAR)
    invokevirtual(KLOX_CAPTURED_VAR, "getValue", "()Ljava/lang/Object;")
    return this
}

/**
 * Load a klox variable.
 *
 * Takes into account if it's captured or not.
 */
fun Composer.load(function: FunctionExpr, varDef: VarDef): Composer = when {
    varDef.isGlobal -> global(varDef)
    else -> aload(function.slot(varDef)).also {
        if (varDef.isCaptured) unbox(varDef)
    }
}

fun Composer.global(varDef: VarDef): Composer = when {
    varDef.isGlobal -> if (varDef.isCaptured) {
        if (targetClass.isMain) {
            getstatic(KLOX_MAIN_CLASS, varDef.javaName, "L$KLOX_CAPTURED_VAR;")
        } else {
            aload_0()
            getfield(targetClass.name, varDef.javaName, "L$KLOX_CAPTURED_VAR;")
        }
        unbox(varDef)
    } else getstatic(KLOX_MAIN_CLASS, varDef.javaName, "Ljava/lang/Object;")
    else -> throw RuntimeException("Cannot load a non-global variable.")
}

/**
 * Store a klox variable.
 *
 * Takes into account if it's captured or not.
 */
fun Composer.store(function: FunctionExpr, varDef: VarDef): Composer = when {
    varDef.isGlobal -> {
        if (varDef.isCaptured) {
            if (targetClass.isMain) {
                getstatic(KLOX_MAIN_CLASS, varDef.javaName, "L$KLOX_CAPTURED_VAR;")
            } else {
                aload_0()
                getfield(targetClass.name, varDef.javaName, "L$KLOX_CAPTURED_VAR;")
            }
            swap()
            invokevirtual(KLOX_CAPTURED_VAR, "setValue", "(Ljava/lang/Object;)V")
        } else putstatic(KLOX_MAIN_CLASS, varDef.javaName, "Ljava/lang/Object;")
    }
    varDef.isCaptured -> {
        aload(function.slot(varDef))
        swap()
        invokevirtual(KLOX_CAPTURED_VAR, "setValue", "(Ljava/lang/Object;)V")
    }
    else -> astore(function.slot(varDef))
}

/**
 * Assuming a KloxFunction is on the stack, load it's related instance.
 */
fun Composer.loadkloxinstance(): Composer {
    aload_0()
    invokeinterface(KLOX_FUNCTION, "getReceiver", "()L$KLOX_INSTANCE;")
    return this
}

/**
 * Assuming a Klox instance is on the stack, get a field from it.
 */
fun Composer.getkloxfield(name: String, expectedType: String, safeAccess: Boolean = false): Composer {
    ldc(name)
    if (safeAccess) {
        iconst_1()
        invokevirtual(KLOX_INSTANCE, "get", "(Ljava/lang/String;Z)Ljava/lang/Object;")
    } else invokevirtual(KLOX_INSTANCE, "get", "(Ljava/lang/String;)Ljava/lang/Object;")
    checkcast(expectedType)
    return this
}

/**
 * Assuming a Klox instance is on the stack, get a field from it.
 *
 * A new value can be provided by using the newValueComposer - this composer
 * should leave a value on the stack.
 */
fun Composer.getkloxfield(name: String, expectedType: String, newValueComposer: Composer.() -> Composer): Composer {
    val (hasField, end) = labels(2)

    dup()
    haskloxfield(name)
    ifne(hasField)
    newValueComposer(this)
    dup_x1()
    setkloxfield(name)
    goto_(end)

    label(hasField)
    getkloxfield(name, expectedType)

    label(end)
    return this
}

fun Composer.kloxfindmethod(name: String): Composer {
    ldc(name)
    invokeinterface(KLOX_CLASS, "findMethod", "(Ljava/lang/String;)L$KLOX_FUNCTION;")
    return this
}

fun Composer.kloxinvoke(numberOfParams: Int = 0): Composer {
    packarray(numberOfParams)
    invokeinterface(KLOX_CALLABLE, "invoke", "([Ljava/lang/Object;)Ljava/lang/Object;")
    return this
}

fun Composer.setkloxfield(name: String): Composer {
    ldc(name)
    swap()
    invokevirtual(KLOX_INSTANCE, "set", "(Ljava/lang/String;Ljava/lang/Object;)V")
    return this
}

fun Composer.removekloxfield(name: String): Composer {
    ldc(name)
    invokevirtual(KLOX_INSTANCE, "removeField", "(Ljava/lang/String;)V")
    return this
}

fun Composer.haskloxfield(name: String): Composer {
    ldc(name)
    invokevirtual(KLOX_INSTANCE, "hasField", "(Ljava/lang/String;)Z")
    return this
}

fun Composer.kloxclass(): Composer {
    invokevirtual(KLOX_INSTANCE, "getKlass", "()L$KLOX_CLASS;")
    return this
}

fun Composer.kloxclassname(): Composer {
    invokeinterface(KLOX_CLASS, "getName", "()Ljava/lang/String;")
    return this
}
