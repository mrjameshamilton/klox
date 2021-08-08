import TokenType.*
import java.lang.RuntimeException
import error as errorFun

class Parser(private val tokens: List<Token>) {
    private var current = 0

    fun parse(): Expr? {
        return try {
            expression()
        } catch (error: ParseError) {
            null
        }
    }

    private fun expression(): Expr {
        var expr = equality()

        // TODO comma expressions
        while (match(COMMA)) {
            val op = previous()
            val right = expression()
            expr = BinaryExpr(expr, op, right)
        }

        return expr
    }

    private fun equality(): Expr {
        var expr = comparison()
        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            val op = previous()
            val right = comparison()
            expr = BinaryExpr(expr, op, right)
        }
        return expr
    }

    private fun comparison(): Expr {
        var expr = term()
        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            val op = previous()
            val right = term()
            expr = BinaryExpr(expr, op, right)
        }
        return expr
    }

    private fun term(): Expr {
        var expr = factor()
        while (match(MINUS, PLUS)) {
            val op = previous()
            val right = factor()
            expr = BinaryExpr(expr, op, right)
        }
        return expr
    }

    private fun factor(): Expr {
        var expr = unary()
        while (match(SLASH, STAR)) {
            val op = previous()
            val right = unary()
            expr = BinaryExpr(expr, op, right)
        }
        return expr
    }

    private fun unary(): Expr {
        if (match(BANG, MINUS)) {
            val op = previous()
            val right = unary()
            return UnaryExpr(op, right)
        }

        return primary()
    }

    private fun primary(): Expr {
        if (match(FALSE)) return LiteralExpr(false)
        if (match(TRUE)) return LiteralExpr(true)
        if (match(NIL)) return LiteralExpr(null)

        if (match(NUMBER, STRING))
            return LiteralExpr(previous().literal)

        if (match(LEFT_PAREN)) {
            val expr = expression()
            consume(RIGHT_PAREN, "Expected ')' after expression")
            return GroupingExpr(expr)
        }

        throw error(peek(), "Expect expression")
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()

        throw error(peek(), message)
    }

    private fun synchronize() {
        advance()
        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return

            when (peek().type) {
                CLASS, FOR, FUN, IF, PRINT, RETURN, VAR, WHILE -> return
                else -> advance()
            }
        }
        advance()
    }

    private fun error(token: Token, message: String): ParseError {
        errorFun(token, message)
        return ParseError()
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) return false
        return peek().type == type
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun isAtEnd() = peek().type == EOF

    private fun peek(): Token = tokens[current]

    private fun previous(): Token = tokens[current - 1]

    class ParseError : RuntimeException()
}
