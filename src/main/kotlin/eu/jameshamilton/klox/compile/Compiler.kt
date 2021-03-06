package eu.jameshamilton.klox.compile

import eu.jameshamilton.klox.compile.Compiler.Companion.KLOX_MAIN_CLASS
import eu.jameshamilton.klox.compile.Resolver.Companion.captured
import eu.jameshamilton.klox.compile.Resolver.Companion.definedIn
import eu.jameshamilton.klox.compile.Resolver.Companion.depth
import eu.jameshamilton.klox.compile.Resolver.Companion.free
import eu.jameshamilton.klox.compile.Resolver.Companion.isCaptured
import eu.jameshamilton.klox.compile.Resolver.Companion.isDefined
import eu.jameshamilton.klox.compile.Resolver.Companion.isGlobal
import eu.jameshamilton.klox.compile.Resolver.Companion.isLateInit
import eu.jameshamilton.klox.compile.Resolver.Companion.javaClassName
import eu.jameshamilton.klox.compile.Resolver.Companion.javaName
import eu.jameshamilton.klox.compile.Resolver.Companion.slot
import eu.jameshamilton.klox.compile.Resolver.Companion.temp
import eu.jameshamilton.klox.compile.Resolver.Companion.varDef
import eu.jameshamilton.klox.compile.Resolver.Companion.variables
import eu.jameshamilton.klox.compile.composer.FALSE
import eu.jameshamilton.klox.compile.composer.TRUE
import eu.jameshamilton.klox.compile.composer.anewarray
import eu.jameshamilton.klox.compile.composer.box
import eu.jameshamilton.klox.compile.composer.boxed
import eu.jameshamilton.klox.compile.composer.break_
import eu.jameshamilton.klox.compile.composer.case
import eu.jameshamilton.klox.compile.composer.catchAll
import eu.jameshamilton.klox.compile.composer.catch_
import eu.jameshamilton.klox.compile.composer.codeAttribute
import eu.jameshamilton.klox.compile.composer.concat
import eu.jameshamilton.klox.compile.composer.continue_
import eu.jameshamilton.klox.compile.composer.helper
import eu.jameshamilton.klox.compile.composer.instanceof_
import eu.jameshamilton.klox.compile.composer.invokedynamic
import eu.jameshamilton.klox.compile.composer.labels
import eu.jameshamilton.klox.compile.composer.loop
import eu.jameshamilton.klox.compile.composer.println
import eu.jameshamilton.klox.compile.composer.switch
import eu.jameshamilton.klox.compile.composer.try_
import eu.jameshamilton.klox.compile.composer.unbox
import eu.jameshamilton.klox.compile.composer.unpackarray
import eu.jameshamilton.klox.debug
import eu.jameshamilton.klox.hadError
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
import eu.jameshamilton.klox.parse.ModifierFlag
import eu.jameshamilton.klox.parse.ModifierFlag.GETTER
import eu.jameshamilton.klox.parse.ModifierFlag.INITIALIZER
import eu.jameshamilton.klox.parse.ModifierFlag.NATIVE
import eu.jameshamilton.klox.parse.MultiVarStmt
import eu.jameshamilton.klox.parse.PrintStmt
import eu.jameshamilton.klox.parse.Program
import eu.jameshamilton.klox.parse.ReturnStmt
import eu.jameshamilton.klox.parse.SetExpr
import eu.jameshamilton.klox.parse.Stmt
import eu.jameshamilton.klox.parse.SuperExpr
import eu.jameshamilton.klox.parse.ThisExpr
import eu.jameshamilton.klox.parse.Token
import eu.jameshamilton.klox.parse.TokenType.*
import eu.jameshamilton.klox.parse.UnaryExpr
import eu.jameshamilton.klox.parse.VarDef
import eu.jameshamilton.klox.parse.VarStmt
import eu.jameshamilton.klox.parse.VariableExpr
import eu.jameshamilton.klox.parse.WhileStmt
import eu.jameshamilton.klox.programClassPool
import proguard.classfile.AccessConstants.ABSTRACT
import proguard.classfile.AccessConstants.FINAL
import proguard.classfile.AccessConstants.INTERFACE
import proguard.classfile.AccessConstants.PRIVATE
import proguard.classfile.AccessConstants.PUBLIC
import proguard.classfile.AccessConstants.STATIC
import proguard.classfile.AccessConstants.VARARGS
import proguard.classfile.ClassPool
import proguard.classfile.Clazz
import proguard.classfile.LibraryClass
import proguard.classfile.ProgramClass
import proguard.classfile.ProgramMethod
import proguard.classfile.VersionConstants.CLASS_VERSION_1_8
import proguard.classfile.attribute.visitor.AllAttributeVisitor
import proguard.classfile.constant.MethodHandleConstant.REF_INVOKE_STATIC
import proguard.classfile.editor.ClassBuilder
import proguard.classfile.editor.CompactCodeAttributeComposer.Label
import proguard.classfile.util.ClassSuperHierarchyInitializer
import proguard.classfile.visitor.AllMethodVisitor
import proguard.classfile.visitor.ClassPrinter
import proguard.classfile.visitor.ClassVersionFilter
import proguard.preverify.CodePreverifier
import java.util.EnumSet
import proguard.classfile.editor.CompactCodeAttributeComposer as Composer

class Compiler : Program.Visitor<ClassPool> {

    lateinit var mainFunction: FunctionStmt
    lateinit var arrayClass: ClassStmt
    lateinit var resultClass: ClassStmt
    lateinit var errorClass: ClassStmt
    lateinit var okClass: ClassStmt
    lateinit var stringClass: ClassStmt
    lateinit var booleanClass: ClassStmt
    lateinit var numberClass: ClassStmt
    lateinit var characterClass: ClassStmt

    fun compile(program: Program): ClassPool {
        initialize(programClassPool)
        return program.accept(this)
    }

    override fun visitProgram(program: Program): ClassPool {
        mainFunction = FunctionStmt(
            Token(FUN, KLOX_MAIN_CLASS),
            modifiers = ModifierFlag.empty(),
            FunctionExpr(params = emptyList(), body = program.stmts)
        )

        Resolver().execute(mainFunction)

        val globalClasses = mainFunction.functionExpr.variables.filterIsInstance<ClassStmt>()
        arrayClass = globalClasses.single { it.name.lexeme == "Array" }
        resultClass = globalClasses.single { it.name.lexeme == "Result" }
        errorClass = globalClasses.single { it.name.lexeme == "Error" }
        okClass = globalClasses.single { it.name.lexeme == "Ok" }
        stringClass = globalClasses.single { it.name.lexeme == "String" }
        booleanClass = globalClasses.single { it.name.lexeme == "Boolean" }
        numberClass = globalClasses.single { it.name.lexeme == "Number" }
        characterClass = globalClasses.single { it.name.lexeme == "Character" }

        if (hadError) return ClassPool()

        val (mainClass, _) = FunctionCompiler().compile(KLOX_MAIN_CLASS, mainFunction.modifiers, mainFunction.name.lexeme, mainFunction.functionExpr)

        ClassBuilder(mainClass)
            .addField(PUBLIC or STATIC, "args", "[Ljava/lang/String;")
            .addMethod(PUBLIC or STATIC, KLOX_MAIN_FUNCTION, "([Ljava/lang/String;)V") {
                aload_0()
                putstatic(targetClass.name, "args", "[Ljava/lang/String;")

                val (tryStart, tryEnd) = try_ {
                    new_(targetClass.name)
                    dup()
                    aconst_null()
                    invokespecial(targetClass.name, "<init>", "(L$KLOX_CALLABLE;)V")
                    aconst_null()
                    invokeinterface(KLOX_CALLABLE, "invoke", "([Ljava/lang/Object;)Ljava/lang/Object;")
                    pop()
                    return_()
                }
                catch_(tryStart, tryEnd, "java/lang/StackOverflowError") {
                    pop()
                    getstatic("java/lang/System", "err", "Ljava/io/PrintStream;")
                    ldc("Stack overflow.")
                    invokevirtual("java/io/PrintStream", "println", "(Ljava/lang/Object;)V")
                    return_()
                }
                catchAll(tryStart, tryEnd) {
                    if (debug == true) dup()
                    getstatic("java/lang/System", "err", "Ljava/io/PrintStream;")
                    swap()
                    invokevirtual("java/lang/Throwable", "getMessage", "()Ljava/lang/String;")
                    invokevirtual("java/io/PrintStream", "println", "(Ljava/lang/Object;)V")
                    if (debug == true) invokevirtual("java/lang/Throwable", "printStackTrace", "()V")
                    return_()
                }
            }

        return preverify(programClassPool)
    }

