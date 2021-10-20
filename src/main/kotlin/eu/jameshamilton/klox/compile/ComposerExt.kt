package eu.jameshamilton.klox.compile

import eu.jameshamilton.klox.compile.Compiler.Companion.KLOX_CAPTURED_VAR
import eu.jameshamilton.klox.compile.Resolver.Companion.isCaptured
import eu.jameshamilton.klox.compile.Resolver.Companion.isGlobalLateInit
import eu.jameshamilton.klox.compile.Resolver.Companion.javaName
import eu.jameshamilton.klox.compile.Resolver.Companion.slot
import eu.jameshamilton.klox.debug
import eu.jameshamilton.klox.parse.FunctionStmt
import eu.jameshamilton.klox.parse.VarDef
import eu.jameshamilton.klox.programClassPool
import proguard.classfile.AccessConstants.PUBLIC
import proguard.classfile.AccessConstants.STATIC
import proguard.classfile.ProgramClass
import proguard.classfile.VersionConstants.CLASS_VERSION_1_8
import proguard.classfile.attribute.CodeAttribute
import proguard.classfile.editor.ClassBuilder
import proguard.classfile.editor.CodeAttributeComposer
import proguard.classfile.editor.CompactCodeAttributeComposer.Label
import proguard.classfile.util.ClassUtil.internalPrimitiveTypeFromNumericClassName
import proguard.classfile.editor.CompactCodeAttributeComposer as Composer

fun Composer.labels(n: Int): List<Label> =
    (1..n).map { this.createLabel() }

fun Composer.TRUE(): Composer =
    getstatic("java/lang/Boolean", "TRUE", "Ljava/lang/Boolean;")

fun Composer.FALSE(): Composer =
    getstatic("java/lang/Boolean", "FALSE", "Ljava/lang/Boolean;")

fun Composer.box(type: String): Composer {
    boxPrimitiveType(internalPrimitiveTypeFromNumericClassName(type))
    return this
}

fun Composer.unbox(type: String): Composer {
    unboxPrimitiveType("Ljava/lang/Object;", internalPrimitiveTypeFromNumericClassName(type).toString())
    return this
}

fun Composer.instanceof_(type: String): Composer =
    instanceof_(type, null)

fun Composer.anewarray(type: String): Composer =
    anewarray(type, null)

fun Composer.boxed(type: String, composer: Composer.() -> Composer): Composer {
    unbox(type)
    composer(this)
    box(type)
    return this
}

fun Composer.invokedynamic(bootStrapMethodIndex: Int, name: String, descriptor: String): Composer =
    invokedynamic(bootStrapMethodIndex, name, descriptor, null)

/**
 * Concatenate strings produced by each composer. Each composer should
 * leave one object on the stack, toString will be called on the object.
 * Primitives are not supported.
 */
fun Composer.concat(vararg composers: Composer.() -> Composer): Composer {
    // TODO invokedynamic
    new_("java/lang/StringBuilder")
    dup()
    invokespecial("java/lang/StringBuilder", "<init>", "()V")
    composers.forEach { composer ->
        composer(this@concat)
        invokevirtual("java/lang/Object", "toString", "()Ljava/lang/String;")
        invokevirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;")
    }
    invokevirtual("java/lang/StringBuilder", "toString", "()Ljava/lang/String;")
    return this
}

/**
 * Writes the wrapped instructions in a static util method.
 * The method will be created, if it doesn't already exist (uses the global programClassPool).
 * The stack should contain `stackInputSize` params which are
 * available as the local variables of the static util method.
 * The return value(s) will be placed on the stack after the helper
 * has executed.
 *
 * Example:
 *     * code is generated in method `Util.swap(Object, Object)[LObject`.
 *     * at the location of the original `composer` an invokestatic is generated.
 *
 * <code>
 *     // Stack before: Object A, Object B
 *     with (composer) {
 *         helper("Util", "swap", 2, 2) {
 *              aload_0()
 *              aload_1()
 *              swap()
 *         }
 *     }
 *     // Stack after: Object B, Object A
 * </code>
 */
