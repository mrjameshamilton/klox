package eu.jameshamilton.klox.compile

import eu.jameshamilton.klox.compile.Compiler.Companion.KLOX_INSTANCE
import eu.jameshamilton.klox.parse.FunctionExpr
import proguard.classfile.AccessConstants.PRIVATE
import proguard.classfile.ProgramClass
import proguard.classfile.ProgramMethod
import proguard.classfile.editor.ClassBuilder
import proguard.classfile.editor.CompactCodeAttributeComposer as Composer

fun findNative(compiler: Compiler, className: String?, functionName: String, func: FunctionExpr): (Composer.() -> Unit)? {
    // if (debug == true) println("findName($className, $functionName)")

    fun Composer.kloxOk(): Composer {
        new_(func, compiler.okClass)
        return this
    }

    fun Composer.kloxError(message: Composer.() -> Composer): Composer {
        message(this)
        new_(func, compiler.errorClass)
        return this
    }

    fun Composer.kloxError(message: String): Composer = kloxError { ldc(message) }


    fun Composer.closestream(name: String, type: String): Composer {
        val (close, end) = labels(2)
        val (tryStart, endTry) = try_ {
            loadkloxinstance()
            dup()
            haskloxfield(name)
            ifne(close)
            pop()
            goto_(end)

            label(close)
            dup()
            getkloxfield(name, type)
            invokevirtual(type, "close", "()V")

            removekloxfield(name)
            label(end)
            TRUE()
            kloxOk()
        }
        catchAll(tryStart, endTry) {
            kloxError {
                invokevirtual("java/lang/Throwable", "getMessage", "()Ljava/lang/String;")
            }
        }
        return this
    }

    when (className) {
        "Object" -> when (functionName) {
            "hashCode" -> return {
                loadkloxinstance()
                invokevirtual("java/lang/Object", "hashCode", "()I")
                i2d()
                box("java/lang/Double")
                areturn()
            }
        }
        "Array" -> when (functionName) {
            "init" -> return {
                val (error) = labels(1)
                loadkloxinstance()
                dup()

                getkloxfield("\$array", "[Ljava/lang/Object;") {
                    aload_1()
                    checktype("java/lang/Integer", "Array size must be a positive integer.")
                    unbox("java/lang/Double")
                    d2i()
                    dup()
                    iflt(error)
                    anewarray("java/lang/Object")
                }
                pop() // pop the underlying array

                areturn() // return the KloxInstance

                label(error)
                pop()
                throw_("java/lang/RuntimeException", "Array size must be a positive integer.")
            }
            "get" -> return {
                loadkloxinstance()
                getkloxfield("\$array", "[Ljava/lang/Object;")
                aload_1()
                checktype("java/lang/Integer", "Array index must be an integer")
                unbox("java/lang/Double")
                d2i()
                aaload()
                areturn()
            }
            "set" -> return {
                loadkloxinstance()
                getkloxfield("\$array", "[Ljava/lang/Object;")
                aload_1()
                checktype("java/lang/Integer", "Array index must be an integer")
                unbox("java/lang/Double")
                d2i()
                aload_2()
                aastore()
                aconst_null()
                areturn()
            }
            "length" -> return {
                loadkloxinstance()
                getkloxfield("\$array", "[Ljava/lang/Object;")
                arraylength()
                i2d()
                box("java/lang/Double")
                areturn()
            }
            "copy" -> return {
                val (tryStart, tryEnd) = try_ {
                    aload_1()
                    checkcast(KLOX_INSTANCE)
                    getkloxfield("\$array", "[Ljava/lang/Object;")
                    aload_2()
                    checktype("java/lang/Integer", "Source position must be a positive integer.")
                    unbox("java/lang/Double")
                    d2i()
                    aload_3()
                    checkcast(KLOX_INSTANCE)
                    getkloxfield("\$array", "[Ljava/lang/Object;")
                    aload(4)
                    checktype("java/lang/Integer", "Source position must be a positive integer.")
                    unbox("java/lang/Double")
                    d2i()
                    aload(5)
                    checktype("java/lang/Integer", "Source position must be a positive integer.")
                    unbox("java/lang/Double")
                    d2i()
                    invokestatic("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V")
                    TRUE()
                    kloxOk()
                }
                catchAll(tryStart, tryEnd) {
                    kloxError {
                        invokevirtual("java/lang/Throwable", "getMessage", "()Ljava/lang/String;")
                    }
                }
                areturn()
            }
        }
        "Math" -> {
            val math = fun Composer.(composer: Composer.() -> Composer) {
                aload_1()
                boxed("java/lang/Double") {
                    composer()
                }
                areturn()
            }
            when (functionName) {
                "sqrt" -> return {
                    math {
                        invokestatic("java/lang/Math", "sqrt", "(D)D")
                    }
                }
                "ceil" -> return {
                    math {
                        invokestatic("java/lang/Math", "ceil", "(D)D")
                    }
                }
                "floor" -> return {
                    math {
                        invokestatic("java/lang/Math", "floor", "(D)D")
                    }
                }
                "round" -> return {
                    math {
                        invokestatic("java/lang/Math", "round", "(D)J")
                        l2d()
                    }
                }
            }
        }
        "System" -> when (functionName) {
            "clock" -> return {
                invokestatic("java/lang/System", "currentTimeMillis", "()J")
                l2d()
                pushDouble(1000.0)
                ddiv()
                box("java/lang/Double")
                areturn()
            }
            "arg" -> return {
                val (tryStart, tryEnd) = try_ {
                    getstatic("Main", "args", "[Ljava/lang/String;")
                    aload_1()
                    checktype("java/lang/Integer", "arg 'index' parameter should be an integer.")
                    unbox("java/lang/Integer")
                    aaload()
                }
                catch_(tryStart, tryEnd, "java/lang/ArrayIndexOutOfBoundsException") {
                    pop()
                    aconst_null()
                }
                catchAll(tryStart, tryEnd) {
                    kloxError {
                        invokevirtual("java/lang/Throwable", "getMessage", "()Ljava/lang/String;")
                    }
                }
                areturn()
            }
            "exit" -> return {
                aload_1()
                checktype("java/lang/Integer", "arg 'index' parameter should be an integer.")
                unbox("java/lang/Integer")
                invokestatic("java/lang/System", "exit", "(I)V")
                aconst_null()
                areturn()
            }
        }
        "File" -> when (functionName) {
            "delete" -> return {
                val (handler) = labels(1)
                new_("java/io/File")
                dup()
                loadkloxinstance()
                getkloxfield("path", "java/lang/String")
                invokespecial("java/io/File", "<init>", "(Ljava/lang/String;)V")
                val (tryStart, tryEnd) = try_ {
                    invokevirtual("java/io/File", "delete", "()Z")
                    ifeq(handler)
                    TRUE()
                    kloxOk()
                    areturn()

                    label(handler)
                    kloxError("Unknown error deleting file")
                    areturn()
                }
                catchAll(tryStart, tryEnd) {
                    kloxError {
                        invokevirtual("java/lang/Throwable", "getMessage", "()Ljava/lang/String;")
                    }
                }
                areturn()
            }
        }
        "FileInputStream" -> {
            val INPUT_STREAM = "\$is"

            fun createReadMethod(targetClass: ProgramClass): ProgramMethod =
                targetClass.findMethod("_read", "()I") as ProgramMethod?
                    ?: ClassBuilder(targetClass).addAndReturnMethod(PRIVATE, "_read", "()I") {
                        loadkloxinstance()

                        getkloxfield(INPUT_STREAM, "java/io/FileInputStream") {
                            loadkloxinstance()
                            getkloxfield("file", KLOX_INSTANCE)
                            getkloxfield("path", "java/lang/String")
                            new_("java/io/FileInputStream")
                            dup_x1()
                            swap()
                            invokespecial("java/io/FileInputStream", "<init>", "(Ljava/lang/String;)V")
                        }

                        invokevirtual("java/io/InputStream", "read", "()I")
                        ireturn()
                    }

            when (functionName) {
                "readByte" -> return {
                    val (tryStart, tryEnd) = try_ {
                        aload_0()
                        invokevirtual(targetClass, createReadMethod(targetClass))
                        i2d()
                        box("java/lang/Double")
                        kloxOk()
                    }
                    catchAll(tryStart, tryEnd) {
                        kloxError {
                            invokevirtual("java/lang/Throwable", "getMessage", "()Ljava/lang/String;")
                        }
                    }
                    areturn()
                }
                "readChar" -> return {
                    val (tryStart, tryEnd) = try_ {
                        val (nil) = labels(1)
                        aload_0()
                        invokevirtual(targetClass, createReadMethod(targetClass))
                        dup()
                        iflt(nil)
                        i2c()
                        box("java/lang/Character")
                        invokevirtual("java/lang/Character", "toString", "()Ljava/lang/String;")
                        kloxOk()
                        areturn()

                        label(nil)
                        pop()
                        aconst_null()
                        kloxOk()
                    }
                    catchAll(tryStart, tryEnd) {
                        kloxError {
                            invokevirtual("java/lang/Throwable", "getMessage", "()Ljava/lang/String;")
                        }
                    }
                    areturn()
                }
                "close" -> return {
                    closestream(INPUT_STREAM, "java/io/InputStream")
                    kloxOk()
                    areturn()
                }
            }
        }
        "FileOutputStream" -> {
            val OUTPUT_STREAM = "\$os"

            fun write(targetClass: ProgramClass): ProgramMethod =
                targetClass.findMethod("_write", "(I)V") as ProgramMethod?
                    ?: ClassBuilder(targetClass).addAndReturnMethod(PRIVATE, "_write", "(I)V") {
                        loadkloxinstance()

                        getkloxfield(OUTPUT_STREAM, "java/io/FileOutputStream") {
                            loadkloxinstance()
                            getkloxfield("file", KLOX_INSTANCE)
                            getkloxfield("path", "java/lang/String")
                            new_("java/io/FileOutputStream")
                            dup_x1()
                            swap()
                            invokespecial("java/io/FileOutputStream", "<init>", "(Ljava/lang/String;)V")
                        }

                        iload_1()
                        invokevirtual("java/io/OutputStream", "write", "(I)V")
                        return_()
                    }

            when (functionName) {
                "writeByte" -> return {
                    val (error) = labels(1)
                    val (tryStart, tryEnd) = try_ {
                        aload_0()
                        aload_1()
                        checktype("java/lang/Integer", "Byte should be an integer between 0 and 255.")
                        unbox("java/lang/Double")
                        d2i()
                        dup()
                        iflt(error)
                        dup()
                        iconst(255)
                        ificmpgt(error)
                        invokevirtual(targetClass, write(targetClass))
                        TRUE()
                        kloxOk()
                    }
                    catchAll(tryStart, tryEnd) {
                        kloxError {
                            invokevirtual("java/lang/Throwable", "getMessage", "()Ljava/lang/String;")
                        }
                    }
                    areturn()

                    label(error)
                    pop()
                    kloxError("Byte should be an integer between 0 and 255.")
                    areturn()
                }
                "writeChar" -> return {
                    val (error) = labels(1)
                    val (tryStart, tryEnd) = try_ {
                        aload_0()
                        aload_1()
                        checktype("java/lang/String", "Parameter should be a single character.")
                        dup()
                        invokevirtual("java/lang/String", "length", "()I")
                        iconst_1()
                        ificmpne(error)
                        iconst_0()
                        invokevirtual("java/lang/String", "charAt", "(I)C")
                        invokevirtual(targetClass, write(targetClass))
                        TRUE()
                        kloxOk()
                    }
                    catchAll(tryStart, tryEnd) {
                        kloxError {
                            invokevirtual("java/lang/Throwable", "getMessage", "()Ljava/lang/String;")
                        }
                    }
                    areturn()

                    label(error)
                    pop()
                    kloxError("Parameter should be a single character.")
                    areturn()
                }
                "close" -> return {
                    closestream(OUTPUT_STREAM, "java/io/OutputStream")
                    kloxOk()
                    areturn()
                }
            }
        }
        "String" -> when (functionName) {
            "length" -> return {
                aload_1()
                stringify()
                checkcast("java/lang/String")
                invokevirtual("java/lang/String", "length", "()I")
                i2d()
                box("java/lang/Double")
                areturn()
            }
            "substr" -> return {
                aload_1()
                stringify()
                checkcast("java/lang/String")
                val (start, end) = try_ {
                    aload_2()
                    checktype("java/lang/Integer", "substr 'start' parameter should be an integer.")
                    unbox("java/lang/Integer")
                    aload_3()
                    checktype("java/lang/Integer", "substr 'end' parameter should be an integer.")
                    unbox("java/lang/Integer")
                    invokevirtual("java/lang/String", "substring", "(II)Ljava/lang/String;")
                    kloxOk()
                }
                catch_(start, end, "java/lang/StringIndexOutOfBoundsException") {
                    pop()
                    kloxError {
                        concat(
                            { ldc("String index out of bounds for '") },
                            { aload_1() },
                            { ldc("': begin ") },
                            { aload_2().stringify() },
                            { ldc(", end ") },
                            { aload_3().stringify() },
                            { ldc(".") }
                        )
                    }
                }
                catchAll(start, end) {
                    kloxError {
                        invokevirtual("java/lang/Throwable", "getMessage", "()Ljava/lang/String;")
                    }
                }
                areturn()
            }
            "toNumber" -> return {
                val (tryStart, tryEnd) = try_ {
                    aload_1()
                    checkcast("java/lang/String")
                    invokestatic("java/lang/Double", "parseDouble", "(Ljava/lang/String;)D")
                    box("java/lang/Double")
                    kloxOk()
                }
                catchAll(tryStart, tryEnd) {
                    pop()
                    kloxError {
                        concat(
                            { ldc("Invalid number '") },
                            { aload_1() },
                            { ldc("'.") }
                        )
                    }
                }
                areturn()
            }
        }
        "Character" -> when (functionName) {
            "fromCharCode" -> return {
                val (tryStart, tryEnd) = try_ {
                    aload_1()
                    checktype("java/lang/Integer", "Parameter should be an integer.")
                    unbox("java/lang/Double")
                    d2i()
                    invokestatic("java/lang/Character", "toString", "(I)Ljava/lang/String;")
                    kloxOk()
                }
                catchAll(tryStart, tryEnd) {
                    kloxError {
                        invokevirtual("java/lang/Throwable", "getMessage", "()Ljava/lang/String;")
                    }
                }
                areturn()
            }
            "toCharCode" -> return {
                val (error) = labels(1)
                aload_1()
                dup()
                instanceof_("java/lang/String")
                ifeq(error)
                checkcast("java/lang/String")
                dup()
                invokevirtual("java/lang/String", "length", "()I")
                iconst_1()
                ificmpne(error)
                iconst_0()
                invokevirtual("java/lang/String", "charAt", "(I)C")
                i2d()
                box("java/lang/Double")
                kloxOk()
                areturn()

                label(error)
                pop()
                kloxError("Parameter should be a single character.")
                areturn()
            }
        }
    }
    return null
}
