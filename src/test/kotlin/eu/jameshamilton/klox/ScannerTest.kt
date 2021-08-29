package eu.jameshamilton.klox

import eu.jameshamilton.klox.TokenType.*
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class ScannerTest : FreeSpec({
    // listeners(listOf(NoSystemErrListener))

    "(()){}" {
        val scanner = Scanner("(()){}")
        scanner.scanTokens() shouldBe listOf(
            Token(LEFT_PAREN, "(", null, 1),
            Token(LEFT_PAREN, "(", null, 1),
            Token(RIGHT_PAREN, ")", null, 1),
            Token(RIGHT_PAREN, ")", null, 1),
            Token(LEFT_BRACE, "{", null, 1),
            Token(RIGHT_BRACE, "}", null, 1),
            Token(EOF, "", null, 1),
        )
    }

    "( )" {
        val scanner = Scanner("( )")
        scanner.scanTokens() shouldBe listOf(
            Token(LEFT_PAREN, "(", null, 1),
            Token(RIGHT_PAREN, ")", null, 1),
            Token(EOF, "", null, 1),
        )
    }

    "With comment" {
        val scanner = Scanner(
            """
            ()
            // comment ()
            ()
            """.trimIndent()
        )
        scanner.scanTokens() shouldBe listOf(
            Token(LEFT_PAREN, "(", null, 1),
            Token(RIGHT_PAREN, ")", null, 1),
            Token(LEFT_PAREN, "(", null, 3),
            Token(RIGHT_PAREN, ")", null, 3),
            Token(EOF, "", null, 3),
        )
    }

    "*!+-/=<> <= == test" {
        val scanner = Scanner("*!+-/=<> <= ==")
        scanner.scanTokens() shouldBe listOf(
            Token(STAR, "*", null, 1),
            Token(BANG, "!", null, 1),
            Token(PLUS, "+", null, 1),
            Token(MINUS, "-", null, 1),
            Token(SLASH, "/", null, 1),
            Token(EQUAL, "=", null, 1),
            Token(LESS, "<", null, 1),
            Token(GREATER, ">", null, 1),
            Token(LESS_EQUAL, "<=", null, 1),
            Token(EQUAL_EQUAL, "==", null, 1),
            Token(EOF, "", null, 1)
        )
    }

    "1*2" {
        val scanner = Scanner("1*2")
        scanner.scanTokens() shouldBe listOf(
            Token(NUMBER, "1", 1.0, 1),
            Token(STAR, "*", null, 1),
            Token(NUMBER, "2", 2.0, 1),
            Token(EOF, "", null, 1)
        )
    }

    "1+1*2" {
        val scanner = Scanner("1+1*2")
        scanner.scanTokens() shouldBe listOf(
            Token(NUMBER, "1", 1.0, 1),
            Token(PLUS, "+", null, 1),
            Token(NUMBER, "1", 1.0, 1),
            Token(STAR, "*", null, 1),
            Token(NUMBER, "2", 2.0, 1),
            Token(EOF, "", null, 1)
        )
    }

    "A string" {
        val scanner = Scanner(
            """
            "string"
            """.trimIndent()
        )
        scanner.scanTokens() shouldBe listOf(
            Token(STRING, """"string"""", "string", 1),
            Token(EOF, "", null, 1)
        )
    }

    "Numbers" - {
        "Single digit" {
            val scanner = Scanner("1")
            scanner.scanTokens() shouldBe listOf(
                Token(NUMBER, "1", 1.0, 1),
                Token(EOF, "", null, 1)
            )
        }

        "With decimal point" {
            val scanner = Scanner("1.1")
            scanner.scanTokens() shouldBe listOf(
                Token(NUMBER, "1.1", 1.1, 1),
                Token(EOF, "", null, 1)
            )
        }
    }

    "Keywords" - {
        "var" {
            val scanner = Scanner("var myVar")
            scanner.scanTokens() shouldBe listOf(
                Token(VAR, "var", null, 1),
                Token(IDENTIFIER, "myVar", null, 1),
                Token(EOF, "", null, 1)
            )
        }
    }
})