fun Composer.helper(
    className: String,
    name: String,
    stackInputSize: Int,
    stackResultSize: Int = 1,
    composer: Composer.(Composer) -> Composer
): Composer {
    val utilClass = programClassPool.getClass(className) ?: ClassBuilder(
        CLASS_VERSION_1_8,
        PUBLIC,
        className,
        "java/lang/Object"
    ).programClass.apply { programClassPool.addClass(this) } as ProgramClass

    val returnType = when (stackResultSize) {
        0 -> "V"
        1 -> "Ljava/lang/Object;"
        else -> "[Ljava/lang/Object;"
    }

    val descriptor = """(${"Ljava/lang/Object;".repeat(stackInputSize)})$returnType"""
    invokestatic(
        utilClass,
        utilClass.findMethod(name, descriptor) ?: ClassBuilder(utilClass as ProgramClass)
            .addAndReturnMethod(PUBLIC or STATIC, name, descriptor) {
                composer(this)
                when (stackResultSize) {
                    0 -> this
                    1 -> areturn()
                    else -> packarray(stackResultSize).areturn()
                }
            }
    )

    if (stackResultSize > 1) unpackarray(stackResultSize)

    return this
}

fun Composer.packarray(n: Int, type: String = "java/lang/Object"): Composer {
    iconst(n)
    anewarray(type)
    for (i in 0 until n) {
        dup_x1()
        swap()
        iconst(i)
        swap()
        aastore()
    }
    return this
}

fun Composer.unpackarray(n: Int, action: (Composer.(i: Int) -> Composer)? = null): Composer {
    for (i in 0 until n) {
        if (i != n - 1) dup()
        iconst(i)
        aaload()
        if (action == null) {
            if (i != n - 1) swap()
        } else {
            action.invoke(this, i)
        }
    }
    return this
}

fun Composer.println(composer: Composer.() -> Composer): Composer {
    getstatic("java/lang/System", "out", "Ljava/io/PrintStream;")
    composer(this)
    invokevirtual("java/io/PrintStream", "println", "(Ljava/lang/Object;)V")
    return this
}

fun Composer.try_(composer: Composer.() -> Composer): Pair<Label, Label> {
    val (tryStart, tryEnd) = labels(2)
    label(tryStart)
    composer(this)
    label(tryEnd)
    return Pair(tryStart, tryEnd)
}

fun Composer.catch_(start: Label, end: Label, type: String, composer: Composer.() -> Composer): Composer {
    val (skip, handler) = labels(2)
    catch_(start, end, handler, type, null)
    goto_(skip)
    label(handler)
    composer(this)
    label(skip)
    return this
}

fun Composer.catchAll(start: Label, end: Label, composer: Composer.() -> Composer): Composer {
    val (skip, handler) = labels(2)
    goto_(skip)
    catchAll(start, end, handler)
    label(handler)
    composer(this)
    label(skip)
    return this
}

fun Composer.throw_(type: String, message: String): Composer = throw_(type) { ldc(message) }

fun Composer.throw_(type: String, message: Composer.() -> Composer): Composer {
    new_(type)
    dup()
    message()
    invokespecial(type, "<init>", "(Ljava/lang/String;)V")
    athrow()
    return this
}

// Custom Klox instructions

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
    val (nonNull, nonString, returnNumeric, nonNumeric) = labels(4)
    aload_0()
    ifnonnull(nonNull)
    ldc("nil")
    areturn()

    label(nonNull)
    aload_0()
    instanceof_("java/lang/String")
    ifeq(nonString)
    aload_0()
    checkcast("java/lang/String")
    areturn()

    label(nonString)
    aload_0()
    instanceof_("java/lang/Number")
    ifeq(nonNumeric)
    aload_0()
    invokevirtual("java/lang/Object", "toString", "()Ljava/lang/String;")
    astore_1()
    aload_1()
    ldc(".0")
    invokevirtual("java/lang/String", "endsWith", "(Ljava/lang/String;)Z")
    ifeq(returnNumeric)
    aload_1()
    iconst_0()
    aload_1()
    invokevirtual("java/lang/String", "length", "()I")
    iconst_2()
    isub()
    invokevirtual("java/lang/String", "substring", "(II)Ljava/lang/String;")
    astore_1()

    label(returnNumeric)
    aload_1()
    areturn()

    label(nonNumeric)
    aload_0()
    invokevirtual("java/lang/Object", "toString", "()Ljava/lang/String;")
    areturn()
}

