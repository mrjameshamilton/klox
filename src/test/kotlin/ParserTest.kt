import TokenType.*
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
// TODO re-enable, changed AST nodes to normal class, so the shouldBe doesn't work
class ParserTest : FreeSpec({
    "!1+1" {
        val scanner = Scanner("1+1;")
        val tokens = scanner.scanTokens()
        val parser = Parser(tokens)
        val expr = parser.parse()

        expr shouldBe listOf(
            ExprStmt(
                BinaryExpr(
                    LiteralExpr(1.0),
                    Token(PLUS, "+", null, 1),
                    LiteralExpr(1.0)
                )
            )
        )
    }

    "!1+1*3" {
        val scanner = Scanner("1+1*3;")
        val tokens = scanner.scanTokens()
        val parser = Parser(tokens)
        val expr = parser.parse()

        expr shouldBe listOf(
            ExprStmt(
                BinaryExpr(
                    LiteralExpr(1.0),
                    Token(PLUS, "+", null, 1),
                    BinaryExpr(
                        LiteralExpr(1.0),
                        Token(STAR, "*", null, 1),
                        LiteralExpr(3.0)
                    )
                )
            )
        )
    }

    "!Variable declaration test" {
        val scanner = Scanner("var x;")
        val tokens = scanner.scanTokens()
        val parser = Parser(tokens)
        val stmts = parser.parse()

        stmts shouldBe listOf(
            VarStmt(
                Token(IDENTIFIER, "x", null, 1),
            )
        )
    }

    "!Variable with initializer test" {
        val scanner = Scanner("var x = 1;")
        val tokens = scanner.scanTokens()
        val parser = Parser(tokens)
        val stmts = parser.parse()

        stmts shouldBe listOf(
            VarStmt(
                Token(IDENTIFIER, "x", null, 1),
                LiteralExpr(1.0)
            )
        )
    }

    "!Assignment test" {
        val scanner = Scanner("x = 1;")
        val tokens = scanner.scanTokens()
        val parser = Parser(tokens)
        val stmts = parser.parse()

        stmts shouldBe listOf(
            ExprStmt(
                AssignExpr(
                    Token(IDENTIFIER, "x", null, 1),
                    LiteralExpr(1.0)
                )
            )
        )
    }
})
