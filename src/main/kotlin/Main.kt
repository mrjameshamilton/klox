import TokenType.EOF
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.system.exitProcess

var hadError: Boolean = false
var hadRuntimeError: Boolean = false

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
    val interpreter = Interpreter()
    while (true) {
        print("> ")
        val line = reader.readLine() ?: break
        run(line, interpreter)
        hadError = false
    }
}

fun runFile(path: String) {
    run(File(path).readText())
    if (hadError) exitProcess(65)
    if (hadError) exitProcess(75)
}

fun run(code: String, interpreter: Interpreter = Interpreter()) {
    hadError = false
    hadRuntimeError = false

    val scanner = Scanner(code)
    val tokens = scanner.scanTokens()
    val parser = Parser(tokens)
    val stmts = parser.parse()

    if (hadError) return

    val checker = Checker()

    checker.check(stmts)

    if (hadError) return

    val resolver = Resolver(interpreter)

    resolver.resolve(stmts)

    if (hadError) return

    try {
        interpreter.interpret(stmts)
    } catch (e : StackOverflowError) {
        System.err.println("Stack overflow.")
        hadRuntimeError = true
    }
}

fun error(line: Int, message: String) {
    report(line, "", message)
}

fun error(token: Token, message: String) {
    if (token.type == EOF) {
        report(token.line, "at end", message)
    } else {
        report(token.line, "at '${token.lexeme}'", message)
    }
}

fun runtimeError(error: RuntimeError) {
    System.err.println("[line ${error.token.line}] ${error.message}")
    hadRuntimeError = true
}

fun report(line: Int, where: String, message: String) {
    System.err.println("[line $line] Error${if (where.isNotBlank()) " " else ""}$where: $message")
    hadError = true
}
