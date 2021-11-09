package eu.jameshamilton.klox.interpret

import eu.jameshamilton.klox.interpret.Interpreter.Companion.stringify
import eu.jameshamilton.klox.parse.FunctionStmt
import eu.jameshamilton.klox.parse.Token
import eu.jameshamilton.klox.parse.TokenType.IDENTIFIER
import java.io.FileInputStream
import java.io.InputStream
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.math.sqrt
import kotlin.system.exitProcess

@ExperimentalContracts
private fun isKloxInteger(index: Any?): Boolean {
    contract {
        returns(true) implies (index is Double)
    }
    return index is Double && index.mod(1.0) == 0.0
}

@OptIn(ExperimentalContracts::class)
fun findNative(interpreter: Interpreter, functionStmt: FunctionStmt): ((Environment, List<Any?>) -> Any?)? {
    val errorClass: LoxClass by lazy {
        interpreter.globals.get(Token(IDENTIFIER, "Error")) as LoxClass
    }

    fun error(message: String) = errorClass.call(interpreter, listOf(message))

    when (functionStmt.classStmt?.name?.lexeme) {
        "Math" -> when (functionStmt.name.lexeme) {
            "sqrt" -> return fun (_, args): Any {
                return if (args.first() is Number) {
                    sqrt(args.first() as Double)
                } else error("sqrt `n` parameter should be a number")
            }
        }
        "System" -> when (functionStmt.name.lexeme) {
            "clock" -> return fun (_, _) { System.currentTimeMillis() / 1000.0 }
            "arg" -> return fun (_, arguments): Any? {
                val index = arguments.first()

                if (!isKloxInteger(index)) return error("arg 'index' parameter should be an integer.")

                return try {
                    interpreter.args[index.toInt()]
                } catch (e: ArrayIndexOutOfBoundsException) {
                    null
                }
            }
            "exit" -> return fun (_, arguments): Any {
                val code = arguments.first()

                if (!isKloxInteger(code)) return error("exit 'code' parameter should be an integer.")

                exitProcess(code.toInt())
            }
        }
        "FileInputStream" -> {
            fun read(env: Environment): Int {
                val loxInstance = env.get(Token(IDENTIFIER, "this")) as LoxInstance
                val inputStream: InputStream = try {
                    loxInstance.get(Token(IDENTIFIER, "\$is")) as InputStream
                } catch (ex: Exception) {
                    val file = env.get(Token(IDENTIFIER, "file")) as LoxInstance
                    val path = file.get(Token(IDENTIFIER, "path")) as String
                    val inputStream = FileInputStream(path)
                    loxInstance.set(Token(IDENTIFIER, "\$is"), inputStream)
                    inputStream
                }
                return inputStream.read()
            }
            when (functionStmt.name.lexeme) {
                "readChar" -> return fun(env, _): Any? = with(read(env)) {
                    return try {
                        if (this == -1) null else this.toChar().toString()
                    } catch (e: Exception) {
                        error(e.message ?: "Unknown error reading character")
                    }
                }
                "readInt" -> return fun(env, _): Any = try {
                    read(env)
                } catch (e: Exception) {
                    error(e.message ?: "Unknown error reading integer")
                }
                "close" -> return fun(env, _) {
                    val loxInstance = env.get(Token(IDENTIFIER, "this")) as LoxInstance
                    val inputStream: InputStream = loxInstance.get(Token(IDENTIFIER, "\$is")) as InputStream
                    inputStream.close()
                    loxInstance.remove(Token(IDENTIFIER, "\$is"))
                }
            }
        }
        "String" -> when (functionStmt.name.lexeme) {
            "strlen" -> return fun (_, args): Double { return stringify(interpreter, args.first()).length.toDouble() }
            "substr" -> return fun (_, args): Any {
                val (str, start, end) = args

                if (!isKloxInteger(start)) return error("substr 'start' parameter should be an integer.")
                if (!isKloxInteger(end)) return error("substr 'end' parameter should be an integer.")

                return try {
                    stringify(interpreter, str).substring(start.toInt(), end.toInt())
                } catch (e: StringIndexOutOfBoundsException) {
                    error(
                        "String index out of bounds for '$str': begin ${stringify(interpreter, start)}, end ${stringify(interpreter, end)}."
                    )
                }
            }
        }
    }

    return null
}
