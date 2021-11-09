package eu.jameshamilton.klox.compile

import eu.jameshamilton.klox.compile.Compiler.Companion.KLOX_CALLABLE
import eu.jameshamilton.klox.compile.Compiler.Companion.KLOX_FUNCTION
import eu.jameshamilton.klox.compile.Compiler.Companion.KLOX_INSTANCE
import eu.jameshamilton.klox.compile.Resolver.Companion.slot
import eu.jameshamilton.klox.compile.Resolver.Companion.variables
import eu.jameshamilton.klox.parse.FunctionStmt
import proguard.classfile.AccessConstants.PRIVATE
import proguard.classfile.ProgramClass
import proguard.classfile.ProgramMethod
import proguard.classfile.editor.ClassBuilder
import proguard.classfile.editor.CompactCodeAttributeComposer as Composer

fun findNative(mainFunction: FunctionStmt, functionStmt: FunctionStmt): (Composer.() -> Unit)? {
    // TODO deal with native functions that capture variables
    //      Error is already captured since the default implementation is to return an Error

    fun Composer.error(func: FunctionStmt, messageComposer: Composer.() -> Composer): Composer {
        val errorClass = mainFunction.variables.single { it.name.lexeme == "Error" }
        messageComposer(this)
        aload(func.slot(errorClass)).unbox(errorClass)
        checkcast(KLOX_CALLABLE)
        swap()
        packarray(1)
        invokeinterface(KLOX_CALLABLE, "invoke", "([Ljava/lang/Object;)Ljava/lang/Object;")
        return this
    }

    fun Composer.getkloxfield(name: String, expectedType: String): Composer {
        ldc(name)
        invokevirtual(KLOX_INSTANCE, "get", "(Ljava/lang/String;)Ljava/lang/Object;")
        checkcast(expectedType)
        return this
    }

    when (functionStmt.classStmt?.name?.lexeme) {
        "Math" -> when (functionStmt.name.lexeme) {
            "sqrt" -> return {
                aload_1()
                boxed("java/lang/Double") {
                    invokestatic("java/lang/Math", "sqrt", "(D)D")
                }
                areturn()
            }
        }
        "System" -> when (functionStmt.name.lexeme) {
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
                    error(functionStmt) {
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
        "File" -> when (functionStmt.name.lexeme) {
            "delete" -> return {
                val (handler) = labels(1)
                new_("java/io/File")
                dup()
                aload_0().invokeinterface(KLOX_FUNCTION, "getReceiver", "()L$KLOX_INSTANCE;")
                getkloxfield("path", "java/lang/String")
                invokespecial("java/io/File", "<init>", "(Ljava/lang/String;)V")
                val (tryStart, tryEnd) = try_ {
                    invokevirtual("java/io/File", "delete", "()Z")
                    ifeq(handler)
                    TRUE()
                    areturn()

                    label(handler)
                    error(functionStmt) {
                        ldc("Unknown error deleting file")
                    }
                    areturn()
                }
                catchAll(tryStart, tryEnd) {
                    error(functionStmt) {
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
                        val (read) = labels(1)

                        aload_0().invokeinterface(KLOX_FUNCTION, "getReceiver", "()L$KLOX_INSTANCE;")
                        dup()
                        ldc(INPUT_STREAM)
                        invokevirtual(KLOX_INSTANCE, "hasField", "(Ljava/lang/String;)Z")
                        ifne(read)
                        dup() // dup the receiver for use at label read

                        // create the underlying Java InputStream
                        ldc(INPUT_STREAM)
                        new_("java/io/FileInputStream")
                        dup()
                        aload_0().invokeinterface(KLOX_FUNCTION, "getReceiver", "()L$KLOX_INSTANCE;")
                        getkloxfield("file", KLOX_INSTANCE)
                        getkloxfield("path", "java/lang/String")
                        invokespecial("java/io/FileInputStream", "<init>", "(Ljava/lang/String;)V")
                        // and store it in the instance as a field
                        invokevirtual(KLOX_INSTANCE, "set", "(Ljava/lang/String;Ljava/lang/Object;)V")

                        label(read)
                        getkloxfield(INPUT_STREAM, "java/io/InputStream")
                        invokevirtual("java/io/InputStream", "read", "()I")
                        ireturn()
                    }

            when (functionStmt.name.lexeme) {
                "readByte" -> return {
                    val (tryStart, tryEnd) = try_ {
                        aload_0()
                        invokevirtual(targetClass, createReadMethod(targetClass))
                        i2d()
                        box("java/lang/Double")
                    }
                    catchAll(tryStart, tryEnd) {
                        error(functionStmt) {
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
                        areturn()

                        label(nil)
                        pop()
                        aconst_null()
                    }
                    catchAll(tryStart, tryEnd) {
                        error(functionStmt) {
                            invokevirtual("java/lang/Throwable", "getMessage", "()Ljava/lang/String;")
                        }
                    }
                    areturn()
                }
                "close" -> return {
                    val (close, end) = labels(2)

                    aload_0().invokeinterface(KLOX_FUNCTION, "getReceiver", "()L$KLOX_INSTANCE;")
                    dup()
                    ldc(INPUT_STREAM)
                    invokevirtual(KLOX_INSTANCE, "hasField", "(Ljava/lang/String;)Z")
                    ifne(close)
                    pop()
                    goto_(end)

                    label(close)
                    dup()
                    getkloxfield(INPUT_STREAM, "java/io/InputStream")
                    invokevirtual("java/io/InputStream", "close", "()V")
                    ldc(INPUT_STREAM)
                    invokevirtual(KLOX_INSTANCE, "removeField", "(Ljava/lang/String;)V")

                    label(end)
                    aconst_null()
                    areturn()
                }
            }
        }
        "String" -> when (functionStmt.name.lexeme) {
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
                }
                catch_(start, end, "java/lang/StringIndexOutOfBoundsException") {
                    pop()
                    error(functionStmt) {
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
                    error(functionStmt) {
                        invokevirtual("java/lang/Throwable", "getMessage", "()Ljava/lang/String;")
                    }
                }
                areturn()
            }
        }
    }
    return null
}
