package eu.jameshamilton.klox

import eu.jameshamilton.klox.compile.Compiler
import eu.jameshamilton.klox.compile.Compiler.Companion.KLOX_MAIN_CLASS
import eu.jameshamilton.klox.compile.Compiler.Companion.KLOX_MAIN_FUNCTION
import eu.jameshamilton.klox.compile.contains
import eu.jameshamilton.klox.interpret.Interpreter
import eu.jameshamilton.klox.interpret.Resolver
import eu.jameshamilton.klox.interpret.RuntimeError
import eu.jameshamilton.klox.io.writeJar
import eu.jameshamilton.klox.parse.Checker
import eu.jameshamilton.klox.parse.Parser
import eu.jameshamilton.klox.parse.Program
import eu.jameshamilton.klox.parse.Scanner
import eu.jameshamilton.klox.parse.Token
import eu.jameshamilton.klox.parse.TokenType.EOF
import eu.jameshamilton.klox.util.ClassPoolClassLoader
import eu.jameshamilton.klox.util.readFiles
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.multiple
import kotlinx.cli.optional
import proguard.classfile.ClassPool
import proguard.classfile.ProgramClass
import proguard.classfile.attribute.Attribute.SOURCE_FILE
import proguard.classfile.attribute.SourceFileAttribute
import proguard.classfile.editor.AttributesEditor
import proguard.classfile.editor.ConstantPoolEditor
import proguard.classfile.visitor.ClassPrinter
import java.io.File
import java.io.InputStreamReader
import kotlin.io.path.name
import kotlin.system.exitProcess

val parser = ArgParser("klox")
val script by parser.argument(ArgType.String, description = "Lox Script").optional()
val outJar by parser.option(ArgType.String, shortName = "o", description = "output jar")
val useInterpreter by parser.option(ArgType.Boolean, shortName = "i", description = "use interpreter instead of JVM compiler when executing")
val debug by parser.option(ArgType.Boolean, description = "enable debugging")
val dumpClasses by parser.option(ArgType.Boolean, shortName = "d", description = "dump textual representation of classes (instead of executing)")
val args by parser.option(ArgType.String, shortName = "arg", description = "additional arguments to pass to the klox program").multiple()

var hadError: Boolean = false
var hadRuntimeError: Boolean = false

val programClassPool = ClassPool()

fun main(args: Array<String>) {
    parser.parse(args)
    if (script != null) {
        if (useInterpreter == true) {
            interpret(File(script))
        } else {
            compile(File(script), if (outJar != null) File(outJar) else null)?.let {
                if (outJar == null && dumpClasses == null) run(it)
            }
        }
        if (hadRuntimeError) exitProcess(65)
        if (hadError) exitProcess(75)
    } else {
        if (outJar != null) {
            error(0, "outJar is only applicable when executing a script")
        }

        if (dumpClasses != null) {
            error(0, "dumpClasses is only applicable when executing a script with the compiler")
        }

        if (hadError) exitProcess(75)

        runPrompt()
    }
}

fun runPrompt() {
    val interpreter = Interpreter(args.toTypedArray())
    while (true) {
        print("> ")
        val program = parse(readLine() ?: break)
        val resolver = Resolver(interpreter)
        program?.let {
            it.accept(resolver)
            if (hadError) return@let
            try {
                interpreter.interpret(program.stmts)
            } catch (e: StackOverflowError) {
                System.err.println("Stack overflow.")
                hadRuntimeError = true
            }
            hadError = false
        }
    }
}

private val stdlib by lazy {
    readFiles("/klox/")
        .filter { it.fileName.name.endsWith(".lox") }
        // ensure that Object.lox is loaded first since it defines the base class for all other classes
        .sortedBy { it.fileName.name != "Object.lox" }
        .map {
            val inputStream = InputStreamReader(it.toUri().toURL().openStream())
            val text = inputStream.readText()
            inputStream.close()
            text
        }
        .map { Scanner(it).scanTokens() }
        .map { Parser(it).parse() }
        .toList()
        .reduce { stdlib, lib -> stdlib + lib }
}

fun parse(code: String): Program? {
    hadError = false
    hadRuntimeError = false

    val scanner = Scanner(code)
    val tokens = scanner.scanTokens()
    val parser = Parser(tokens)
    val program = stdlib + parser.parse()

    if (hadError) return null

    val checker = Checker()

    program.accept(checker)

    if (hadError) return null

    return program
}

fun interpret(file: File) {
    val code = file.readText()
    val program = parse(code)
    val interpreter = Interpreter(args.toTypedArray())
    val resolver = Resolver(interpreter)
    program?.let {
        it.accept(resolver)
        if (hadError) return
        try {
            interpreter.interpret(program.stmts)
        } catch (e: StackOverflowError) {
            System.err.println("Stack overflow.")
            hadRuntimeError = true
        }
    }
}

fun compile(file: File, outJar: File? = null): ClassPool? {
    if (debug == true) println("Compiling $file...")
    val code = file.readText()
    val program = parse(code)
    program?.let {
        val programClassPool = Compiler().compile(program)

        programClassPool.classesAccept { clazz ->
            with(ConstantPoolEditor(clazz as ProgramClass)) {
                AttributesEditor(clazz, true).addAttribute(
                    SourceFileAttribute(
                        addUtf8Constant(SOURCE_FILE),
                        addUtf8Constant(file.name)
                    )
                )
            }
        }

        if (outJar != null) writeJar(programClassPool, outJar, "Main")
        if (dumpClasses == true) programClassPool.classesAccept(ClassPrinter())
        return programClassPool
    }
    return null
}

fun run(programClassPool: ClassPool) {
    if (programClassPool.contains("Main")) {
        if (debug == true) println("Executing...")
        val clazzLoader = ClassPoolClassLoader(programClassPool)
        clazzLoader
            .loadClass(KLOX_MAIN_CLASS)
            .declaredMethods
            .single { it.name == KLOX_MAIN_FUNCTION }
            .invoke(null, args.toTypedArray())
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
