package eu.jameshamilton.klox.compile

import eu.jameshamilton.klox.compile.Resolver.Companion.classStmt
import eu.jameshamilton.klox.compile.Resolver.Companion.slot
import eu.jameshamilton.klox.compile.Resolver.Companion.variables
import eu.jameshamilton.klox.parse.FunctionStmt
import proguard.classfile.editor.CompactCodeAttributeComposer as Composer

fun findNative(mainFunction: FunctionStmt, functionStmt: FunctionStmt): (Composer.() -> Unit)? {
    // TODO deal with native functions that capture variables
    //      Error is already captured since the default implementation is to return an Error

    fun Composer.error(func: FunctionStmt, messageComposer: Composer.() -> Composer): Composer {
        val errorClass = mainFunction.variables.single { it.name.lexeme == "Error" }
        messageComposer(this)
        aload(func.slot(errorClass)).unbox(errorClass)
        checkcast(Compiler.KLOX_CALLABLE)
        swap()
        packarray(1)
        invokeinterface(Compiler.KLOX_CALLABLE, "invoke", "([Ljava/lang/Object;)Ljava/lang/Object;")
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
        }
        "String" -> when (functionStmt.name.lexeme) {
            "strlen" -> return {
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
