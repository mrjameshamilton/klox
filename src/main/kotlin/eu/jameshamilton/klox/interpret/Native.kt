package eu.jameshamilton.klox.interpret

import eu.jameshamilton.klox.interpret.Interpreter.Companion.stringify
import eu.jameshamilton.klox.parse.FunctionStmt
import eu.jameshamilton.klox.parse.Token
import eu.jameshamilton.klox.parse.TokenType.IDENTIFIER
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
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
            "sqrt" -> return fun (_, args): Any =
                if (args.first() is Number) sqrt(args.first() as Double)
                else error("sqrt `n` parameter should be a number")
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
        "File" -> {
            when (functionStmt.name.lexeme) {
                "delete" -> return fun(env, _): Any = try {
                    val file = env.get(Token(IDENTIFIER, "file")) as LoxInstance
                    val path = file.get(Token(IDENTIFIER, "path")) as String
                    if (!File(path).delete()) true
                    else error("Unknown error deleting file")
                } catch (e: Exception) {
                    error(e.message ?: "Unknown error deleting file")
                }
            }
        }
        "FileInputStream" -> {
            fun read(env: Environment): Int {
                val loxInstance = env.get(Token(IDENTIFIER, "this")) as LoxInstance
                val inputStream = if (loxInstance.hasField(Token(IDENTIFIER, "\$is"))) {
                    loxInstance.get(Token(IDENTIFIER, "\$is")) as InputStream
                } else {
                    val file = env.get(Token(IDENTIFIER, "file")) as LoxInstance
                    val path = file.get(Token(IDENTIFIER, "path")) as String
                    val inputStream = FileInputStream(path)
                    loxInstance.set(Token(IDENTIFIER, "\$is"), inputStream)
                    inputStream
                }

                return inputStream.read()
            }
            when (functionStmt.name.lexeme) {
                "readChar" -> return fun(env, _): Any? = try {
                    val i = read(env)
                    if (i == -1) null else i.toChar().toString()
                } catch (e: Exception) {
                    error(e.message ?: "Unknown error reading character")
                }
                "readByte" -> return fun(env, _): Any = try {
                    read(env)
                } catch (e: Exception) {
                    error(e.message ?: "Unknown error reading byte")
                }
                "close" -> return fun(env, _): Any = try {
                    val loxInstance = env.get(Token(IDENTIFIER, "this")) as LoxInstance
                    if (loxInstance.hasField(Token(IDENTIFIER, "\$is"))) {
                        val inputStream: InputStream = loxInstance.get(Token(IDENTIFIER, "\$is")) as InputStream
                        inputStream.close()
                        loxInstance.remove(Token(IDENTIFIER, "\$is"))
                    }
                    true
                } catch (e: Exception) {
                    error(e.message ?: "Unknown error closing file")
                }
            }
        }
        "FileOutputStream" -> {
            fun write(env: Environment, value: Any): Any {
                val loxInstance = env.get(Token(IDENTIFIER, "this")) as LoxInstance
                val outputStream = if (loxInstance.hasField(Token(IDENTIFIER, "\$os"))) {
                    loxInstance.get(Token(IDENTIFIER, "\$os")) as OutputStream
                } else {
                    val file = env.get(Token(IDENTIFIER, "file")) as LoxInstance
                    val path = file.get(Token(IDENTIFIER, "path")) as String
                    val outputStream = FileOutputStream(path)
                    loxInstance.set(Token(IDENTIFIER, "\$os"), outputStream)
                    outputStream
                }

                when (value) {
                    is Number -> outputStream.write(value.toInt())
                    is Char -> outputStream.write(value.code)
                    else -> return error("Parameter should be a number or character")
                }

                return true
            }
            when (functionStmt.name.lexeme) {
                "writeByte" -> return fun (env, args): Any {
                    if (!isKloxInteger(args.first())) return error("Byte should be an integer between 0 and 255.")
                    val i = args.first() as Double
                    if (i > 255 || i < 0) return error("Byte should be an integer between 0 and 255.")
                    return write(env, i)
                }
                "writeChar" -> return fun (env, args): Any {
                    if (args.first() !is String) return error("Parameter should be a single character.")

                    val str = args.first() as String
                    if (str.length > 1) return error("Parameter should be a single character.")
                    return write(env, str.first())
                }
                "close" -> return fun (env, _): Any = try {
                    val loxInstance = env.get(Token(IDENTIFIER, "this")) as LoxInstance
                    if (loxInstance.hasField(Token(IDENTIFIER, "\$os"))) {
                        val outputStream = loxInstance.get(Token(IDENTIFIER, "\$os")) as OutputStream
                        outputStream.close()
                        loxInstance.remove(Token(IDENTIFIER, "\$os"))
                    }
                    true
                } catch (e: Exception) {
                    error(e.message ?: "Unknown error closing file")
                }
            }
        }
        "String" -> when (functionStmt.name.lexeme) {
            "length" -> return fun (_, args): Double { return stringify(interpreter, args.first()).length.toDouble() }
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
