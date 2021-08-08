import TokenType.EOF
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.system.exitProcess

var hadError: Boolean = false
var hadRuntimeError: Boolean = false
val interpreter = Interpreter()

fun main(args: Array<String>) {
    if (args.size > 1) {
        println("Usage: klox [script]")
        exitProcess(64)
    } else if (args.size == 1) {
        runFile(args[0])
    } else {
        runPrompt()
    }
}

fun runPrompt() {
    val input = InputStreamReader(System.`in`)
    val reader = BufferedReader(input)
    while (true) {
        print("> ")
        val line = reader.readLine() ?: break
        run(line)
        hadError = false
    }
}

fun runFile(path: String) {
    run(File(path).readText())
    if (hadError) exitProcess(65)
    if (hadError) exitProcess(75)
}

fun run(code: String) {
    val scanner = Scanner(code)
    val tokens = scanner.scanTokens()
    val parser = Parser(tokens)
    val expr = parser.parse()

    if (hadError || expr == null) return;

    interpreter.interpret(expr)?.let { println(it) }
}

fun error(line: Int, message: String) {
    report(line, "", message)
}

fun error(token: Token, message: String) {
    if (token.type == EOF) {
        report(token.line, " at end", message)
    } else {
        report(token.line, "at '${token.lexeme}'", message)
    }
}

fun runtimeError(error: RuntimeError) {
    System.err.println("${error.message} [line ${error.token.line}]")
    hadRuntimeError = true
}

fun report(line: Int, where: String, message: String) {
    System.err.println("[line $line] Error $where: $message")
    hadError = true
}
