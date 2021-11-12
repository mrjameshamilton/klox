package eu.jameshamilton.klox.compile

import eu.jameshamilton.klox.compile.Resolver.Companion.captured
import eu.jameshamilton.klox.compile.Resolver.Companion.definedIn
import eu.jameshamilton.klox.compile.Resolver.Companion.depth
import eu.jameshamilton.klox.compile.Resolver.Companion.isCaptured
import eu.jameshamilton.klox.compile.Resolver.Companion.isDefined
import eu.jameshamilton.klox.compile.Resolver.Companion.isGlobalLateInit
import eu.jameshamilton.klox.compile.Resolver.Companion.javaClassName
import eu.jameshamilton.klox.compile.Resolver.Companion.javaName
import eu.jameshamilton.klox.compile.Resolver.Companion.slot
import eu.jameshamilton.klox.compile.Resolver.Companion.varDef
import eu.jameshamilton.klox.compile.Resolver.Companion.variables
import eu.jameshamilton.klox.debug
import eu.jameshamilton.klox.hadError
import eu.jameshamilton.klox.parse.Access
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
import eu.jameshamilton.klox.parse.FunctionType.*
import eu.jameshamilton.klox.parse.GetExpr
import eu.jameshamilton.klox.parse.GroupingExpr
import eu.jameshamilton.klox.parse.IfStmt
import eu.jameshamilton.klox.parse.LiteralExpr
import eu.jameshamilton.klox.parse.LogicalExpr
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
import proguard.classfile.LibraryClass
import proguard.classfile.ProgramClass
import proguard.classfile.ProgramMethod
import proguard.classfile.VersionConstants.CLASS_VERSION_1_8
import proguard.classfile.attribute.visitor.AllAttributeVisitor
import proguard.classfile.constant.MethodHandleConstant.REF_INVOKE_STATIC
import proguard.classfile.editor.ClassBuilder
import proguard.classfile.editor.CompactCodeAttributeComposer.Label
import proguard.classfile.visitor.AllMethodVisitor
import proguard.classfile.visitor.ClassPrinter
import proguard.classfile.visitor.ClassVersionFilter
import proguard.preverify.CodePreverifier
import proguard.classfile.editor.CompactCodeAttributeComposer as Composer

class Compiler : Program.Visitor<ClassPool> {

    private lateinit var mainFunction: FunctionStmt

    fun compile(program: Program): ClassPool {
        initialize(programClassPool)
        return program.accept(this)
    }

