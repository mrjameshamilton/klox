import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class KLoxTest : FunSpec({
    val dir = File(object {}.javaClass.getResource("lox").file)
    val regex = Regex("// expect( (runtime )?error)?: (.*)")
    dir.walk().forEach { file ->
        if (file.name.endsWith(".lox")) {
            test(file.path.removePrefix(dir.path + "/").substringBefore(".lox")) {
                val text = file.readText()
                val matches = regex.findAll(text)
                val expectError = matches.any { it.groupValues[1] == " error" }
                val expectRuntimeError = matches.any { it.groupValues[1] == " runtime error" }
                val expected = matches.map { it.groupValues[3] }.joinToString(separator = "\n")
                val oldOut = System.out
                val oldErr = System.err
                val myOut = ByteArrayOutputStream()
                val myErr = ByteArrayOutputStream()
                System.setOut(PrintStream(myOut))
                System.setErr(PrintStream(myErr))
                run(text)
                System.setOut(oldOut)
                System.setErr(oldErr)
                val errText = myErr.toByteArray().decodeToString()

                if (!(expectError || expectRuntimeError)) {
                    errText.trimEnd() shouldBe ""
                }

                if (expected.isNotBlank()) {
                    if (expectError || expectRuntimeError) {
                        errText.trimEnd() shouldContain expected
                    } else {
                        val outText = myOut.toByteArray().decodeToString()
                        outText.trimEnd() shouldBe expected
                    }
                }

                withClue("hadError") { hadError shouldBe expectError }
                withClue("hadRuntimeError") { hadRuntimeError shouldBe expectRuntimeError }
            }
        }
    }
})
