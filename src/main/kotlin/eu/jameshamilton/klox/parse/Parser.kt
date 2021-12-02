package eu.jameshamilton.klox.parse

import eu.jameshamilton.klox.parse.FunctionFlag.*
import eu.jameshamilton.klox.parse.TokenType.AMPERSAND
import eu.jameshamilton.klox.parse.TokenType.AND
import eu.jameshamilton.klox.parse.TokenType.BANG
import eu.jameshamilton.klox.parse.TokenType.BANG_EQUAL
import eu.jameshamilton.klox.parse.TokenType.BANG_QUESTION
import eu.jameshamilton.klox.parse.TokenType.BREAK
import eu.jameshamilton.klox.parse.TokenType.CARET
import eu.jameshamilton.klox.parse.TokenType.CLASS
import eu.jameshamilton.klox.parse.TokenType.COLON
import eu.jameshamilton.klox.parse.TokenType.COMMA
import eu.jameshamilton.klox.parse.TokenType.CONTINUE
import eu.jameshamilton.klox.parse.TokenType.DOT
import eu.jameshamilton.klox.parse.TokenType.ELSE
import eu.jameshamilton.klox.parse.TokenType.EOF
import eu.jameshamilton.klox.parse.TokenType.EQUAL
import eu.jameshamilton.klox.parse.TokenType.EQUAL_EQUAL
import eu.jameshamilton.klox.parse.TokenType.FALSE
import eu.jameshamilton.klox.parse.TokenType.FOR
import eu.jameshamilton.klox.parse.TokenType.FUN
import eu.jameshamilton.klox.parse.TokenType.GREATER
import eu.jameshamilton.klox.parse.TokenType.GREATER_EQUAL
import eu.jameshamilton.klox.parse.TokenType.GREATER_GREATER
import eu.jameshamilton.klox.parse.TokenType.GREATER_GREATER_GREATER
import eu.jameshamilton.klox.parse.TokenType.IDENTIFIER
import eu.jameshamilton.klox.parse.TokenType.IF
import eu.jameshamilton.klox.parse.TokenType.IS
import eu.jameshamilton.klox.parse.TokenType.LEFT_BRACE
import eu.jameshamilton.klox.parse.TokenType.LEFT_BRACKET
import eu.jameshamilton.klox.parse.TokenType.LEFT_PAREN
import eu.jameshamilton.klox.parse.TokenType.LESS
import eu.jameshamilton.klox.parse.TokenType.LESS_EQUAL
import eu.jameshamilton.klox.parse.TokenType.LESS_LESS
import eu.jameshamilton.klox.parse.TokenType.MINUS
import eu.jameshamilton.klox.parse.TokenType.MINUS_MINUS
import eu.jameshamilton.klox.parse.TokenType.NIL
import eu.jameshamilton.klox.parse.TokenType.NUMBER
import eu.jameshamilton.klox.parse.TokenType.OR
import eu.jameshamilton.klox.parse.TokenType.PERCENT
import eu.jameshamilton.klox.parse.TokenType.PIPE
import eu.jameshamilton.klox.parse.TokenType.PLUS
import eu.jameshamilton.klox.parse.TokenType.PLUS_PLUS
import eu.jameshamilton.klox.parse.TokenType.PRINT
import eu.jameshamilton.klox.parse.TokenType.QUESTION_DOT
import eu.jameshamilton.klox.parse.TokenType.RETURN
import eu.jameshamilton.klox.parse.TokenType.RIGHT_BRACE
import eu.jameshamilton.klox.parse.TokenType.RIGHT_BRACKET
import eu.jameshamilton.klox.parse.TokenType.RIGHT_PAREN
import eu.jameshamilton.klox.parse.TokenType.SEMICOLON
import eu.jameshamilton.klox.parse.TokenType.SLASH
import eu.jameshamilton.klox.parse.TokenType.STAR
import eu.jameshamilton.klox.parse.TokenType.STRING
import eu.jameshamilton.klox.parse.TokenType.SUPER
import eu.jameshamilton.klox.parse.TokenType.THIS
import eu.jameshamilton.klox.parse.TokenType.TILDE
import eu.jameshamilton.klox.parse.TokenType.TRUE
import eu.jameshamilton.klox.parse.TokenType.UNDERSCORE
import eu.jameshamilton.klox.parse.TokenType.VAR
import eu.jameshamilton.klox.parse.TokenType.WHILE
import java.util.EnumSet
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
            if (check(FUN, IDENTIFIER)) {
                consume(FUN, "")
                return function(flags = FunctionFlag.empty(), initializer = ::FunctionStmt)
            }
            if (match(VAR)) return varDeclaration()
            return statement()
        } catch (error: ParseError) {
            synchronize()
            return null
        }
    }

    private fun classDeclaration(): Stmt {
        val className = consume(IDENTIFIER, "Expect class name.")
        val superClass = if (match(LESS)) {
            consume(IDENTIFIER, "Expect superclass name.")
            VariableExpr(previous())
        } else if (className.lexeme != "Object") {
            VariableExpr(Token(IDENTIFIER, "Object", previous().line))
        } else null // Only Object has no superclass

        consume(LEFT_BRACE, "Expect '{' before class body.")

        val methods = mutableListOf<FunctionStmt>()
        val classStmt = ClassStmt(className, superClass, methods)
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            val flags = FunctionFlag.empty()
            flags.add(METHOD)
            if (match(CLASS)) flags.add(STATIC)
            methods.add(
                function(flags) { name, body ->
                    if (name.lexeme == "init") flags.add(INITIALIZER)
                    FunctionStmt(name, body, classStmt)
                }
            )
        }

        consume(RIGHT_BRACE, "Expect '}' after class body.")

        return classStmt
    }

    private fun function(flags: EnumSet<FunctionFlag>, initializer: (Token, FunctionExpr) -> FunctionStmt): FunctionStmt =
        initializer(consume(IDENTIFIER, "Expect function name."), functionBody(flags))

    private fun functionBody(flags: EnumSet<FunctionFlag>): FunctionExpr {
        val parameters = if (check(LEFT_PAREN)) {
            val parameters = mutableListOf<Parameter>()
            consume(LEFT_PAREN, "Expected '(' after function name")
            if (!check(RIGHT_PAREN)) {
                do {
                    if (parameters.size >= 255) {
                        error(peek(), "Can't have more than 255 parameters.")
                    }
                    parameters.add(Parameter(consume(IDENTIFIER, "Expect parameter name.")))
                } while (match(COMMA))
            }
            consume(RIGHT_PAREN, "Expect ')' after parameters.")
            parameters
        } else if (flags.contains(METHOD)) {
            flags.add(GETTER)
            emptyList()
        } else if (flags.contains(ANONYMOUS)) {
            throw error(peek(), "Named function not allowed here.")
        } else {
            throw error(peek(), "Expect ')' after parameters.")
        }

        val body: List<Stmt> = if (match(EQUAL)) {
            val singleExprBody = listOf(ReturnStmt(Token(RETURN, "return"), expression()))
            optional(SEMICOLON)
            singleExprBody
        }
        else
        {
            consume(LEFT_BRACE, "Expect '{' before function body.")
            block()
        }

        return FunctionExpr(flags, parameters, body)
    }

    private fun varDeclaration(): Stmt {
        if (match(LEFT_PAREN)) {
            // destructuring declaration e.g. var (a, b) = [1, 2];
            // syntactic sugar for
            //     var temp = [1, 2]; var a = temp.get(0); var b = temp.get(1); temp = null;

            if (check(RIGHT_PAREN)) throw error(peek(), "Expect variable name after '('.")

            val names = mutableListOf<Token>()
            do {
                if (match(UNDERSCORE)) names.add(previous())
                else names.add(consume(IDENTIFIER, "Expect variable name."))
            } while (match(COMMA))

            consume(RIGHT_PAREN, "Expect ')' after variable list.")

            if (match(EQUAL)) {
                val tmp = Token(IDENTIFIER, nameFactory.next())

                val variables: Array<Stmt> = names.mapIndexed { index, name ->
                    if (name.type != UNDERSCORE) {
                        VarStmt(
                            name,
                            CallExpr(
                                GetExpr(VariableExpr(tmp), Token(IDENTIFIER, "get")),
                                Token(LEFT_PAREN, ")"),
                                listOf(LiteralExpr(index.toDouble()))
                            )
                        )
                    } else null
                }.filterNotNull().toTypedArray()

                val declaration = MultiStmt(
                    VarStmt(tmp, expression()),
                    *variables,
                    ExprStmt(AssignExpr(tmp, LiteralExpr(null)))
                )

                consume(SEMICOLON, "Expect ';' after variable declaration.")

                return declaration
            } else throw error(peek(), "Expect '=' with destructuring declaration.")
        } else {
            val varStmts = mutableListOf<Stmt>()
            do {
                val name = consume(IDENTIFIER, "Expect variable name.")
                val initializer = if (match(EQUAL)) expression(allowCommaExpr = false) else null
                varStmts.add(VarStmt(name, initializer))
            } while (match(COMMA))
            consume(SEMICOLON, "Expect ';' after variable declaration.")
            return MultiStmt(*varStmts.toTypedArray())
        }
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

    private fun expression(allowCommaExpr: Boolean = true): Expr = comma(allowCommaExpr)

    private fun comma(allowCommaExpr: Boolean): Expr {
        val expr = assignment()

        if (allowCommaExpr && match(COMMA)) {
            val op = previous()
            val right = expression()
            return BinaryExpr(expr, op, right)
        }

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
        var expr = bitwise()

        while (match(AND)) {
            val operator = previous()
            val right = bitwise()
            expr = LogicalExpr(expr, operator, right)
        }

        return expr
    }

    private fun bitwise(): Expr {
        var expr = equality()

        while (match(PIPE, AMPERSAND, CARET)) {
            val operator = previous()
            val right = equality()
            expr = BinaryExpr(expr, operator, right)
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
        var expr = instance()
        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            val op = previous()
            val right = instance()
            expr = BinaryExpr(expr, op, right)
        }
        return expr
    }

    private fun instance(): Expr {
        var expr = shift()
        while (match(IS)) {
            val op = previous()
            val right = shift()
            expr = BinaryExpr(expr, op, right)
        }
        return expr
    }

    private fun shift(): Expr {
        var expr = term()

        while (match(GREATER_GREATER, GREATER_GREATER_GREATER, LESS_LESS)) {
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
        var expr = prefix()
        while (match(SLASH, STAR, PERCENT)) {
            val op = previous()
            val right = prefix()
            expr = BinaryExpr(expr, op, right)
        }
        return expr
    }

    private fun prefix(): Expr {
        if (match(PLUS_PLUS, MINUS_MINUS)) {
            val op = previous()
            val right = primary()
            return UnaryExpr(op, right)
        }

        return unary()
    }

    private fun unary(): Expr {
        if (match(BANG, MINUS, TILDE)) {
            val op = previous()
            val right = unary()
            return UnaryExpr(op, right)
        }

        return postfix()
    }

    private fun postfix(): Expr {
        if (matchNext(PLUS_PLUS, MINUS_MINUS)) {
            // i|++
            // --^
            val op = back()
            // i|++
            // ^
            val left = primary()
            // i|++
            // --^
            advance()
            // i|++|
            // -----^
            return UnaryExpr(op, left, true)
        }

        return call()
    }

    private fun call(): Expr {
        var expr = arrayAccess()

        while (true) expr = when {
            match(LEFT_PAREN) -> finishCall(expr)
            match(DOT) -> GetExpr(expr, consume(IDENTIFIER, "Expect property name after '.'."))
            match(QUESTION_DOT) -> GetExpr(expr, consume(IDENTIFIER, "Expect property name after '?.'."), safeAccess = true)
            match(BANG_QUESTION) -> UnaryExpr(previous(), expr)
            else -> break
        }

        return expr
    }

    private fun arrayAccess(): Expr {
        var expr = primary()

        while (match(LEFT_BRACKET)) {
            var start: Expr = LiteralExpr(null)
            var stop: Expr = LiteralExpr(null)
            var step: Expr = LiteralExpr(null)
            var isSlice = false

            val matchColon = {
                if (match(COLON)) { isSlice = true; true } else false
            }

            if (check(COLON, COLON)) { // [::step] or [::]
                // [::step]
                matchColon(); matchColon()
                if (!check(RIGHT_BRACKET)) step = or()
            } else { // [start:stop:step]
                // start
                if (!matchColon()) start = or()
                // [:stop[:step]]
                if (matchColon()) {
                    if (!matchColon() && !check(RIGHT_BRACKET)) stop = or()
                    if (matchColon()) step = or()
                } else if (!check(RIGHT_BRACKET)) {
                    stop = or()
                    if (matchColon() && !check(RIGHT_BRACKET)) step = or()
                }
            }

            consume(RIGHT_BRACKET, "Expect ']' after index.")

            expr = when {
                isSlice -> CallExpr(GetExpr(expr, Token(IDENTIFIER, "slice")), Token(LEFT_PAREN, "("), listOf(start, stop, step))
                match(EQUAL) -> CallExpr(GetExpr(expr, Token(IDENTIFIER, "set")), Token(LEFT_PAREN, "("), listOf(start, or()))
                else -> CallExpr(GetExpr(expr, Token(IDENTIFIER, "get")), Token(LEFT_PAREN, "("), listOf(start))
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
                arguments.add(expression(allowCommaExpr = false))
            } while (match(COMMA))
        }

        val paren = consume(RIGHT_PAREN, "Expect ')' after arguments.")

        return CallExpr(callee, paren, arguments)
    }

    private fun primary(): Expr = when {
        match(FALSE) -> LiteralExpr(false)
        match(TRUE) -> LiteralExpr(true)
        match(NIL) -> LiteralExpr(null)
        match(NUMBER, STRING) -> LiteralExpr(previous().literal)
        match(LEFT_BRACKET) -> list()
        match(SUPER) -> {
            val keyword = previous()
            consume(DOT, "Expect '.' after 'super'.")
            SuperExpr(
                keyword,
                method = consume(IDENTIFIER, "Expect superclass method name.")
            )
        }
        match(THIS) -> ThisExpr(previous())
        match(IDENTIFIER) -> VariableExpr(previous())
        match(FUN) -> functionBody(EnumSet.of(ANONYMOUS))
        match(LEFT_PAREN) -> {
            val expr = expression()
            consume(RIGHT_PAREN, "Expect ')' after expression.")
            GroupingExpr(expr)
        }
        else -> throw error(peek(), "Expect expression.")
    }

    private fun list(): Expr {
        val elements = mutableListOf<Expr>()

        if (!check(RIGHT_BRACKET)) do {
            if (check(RIGHT_BRACKET)) break // trailing comma
            elements.add(or())
        } while (match(COMMA))

        consume(RIGHT_BRACKET, "Expect ']'.")

        return ArrayExpr(elements.toList())
    }

    private fun optional(type: TokenType): Token? =
        if (check(type)) advance() else null

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
        for (type in types) if (check(type)) {
            advance()
            return true
        }
        return false
    }

    private fun matchNext(vararg types: TokenType): Boolean {
        for (type in types) if (checkNext(type)) {
            advance()
            return true
        }
        return false
    }

    private fun check(type: TokenType, nextType: TokenType? = null): Boolean = when {
        isAtEnd() -> false
        nextType == null -> peek().type == type
        else -> peek().type == type && checkNext(nextType)
    }

    private fun checkNext(type: TokenType): Boolean = when {
        isAtEnd() -> false
        tokens[current + 1].type == EOF -> false
        else -> tokens[current + 1].type == type
    }

    private fun back(): Token {
        if (current > 0) current--
        return tokens[current + 1]
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun isAtEnd() = peek().type == EOF

    private fun peek(): Token = tokens[current]

    private fun previous(): Token = tokens[current - 1]

    class ParseError : RuntimeException()

    companion object {
        private val nameFactory = NameFactory("v")
    }

    private class NameFactory(private val prefix: String) {
        private var index = 0
        fun next(): String {
            index++
            return "$prefix$index"
        }
    }
}
