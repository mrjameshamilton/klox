package eu.jameshamilton.klox

import eu.jameshamilton.klox.parse.GroupingExpr
import eu.jameshamilton.klox.parse.PrintStmt
import eu.jameshamilton.klox.parse.Token
import eu.jameshamilton.klox.parse.TokenType.IDENTIFIER
import eu.jameshamilton.klox.parse.TokenType.PLUS_PLUS
import eu.jameshamilton.klox.parse.UnaryExpr
import eu.jameshamilton.klox.parse.VariableExpr
import eu.jameshamilton.klox.parse.visitor.AllExprVisitor
import eu.jameshamilton.klox.parse.visitor.GroupingExprSimplifier
import eu.jameshamilton.klox.util.parse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class GroupingExprSimplifierTest : FunSpec({
    test("Unary ++ expressions should be simplified") {
        val right = VariableExpr(Token(IDENTIFIER, "i", null, 1))
        val unaryExpr = UnaryExpr(
            Token(PLUS_PLUS, "++", null, 1),
            GroupingExpr(GroupingExpr(right))
        )
        unaryExpr.accept(GroupingExprSimplifier())
        unaryExpr.right shouldBe right
    }

    test("Unary ++ in print should be simplified") {
        val right = VariableExpr(Token(IDENTIFIER, "i", null, 1))
        val unaryExpr = PrintStmt(
            UnaryExpr(
                Token(PLUS_PLUS, "++", null, 1),
                GroupingExpr(GroupingExpr(right))
            )
        )
        unaryExpr.accept(AllExprVisitor(GroupingExprSimplifier()))
        (unaryExpr.expression as UnaryExpr).right shouldBe right
    }

    test("Unary ++") {
        val program = """
            var foo = 0;
            print ++(foo);
        """.parse()
        program.statementAccept(AllExprVisitor(GroupingExprSimplifier()))
        val print = program.stmts.last()
        print.shouldBeInstanceOf<PrintStmt>()
        print.expression.shouldBeInstanceOf<UnaryExpr>()
        (print.expression as UnaryExpr).right.shouldBeInstanceOf<VariableExpr>()
        ((print.expression as UnaryExpr).right as VariableExpr).name.lexeme shouldBe "foo"
    }
})