/**
 * Declare a Klox local variable - takes into account if the variable is captured or not.
 */
fun Composer.declare(functionStmt: FunctionStmt, varDef: VarDef): Composer {
    if (varDef.isCaptured) when {
        varDef.isGlobalLateInit -> {
            // Variable was not yet initialized, so set the initial value.
            aload_0()
            ldc(varDef.javaName)
            invokevirtual(targetClass.name, "getCaptured", "(Ljava/lang/String;)L$KLOX_CAPTURED_VAR;")
            dup_x1()
            swap()
            invokevirtual(KLOX_CAPTURED_VAR, "setValue", "(Ljava/lang/Object;)V")
        }
        else -> {
            box(varDef)
            dup()
            aload_0()
            swap()
            ldc(varDef.javaName)
            swap()
            invokevirtual(targetClass.name, "capture", "(Ljava/lang/String;L$KLOX_CAPTURED_VAR;)V")
        }
    }

    astore(functionStmt.slot(varDef))

    return this
}

/**
 * Box a Klox captured variable in a CapturedVar container.
 */
fun Composer.box(@Suppress("UNUSED_PARAMETER") varDef: VarDef): Composer {
    new_(KLOX_CAPTURED_VAR)
    dup_x1()
    swap()
    invokespecial(KLOX_CAPTURED_VAR, "<init>", "(Ljava/lang/Object;)V")
    return this
}

/**
 * Unbox a captured Klox variable.
 */
fun Composer.unbox(@Suppress("UNUSED_PARAMETER") varDef: VarDef): Composer {
    checkcast(KLOX_CAPTURED_VAR)
    invokevirtual(KLOX_CAPTURED_VAR, "getValue", "()Ljava/lang/Object;")
    return this
}

// Useful for Debugging

fun Composer.printlnerr(composer: Composer.() -> Composer): Composer {
    getstatic("java/lang/System", "err", "Ljava/io/PrintStream;")
    composer(this)
    invokevirtual("java/io/PrintStream", "println", "(Ljava/lang/Object;)V")
    return this
}

fun Composer.printlndebug(str: String): Composer = if (debug == true) {
    println { ldc(str) }
} else this

fun Composer.printlnpeek(prefix: String? = null): Composer {
    if (debug != true) return this

    dup()
    getstatic("java/lang/System", "out", "Ljava/io/PrintStream;")
    dup_x1()
    swap()
    if (prefix != null) {
        getstatic("java/lang/System", "out", "Ljava/io/PrintStream;")
        ldc(prefix)
        invokevirtual("java/io/PrintStream", "print", "(Ljava/lang/Object;)V")
    }
    invokevirtual("java/io/PrintStream", "print", "(Ljava/lang/Object;)V")
    ldc(" (stack top at offset ${this.codeLength})")
    invokevirtual("java/io/PrintStream", "println", "(Ljava/lang/Object;)V")
    return this
}

val Composer.codeAttribute: CodeAttribute
    get() {
        return javaClass.getDeclaredField("codeAttributeComposer").let { field ->
            field.isAccessible = true
            val codeAttributeComposer = field.get(this) as CodeAttributeComposer
            val code = codeAttributeComposer.javaClass.getDeclaredField("code").let {
                it.isAccessible = true
                it.get(codeAttributeComposer) as ByteArray
            }
            val codeLength = codeAttributeComposer.javaClass.getDeclaredField("codeLength").let {
                it.isAccessible = true
                it.getInt(codeAttributeComposer)
            }
            CodeAttribute(0, 0, 0, codeLength, code, 0, null, 0, null)
        }
    }
