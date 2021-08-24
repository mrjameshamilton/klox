import TokenType.*
import kotlin.math.exp
import error as errorFun

class Parser(private val tokens: List<Token>) {
    private var current = 0

    fun parse(): List<Stmt> {
        val statements = mutableListOf<Stmt>()
        while (!isAtEnd()) {
            declaration()?.let { statements.add(it) }
        }
        return statements
    }

    private fun declaration(): Stmt? {
        try {
            if (match(VAR)) return varDeclaration()
            return statement()
        } catch (error: ParseError) {
            synchronize()
            return null
        }
    }

    private fun varDeclaration(): Stmt {
        val name = consume(IDENTIFIER, "Expected variable name")
        val initializer = if (match(EQUAL)) expression() else null
        consume(SEMICOLON, "Expected ';' after variable declaration")
        return VarStmt(name, initializer)
    }

    private fun statement(): Stmt {
        if (match(IF)) return ifStatement()
        if (match(PRINT)) return printStatement()
        if (match(WHILE)) return whileStatement()
        if (match(LEFT_BRACE)) return BlockStmt(block())

        return expressionStmt()
    }

    private fun block(): List<Stmt> {
        val stmts = mutableListOf<Stmt>()
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            declaration()?.let { stmts.add(it) }
        }
        consume(RIGHT_BRACE, "Expected '}' after block")
        return stmts
    }

    private fun expressionStmt(): ExprStmt {
        val expr = expression()
        consume(SEMICOLON, "Expected ';' after expression")
        return ExprStmt(expr)
    }

    private fun ifStatement(): IfStmt {
        consume(LEFT_PAREN, "Expected '(' after 'if'")
        val condition = expression()
        consume(RIGHT_PAREN, "Expected ')' after 'if' condition")

        return IfStmt(condition, statement(), if (match(ELSE)) statement() else null)
    }

    private fun printStatement(): PrintStmt {
        val expr = expression()
        consume(SEMICOLON, "Expected ';' after value")
        return PrintStmt(expr)
    }

    private fun whileStatement(): WhileStmt {
        consume(LEFT_PAREN, "Expected '(' after 'while'")
        val condition = expression()
        consume(RIGHT_PAREN, "Expected ')' after 'while' condition")

        return WhileStmt(condition, body = statement())
    }

    private fun expression(): Expr {
        val expr = assignment()

/*        // TODO comma expressions
        while (match(COMMA)) {
            val op = previous()
            val right = expression()
            expr = BinaryExpr(expr, op, right)
        }*/

        return expr
    }

    private fun assignment(): Expr {
        val expr = or()

        if (match(EQUAL)) {
            val equals = previous()
            val value = assignment()

            if (expr is VariableExpr) {
                return AssignExpr(expr.name, value)
            }

            error(equals, "Invalid assignment target")
        }

        return expr
    }

    private fun or(): Expr {
        var expr = and()

        while (match(OR)) {
            val operator = previous()
            val right = and()
            expr = LogicalExpr(expr, operator, right)
        }

        return expr
    }

    private fun and(): Expr {
        var expr = equality()

        while (match(AND)) {
            val operator = previous()
            val right = equality()
            expr = LogicalExpr(expr, operator, right)
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

        if (match(IDENTIFIER))
            return VariableExpr(previous())

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
