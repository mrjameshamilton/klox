import TokenType.PLUS
import TokenType.STAR
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class ParserTest : FreeSpec({
    "1+1" {
        val scanner = Scanner("1+1")
        val tokens = scanner.scanTokens()
        val parser = Parser(tokens)
        val expr = parser.parse()

        expr shouldBe BinaryExpr(
            LiteralExpr(1.0),
            Token(PLUS, "+", null, 1),
            LiteralExpr(1.0)
        )
    }

    "1+1*3" {
        val scanner = Scanner("1+1*3")
        val tokens = scanner.scanTokens()
        val parser = Parser(tokens)
        val expr = parser.parse()

        expr shouldBe
        BinaryExpr(
            LiteralExpr(1.0),
            Token(PLUS, "+", null, 1),
            BinaryExpr(
                LiteralExpr(1.0),
                Token(STAR, "*", null, 1),
                LiteralExpr(3.0)
            )
        )
    }
})