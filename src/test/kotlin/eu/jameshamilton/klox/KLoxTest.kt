package eu.jameshamilton.klox

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

/**
 * Runs any *.lox files in the resources/lox directory or sub-directories.
 * They can contain expected results as comments:
 *
 * <code>
 * print "foo"; // expect: foo
 * </code>
 *
 * <code>
 * print x; // expect runtime error: [line 1] Undefined variable 'x'.
 * </code>
 *
 * <code>
 * print; // expect error: [line 1] Error at ';': Expect expression.
 * </code>
 *
 * The official lox test suite can be copied into the resources/lox folder.
 * Some official tests such as the benchmark tests are excluded.
 * The lack of "expect *." in some official lox tests is taken into account.
 */
private val regex = Regex("// (.*)")

class KLoxTest : FunSpec({
    val resource = object {}.javaClass.getResource("/lox")
    val dir = File(resource.file)
    dir.walk().forEach { file ->
        if (!file.path.contains("/benchmark/") &&

            !file.path.contains("/expressions/") && // only for AST printing chapter
            !file.path.contains("/scanning/") && // only for expression scanning chapter
            !file.path.contains("field/get_on_class.lox") && // klox implements static methods
            !file.path.contains("super/extra_arguments.lox") && // TODO: need to check stdout / stderr separately

            // klox classes have Object as super class
            !file.path.contains("super/no_superclass_call.lox") &&
            !file.path.contains("super/no_superclass_bind.lox") &&

            !file.path.contains("function/print.lox") && // Lox native functions all print as <native fn>

            // klox supports function expressions
            !file.path.contains("for/fun_in_body.lox") &&
            !file.path.contains("while/fun_in_body.lox") &&
            !file.path.contains("if/fun_in_then.lox") &&
            !file.path.contains("if/fun_in_else.lox") &&

            // klox supports bitwise or
            !file.path.contains("unexpected_character.lox") &&

            // not relevant for klox?
            !file.path.contains("limit/too_many_constants.lox") &&
            !file.path.contains("limit/loop_too_large.lox") &&
            !file.path.contains("limit/no_reuse_constants.lox") &&
            !file.path.contains("limit/too_many_upvalues.lox") &&

            file.path.endsWith(".lox")
        ) {
            val name = file.path.removePrefix(dir.path + "/").substringBefore(".lox")
            test("Interpreter: $name") {
                execute(file) { interpret(file) }
                // withClue("hadError") { hadError shouldBe expectError }
                // withClue("hadRuntimeError") { hadRuntimeError shouldBe expectRuntimeError }
            }
            test("Compiler: $name") {
                execute(file) { compile(file)?.let { run(it) } }
            }
        }
    }
})

fun removeLineInfo(str: String): String {
    return str.lines()
        .joinToString(separator = "\n") { if (it.contains("[line ")) it.substringAfter("] ") else it }
        .trimEnd()
}

fun execute(file: File, executor: (String) -> Unit) {
    val text = file.readText()
    val matches = regex.findAll(text)
    // only include comments that have "expect" or "Error"
    val map = matches.map { it.groupValues[1] }
        .filter { (it.contains("expect") || it.contains("Error at") || it.contains("Error:")) }
    // An error is expected if "error" is in the expected string
    val expectError = map.count { it.contains("expect error") || it.contains("Error at") || it.contains("Error:") } > 0
    val expectRuntimeError = map.count { it.contains("expect runtime error") } > 0

    val expected = if (map.count { it.contains("Error at") || it.contains("[line ") || it.contains("] Error:") } > 0) {
        // Some of the official lox tests don't include the "expect" in the comment;
        // the whole comment is the expected value.
        // Strip the "[c...]" and "[java...]" lines from official tests
        map.filter { !it.contains("[c") && !it.contains("[java") }.joinToString(separator = "\n")
    } else {
        map.map {
            // The klox test suite always uses "expect" with the expected string
            // after the ':'
            it.substringAfter(':').trimStart()
        }.joinToString(separator = "\n")
    }
    val oldOut = System.out
    val oldErr = System.err
    val myOut = ByteArrayOutputStream()
    val myErr = ByteArrayOutputStream()
    System.setOut(PrintStream(myOut))
    System.setErr(PrintStream(myErr))
    shouldNotThrowAny {
        executor(text)
    }
    System.setOut(oldOut)
    System.setErr(oldErr)
    val errText = myErr.toByteArray().decodeToString()
    val outText = myOut.toByteArray().decodeToString()

    if (!(expectError || expectRuntimeError)) {
        errText.trimEnd() shouldBe ""
    }

    if (expected.isNotBlank()) {
        if (expectError || expectRuntimeError) {
            // Discard line numbers from the comparison
            removeLineInfo(errText) shouldContain removeLineInfo(expected)
        } else {
            outText.trimEnd() shouldBe expected
        }
    }
}