    override fun visitProgram(program: Program): ClassPool {
        mainFunction = FunctionStmt(
            Access.empty(),
            Token(FUN, "Main"),
            SCRIPT,
            params = emptyList(),
            body = program.stmts
        )

        mainFunction.accept(Resolver())

        if (hadError) return ClassPool()

        val (mainClass, _) = FunctionCompiler().compile(mainFunction)

        ClassBuilder(mainClass)
            .addField(PUBLIC or STATIC, "args", "[Ljava/lang/String;")
            .addMethod(PUBLIC or STATIC, "main", "([Ljava/lang/String;)V") {
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
        private lateinit var functionStmt: FunctionStmt

        fun compile(functionStmt: FunctionStmt): Pair<ProgramClass, ProgramMethod> {
            this.functionStmt = functionStmt
            val (clazz, method) = create(functionStmt)
            composer = Composer(clazz)
            with(composer) {
                beginCodeFragment(65_535)

                if (functionStmt.params.isNotEmpty()) {
                    aload_1()
                    unpackarray(functionStmt.params.size) { i ->
                        declare(functionStmt, functionStmt.params[i])
                    }
                }

                for (captured in functionStmt.captured) {
                    aload_0()
                    getfield(targetClass.name, captured.javaName, "L$KLOX_CAPTURED_VAR;")
                    astore(functionStmt.slot(captured))
                }

                val native = findNative(mainFunction, functionStmt)
                if (native != null) {
                    native(this)
                } else {
                    functionStmt.body.forEach {
                        it.accept(this@FunctionCompiler)
                    }

                    if (functionStmt.kind == INITIALIZER) {
                        aload_0()
                        invokeinterface(KLOX_FUNCTION, "getReceiver", "()L$KLOX_INSTANCE;")
                        areturn()
                    } else if (functionStmt.body.count { it is ReturnStmt } == 0) {
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

            declare(functionStmt, varStmt)
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

        private lateinit var currentLoopBodyLabel: Label
        private lateinit var currentLoopEndLabel: Label

        override fun visitWhileStmt(whileStmt: WhileStmt): Unit = with(composer) {
            val (conditionLabel, loopBody, endLabel) = labels(3)
            currentLoopBodyLabel = loopBody
            currentLoopEndLabel = endLabel
            label(conditionLabel)
            whileStmt.condition.accept(this@FunctionCompiler)
            ifnontruthy(endLabel)
            label(loopBody)
            whileStmt.body.accept(this@FunctionCompiler)
            goto_(conditionLabel)
            label(endLabel)
        }

        override fun visitBreakStmt(breakStmt: BreakStmt): Unit = with(composer) {
            goto_(currentLoopEndLabel)
        }

        override fun visitContinueStmt(continueStmt: ContinueStmt): Unit = with(composer) {
            goto_(currentLoopBodyLabel)
        }

        override fun visitBinaryExpr(binaryExpr: BinaryExpr) {
            fun binaryOp(resultType: String, op: Composer.() -> Unit) = with(composer) {
                val (tryStart, tryEnd) = try_ {
                    binaryExpr.left.accept(this@FunctionCompiler)
                    unbox("java/lang/Double")
                    binaryExpr.right.accept(this@FunctionCompiler)
                    unbox("java/lang/Double")
                }
                op(this)
                box(resultType)
                catch_(tryStart, tryEnd, "java/lang/ClassCastException") {
                    pop()
                    throw_("java/lang/RuntimeException", "Operands must be numbers.")
                }
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
                val (notNaN, notNaNPop, end) = labels(3)
                binaryExpr.left.accept(this@FunctionCompiler)
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
                goto_(end)

                label(notNaNPop) // there's an extra first param on the stack
                // A, A, B
                invokestatic("java/util/Objects", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z")
                swap()
                pop()
                goto_(end)

                label(notNaN)
                invokestatic("java/util/Objects", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z")

                label(end)
                resultComposer?.let { it(composer) }
                box("java/lang/Boolean")
            }

            when (binaryExpr.operator.type) {
                PLUS -> {
                    binaryExpr.left.accept(this@FunctionCompiler)
                    binaryExpr.right.accept(this@FunctionCompiler)
                    composer.helper("Main", "add", stackInputSize = 2, stackResultSize = 1) {
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
                        throw_("java/lang/RuntimeException", "Operands must be two numbers or two strings.")
                    }
                }
                MINUS -> binaryOp("java/lang/Double") { dsub() }
                SLASH -> binaryOp("java/lang/Double") { ddiv() }
                STAR -> binaryOp("java/lang/Double") { dmul() }
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
                    composer.helper("Main", "is", stackInputSize = 2, stackResultSize = 1) {
                        val (isInstance, notInstance, loopStart) = labels(4)
                        aload_0()
                        instanceof_(KLOX_INSTANCE)
                        ifeq(notInstance)

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

                        label(notInstance)
                        FALSE()
                        areturn()
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
                    val (tryStart, tryEnd) = try_ {
                        boxed("java/lang/Double") {
                            dneg()
                        }
                    }
                    catch_(tryStart, tryEnd, "java/lang/RuntimeException") {
                        pop()
                        throw_("java/lang/RuntimeException", "Operand must be a number.")
                    }
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
                aload(functionStmt.slot(variableExpr.varDef!!))
                if (variableExpr.varDef!!.isCaptured) unbox(variableExpr.varDef!!)
            } else {
                throw_("java/lang/RuntimeException", "Undefined variable '${variableExpr.name.lexeme}'.")
            }
        }

        override fun visitAssignExpr(assignExpr: AssignExpr): Unit = with(composer) {
            if (!assignExpr.isDefined) {
                throw_("java/lang/RuntimeException", "Undefined variable '${assignExpr.name.lexeme}'.")
                return
            }

            val varDef = assignExpr.varDef!!

            if (varDef.isCaptured) {
                aload(functionStmt.slot(varDef))
                assignExpr.value.accept(this@FunctionCompiler)
                dup_x1()
                invokevirtual(KLOX_CAPTURED_VAR, "setValue", "(Ljava/lang/Object;)V")
            } else {
                assignExpr.value.accept(this@FunctionCompiler)
                dup()
                astore(functionStmt.slot(varDef))
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

            val (tryStart, tryEnd) = try_ {
                checkcast(KLOX_CALLABLE)
            }

            callExpr.arguments.forEach { it.accept(this@FunctionCompiler) }
            invokedynamic(
                0,
                "invoke",
                """(L$KLOX_CALLABLE;${"Ljava/lang/Object;".repeat(callExpr.arguments.size)})Ljava/lang/Object;"""
            )

            // TODO create only one of these handlers per method
            catch_(tryStart, tryEnd, "java/lang/ClassCastException") {
                pop()
                new_("java/lang/RuntimeException")
                dup()
                ldc("Can only call functions and classes.")
                invokespecial("java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V")
                athrow()
            }
        }

        override fun visitFunctionStmt(functionStmt: FunctionStmt): Unit = with(composer) {
            val (clazz, _) = FunctionCompiler(enclosingCompiler = this@FunctionCompiler).compile(functionStmt)
            new_(clazz)
            dup()
            aload_0()
            invokespecial(clazz.name, "<init>", "(L$KLOX_CALLABLE;)V")

            if (functionStmt.captured.isNotEmpty()) dup()

            if (functionStmt.kind != INITIALIZER && functionStmt.kind != METHOD && functionStmt.kind != GETTER && !functionStmt.accessFlags.contains(Access.STATIC)) {
                // Don't need to store, it should remain on the stack so that it can be added to the class
                declare(this@FunctionCompiler.functionStmt, functionStmt)
            }

            if (functionStmt.captured.isNotEmpty()) {
                for (varDef in functionStmt.captured) {
                    dup()
                    aload_0()
                    if (enclosingCompiler?.functionStmt != null) {
                        for (i in enclosingCompiler.functionStmt.depth downTo varDef.definedIn.depth) {
                            invokeinterface(KLOX_CALLABLE, "getEnclosing", "()L$KLOX_CALLABLE;")
                        }
                        checkcast(varDef.definedIn.javaClassName)
                    }
                    getfield(varDef.definedIn.javaClassName, varDef.javaName, "L$KLOX_CAPTURED_VAR;")
                    putfield(functionStmt.javaClassName, varDef.javaName, "L$KLOX_CAPTURED_VAR;")
                }
                pop()
            }
        }

        override fun visitReturnStmt(returnStmt: ReturnStmt): Unit = with(composer) {
            when {
                returnStmt.value != null -> returnStmt.value.accept(this@FunctionCompiler)
                functionStmt.kind == INITIALIZER -> aload_0().invokeinterface(KLOX_FUNCTION, "getReceiver", "()L$KLOX_INSTANCE;")
                else -> aconst_null()
            }

            areturn()
        }

        override fun visitClassStmt(classStmt: ClassStmt): Unit = with(composer) {
            val clazz = create(classStmt)
            val (isSuperClass) = labels(1)
            new_(clazz)
            dup()
            aload_0()
            if (classStmt.superClass != null) {
                classStmt.superClass.accept(this@FunctionCompiler)
                dup()
                instanceof_(KLOX_CLASS)
                ifne(isSuperClass)
                throw_("java/lang/RuntimeException", "Superclass must be a class.")
                label(isSuperClass)
                invokespecial(clazz.name, "<init>", "(L$KLOX_CALLABLE;L$KLOX_CLASS;)V")
            } else {
                invokespecial(clazz.name, "<init>", "(L$KLOX_CALLABLE;)V")
            }

            if (classStmt.methods.isNotEmpty()) dup()

            declare(functionStmt, classStmt)

            for (method in classStmt.methods) {
                dup()
                dup()
                method.accept(this@FunctionCompiler)
                dup_x2()
                invokevirtual(classStmt.javaClassName, "addMethod", "(L$KLOX_FUNCTION;)V")
                invokevirtual(method.javaClassName, "setOwner", "(L$KLOX_CLASS;)V")
            }
        }

        override fun visitGetExpr(getExpr: GetExpr): Unit = with(composer) {
            val (notInstance, notInstanceAndNotClass, notStatic, maybeGetter, end) = labels(5)
            val (notFound) = labels(1)

            getExpr.obj.accept(this@FunctionCompiler)
            dup()
            instanceof_(KLOX_INSTANCE)
            ifeq(notInstance)
            checkcast(KLOX_INSTANCE)
            ldc(getExpr.name.lexeme)
            invokevirtual(KLOX_INSTANCE, "get", "(Ljava/lang/String;)Ljava/lang/Object;")
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
            pop()
            throw_("java/lang/RuntimeException", "Only instances have properties.")

            label(notStatic)
            throw_("java/lang/RuntimeException") {
                invokeinterface(KLOX_FUNCTION, "getName", "()Ljava/lang/String;")
                astore_1()
                concat(
                    { ldc("'") },
                    { aload_1() },
                    { ldc("' is not a static class method.") }
                )
            }

            label(notFound)
            pop()
            throw_("java/lang/RuntimeException") {
                concat(
                    { ldc("Method '") },
                    { ldc(getExpr.name.lexeme) },
                    { ldc("' not found.") }
                )
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
            throw_("java/lang/RuntimeException", "Only instances have fields.")

            label(end)
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

            throw_("java/lang/RuntimeException") {
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
                .addField(PRIVATE or FINAL, "__methods", "Ljava/util/Map;")
                .apply { if (classStmt.superClass != null) addField(PRIVATE or FINAL, "__superClass", "L$KLOX_CLASS;") }
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
                    aload_0()
                    new_("java/util/HashMap")
                    dup()
                    invokespecial("java/util/HashMap", "<init>", "()V")
                    putfield(targetClass.name, "__methods", "Ljava/util/Map;")
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
                    ldc(classStmt.javaClassName)
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
                .addMethod(PUBLIC, "addMethod", "(L$KLOX_FUNCTION;)V") {
                    aload_0()
                    getfield(targetClass.name, "__methods", "Ljava/util/Map;")
                    aload_1()
                    invokeinterface(KLOX_FUNCTION, "getName", "()Ljava/lang/String;")
                    aload_1()
                    invokeinterface(
                        "java/util/Map",
                        "put",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
                    )
                    pop()
                    return_()
                }
                .addMethod(PUBLIC, "findMethod", "(Ljava/lang/String;)L$KLOX_FUNCTION;") {
                    val (end, endNull) = labels(2)
                    aload_0()
                    getfield(targetClass.name, "__methods", "Ljava/util/Map;")
                    aload_1()
                    invokeinterface("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;")
                    dup()
                    ifnonnull(end)
                    pop()
                    aload_0()
                    invokeinterface(KLOX_CLASS, "getSuperClass", "()L$KLOX_CLASS;")
                    dup()
                    ifnull(endNull)
                    aload_1()
                    invokeinterface(KLOX_CLASS, "findMethod", "(Ljava/lang/String;)L$KLOX_FUNCTION;")

                    label(end)
                    checkcast(KLOX_FUNCTION)
                    areturn()

                    label(endNull)
                    aconst_null()
                    areturn()
                }
                .addMethod(PUBLIC, "toString", "()Ljava/lang/String;") {
                    ldc(classStmt.javaClassName)
                    areturn()
                }
                .apply {
                    programClassPool.addClass(programClass)
                }.programClass

            return clazz
        }

        private fun create(functionStmt: FunctionStmt): Pair<ProgramClass, ProgramMethod> {
            val clazz = ClassBuilder(
                CLASS_VERSION_1_8,
                PUBLIC,
                functionStmt.javaClassName,
                "java/lang/Object"
            )
                .addInterface(KLOX_FUNCTION)
                .addField(PRIVATE or FINAL, "__enclosing", "L$KLOX_CALLABLE;")
                .addField(PRIVATE or FINAL, "__owner", "L$KLOX_CLASS;")
                .apply {
                    for (captured in functionStmt.captured + functionStmt.variables.filter { it.isCaptured }) {
                        addField(PUBLIC, captured.javaName, "L$KLOX_CAPTURED_VAR;")
                    }
                }
                .addMethod(PUBLIC, "<init>", "(L$KLOX_CALLABLE;)V") {
                    aload_0()
                    dup()
                    invokespecial("java/lang/Object", "<init>", "()V")
                    aload_1()
                    putfield(targetClass.name, "__enclosing", "L$KLOX_CALLABLE;")
                    val globalLateInitVariables = functionStmt.variables.filter { it.isGlobalLateInit }
                    // Create null captured values for global lateinit variables
                    for ((i, variable) in globalLateInitVariables.withIndex()) {
                        if (i == 0) aload_0()
                        dup()
                        aconst_null()
                        box(variable)
                        putfield(targetClass.name, variable.javaName, "L$KLOX_CAPTURED_VAR;")
                        if (i == globalLateInitVariables.size - 1) pop()
                    }
                    return_()
                }
                .addMethod(PUBLIC, "getName", "()Ljava/lang/String;") {
                    ldc(functionStmt.name.lexeme)
                    areturn()
                }
                .addMethod(PUBLIC, "arity", "()I") {
                    if (functionStmt.kind == GETTER) iconst_m1() else iconst(functionStmt.params.size)
                    ireturn()
                }
                .addMethod(PUBLIC, "bind", "(L$KLOX_INSTANCE;)L$KLOX_FUNCTION;") {
                    if (functionStmt.isBindable) {
                        aload_0()
                        invokevirtual(targetClass.name, "clone", "()Ljava/lang/Object;")
                        checkcast(targetClass.name)
                        dup()
                        aload_1()
                        putfield(targetClass.name, "this", "L$KLOX_INSTANCE;")
                        areturn()
                    } else {
                        throw_("java/lang/UnsupportedOperationException", "${functionStmt.name.lexeme} cannot be bound.")
                    }
                }
                .addMethod(PUBLIC, "isStatic", "()Z") {
                    iconst(if (functionStmt.accessFlags.contains(Access.STATIC)) 1 else 0)
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
                    if (functionStmt.isBindable) {
                        aload_0()
                        getfield(targetClass.name, "this", "L$KLOX_INSTANCE;")
                        areturn()
                    } else {
                        throw_("java/lang/UnsupportedOperationException", "${functionStmt.name.lexeme} cannot be bound.")
                    }
                }
                .addMethod(PUBLIC or VARARGS, "invoke", "([Ljava/lang/Object;)Ljava/lang/Object;")
                .addMethod(PUBLIC, "toString", "()Ljava/lang/String;") {
                    ldc("<fn ${functionStmt.name.lexeme}>")
                    areturn()
                }
                .apply {
                    if (InvokeDynamicCounter().count(functionStmt) > 0) {
                        addBootstrapMethod(
                            REF_INVOKE_STATIC,
                            KLOX_INVOKER,
                            "bootstrap",
                            "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;"
                        )
                    }
                    if (functionStmt.isBindable) {
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

    private val FunctionStmt.isBindable
        get() = kind == FUNCTION || kind == METHOD || kind == INITIALIZER || kind == GETTER

    private fun initialize(programClassPool: ClassPool) = with(programClassPool) {
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
                    val (bind, checkMethod, end) = labels(3)
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
                    pop()
                    throw_("java/lang/RuntimeException") {
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
                    val (default) = labels(1)
                    val (tryStart, tryEnd) = try_ {
                        aload_0()
                        ldc("toString")
                        invokevirtual(targetClass.name, "get", "(Ljava/lang/String;)Ljava/lang/Object;")
                        dup()
                        instanceof_(KLOX_FUNCTION)
                        ifeq(default)
                        checkcast(KLOX_FUNCTION)
                        iconst_0()
                        anewarray("java/lang/Object")
                        invokeinterface(KLOX_FUNCTION, "invoke", "([Ljava/lang/Object;)Ljava/lang/Object;")
                        checkcast("java/lang/String")
                    }
                    catchAll(tryStart, tryEnd) {
                        label(default)
                        pop()
                        concat(
                            { aload_0().getfield(targetClass.name, "klass", "L$KLOX_CLASS;") },
                            { ldc(" instance") }
                        )
                    }
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
                    val (nonNull, nonNegativeArity, correctArity) = labels(3)
                    aload_0()
                    ifnonnull(nonNull)
                    throw_("java/lang/Exception", "Can only call functions and classes.")

                    label(nonNull)
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
                    throw_("java/lang/RuntimeException") {
                        concat(
                            { ldc("Expected ") },
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
                    throw_("java/lang/RuntimeException") {
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

    companion object {
        const val KLOX_CALLABLE = "klox/KLoxCallable"
        const val KLOX_FUNCTION = "klox/KLoxFunction"
        const val KLOX_CLASS = "klox/KLoxClass"
        const val KLOX_INSTANCE = "klox/KLoxInstance"
        const val KLOX_CAPTURED_VAR = "klox/CapturedVar"
        const val KLOX_INVOKER = "klox/Invoker"
    }
}