    /**
     * Preverifies the code of the classes in the given class pool,
     * adding StackMapTable attributes to code that requires it.
     * @param programClassPool the classes to be preverified.
     */
    private fun preverify(programClassPool: ClassPool): ClassPool {
        val libraryClassPool = ClassPool().apply {
            // The following classes are required for the preverifier.
            //
            // For example, to complete the class hierarchy in a klox program like:
            // two classes will be generated and the preverifier will need to know their common superclass.
            // var f;
            // if (true) f = fun() { return 1; } else; f = fun() { return 2; };
            // f();
            //
            addClass(LibraryClass(PUBLIC, "java/lang/Object", null))
        }
        programClassPool.classesAccept(
            ClassSuperHierarchyInitializer(programClassPool, libraryClassPool)
        )
        programClassPool.classesAccept { clazz ->
            try {
                clazz.accept(
                    ClassVersionFilter(
                        CLASS_VERSION_1_8,
                        AllMethodVisitor(
                            AllAttributeVisitor(
                                CodePreverifier(false)
                            )
                        )
                    )
                )
            } catch (e: Exception) {
                clazz.accept(ClassPrinter())
                throw e
            }
        }

        return programClassPool
    }

    private inner class FunctionCompiler(private val enclosingCompiler: FunctionCompiler? = null) : Stmt.Visitor<Unit>, Expr.Visitor<Unit> {
        private lateinit var composer: Composer
        private lateinit var modifiers: EnumSet<ModifierFlag>
        private lateinit var function: FunctionExpr

        fun compile(className: String?, modifiers: EnumSet<ModifierFlag>, name: String?, function: FunctionExpr): Pair<ProgramClass, ProgramMethod> {
            this.modifiers = modifiers
            this.function = function

            val (clazz, method) = create(modifiers, name, function)
            composer = Composer(clazz)
            with(composer) {
                beginCodeFragment(65_535)

                if (function.params.isNotEmpty()) {
                    aload_1()
                    unpackarray(function.params.size) { i ->
                        declare(function, function.params[i])
                    }
                }

                for (captured in function.captured.filterNot { it.isGlobal }) {
                    // Globals are not assigned slots, they are accessed directly.
                    aload_0()
                    getfield(targetClass.name, captured.javaName, "L$KLOX_CAPTURED_VAR;")
                    astore(function.slot(captured))
                }

                if (modifiers.contains(NATIVE)) {
                    assert(name != null) { "Native functions must have a name." }
                    findNative(this@Compiler, className, name!!, function)(this)
                } else {
                    function.body.forEach {
                        it.accept(this@FunctionCompiler)
                    }

                    if (modifiers.contains(INITIALIZER)) {
                        aload_0()
                        invokeinterface(KLOX_FUNCTION, "getReceiver", "()L$KLOX_INSTANCE;")
                        areturn()
                    } else if (function.body.count { it is ReturnStmt } == 0) {
                        aconst_null()
                        areturn()
                    }
                }
                endCodeFragment()

                try {
                    addCodeAttribute(clazz, method)
                } catch (e: Exception) {
                    codeAttribute.accept(clazz, method, ClassPrinter())
                    throw e
                }

                return Pair(clazz, method)
            }
        }

        /**
         * Compile a function, using the receiver Composer to generate the bytecode.
         */
        private fun Composer.compile(
            currentCompiler: FunctionCompiler,
            function: FunctionExpr,
            className: String? = null,
            modifiers: EnumSet<ModifierFlag> = ModifierFlag.empty(),
            name: String? = null,
            initializer: (proguard.classfile.editor.CompactCodeAttributeComposer.() -> Unit)? = null
        ) {
            val (clazz, _) = FunctionCompiler(enclosingCompiler = currentCompiler)
                .compile(className, modifiers, name, function)

            new_(clazz)
            dup()
            aload_0()
            invokespecial(clazz.name, "<init>", "(L$KLOX_CALLABLE;)V")

            if (function.captured.isNotEmpty()) dup()

            initializer?.invoke(this)

            if (function.captured.isNotEmpty()) {
                for (varDef in function.captured) {
                    dup() // the function object
                    if (varDef.isGlobal) {
                        getstatic(KLOX_MAIN_CLASS, varDef.javaName, "L$KLOX_CAPTURED_VAR;")
                        putfield(function.javaClassName, varDef.javaName, "L$KLOX_CAPTURED_VAR;")
                    } else {
                        aload_0()
                        if (currentCompiler.enclosingCompiler?.function != null) {
                            for (i in currentCompiler.enclosingCompiler.function.depth downTo varDef.definedIn.depth) {
                                invokeinterface(KLOX_CALLABLE, "getEnclosing", "()L$KLOX_CALLABLE;")
                            }
                            checkcast(varDef.definedIn.javaClassName)
                        }
                        getfield(varDef.definedIn.javaClassName, varDef.javaName, "L$KLOX_CAPTURED_VAR;")
                        putfield(function.javaClassName, varDef.javaName, "L$KLOX_CAPTURED_VAR;")
                    }
                }
                pop()
            }
        }

        override fun visitExprStmt(exprStmt: ExprStmt): Unit = with(composer) {
            exprStmt.expression.accept(this@FunctionCompiler)
            // Anything left on the stack by the expression should be discarded because statements don't produce values.
            for (i in 0 until exprStmt.expression.accept(StackSizeComputer())) pop()
        }

        override fun visitPrintStmt(printStmt: PrintStmt): Unit = with(composer) {
            println {
                printStmt.expression.accept(this@FunctionCompiler)
                stringify()
            }
        }

        override fun visitVarStmt(varStmt: VarStmt): Unit = with(composer) {
            if (varStmt.initializer != null) varStmt.initializer!!.accept(this@FunctionCompiler) else aconst_null()

            declare(function, varStmt)
        }

        override fun visitBlockStmt(block: BlockStmt) = block.stmts.forEach { it.accept(this) }

        override fun visitIfStmt(ifStmt: IfStmt): Unit = with(composer) {
            val (elseLabel, endLabel) = labels(2)
            ifStmt.condition.accept(this@FunctionCompiler)
            ifnontruthy(elseLabel)
            ifStmt.thenBranch.accept(this@FunctionCompiler)
            goto_(endLabel)
            label(elseLabel)
            ifStmt.elseBranch?.accept(this@FunctionCompiler)
            label(endLabel)
        }

        override fun visitWhileStmt(whileStmt: WhileStmt): Unit = with(composer) {
            loop { condition, body, end ->
                label(condition)
                whileStmt.condition.accept(this@FunctionCompiler)
                ifnontruthy(end)
                label(body)
                whileStmt.body.accept(this@FunctionCompiler)
                goto_(condition)
            }
        }

        override fun visitDoWhileStmt(whileStmt: DoWhileStmt): Unit = with(composer) {
            loop { condition, body, _ ->
                label(body)
                whileStmt.body.accept(this@FunctionCompiler)
                label(condition)
                whileStmt.condition.accept(this@FunctionCompiler)
                iftruthy(body)
            }
        }

        override fun visitBreakStmt(breakStmt: BreakStmt): Unit = with(composer) {
            break_()
        }

        override fun visitContinueStmt(continueStmt: ContinueStmt): Unit = with(composer) {
            continue_()
        }

        override fun visitMultiVarStmt(multiVarStmt: MultiVarStmt) =
            multiVarStmt.statements.forEach { it.accept(this) }

        override fun visitBinaryExpr(binaryExpr: BinaryExpr) {
            // `plus` assumes that left is already on the stack
            fun Composer.plus() = binaryExpr.right.accept(this@FunctionCompiler).also {
                helper(KLOX_MAIN_CLASS, "add", stackInputSize = 2, stackResultSize = 1) {
                    val (addDoubleString, throwLabel, addNumeric, addStringify) = labels(4)
                    aload_0()
                    instanceof_("java/lang/Double")
                    ifne(addDoubleString)
                    aload_0()
                    instanceof_("java/lang/String")
                    ifne(addDoubleString)
                    aload_0()
                    instanceof_(KLOX_INSTANCE)
                    ifeq(throwLabel)

                    label(addDoubleString)
                    aload_1()
                    instanceof_("java/lang/Double")
                    ifne(addNumeric)
                    aload_1()
                    instanceof_("java/lang/String")
                    ifne(addNumeric)
                    aload_1()
                    instanceof_(KLOX_INSTANCE)
                    ifeq(throwLabel)

                    label(addNumeric)
                    aload_0()
                    instanceof_("java/lang/Double")
                    ifeq(addStringify)
                    aload_1()
                    instanceof_("java/lang/Double")
                    ifeq(addStringify)
                    aload_0()
                    unbox("java/lang/Double")
                    aload_1()
                    unbox("java/lang/Double")
                    dadd()
                    box("java/lang/Double")
                    areturn()

                    label(addStringify)
                    concat(
                        { aload_0().stringify() },
                        { aload_1().stringify() }
                    )
                    areturn()

                    label(throwLabel)
                    kloxthrow("Operands must be two numbers or two strings.")
                }
            }

            fun Composer.tryoverloaded(isOverloadedJumpTo: Label) = binaryExpr.left.accept(this@FunctionCompiler).also {
                if (binaryExpr.isOverloadable && binaryExpr.left !is LiteralExpr) {
                    val (notInstance, methodDoesNotExist) = labels(2)
                    dup() // left
                    // L, L
                    instanceof_(KLOX_INSTANCE)
                    ifeq(notInstance)
                    checkcast(KLOX_INSTANCE)
                    // L
                    dup()
                    // L, L
                    getkloxfield(binaryExpr.overloadMethodName, KLOX_FUNCTION, safeAccess = true)

                    dup()
                    // L, OP, OP
                    ifnull(methodDoesNotExist)

                    binaryExpr.right.accept(this@FunctionCompiler)
                    kloxinvoke(numberOfParams = 1)
                    if (binaryExpr.operator.type == BANG_EQUAL) {
                        boxed("java/lang/Boolean") {
                            iconst_1()
                            ixor()
                        }
                    }
                    swap().pop() // swap and pop L
                    goto_(isOverloadedJumpTo)

                    label(methodDoesNotExist)
                    if (binaryExpr.operator.type == PLUS) {
                        // instances can have toString called on them with +.
                        pop() // pop OP
                        // L
                        plus()
                        // L + R
                        goto_(isOverloadedJumpTo)
                    } else {
                        pop() // pop OP
                        // L
                        kloxthrow(binaryExpr.operator) {
                            concat(
                                { ldc("'") },
                                {
                                    swap() // the ' and the L (klox instance)
                                    invokevirtual(KLOX_INSTANCE, "getKlass", "()L$KLOX_CLASS;")
                                    invokeinterface(KLOX_CLASS, "getName", "()Ljava/lang/String;")
                                },
                                { ldc("' does not have an operator method '${binaryExpr.overloadMethodName}'.") }
                            )
                        }
                    }
                    label(notInstance)
                }
            }

            fun binaryOp(resultType: String, op: Composer.() -> Unit) = with(composer) {
                val (end) = labels(1)
                if (binaryExpr.left is LiteralExpr && binaryExpr.left.value is Double) {
                    ldc2_w(binaryExpr.left.value)
                } else {
                    tryoverloaded(isOverloadedJumpTo = end)
                    // not overloaded, left is still on the stack
                    checktype(binaryExpr.operator, "java/lang/Double", "Operands must be numbers.")
                    unbox("java/lang/Double")
                }

                if (binaryExpr.right is LiteralExpr && binaryExpr.right.value is Double) {
                    ldc2_w(binaryExpr.right.value)
                } else {
                    binaryExpr.right.accept(this@FunctionCompiler)
                    checktype(binaryExpr.operator, "java/lang/Double", "Operands must be numbers.")
                    unbox("java/lang/Double")
                }

                op(this)
                box(resultType)

                label(end)
            }

            fun bitwise(op: Composer.() -> Composer) = binaryOp("java/lang/Double") {
                d2i()
                dup_x2().pop() // swap
                d2i()
                swap()
                op(this)
                i2d()
            }

            fun comparison(op: Composer.(label: Label) -> Unit) = binaryOp("java/lang/Boolean") {
                val (l0, l1) = labels(2)
                op(composer, l0)
                iconst_1()
                goto_(l1)
                label(l0)
                iconst_0()
                label(l1)
            }

            fun equalequal(resultComposer: (Composer.() -> Unit)? = null) = with(composer) {
                val (notNaN, notNaNPop, result, end) = labels(4)
                tryoverloaded(isOverloadedJumpTo = end)
                // not overloaded, left is still on the stack
                dup()
                binaryExpr.right.accept(this@FunctionCompiler)
                dup()

                // A, A, B, B
                instanceof_("java/lang/Double")
                ifeq(notNaNPop)
                dup()
                checkcast("java/lang/Double")
                invokevirtual("java/lang/Double", "isNaN", "()Z")
                ifeq(notNaNPop)
                dup_x2()
                pop()
                instanceof_("java/lang/Double")
                ifeq(notNaN)
                dup()
                checkcast("java/lang/Double")
                invokevirtual("java/lang/Double", "isNaN", "()Z")
                ifeq(notNaN)
                pop2() // both NaN, so not equal
                iconst_0()
                goto_(result)

                label(notNaNPop) // there's an extra first param on the stack
                // A, A, B
                invokestatic("java/util/Objects", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z")
                swap()
                pop()
                goto_(result)

                label(notNaN)
                invokestatic("java/util/Objects", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z")

                label(result)
                resultComposer?.let { it(composer) }
                box("java/lang/Boolean")

                label(end)
            }

            when (binaryExpr.operator.type) {
                PLUS -> with(composer) {
                    val (end) = labels(1)
                    tryoverloaded(isOverloadedJumpTo = end)
                    // not overloaded, left is still on the stack
                    plus()

                    label(end)
                }
                MINUS -> binaryOp("java/lang/Double") { dsub() }
                SLASH -> binaryOp("java/lang/Double") { ddiv() }
                STAR -> binaryOp("java/lang/Double") { dmul() }
                STAR_STAR -> binaryOp("java/lang/Double") {
                    invokestatic("java/lang/Math", "pow", "(DD)D")
                }
                PERCENT -> binaryOp("java/lang/Double") { drem() }
                GREATER -> comparison { dcmpl(); ifle(it) }
                GREATER_EQUAL -> comparison { dcmpl(); iflt(it) }
                LESS -> comparison { dcmpg(); ifge(it) }
                LESS_EQUAL -> comparison { dcmpg(); ifgt(it) }
                BANG_EQUAL -> equalequal {
                    iconst_1()
                    ixor()
                }
                EQUAL_EQUAL -> equalequal()
                IS -> {
                    binaryExpr.left.accept(this@FunctionCompiler)
                    binaryExpr.right.accept(this@FunctionCompiler)
                    composer.helper(KLOX_MAIN_CLASS, "is", stackInputSize = 2, stackResultSize = 1) {
                        val (isInstance, loopStart, notInstance) = labels(3)
                        val (checkPrimitives, checkString, checkNumber, checkBoolean) = labels(4)

                        data class PrimitiveInfo(val varDef: VarDef, val javaType: String, val label: Label)
                        val primitives = listOf(
                            PrimitiveInfo(stringClass, "java/lang/String", checkString),
                            PrimitiveInfo(numberClass, "java/lang/Double", checkNumber),
                            PrimitiveInfo(booleanClass, "java/lang/Boolean", checkBoolean)
                        )

                        aload_0()
                        instanceof_(KLOX_INSTANCE)
                        ifeq(checkPrimitives)

                        aload_0()
                        checkcast(KLOX_INSTANCE)
                        invokevirtual(KLOX_INSTANCE, "getKlass", "()L$KLOX_CLASS;")
                        astore_0()

                        label(loopStart)
                        aload_0()
                        ifnull(notInstance)

                        aload_0()
                        aload_1()
                        ifacmpeq(isInstance)

                        aload_0()
                        invokeinterface(KLOX_CLASS, "getSuperClass", "()L$KLOX_CLASS;")
                        astore_0()
                        goto_(loopStart)

                        label(isInstance)
                        TRUE()
                        areturn()

                        label(checkPrimitives)
                        for ((index, primitive) in primitives.withIndex()) {
                            label(primitive.label)
                            aload_0()
                            instanceof_(primitive.javaType)
                            ifeq(if (index == primitives.lastIndex) notInstance else primitives[index + 1].label)
                            global(primitive.varDef)
                            aload_1()
                            ifacmpne(notInstance)
                            TRUE()
                            areturn()
                        }

                        label(notInstance)
                        FALSE()
                        areturn()
                    }
                }
                COMMA -> {
                    binaryExpr.left.accept(this@FunctionCompiler)
                    composer.pop()
                    binaryExpr.right.accept(this@FunctionCompiler)
                }
                PIPE -> bitwise { ior() }
                AMPERSAND -> bitwise { iand() }
                CARET -> bitwise { ixor() }
                LESS_LESS -> bitwise { ishl() }
                GREATER_GREATER -> bitwise { ishr() }
                GREATER_GREATER_GREATER -> bitwise { iushr() }
                DOT_DOT -> with(composer) {
                    // TODO move this to maybeoverloaded helper
                    when {
                        binaryExpr.left is LiteralExpr && binaryExpr.left.value is Double &&
                            binaryExpr.right is LiteralExpr && binaryExpr.right.value is Double -> {
                            load(function, numberClass)
                            kloxfindmethod("rangeTo")
                            ldc2_w(binaryExpr.right.value)
                            box("java/lang/Double")
                            ldc2_w(binaryExpr.left.value)
                            box("java/lang/Double")
                            kloxinvoke(numberOfParams = 2)
                        }
                        binaryExpr.left is LiteralExpr && binaryExpr.left.value is String &&
                            binaryExpr.right is LiteralExpr && binaryExpr.right.value is String -> {
                            load(function, characterClass)
                            kloxfindmethod("rangeTo")
                            ldc(binaryExpr.right.value.first().toString())
                            ldc(binaryExpr.left.value.first().toString())
                            kloxinvoke(numberOfParams = 2)
                        }
                        else -> {
                            val (left, right) = listOf(function.temp(), function.temp())
                            binaryExpr.right.accept(this@FunctionCompiler)
                            astore(function.slot(right))
                            binaryExpr.left.accept(this@FunctionCompiler)
                            astore(function.slot(left))

                            val (maybeString, other, end) = labels(3)

                            aload(function.slot(right))
                            instanceof_("java/lang/Double")
                            ifeq(maybeString)
                            aload(function.slot(left))
                            instanceof_("java/lang/Double")
                            ifeq(maybeString)

                            load(function, numberClass)
                            kloxfindmethod("rangeTo")
                            aload(function.slot(right))
                            aload(function.slot(left))
                            kloxinvoke(numberOfParams = 2)
                            goto_(end)

                            label(maybeString)
                            aload(function.slot(right))
                            instanceof_("java/lang/String")
                            ifeq(other)
                            aload(function.slot(left))
                            instanceof_("java/lang/String")
                            ifeq(other)

                            load(function, characterClass)
                            kloxfindmethod("rangeTo")
                            aload(function.slot(right))
                            aload(function.slot(left))
                            kloxinvoke(numberOfParams = 2)
                            goto_(end)

                            label(other)
                            aload(function.slot(left))
                            checktype(KLOX_INSTANCE, "Expect an instance of a class that implements 'rangeTo'.")
                            getkloxfield("rangeTo", KLOX_CALLABLE)
                            aload(function.slot(right))
                            kloxinvoke(numberOfParams = 1)

                            label(end)
                            function.free(left, right)
                        }
                    }
                }
                else -> {}
            }
        }

        override fun visitUnaryExpr(unaryExpr: UnaryExpr): Unit = with(composer) {
            unaryExpr.right.accept(this@FunctionCompiler)

            when (unaryExpr.operator.type) {
                BANG -> {
                    val (label0, end) = labels(2)
                    ifnontruthy(label0)
                    FALSE()
                    goto_(end)
                    label(label0)
                    TRUE()
                    label(end)
                }
                MINUS -> {
                    checktype(unaryExpr.operator, "java/lang/Double", "Operand must be a number.")
                    boxed("java/lang/Double") {
                        dneg()
                    }
                }
                TILDE -> {
                    checktype(unaryExpr.operator, "java/lang/Integer", "Operand must be an integer.")
                    boxed("java/lang/Double") {
                        d2i()
                        ineg()
                        iconst_1()
                        isub()
                        i2d()
                    }
                }
                PLUS_PLUS, MINUS_MINUS -> {
                    val (isNull, end) = labels(2)
                    val varDef = (unaryExpr.right as VariableExpr)
                    if (!varDef.isDefined) {
                        pop()
                        kloxthrow(unaryExpr.operator, "Variable ${varDef.name} is not defined.")
                    } else {
                        val isIncrement = unaryExpr.operator.type == PLUS_PLUS
                        dup()
                        ifnull(isNull)
                        checktype(unaryExpr.operator, "java/lang/Double", "${unaryExpr.operator.lexeme} operand must be a number.")

                        if (unaryExpr.postfix) dup()
                        boxed("java/lang/Double") {
                            dconst_1()
                            if (isIncrement) dadd() else dsub()
                        }
                        if (!unaryExpr.postfix) dup()

                        store(function, varDef.varDef!!)
                        goto_(end)

                        label(isNull)
                        pop()
                        kloxthrow(
                            unaryExpr.operator,
                            "${unaryExpr.operator.lexeme} operand is 'nil'."
                        )

                        label(end)
                    }
                }
                BANG_QUESTION -> {
                    val (notResult, isOk, end) = labels(3)
                    dup()
                    instanceof_(KLOX_INSTANCE)
                    ifeq(notResult)
                    checkcast(KLOX_INSTANCE)
                    dup()
                    getkloxfield("isError", KLOX_FUNCTION)
                    iconst_0()
                    anewarray("java/lang/Object")
                    invokeinterface(KLOX_FUNCTION, "invoke", "([Ljava/lang/Object;)Ljava/lang/Object;")
                    unbox("java/lang/Boolean")
                    ifeq(isOk)
                    // isError
                    areturn()

                    label(isOk)
                    getkloxfield("value", "java/lang/Object")
                    goto_(end)

                    label(notResult)
                    pop()
                    kloxthrow(
                        unaryExpr.operator,
                        "!? operator can only be used with functions that return 'Result'."
                    )

                    label(end)
                }
                else -> {}
            }
        }

        override fun visitGroupingExpr(groupingExpr: GroupingExpr) = groupingExpr.expression.accept(this)

        override fun visitLiteralExpr(literalExpr: LiteralExpr): Unit = with(composer) {
            when (literalExpr.value) {
                is Boolean -> if (literalExpr.value) TRUE() else FALSE()
                is String -> ldc(literalExpr.value)
                is Double -> pushDouble(literalExpr.value).box("java/lang/Double")
                else -> if (literalExpr.value == null) aconst_null()
            }
        }

        override fun visitVariableExpr(variableExpr: VariableExpr): Unit = with(composer) {
            if (variableExpr.isDefined) {
                load(function, variableExpr.varDef!!)
            } else {
                kloxthrow(variableExpr.name, "Undefined variable '${variableExpr.name.lexeme}'.")
            }
        }

        override fun visitAssignExpr(assignExpr: AssignExpr): Unit = with(composer) {
            if (!assignExpr.isDefined) {
                kloxthrow(assignExpr.name, "Undefined variable '${assignExpr.name.lexeme}'.")
            } else {
                assignExpr.value.accept(this@FunctionCompiler)
                dup() // assignments leave the value on the stack after storing it
                store(function, assignExpr.varDef!!)
            }
        }

        override fun visitLogicalExpr(logicalExpr: LogicalExpr): Unit = with(composer) {
            val (end) = labels(1)
            when (logicalExpr.operator.type) {
                OR -> {
                    logicalExpr.left.accept(this@FunctionCompiler)
                    dup()
                    iftruthy(end)
                    pop()
                    logicalExpr.right.accept(this@FunctionCompiler)
                }
                AND -> {
                    logicalExpr.left.accept(this@FunctionCompiler)
                    dup()
                    ifnontruthy(end)
                    pop()
                    logicalExpr.right.accept(this@FunctionCompiler)
                }
                else -> {}
            }
            label(end)
        }

        override fun visitCallExpr(callExpr: CallExpr): Unit = with(composer) {
            callExpr.callee.accept(this@FunctionCompiler)
            val (notNull, end) = labels(2)

            if (callExpr.callee is GetExpr && callExpr.callee.safeAccess) {
                dup()
                ifnonnull(notNull)
                goto_(end)
            }

            label(notNull)
            checktype(callExpr.paren, KLOX_CALLABLE, "Can only call functions and classes.")

            callExpr.arguments.forEach { it.accept(this@FunctionCompiler) }
            invokedynamic(
                0,
                "invoke",
                """(L$KLOX_CALLABLE;${"Ljava/lang/Object;".repeat(callExpr.arguments.size)})Ljava/lang/Object;"""
            )

            label(end)
        }

        override fun visitFunctionStmt(functionStmt: FunctionStmt): Unit = with(composer) {
            compile(
                currentCompiler = this@FunctionCompiler,
                function = functionStmt.functionExpr,
                className = functionStmt.classStmt?.name?.lexeme,
                modifiers = functionStmt.modifiers,
                name = functionStmt.name.lexeme
            ) {
                // Function statements should declare and store the function in the current scope.
                declare(function, functionStmt)
            }
        }

        override fun visitFunctionExpr(functionExpr: FunctionExpr) = with(composer) {
            compile(currentCompiler = this@FunctionCompiler, function = functionExpr)
        }

        override fun visitReturnStmt(returnStmt: ReturnStmt): Unit = with(composer) {
            when {
                returnStmt.value != null -> returnStmt.value.accept(this@FunctionCompiler)
                modifiers.contains(INITIALIZER) -> aload_0().invokeinterface(KLOX_FUNCTION, "getReceiver", "()L$KLOX_INSTANCE;")
                else -> aconst_null()
            }

            areturn()
        }

        override fun visitClassStmt(classStmt: ClassStmt): Unit = with(composer) {
            val clazz = create(classStmt)
            val (isSuperClass, end) = labels(2)
            new_(clazz)
            dup()
            aload_0()
            if (classStmt.superClass != null) {
                classStmt.superClass.accept(this@FunctionCompiler)
                dup()
                instanceof_(KLOX_CLASS)
                ifne(isSuperClass)
                kloxthrow(classStmt.name, "Superclass must be a class.")
                label(isSuperClass)

                if (classStmt.methods.singleOrNull { it.name.lexeme == "init" }?.let { SuperConstructorCallCounter().count(it.functionExpr) } == 0) {
                    // check that the constructor calls the super constructor if it exists
                    dup()
                    kloxfindmethod("init") // super.init()
                    ifnull(end) // no constructor, no problem
                    dup()
                    invokeinterface(KLOX_CALLABLE, "arity", "()I")
                    ifeq(end) // no-arg constructor, no problem // TODO: call no-arg constructor by default?
                    kloxthrow(classStmt.name) {
                        invokeinterface(KLOX_CLASS, "getName", "()Ljava/lang/String;")
                        val temp = function.temp().also {
                            store(function, it)
                        }
                        concat(
                            { ldc("'${classStmt.name.lexeme}' does not call superclass '") },
                            { load(function, temp).also { function.free(temp) } },
                            { ldc("' constructor.") }
                        )
                    }
                }

                label(end)
                invokespecial(clazz.name, "<init>", "(L$KLOX_CALLABLE;L$KLOX_CLASS;)V")
            } else {
                invokespecial(clazz.name, "<init>", "(L$KLOX_CALLABLE;)V")
            }

            declare(function, classStmt)
        }

        override fun visitGetExpr(getExpr: GetExpr): Unit = with(composer) {
            val (notInstance, notInstanceAndNotClass, notStatic, maybeGetter, end) = labels(5)
            val (notFound, throwOnlyInstancesHaveProperties) = labels(2)

            getExpr.obj.accept(this@FunctionCompiler)
            dup()
            instanceof_(KLOX_INSTANCE)
            ifeq(notInstance)
            checkcast(KLOX_INSTANCE)
            ldc(getExpr.name.lexeme)
            iconst(if (getExpr.safeAccess) 1 else 0)
            invokevirtual(KLOX_INSTANCE, "get", "(Ljava/lang/String;Z)Ljava/lang/Object;")
            dup()
            instanceof_(KLOX_FUNCTION)
            ifne(maybeGetter)
            goto_(end)

            label(notInstance)
            dup()
            instanceof_(KLOX_CLASS)
            ifeq(notInstanceAndNotClass)
            checkcast(KLOX_CLASS)
            ldc(getExpr.name.lexeme)
            invokeinterface(KLOX_CLASS, "findMethod", "(Ljava/lang/String;)L$KLOX_FUNCTION;")
            dup()
            ifnull(notFound)
            dup()
            invokeinterface(KLOX_FUNCTION, "isStatic", "()Z")
            ifeq(notStatic)
            // fall-through to maybeGetter

            label(maybeGetter)
            dup()
            invokeinterface(KLOX_CALLABLE, "arity", "()I")
            iconst_m1()
            ificmpne(end)
            invokedynamic(0, "invoke", "(L$KLOX_CALLABLE;)Ljava/lang/Object;")
            goto_(end)

            label(notInstanceAndNotClass)
            if (getExpr.safeAccess) {
                dup()
                ifnonnull(throwOnlyInstancesHaveProperties)
                // otherwise, safeAccess permits returning null
                goto_(end)
            }

            label(throwOnlyInstancesHaveProperties)
            kloxthrow(getExpr.name) {
                stringify()
                astore_1()
                concat(
                    { ldc("Can't read property '${getExpr.name.lexeme}' of '") },
                    { aload_1() },
                    { ldc("'.") }
                )
            }

            label(notStatic)
            kloxthrow(getExpr.name) {
                invokeinterface(KLOX_FUNCTION, "getName", "()Ljava/lang/String;")
                astore_1()
                concat(
                    { ldc("'") },
                    { aload_1() },
                    { ldc("' is not a static class method.") }
                )
            }

            label(notFound)
            if (getExpr.safeAccess) {
                goto_(end)
            } else {
                pop()
                kloxthrow(getExpr.name) {
                    concat(
                        { ldc("Method '") },
                        { ldc(getExpr.name.lexeme) },
                        { ldc("' not found.") }
                    )
                }
            }

            label(end)
        }

        override fun visitSetExpr(setExpr: SetExpr): Unit = with(composer) {
            val (notInstance, end) = labels(2)
            setExpr.obj.accept(this@FunctionCompiler)
            dup()
            instanceof_(KLOX_INSTANCE)
            ifeq(notInstance)
            checkcast(KLOX_INSTANCE)
            ldc(setExpr.name.lexeme)
            setExpr.value.accept(this@FunctionCompiler)
            dup_x2()
            invokevirtual(KLOX_INSTANCE, "set", "(Ljava/lang/String;Ljava/lang/Object;)V")
            goto_(end)

            label(notInstance)
            pop()
            kloxthrow(setExpr.name, "Only instances have fields.")

            label(end)
        }

        override fun visitArrayExpr(arrayExpr: ArrayExpr): Unit = with(composer) {
            iconst(arrayExpr.elements.size)
            i2d()
            box("java/lang/Double")
            new_(function, arrayClass)
            dup()

            getkloxfield("\$array", "[Ljava/lang/Object;")
            for ((index, element) in arrayExpr.elements.withIndex()) {
                dup()
                iconst(index)
                element.accept(this@FunctionCompiler)
                aastore()
            }
            pop() // the underlying array
            // the klox Array instance is on the stack
        }

        override fun visitSuperExpr(superExpr: SuperExpr): Unit = with(composer) {
            val (superMethodNotFound, end) = labels(2)
            ldc(superExpr.method.lexeme)
            aload_0()
            for (i in 0 until superExpr.depth - 1) {
                invokeinterface(KLOX_CALLABLE, "getEnclosing", "()L$KLOX_CALLABLE;")
            }
            checkcast(KLOX_FUNCTION)
            invokeinterface(KLOX_FUNCTION, "getOwner", "()L$KLOX_CLASS;")
            invokeinterface(KLOX_CLASS, "getSuperClass", "()L$KLOX_CLASS;")
            swap()
            invokeinterface(KLOX_CLASS, "findMethod", "(Ljava/lang/String;)L$KLOX_FUNCTION;")
            dup()
            ifnull(superMethodNotFound)

            aload_0()
            invokeinterface(KLOX_FUNCTION, "getReceiver", "()L$KLOX_INSTANCE;")
            invokeinterface(KLOX_FUNCTION, "bind", "(L$KLOX_INSTANCE;)L$KLOX_FUNCTION;")
            goto_(end)

            label(superMethodNotFound)
            pop()

            kloxthrow(superExpr.name) {
                concat(
                    { ldc("Undefined property '") },
                    { ldc(superExpr.method.lexeme) },
                    { ldc("'.") }
                )
            }

            label(end)
        }

        override fun visitThisExpr(thisExpr: ThisExpr): Unit = with(composer) {
            aload_0()
            for (i in 0 until thisExpr.depth - 1) {
                invokeinterface(KLOX_CALLABLE, "getEnclosing", "()L$KLOX_CALLABLE;")
            }
            checkcast(KLOX_FUNCTION)
            invokeinterface(KLOX_FUNCTION, "getReceiver", "()L$KLOX_INSTANCE;")
        }

        fun create(classStmt: ClassStmt): ProgramClass {
            val clazz = ClassBuilder(
                CLASS_VERSION_1_8,
                PUBLIC,
                classStmt.javaClassName,
                "java/lang/Object"
            )
                .addInterface(KLOX_CLASS)
                .addInterface(KLOX_CALLABLE)
                .addField(PRIVATE or FINAL, "__enclosing", "L$KLOX_CALLABLE;")
                .apply { if (classStmt.superClass != null) addField(PRIVATE or FINAL, "__superClass", "L$KLOX_CLASS;") }
                .apply {
                    for (method in classStmt.methods) {
                        addField(PRIVATE or FINAL, method.javaName, "L$KLOX_FUNCTION;")
                    }
                }
                .addMethod(PUBLIC, "<init>", """(L$KLOX_CALLABLE;${if (classStmt.superClass != null) "L$KLOX_CLASS;" else ""})V""") {
                    aload_0()
                    dup()
                    invokespecial("java/lang/Object", "<init>", "()V")
                    aload_1()
                    putfield(targetClass.name, "__enclosing", "L$KLOX_CALLABLE;")
                    if (classStmt.superClass != null) {
                        aload_0()
                        aload_2()
                        putfield(targetClass.name, "__superClass", "L$KLOX_CLASS;")
                    }
                    for (method in classStmt.methods) compile(
                        currentCompiler = this@FunctionCompiler,
                        function = method.functionExpr,
                        className = method.classStmt?.name?.lexeme,
                        modifiers = method.modifiers,
                        name = method.name.lexeme
                    ) {
                        dup()
                        aload_0()
                        invokevirtual(method.functionExpr.javaClassName, "setOwner", "(L$KLOX_CLASS;)V")
                        aload_0()
                        swap()
                        putfield(targetClass.name, method.javaName, "L$KLOX_FUNCTION;")
                    }
                    return_()
                }
                .addMethod(PUBLIC or VARARGS, "invoke", "([Ljava/lang/Object;)Ljava/lang/Object;") {
                    val (noConstructor, end) = labels(2)
                    new_(KLOX_INSTANCE)
                    dup()
                    dup()
                    aload_0()
                    invokespecial(KLOX_INSTANCE, "<init>", "(L$KLOX_CLASS;)V")
                    aload_0()
                    ldc("init")
                    invokeinterface(KLOX_CLASS, "findMethod", "(Ljava/lang/String;)L$KLOX_FUNCTION;")
                    dup()
                    ifnull(noConstructor)
                    checkcast(KLOX_FUNCTION)
                    swap()
                    invokeinterface(KLOX_FUNCTION, "bind", "(L$KLOX_INSTANCE;)L$KLOX_FUNCTION;")
                    aload_1()
                    invokeinterface(KLOX_FUNCTION, "invoke", "([Ljava/lang/Object;)Ljava/lang/Object;")
                    pop()
                    goto_(end)

                    label(noConstructor)
                    pop2()

                    label(end)
                    areturn()
                }
                .addMethod(PUBLIC, "getName", "()Ljava/lang/String;") {
                    ldc(classStmt.name.lexeme)
                    areturn()
                }
                .addMethod(PUBLIC, "arity", "()I") {
                    val (end) = labels(1)
                    aload_0()
                    ldc("init")
                    invokevirtual(targetClass.name, "findMethod", "(Ljava/lang/String;)L$KLOX_FUNCTION;")
                    dup()
                    ifnull(end)
                    invokeinterface(KLOX_CALLABLE, "arity", "()I")
                    ireturn()

                    label(end)
                    pop()
                    iconst_0()
                    ireturn()
                }
                .addMethod(PUBLIC, "getEnclosing", "()L$KLOX_CALLABLE;") {
                    aload_0()
                    getfield(targetClass.name, "__enclosing", "L$KLOX_CALLABLE;")
                    areturn()
                }
                .addMethod(PUBLIC, "getSuperClass", "()L$KLOX_CLASS;") {
                    if (classStmt.superClass != null) {
                        aload_0().getfield(targetClass.name, "__superClass", "L$KLOX_CLASS;")
                    } else {
                        aconst_null()
                    }
                    areturn()
                }
                .addMethod(PUBLIC, "findMethod", "(Ljava/lang/String;)L$KLOX_FUNCTION;") {
                    aload_1()
                    switch(
                        temporary = 2,
                        *classStmt.methods.map {
                            it.name.lexeme case {
                                aload_0()
                                getfield(targetClass.name, it.javaName, "L$KLOX_FUNCTION;")
                                areturn()
                            }
                        }.toTypedArray(),
                        default = {
                            if (classStmt.superClass != null) {
                                aload_0()
                                getfield(targetClass.name, "__superClass", "L$KLOX_CLASS;")
                                aload_1()
                                invokeinterface(KLOX_CLASS, "findMethod", "(Ljava/lang/String;)L$KLOX_FUNCTION;")
                            } else {
                                aconst_null()
                            }
                            areturn()
                        }
                    )
                }
                .addMethod(PUBLIC, "toString", "()Ljava/lang/String;") {
                    ldc(classStmt.name.lexeme)
                    areturn()
                }
                .apply {
                    programClassPool.addClass(programClass)
                }.programClass

            return clazz
        }

        private fun create(modifiers: EnumSet<ModifierFlag>, name: String?, functionExpr: FunctionExpr): Pair<ProgramClass, ProgramMethod> {
            if (debug == true) {
                println("Compiling function ${functionExpr.javaClassName}")
            }
            val isAnonymous = name == null
            val clazz = ClassBuilder(
                CLASS_VERSION_1_8,
                PUBLIC,
                functionExpr.javaClassName,
                "java/lang/Object"
            )
                .addInterface(KLOX_FUNCTION)
                .addField(PRIVATE or FINAL, "__enclosing", "L$KLOX_CALLABLE;")
                .addField(PRIVATE or FINAL, "__owner", "L$KLOX_CLASS;")
                .apply {
                    if (functionExpr.isMain) {
                        for (global in functionExpr.variables) {
                            addField(PUBLIC or STATIC, global.javaName, if (global.isCaptured) "L$KLOX_CAPTURED_VAR;" else "Ljava/lang/Object;")
                        }
                    } else {
                        for (captured in functionExpr.captured + functionExpr.variables.filter { it.isCaptured }) {
                            addField(PUBLIC, captured.javaName, "L$KLOX_CAPTURED_VAR;")
                        }
                    }
                }
                .addMethod(PUBLIC, "<init>", "(L$KLOX_CALLABLE;)V") {
                    aload_0()
                    dup()
                    invokespecial("java/lang/Object", "<init>", "()V")
                    aload_1()
                    putfield(targetClass.name, "__enclosing", "L$KLOX_CALLABLE;")
                    val lateInitVars = functionExpr.variables.filter { it.isLateInit }
                    // Create null captured values for lateinit variables
                    for ((i, variable) in lateInitVars.withIndex()) {
                        if (i == 0) aload_0()
                        if (!targetClass.isMain) dup()
                        aconst_null()
                        box(variable)
                        if (targetClass.isMain) {
                            putstatic(KLOX_MAIN_CLASS, variable.javaName, "L$KLOX_CAPTURED_VAR;")
                        } else {
                            putfield(targetClass.name, variable.javaName, "L$KLOX_CAPTURED_VAR;")
                        }
                        if (i == lateInitVars.size - 1) pop()
                    }
                    return_()
                }
                .addMethod(PUBLIC, "getName", "()Ljava/lang/String;") {
                    ldc(name ?: functionExpr.javaClassName)
                    areturn()
                }
                .addMethod(PUBLIC, "arity", "()I") {
                    if (modifiers.contains(GETTER)) iconst_m1() else iconst(functionExpr.params.size)
                    ireturn()
                }
                .addMethod(PUBLIC, "bind", "(L$KLOX_INSTANCE;)L$KLOX_FUNCTION;") {
                    if (!isAnonymous && !modifiers.contains(ModifierFlag.STATIC)) {
                        aload_0()
                        invokevirtual(targetClass.name, "clone", "()Ljava/lang/Object;")
                        checkcast(targetClass.name)
                        dup()
                        aload_1()
                        putfield(targetClass.name, "this", "L$KLOX_INSTANCE;")
                        areturn()
                    } else {
                        kloxthrow("${name ?: functionExpr.javaClassName} cannot be bound.")
                    }
                }
                .addMethod(PUBLIC, "isStatic", "()Z") {
                    iconst(if (modifiers.contains(ModifierFlag.STATIC)) 1 else 0)
                    ireturn()
                }
                .addMethod(PUBLIC, "getEnclosing", "()L$KLOX_CALLABLE;") {
                    aload_0()
                    getfield(targetClass.name, "__enclosing", "L$KLOX_CALLABLE;")
                    areturn()
                }
                .addMethod(PUBLIC, "getOwner", "()L$KLOX_CLASS;") {
                    aload_0().getfield(targetClass.name, "__owner", "L$KLOX_CLASS;")
                    areturn()
                }
                .addMethod(PUBLIC, "setOwner", "(L$KLOX_CLASS;)V") {
                    aload_0()
                    aload_1()
                    putfield(targetClass.name, "__owner", "L$KLOX_CLASS;")
                    return_()
                }
                .addMethod(PUBLIC, "getReceiver", "()L$KLOX_INSTANCE;") {
                    if (!isAnonymous && !modifiers.contains(ModifierFlag.STATIC)) {
                        aload_0()
                        getfield(targetClass.name, "this", "L$KLOX_INSTANCE;")
                        areturn()
                    } else {
                        kloxthrow("${name ?: functionExpr.javaClassName} cannot be bound.")
                    }
                }
                .addMethod(PUBLIC or VARARGS, "invoke", "([Ljava/lang/Object;)Ljava/lang/Object;")
                .addMethod(PUBLIC, "toString", "()Ljava/lang/String;") {
                    ldc("<fn ${name ?: functionExpr.javaClassName}>")
                    areturn()
                }
                .apply {
                    if (InvokeDynamicCounter().count(functionExpr) > 0) {
                        addBootstrapMethod(
                            REF_INVOKE_STATIC,
                            KLOX_INVOKER,
                            "bootstrap",
                            "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;"
                        )
                    }
                    if (!isAnonymous && !modifiers.contains(ModifierFlag.STATIC)) {
                        addInterface("java/lang/Cloneable")
                        addField(PRIVATE, "this", "L$KLOX_INSTANCE;")
                        addMethod(PUBLIC, "clone", "()Ljava/lang/Object;") {
                            aload_0()
                            invokespecial("java/lang/Object", "clone", "()Ljava/lang/Object;")
                            areturn()
                        }
                    }
                    programClassPool.addClass(programClass)
                }.programClass

            return Pair(clazz, clazz.findMethod("invoke", null) as ProgramMethod)
        }
    }

    private fun initialize(programClassPool: ClassPool) = with(programClassPool) {
        addClass(
            ClassBuilder(
                CLASS_VERSION_1_8,
                PUBLIC,
                KLOX_EXCEPTION,
                "java/lang/RuntimeException"
            )
                .addField(PUBLIC or FINAL, "line", "I")
                .addMethod(PUBLIC, "<init>", "(Ljava/lang/String;I)V") {
                    aload_0()
                    dup()
                    iload_2()
                    putfield(targetClass.name, "line", "I")
                    aload_1()
                    invokespecial("java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V")
                    return_()
                }
                .addMethod(PUBLIC, "<init>", "(Ljava/lang/String;)V") {
                    aload_0()
                    aload_1()
                    iconst_m1()
                    invokespecial(targetClass.name, "<init>", "(Ljava/lang/String;I)V")
                    return_()
                }
                .addMethod(PUBLIC, "getMessage", "()Ljava/lang/String;") {
                    val (hasLine) = labels(1)
                    aload_0()
                    getfield(targetClass.name, "line", "I")
                    ifgt(hasLine)
                    aload_0().invokespecial(targetClass.superName, "getMessage", "()Ljava/lang/String;")
                    areturn()

                    label(hasLine)
                    concat(
                        { ldc("[line ") },
                        { aload_0().getfield(targetClass.name, "line", "I").box("java/lang/Integer") },
                        { ldc("] ") },
                        { aload_0().invokespecial(targetClass.superName, "getMessage", "()Ljava/lang/String;") },
                    )
                    areturn()
                }
                .programClass
        )

        addClass(
            ClassBuilder(
                CLASS_VERSION_1_8,
                PUBLIC or ABSTRACT or INTERFACE,
                KLOX_CALLABLE,
                "java/lang/Object"
            )
                .addMethod(PUBLIC or ABSTRACT, "getName", "()Ljava/lang/String;")
                .addMethod(PUBLIC or ABSTRACT, "arity", "()I")
                .addMethod(PUBLIC or ABSTRACT, "getEnclosing", "()L$KLOX_CALLABLE;")
                .addMethod(PUBLIC or ABSTRACT or VARARGS, "invoke", "([Ljava/lang/Object;)Ljava/lang/Object;")
                .programClass
        )

        addClass(
            ClassBuilder(
                CLASS_VERSION_1_8,
                PUBLIC or ABSTRACT or INTERFACE,
                KLOX_FUNCTION,
                "java/lang/Object"
            )
                .addInterface(KLOX_CALLABLE)
                .addMethod(PUBLIC or ABSTRACT, "bind", "(L$KLOX_INSTANCE;)L$KLOX_FUNCTION;")
                .addMethod(PUBLIC or ABSTRACT, "getReceiver", "()L$KLOX_INSTANCE;")
                .addMethod(PUBLIC or ABSTRACT, "getOwner", "()L$KLOX_CLASS;")
                .addMethod(PUBLIC or ABSTRACT, "isStatic", "()Z")
                .programClass
        )

        addClass(
            ClassBuilder(
                CLASS_VERSION_1_8,
                PUBLIC or ABSTRACT or INTERFACE,
                KLOX_CLASS,
                "java/lang/Object"
            )
                .addInterface(KLOX_CALLABLE)
                .addMethod(PUBLIC or ABSTRACT, "getSuperClass", "()L$KLOX_CLASS;")
                .addMethod(PUBLIC or ABSTRACT, "findMethod", "(Ljava/lang/String;)L$KLOX_FUNCTION;")
                .addMethod(PUBLIC or ABSTRACT, "getName", "()Ljava/lang/String;")
                .programClass
        )

        addClass(
            ClassBuilder(
                CLASS_VERSION_1_8,
                PUBLIC,
                KLOX_CAPTURED_VAR,
                "java/lang/Object"
            )
                .addField(PRIVATE or FINAL, "value", "Ljava/lang/Object;")
                .addMethod(PUBLIC, "<init>", "(Ljava/lang/Object;)V") {
                    aload_0()
                    dup()
                    invokespecial("java/lang/Object", "<init>", "()V")
                    aload_1()
                    putfield(targetClass.name, "value", "Ljava/lang/Object;")
                    return_()
                }
                .addMethod(PUBLIC, "getValue", "()Ljava/lang/Object;") {
                    aload_0()
                    getfield(targetClass.name, "value", "Ljava/lang/Object;")
                    areturn()
                }
                .addMethod(PUBLIC, "setValue", "(Ljava/lang/Object;)V") {
                    aload_0()
                    aload_1()
                    putfield(targetClass.name, "value", "Ljava/lang/Object;")
                    return_()
                }
                .addMethod(PUBLIC, "toString", "()Ljava/lang/String;") {
                    concat(
                        { ldc("<captured ") },
                        { aload_0().invokevirtual(targetClass.name, "getValue", "()Ljava/lang/Object;") },
                        { ldc(">") }
                    )
                    areturn()
                }
                .programClass
        )

        addClass(
            ClassBuilder(
                CLASS_VERSION_1_8,
                PUBLIC,
                KLOX_INSTANCE,
                "java/lang/Object"
            )
                .addField(PRIVATE or FINAL, "klass", "L$KLOX_CLASS;")
                .addField(PRIVATE or FINAL, "fields", "Ljava/util/Map;")
                .addMethod(PUBLIC, "<init>", "(L$KLOX_CLASS;)V") {
                    aload_0()
                    invokespecial("java/lang/Object", "<init>", "()V")
                    aload_0()
                    aload_1()
                    putfield(targetClass.name, "klass", "L$KLOX_CLASS;")
                    aload_0()
                    new_("java/util/HashMap")
                    dup()
                    invokespecial("java/util/HashMap", "<init>", "()V")
                    putfield(targetClass.name, "fields", "Ljava/util/Map;")
                    return_()
                }
                .addMethod(PUBLIC, "getKlass", "()L$KLOX_CLASS;") {
                    aload_0()
                    getfield(targetClass.name, "klass", "L$KLOX_CLASS;")
                    areturn()
                }
                .addMethod(PUBLIC, "get", "(Ljava/lang/String;)Ljava/lang/Object;") {
                    aload_0()
                    aload_1()
                    iconst_0()
                    invokevirtual(targetClass.name, "get", "(Ljava/lang/String;Z)Ljava/lang/Object;")
                    areturn()
                }
                .addMethod(PUBLIC, "get", "(Ljava/lang/String;Z)Ljava/lang/Object;") {
                    val (bind, checkMethod, throwUndefined, end) = labels(4)
                    aload_0()
                    getfield(targetClass.name, "fields", "Ljava/util/Map;")
                    dup()
                    aload_1()
                    invokeinterface("java/util/Map", "containsKey", "(Ljava/lang/Object;)Z")
                    ifeq(checkMethod)
                    aload_1()
                    invokeinterface("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;")
                    goto_(end)

                    label(checkMethod)
                    pop()
                    aload_0()
                    getfield(targetClass.name, "klass", "L$KLOX_CLASS;")
                    aload_1()
                    invokeinterface(KLOX_CLASS, "findMethod", "(Ljava/lang/String;)L$KLOX_FUNCTION;")
                    dup()
                    ifnonnull(bind)
                    iload_2()
                    ifeq(throwUndefined) // safeAccess allows null
                    areturn()

                    label(throwUndefined)
                    pop()
                    kloxthrow {
                        concat(
                            { ldc("Undefined property '") },
                            { aload_1() },
                            { ldc("'.") }
                        )
                    }

                    label(bind)
                    checkcast(KLOX_FUNCTION)
                    aload_0()
                    invokeinterface(KLOX_FUNCTION, "bind", "(L$KLOX_INSTANCE;)L$KLOX_FUNCTION;")

                    label(end)
                    areturn()
                }
                .addMethod(PUBLIC, "set", "(Ljava/lang/String;Ljava/lang/Object;)V") {
                    aload_0()
                    getfield(targetClass.name, "fields", "Ljava/util/Map;")
                    aload_1()
                    aload_2()
                    invokeinterface("java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
                    pop()
                    return_()
                }
                .addMethod(PUBLIC, "hasField", "(Ljava/lang/String;)Z") {
                    aload_0()
                    getfield(targetClass.name, "fields", "Ljava/util/Map;")
                    aload_1()
                    invokeinterface("java/util/Map", "containsKey", "(Ljava/lang/Object;)Z")
                    ireturn()
                }
                .addMethod(PUBLIC, "removeField", "(Ljava/lang/String;)V") {
                    aload_0()
                    getfield(targetClass.name, "fields", "Ljava/util/Map;")
                    aload_1()
                    invokeinterface("java/util/Map", "remove", "(Ljava/lang/Object;)Ljava/lang/Object;")
                    pop()
                    return_()
                }
                .addMethod(PUBLIC, "toString", "()Ljava/lang/String;") {
                    aload_0()
                    kloxclass()
                    kloxfindmethod("toString")
                    aload_0()
                    invokeinterface(KLOX_FUNCTION, "bind", "(L$KLOX_INSTANCE;)L$KLOX_FUNCTION;")
                    iconst_0()
                    anewarray("java/lang/Object")
                    invokeinterface(KLOX_FUNCTION, "invoke", "([Ljava/lang/Object;)Ljava/lang/Object;")
                    stringify()
                    checkcast("java/lang/String")
                    areturn()
                }
                .programClass
        )

        addClass(
            ClassBuilder(
                CLASS_VERSION_1_8,
                PUBLIC,
                KLOX_INVOKER,
                "java/lang/Object"
            )
                .addMethod(PUBLIC, "<init>", "()V") {
                    aload_0()
                    invokespecial("java/lang/Object", "<init>", "()V")
                    return_()
                }
                .addMethod(PUBLIC or STATIC or VARARGS, "invoke", "(L$KLOX_CALLABLE;[Ljava/lang/Object;)Ljava/lang/Object;") {
                    val (nonNegativeArity, correctArity) = labels(2)
                    aload_0()
                    checknonnull("Can only call functions and classes.")

                    aload_0()
                    invokeinterface(KLOX_CALLABLE, "arity", "()I")
                    iconst_m1()
                    ificmpne(nonNegativeArity)
                    // Getter has arity -1
                    aload_0()
                    iconst_0()
                    anewarray("java/lang/Object")
                    invokeinterface(KLOX_CALLABLE, "invoke", "([Ljava/lang/Object;)Ljava/lang/Object;")
                    areturn()

                    label(nonNegativeArity)
                    aload_0()
                    invokeinterface(KLOX_CALLABLE, "arity", "()I")
                    dup()
                    istore(4)
                    aload_1()
                    arraylength()
                    dup()
                    istore(5)
                    ificmpeq(correctArity)
                    kloxthrow {
                        concat(
                            { aload_0().invokeinterface(KLOX_CALLABLE, "getName", "()Ljava/lang/String;") },
                            { ldc(": Expected ") },
                            { iload(4).box("java/lang/Integer") },
                            { ldc(" arguments but got ") },
                            { iload(5).box("java/lang/Integer") },
                            { ldc(".") }
                        )
                    }

                    label(correctArity)
                    aload_0()
                    aload_1()
                    invokeinterface(KLOX_CALLABLE, "invoke", "([Ljava/lang/Object;)Ljava/lang/Object;")
                    areturn()
                }
                .addMethod(PUBLIC or STATIC, "bootstrap", "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;") {
                    val (default, multipleParams, createCallSite) = labels(3)
                    aload_2()
                    invokevirtual("java/lang/invoke/MethodType", "parameterCount", "()I")
                    istore_3()
                    ldc("invoke")
                    aload_1()
                    invokevirtual("java/lang/String", "equals", "(Ljava/lang/Object;)Z")
                    ifeq(default)
                    aload_0()
                    ldc(targetClass)
                    aload_1()
                    ldc(LibraryClass(PUBLIC, "java/lang/Object", null))
                    ldc(programClassPool.getClass(KLOX_CALLABLE))
                    iconst_1()
                    anewarray("java/lang/Class")
                    dup()
                    iconst_0()
                    ldc(LibraryClass(PUBLIC, "[Ljava/lang/Object;", "java/lang/Object"))
                    aastore()
                    invokestatic("java/lang/invoke/MethodType", "methodType", "(Ljava/lang/Class;Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/invoke/MethodType;")
                    invokevirtual("java/lang/invoke/MethodHandles\$Lookup", "findStatic", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;")
                    astore(4)
                    iload_3()
                    iconst_1()
                    ificmpne(multipleParams)
                    // single param
                    aload(4)
                    dup()
                    invokevirtual("java/lang/invoke/MethodHandle", "type", "()Ljava/lang/invoke/MethodType;")
                    iconst_1()
                    iconst_2()
                    invokevirtual("java/lang/invoke/MethodType", "dropParameterTypes", "(II)Ljava/lang/invoke/MethodType;")
                    invokevirtual("java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;")
                    astore(4)
                    goto_(createCallSite)

                    label(multipleParams)
                    aload(4)
                    dup()
                    invokevirtual("java/lang/invoke/MethodHandle", "type", "()Ljava/lang/invoke/MethodType;")
                    iconst_1()
                    invokevirtual("java/lang/invoke/MethodType", "parameterType", "(I)Ljava/lang/Class;")
                    invokevirtual("java/lang/invoke/MethodHandle", "asVarargsCollector", "(Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;")
                    astore(4)

                    label(createCallSite)
                    aload(4)
                    aload_2()
                    invokevirtual("java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;")
                    astore(4)
                    new_("java/lang/invoke/ConstantCallSite")
                    dup()
                    aload(4)
                    invokespecial("java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V")
                    areturn()

                    label(default)
                    kloxthrow {
                        concat(
                            { ldc("Invalid dynamic method call '") },
                            { aload_1() },
                            { ldc("'.") }
                        )
                    }
                }
                .programClass
        )
    }

    val FunctionExpr.isMain: Boolean
        get() = this == mainFunction.functionExpr

    companion object {
        const val KLOX_CALLABLE = "klox/KLoxCallable"
        const val KLOX_FUNCTION = "klox/KLoxFunction"
        const val KLOX_CLASS = "klox/KLoxClass"
        const val KLOX_INSTANCE = "klox/KLoxInstance"
        const val KLOX_CAPTURED_VAR = "klox/CapturedVar"
        const val KLOX_INVOKER = "klox/Invoker"
        const val KLOX_EXCEPTION = "klox/Exception"
        const val KLOX_MAIN_CLASS = "Main"
        const val KLOX_MAIN_FUNCTION = "main"
    }
}

val Clazz.isMain: Boolean
    get() = this.name == KLOX_MAIN_CLASS
