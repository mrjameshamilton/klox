package eu.jameshamilton.klox.parse

import eu.jameshamilton.klox.parse.FunctionType.*
import eu.jameshamilton.klox.parse.TokenType.*
import eu.jameshamilton.klox.parse.TokenType.CLASS
import eu.jameshamilton.klox.error as errorFun

class Parser(private val tokens: List<Token>) {
    private var current = 0

    fun parse(): Program {
        val statements = mutableListOf<Stmt>()
        while (!isAtEnd()) {
            declaration()?.let { statements.add(it) }
        }
        return Program(statements)
    }

    private fun declaration(): Stmt? {
        try {
            if (match(CLASS)) return classDeclaration()
            if (match(FUN)) return function(FUNCTION)
            if (match(VAR)) return varDeclaration()
            return statement()
        } catch (error: ParseError) {
            synchronize()
            return null
        }
    }

    private fun classDeclaration(): Stmt {
        val name = consume(IDENTIFIER, "Expect class name.")
        val superClass = if (match(LESS)) {
            consume(IDENTIFIER, "Expect superclass name.")
            VariableExpr(previous())
        } else null

        consume(LEFT_BRACE, "Expect '{' before class body.")

        val methods = mutableListOf<FunctionStmt>()
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            methods.add(function(METHOD))
        }

        consume(RIGHT_BRACE, "Expect '}' after class body.")

        return ClassStmt(name, superClass, methods)
    }

    private fun function(originalKind: FunctionType): FunctionStmt {
        val isClassMethod = check(CLASS)
        if (isClassMethod) consume(CLASS, "")

        val name = consume(IDENTIFIER, "Expect $originalKind name.")
        val parameters = mutableListOf<Parameter>()

        var kind = when {
            isClassMethod -> FunctionType.CLASS
            originalKind == METHOD && name.lexeme == "init" -> INITIALIZER
            else -> originalKind
        }

        if (check(LEFT_PAREN)) {
            consume(LEFT_PAREN, "Expected '(' after $kind name")
            if (!check(RIGHT_PAREN)) {
                do {
                    if (parameters.size >= 255) {
                        error(peek(), "Can't have more than 255 parameters.")
                    }
                    parameters.add(Parameter(consume(IDENTIFIER, "Expect parameter name.")))
                } while (match(COMMA))
            }
            consume(RIGHT_PAREN, "Expect ')' after parameters.")
        } else {
            kind = GETTER
        }

        consume(LEFT_BRACE, "Expect '{' before $kind body.")
        return FunctionStmt(name, kind, parameters, body = block())
    }

    private fun varDeclaration(): Stmt {
        val name = consume(IDENTIFIER, "Expect variable name.")
        val initializer = if (match(EQUAL)) expression() else null
        consume(SEMICOLON, "Expect ';' after variable declaration.")
        return VarStmt(name, initializer)
    }

    private fun statement(): Stmt {
        if (match(FOR)) return forStatement()
        if (match(IF)) return ifStatement()
        if (match(PRINT)) return printStatement()
        if (match(RETURN)) return returnStatement()
        if (match(WHILE)) return whileStatement()
        if (match(BREAK)) return breakStatement()
        if (match(CONTINUE)) return continueStatement()
        if (match(LEFT_BRACE)) return BlockStmt(block())

        return expressionStmt()
    }

    private fun block(): MutableList<Stmt> {
        val stmts = mutableListOf<Stmt>()
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            declaration()?.let { stmts.add(it) }
        }
        consume(RIGHT_BRACE, "Expect '}' after block.")
        return stmts
    }

    private fun expressionStmt(): ExprStmt {
        val expr = expression()
        consume(SEMICOLON, "Expect ';' after expression.")
        return ExprStmt(expr)
    }

    private fun ifStatement(): IfStmt {
        consume(LEFT_PAREN, "Expect '(' after 'if'.")
        val condition = expression()
        consume(RIGHT_PAREN, "Expect ')' after 'if' condition.")

        return IfStmt(condition, statement(), if (match(ELSE)) statement() else null)
    }

    private fun printStatement(): PrintStmt {
        val expr = expression()
        consume(SEMICOLON, "Expect ';' after value.")
        return PrintStmt(expr)
    }

    private fun returnStatement(): ReturnStmt {
        val keyword = previous()
        val value = if (!check(SEMICOLON)) expression(); else null
        consume(SEMICOLON, "Expect ';' after return value.")
        return ReturnStmt(keyword, value)
    }

    private fun whileStatement(): WhileStmt {
        consume(LEFT_PAREN, "Expect '(' after 'while'.")
        val condition = expression()
        consume(RIGHT_PAREN, "Expect ')' after 'while' condition.")

        return WhileStmt(condition, body = statement())
    }

    private fun forStatement(): Stmt {
        // desugar for loops to while loops

        consume(LEFT_PAREN, "Expect '(' after 'for'.")

        val initializer: Stmt? = if (match(SEMICOLON)) null
        else if (match(VAR)) varDeclaration()
        else expressionStmt()

        val condition = if (!check(SEMICOLON)) expression(); else LiteralExpr(true)

        consume(SEMICOLON, "Expect ';' after loop condition.")

        val increment = if (!check(RIGHT_PAREN)) expression(); else null

        consume(RIGHT_PAREN, "Expect ')' after for clauses.")

        var body = statement()

        if (increment != null) {
            body = BlockStmt(listOf(body, ExprStmt(increment)))
        }

        body = WhileStmt(condition, body)

        if (initializer != null) {
            body = BlockStmt(listOf(initializer, body))
        }

        return body
    }

    private fun breakStatement(): Stmt {
        val breakStmt = BreakStmt()
        consume(SEMICOLON, "Expect ';' after 'break'.")
        return breakStmt
    }

    private fun continueStatement(): Stmt {
        val continueStmt = ContinueStmt()
        consume(SEMICOLON, "Expect ';' after 'continue'.")
        return continueStmt
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
            } else if (expr is GetExpr) {
                return SetExpr(expr.obj, expr.name, value)
            }

            error(equals, "Invalid assignment target.")
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

        return call()
    }

    private fun call(): Expr {
        var expr = primary()

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr)
            } else if (match(DOT)) {
                expr = GetExpr(expr, consume(IDENTIFIER, "Expect property name after '.'."))
            } else {
                break
            }
        }

        return expr
    }

    private fun finishCall(callee: Expr): Expr {
        val arguments = mutableListOf<Expr>()

        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size >= 255) {
                    error(peek(), "Can't have more than 255 arguments.")
                }
                arguments.add(expression())
            } while (match(COMMA))
        }

        val paren = consume(RIGHT_PAREN, "Expect ')' after arguments.")

        return CallExpr(callee, paren, arguments)
    }

    private fun primary(): Expr {
        if (match(FALSE)) return LiteralExpr(false)
        if (match(TRUE)) return LiteralExpr(true)
        if (match(NIL)) return LiteralExpr(null)

        if (match(NUMBER, STRING)) {
            return LiteralExpr(previous().literal)
        }

        if (match(SUPER)) {
            val keyword = previous()
            consume(DOT, "Expect '.' after 'super'.")
            return SuperExpr(
                keyword,
                method = consume(IDENTIFIER, "Expect superclass method name.")
            )
        }

        if (match(THIS)) return ThisExpr(previous())

        if (match(IDENTIFIER)) {
            return VariableExpr(previous())
        }

        if (match(LEFT_PAREN)) {
            val expr = expression()
            consume(RIGHT_PAREN, "Expect ')' after expression.")
            return GroupingExpr(expr)
        }

        throw error(peek(), "Expect expression.")
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
