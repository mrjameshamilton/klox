package eu.jameshamilton.klox.interpret

import eu.jameshamilton.klox.interpret.Interpreter.Companion.stringify
import eu.jameshamilton.klox.parse.Token
import eu.jameshamilton.klox.parse.TokenType.IDENTIFIER
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.lang.RuntimeException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
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
fun findNative(interpreter: Interpreter, className: String?, name: String): ((Environment, List<Any?>) -> Any?)? {
    val errorClass: LoxClass by lazy {
        interpreter.globals.get(Token(IDENTIFIER, "Error")) as LoxClass
    }

    fun kloxError(message: String) = errorClass.call(interpreter, listOf(message))

    when (className) {
        "Array" -> {
            when (name) {
                "init" -> return { env, args ->
                    val loxInstance = env.get(Token(IDENTIFIER, "this")) as LoxInstance
                    val size = args.first()
                    if (!isKloxInteger(size)) throw RuntimeError(Token(IDENTIFIER, "Array"), "Array size must be a positive integer.")
                    val sizeInt = size.toInt()
                    if (size < 0) throw RuntimeError(Token(IDENTIFIER, "Array"), "Array size must be a positive integer.")
                    val array = Array<Any?>(sizeInt) { null }
                    loxInstance.set(Token(IDENTIFIER, "\$array"), array)
                    loxInstance
                }
                "set" -> return { env, args ->
                    try {
                        val loxInstance = env.get(Token(IDENTIFIER, "this")) as LoxInstance

                        @Suppress("UNCHECKED_CAST")
                        val array = loxInstance.get(Token(IDENTIFIER, "\$array")) as Array<Any?>
                        val (index, value) = args
                        if (!isKloxInteger(index)) throw RuntimeError(
                            Token(IDENTIFIER, "Array"),
                            "Array index must be a positive integer."
                        )
                        val indexInt = index.toInt()
                        if (index < 0 || index > array.size) throw RuntimeError(
                            Token(IDENTIFIER, "Array"),
                            "Index $indexInt out of bounds for length ${array.size}"
                        )
                        array[indexInt] = value
                        null
                    } catch (e: Exception) {
                        throw RuntimeError(Token(IDENTIFIER, "Array"), e.message ?: "Array set failed")
                    }
                }
                "get" -> return { env, args ->
                    try {
                        val loxInstance = env.get(Token(IDENTIFIER, "this")) as LoxInstance

                        @Suppress("UNCHECKED_CAST")
                        val array = loxInstance.get(Token(IDENTIFIER, "\$array")) as Array<Any?>
                        val index = args.first()
                        if (!isKloxInteger(index)) throw RuntimeError(Token(IDENTIFIER, "Array"), "Array index must be a positive integer.")
                        val indexInt = index.toInt()
                        if (index < 0 || index > array.size) throw RuntimeError(Token(IDENTIFIER, "Array"), "Index $indexInt out of bounds for length ${array.size}")
                        array[indexInt]
                    } catch (e: Exception) {
                        throw RuntimeError(Token(IDENTIFIER, "Array"), e.message ?: "Array get failed")
                    }
                }
                "length" -> return { env, _ ->
                    val loxInstance = env.get(Token(IDENTIFIER, "this")) as LoxInstance

                    @Suppress("UNCHECKED_CAST")
                    val array = loxInstance.get(Token(IDENTIFIER, "\$array")) as Array<Any?>
                    array.size.toDouble()
                }
                "copy" -> return { _, args ->
                    if (args.first() !is LoxInstance) throw RuntimeError(Token(IDENTIFIER, "Array"), "Source must be an array.")
                    if (!isKloxInteger(args[1])) throw RuntimeError(Token(IDENTIFIER, "Array"), "Source position must be a positive integer.")
                    if (args[2] !is LoxInstance) throw RuntimeError(Token(IDENTIFIER, "Array"), "Destination must be an array.")
                    if (!isKloxInteger(args[3])) throw RuntimeError(Token(IDENTIFIER, "Array"), "Destination position must be a positive integer.")
                    if (!isKloxInteger(args[4])) throw RuntimeError(Token(IDENTIFIER, "Array"), "Length must be a positive integer.")

                    try {
                        val src = args.first() as LoxInstance
                        val srcPos = args[1] as Double
                        val dest = args[2] as LoxInstance
                        val destPos = args[3] as Double
                        val length = args[4] as Double

                        @Suppress("UNCHECKED_CAST")
                        val srcArray = src.get(Token(IDENTIFIER, "\$array")) as Array<Any?>

                        @Suppress("UNCHECKED_CAST")
                        val destArray = dest.get(Token(IDENTIFIER, "\$array")) as Array<Any?>

                        System.arraycopy(srcArray, srcPos.toInt(), destArray, destPos.toInt(), length.toInt())
                        true
                    } catch (e: Exception) {
                        throw RuntimeError(Token(IDENTIFIER, "Array"), "Array copy failed.")
                    }
                }
            }
        }
        "Math" -> when (name) {
            "sqrt" -> return fun (_, args): Any =
                if (args.first() is Number) sqrt(args.first() as Double)
                else kloxError("sqrt `n` parameter should be a number")
            "ceil" -> return fun (_, args): Any =
                if (args.first() is Number) ceil(args.first() as Double)
                else kloxError("ceil `n` parameter should be a number")
            "floor" -> return fun (_, args): Any =
                if (args.first() is Number) floor(args.first() as Double)
                else kloxError("floor `n` parameter should be a number")
            "round" -> return fun (_, args): Any =
                if (args.first() is Number) round(args.first() as Double)
                else kloxError("round `n` parameter should be a number")
        }
        "System" -> when (name) {
            "clock" -> return fun (_, _) { System.currentTimeMillis() / 1000.0 }
            "arg" -> return fun (_, arguments): Any? {
                val index = arguments.first()

                if (!isKloxInteger(index)) return kloxError("arg 'index' parameter should be an integer.")

                return try {
                    interpreter.args[index.toInt()]
                } catch (e: ArrayIndexOutOfBoundsException) {
                    null
                }
            }
            "exit" -> return fun (_, arguments): Any {
                val code = arguments.first()

                if (!isKloxInteger(code)) return kloxError("exit 'code' parameter should be an integer.")

                exitProcess(code.toInt())
            }
        }
        "File" -> {
            when (name) {
                "delete" -> return fun(env, _): Any = try {
                    val file = env.get(Token(IDENTIFIER, "file")) as LoxInstance
                    val path = file.get(Token(IDENTIFIER, "path")) as String
                    if (!File(path).delete()) true
                    else kloxError("Unknown error deleting file")
                } catch (e: Exception) {
                    kloxError(e.message ?: "Unknown error deleting file")
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
            when (name) {
                "readChar" -> return fun(env, _): Any? = try {
                    val i = read(env)
                    if (i == -1) null else i.toChar().toString()
                } catch (e: Exception) {
                    kloxError(e.message ?: "Unknown error reading character")
                }
                "readByte" -> return fun(env, _): Any = try {
                    read(env)
                } catch (e: Exception) {
                    kloxError(e.message ?: "Unknown error reading byte")
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
                    kloxError(e.message ?: "Unknown error closing file")
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
                    else -> return kloxError("Parameter should be a number or character")
                }

                return true
            }
            when (name) {
                "writeByte" -> return fun (env, args): Any {
                    if (!isKloxInteger(args.first())) return kloxError("Byte should be an integer between 0 and 255.")
                    val i = args.first() as Double
                    if (i > 255 || i < 0) return kloxError("Byte should be an integer between 0 and 255.")
                    return write(env, i)
                }
                "writeChar" -> return fun (env, args): Any {
                    if (args.first() !is String) return kloxError("Parameter should be a single character.")

                    val str = args.first() as String
                    if (str.length > 1) return kloxError("Parameter should be a single character.")
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
                    kloxError(e.message ?: "Unknown error closing file")
                }
            }
        }
        "String" -> when (name) {
            "length" -> return fun (_, args): Double { return stringify(interpreter, args.first()).length.toDouble() }
            "substr" -> return fun (_, args): Any {
                val (str, start, end) = args

                if (!isKloxInteger(start)) return kloxError("substr 'start' parameter should be an integer.")
                if (!isKloxInteger(end)) return kloxError("substr 'end' parameter should be an integer.")

                return try {
                    stringify(interpreter, str).substring(start.toInt(), end.toInt())
                } catch (e: StringIndexOutOfBoundsException) {
                    kloxError(
                        "String index out of bounds for '$str': begin ${stringify(interpreter, start)}, end ${stringify(interpreter, end)}."
                    )
                }
            }
            "toNumber" -> return fun (_, args): Any = try {
                (args.first() as String).toDouble()
            } catch (e: Exception) {
                kloxError("Invalid number '${args.first()}'.")
            }
        }
    }

    return null
}
