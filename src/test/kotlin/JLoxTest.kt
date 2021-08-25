import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class JLoxTest: FunSpec({
    val dir = File(object {}.javaClass.getResource("lox").file)
    val regex = Regex("// expect: (.*)")
    dir.walk().forEach { file ->
        if (file.name.endsWith(".lox")) {
            test(file.path.removePrefix(dir.path + "/").substringBefore(".lox")) {
                val text = file.readText()
                val matches = regex.findAll(text)
                val expected = matches.map { it.groupValues[1] }.joinToString(separator = "\n", postfix = "\n")
                val oldOut = System.out
                val myOut = ByteArrayOutputStream()
                System.setOut(PrintStream(myOut))
                main(arrayOf(file.absolutePath.toString()))
                myOut.toByteArray().decodeToString() shouldBe expected
                System.setOut(oldOut)
            }
        }
    }
})