package eu.jameshamilton.klox.parse

import eu.jameshamilton.klox.parse.ModifierFlag.*
import eu.jameshamilton.klox.parse.Scanner.Companion.MODIFIER_KEYWORDS
import eu.jameshamilton.klox.parse.TokenType.*
import eu.jameshamilton.klox.parse.visitor.AllClassStmtVisitor
import eu.jameshamilton.klox.parse.visitor.AllExprVisitor
import eu.jameshamilton.klox.parse.visitor.DataClassInitializer
import eu.jameshamilton.klox.parse.visitor.GroupingExprSimplifier
import java.util.EnumSet
import eu.jameshamilton.klox.error as errorFun

class Parser(private val tokens: List<Token>) {
    private var current = 0

    fun parse(): Program {
        val statements = mutableListOf<Stmt>()
        while (!isAtEnd()) {
            declaration()?.let { statements.add(it) }
        }
        return Program(statements).also {
            it.statementAccept(AllExprVisitor(GroupingExprSimplifier()))
            it.statementAccept(AllClassStmtVisitor(DataClassInitializer()))
        }
    }

    private fun modifiers(): EnumSet<ModifierFlag> {
        val modifiers = ModifierFlag.empty()
        while (MODIFIER_KEYWORDS.containsKey(peek().lexeme) && !isAtEnd()) {
            if (modifiers.contains(MODIFIER_KEYWORDS[peek().lexeme])) {
                error(peek(), "Modifier already used.")
            }

            modifiers.add(MODIFIER_KEYWORDS[advance().lexeme])
        }
        return modifiers
    }

    private fun declaration(): Stmt? {
        try {
            val modifiers = modifiers()
            if (match(CLASS)) return classDeclaration(modifiers)
            if (check(FUN, IDENTIFIER)) {
                consume(FUN, "")
                return FunctionStmt(
                    consume(IDENTIFIER, "Expect function name."),
                    modifiers,
                    functionBody(modifiers, functionParameters())
                )
            }
            if (match(VAR)) {
                val varDeclaration = varDeclaration()
                consume(SEMICOLON, "Expect ';' after variable declaration.")
                return varDeclaration
            }
            return statement()
        } catch (error: ParseError) {
            synchronize()
            return null
        }
    }

    private fun classDeclaration(modifiers: EnumSet<ModifierFlag>): Stmt {
        val className = consume(IDENTIFIER, "Expect class name.")
        val initParameters = if (check(LEFT_PAREN)) functionParameters() else null

        var superConstructorCall: Expr? = null
        val superClass = if (match(LESS)) {
            consume(IDENTIFIER, "Expect superclass name.")
            val tempSuper = VariableExpr(previous())
            if (match(LEFT_PAREN)) {
                superConstructorCall = finishCall(
                    SuperExpr(
                        Token(IDENTIFIER, "super", previous().line),
                        Token(IDENTIFIER, "init", previous().line)
                    )
                )
            }
            tempSuper
        } else if (className.lexeme != "Object") {
            VariableExpr(Token(IDENTIFIER, "Object", previous().line))
        } else null // Only Object has no superclass

        val methods = mutableListOf<FunctionStmt>()
        val classStmt = ClassStmt(modifiers, className, superClass, methods)

        initParameters?.run {
            methods += FunctionStmt(
                Token(IDENTIFIER, "init"),
                EnumSet.of(INITIALIZER),
                FunctionExpr(
                    params = this,
                    body = (
                        listOfNotNull(superConstructorCall) + map {
                            SetExpr(
                                ThisExpr(Token(IDENTIFIER, "this")),
                                it.name,
                                VariableExpr(it.name),
                            )
                        }
                        ).map { ExprStmt(it) }
                ),
                classStmt
            )
        }

        if (match(LEFT_BRACE)) {
            while (!check(RIGHT_BRACE) && !isAtEnd()) {
                val methodModifiers = modifiers()
                val name = consume(IDENTIFIER, "Expect function name.")
                if (name.lexeme == "init") {
                    if (methods.count { it.name.lexeme == "init" } > 0) {
                        throw error(name, "A class can only have one initializer.")
                    }

                    methodModifiers.add(INITIALIZER)
                }

                val parameters = if (check(LEFT_PAREN)) functionParameters()
                else emptyList<Parameter>().also { methodModifiers.add(GETTER) }

                methods.add(
                    FunctionStmt(
                        name,
                        methodModifiers,
                        functionBody(methodModifiers, parameters),
                        classStmt
                    )
                )
            }
            consume(RIGHT_BRACE, "Expect '}' after class body.")
        } else optional(SEMICOLON)

        return classStmt
    }

    private fun functionParameters(): List<Parameter> {
        consume(LEFT_PAREN, "Expected '(' after function name")
        return mutableListOf<Parameter>().also {
            if (!check(RIGHT_PAREN)) {
                do {
                    if (it.size >= 255) {
                        error(peek(), "Can't have more than 255 parameters.")
                    }
                    it.add(Parameter(consume(IDENTIFIER, "Expect parameter name.")))
                } while (match(COMMA))
            }
            consume(RIGHT_PAREN, "Expect ')' after parameters.")
        }
    }

    private fun functionBody(modifiers: EnumSet<ModifierFlag>, parameters: List<Parameter>): FunctionExpr = FunctionExpr(
        parameters,
        body = when {
            match(EQUAL) -> listOf(ReturnStmt(Token(RETURN, "return"), expression())).also { optional(SEMICOLON) }
            modifiers.contains(NATIVE) -> consume(SEMICOLON, "Expect ';' after native declaration.").run {
                emptyList()
            }
            else -> consume(LEFT_BRACE, "Expect '{' before function body.").run {
                block()
            }
        }
    )

    // for-in loops are desugared to do-while loops:
    // This helper class is used to keep track of the iterator variable and
    // the variable(s) used in the declaration.
    private class ForInVarStmt(val iteratorExpr: Expr, val isDestructuring: Boolean, val varStmts: List<VarStmt>) :
        MultiVarStmt(varStmts)

    private fun varDeclaration(): MultiVarStmt {
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

            if (match(IN)) {
                return ForInVarStmt(
                    expression(allowCommaExpr = false),
                    isDestructuring = true,
                    names.map { VarStmt(it) }
                )
            } else if (match(EQUAL)) {
                val tempForDestructure = Token(IDENTIFIER, nameFactory.next(), line = previous().line)

                val variables: List<VarStmt> = names.mapIndexed { index, name ->
                    VarStmt(
                        name,
                        if (name.type != UNDERSCORE) CallExpr(
                            GetExpr(VariableExpr(tempForDestructure), Token(IDENTIFIER, "get", line = name.line)),
                            Token(LEFT_PAREN, ")", line = name.line),
                            listOf(LiteralExpr(index.toDouble()))
                        ) else null
                    )
                }

                return MultiVarStmt(listOf(VarStmt(tempForDestructure, expression())) + variables)
            } else throw error(peek(), "Expect '=' with destructuring declaration.")
        } else {
            val varStmts = mutableListOf<VarStmt>()
            do {
                val name = consume(IDENTIFIER, "Expect variable name.")
                val initializer = when {
                    match(EQUAL) -> expression(allowCommaExpr = false)
                    match(IN) -> {
                        if (varStmts.isNotEmpty()) {
                            throw error(previous(), "Only a single variable declaration is allow in for-in loops.")
                        }

                        return ForInVarStmt(
                            expression(allowCommaExpr = false),
                            isDestructuring = false,
                            listOf(VarStmt(name))
                        )
                    }
                    else -> null
                }
                varStmts.add(VarStmt(name, initializer))
            } while (match(COMMA))

            return MultiVarStmt(varStmts)
        }
    }

    private fun statement(): Stmt {
        if (match(FOR)) return forStatement()
        if (match(IF)) return ifStatement()
        if (match(PRINT)) return printStatement()
        if (match(RETURN)) return returnStatement()
        if (match(DO)) return doWhileStatement()
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

    private fun doWhileStatement(): DoWhileStmt {
        consume(LEFT_BRACE, "Expect '{' before 'do'.")
        val body = BlockStmt(block())
        consume(WHILE, "Expect 'while' after do-block.")
        consume(LEFT_PAREN, "Expect '(' after 'while'.")
        val condition = expression()
        consume(RIGHT_PAREN, "Expect ')' after 'while' condition.")
        consume(SEMICOLON, "Expect ';' after do-while condition.")

        return DoWhileStmt(condition, body)
    }

    private fun forStatement(): Stmt {
        // desugar for(-in) loops to while loops

        consume(LEFT_PAREN, "Expect '(' after 'for'.")

        val initializer: Stmt? = if (match(SEMICOLON)) null
        else if (match(VAR)) varDeclaration()
        else expressionStmt()

        if (initializer is ForInVarStmt) {
            consume(RIGHT_PAREN, "Expect ')' after for clauses.")
            val line = previous().line

            val iterator = VarStmt(
                Token(IDENTIFIER, nameFactory.next(), line = line),
                CallExpr(
                    GetExpr(initializer.iteratorExpr, Token(IDENTIFIER, "iterator", line = line)),
                    Token(LEFT_PAREN, ")"),
                    listOf()
                )
            )

            val condition = BinaryExpr(
                CallExpr(
                    GetExpr(VariableExpr(iterator.name), Token(IDENTIFIER, "hasNext", line = line)),
                    Token(LEFT_PAREN, "(", line = line),
                    listOf()
                ),
                Token(EQUAL_EQUAL, "==", line = line),
                LiteralExpr(true)
            )

            var body = statement()

            // Wrap the body in a new body that contains the variable initialization
            body = if (initializer.isDestructuring) {
                /*
                 for (var (a, b) = [1, 2, 3]) { ... }
                     is desugared to
                 {
                     var iterator = [1, 2, 3].iterator()
                     var tempIteratorNext, a, b;
                     do {
                         tempIteratorNext = iterator.next();
                         a = tempIteratorNext.get(0);
                         b = tempIteratorNext.get(1);
                         ...
                     } while (iterator.hasNext())
                 }
                 */
                val tempIteratorNext = VarStmt(Token(IDENTIFIER, nameFactory.next(), line = line))
                val assignments = mutableListOf<Expr>()

                assignments.add(
                    AssignExpr(
                        tempIteratorNext.name,
                        CallExpr(
                            GetExpr(VariableExpr(iterator.name), Token(IDENTIFIER, "next", line = line)),
                            Token(LEFT_PAREN, "(", line = line),
                            listOf()
                        )
                    )
                )

                for ((index, varStmt) in initializer.varStmts.withIndex()) {
                    if (varStmt.name.type != UNDERSCORE) {
                        assignments.add(
                            AssignExpr(
                                varStmt.name,
                                CallExpr(
                                    GetExpr(VariableExpr(tempIteratorNext.name), Token(IDENTIFIER, "get", line = line)),
                                    Token(LEFT_PAREN, ")", line = line),
                                    listOf(LiteralExpr(index.toDouble()))
                                )
                            )
                        )
                    }
                }

                BlockStmt(listOf(tempIteratorNext) + assignments.map { ExprStmt(it) } + listOf(body))
            } else {
                BlockStmt(
                    listOf(
                        ExprStmt(
                            AssignExpr(
                                // assume only one
                                initializer.varStmts.first().name,
                                CallExpr(
                                    GetExpr(VariableExpr(iterator.name), Token(IDENTIFIER, "next", line = line)),
                                    Token(LEFT_PAREN, "(", line = line),
                                    listOf()
                                ),
                            )
                        ),
                        body
                    )
                )
            }

            return BlockStmt(listOf(iterator, initializer, DoWhileStmt(condition, body)))
        } else {
            if (initializer is MultiVarStmt || initializer is VarStmt) {
                consume(SEMICOLON, "Expect ';' after variable declaration.")
            }

            val condition = if (!check(SEMICOLON)) expression(); else LiteralExpr(true)
            consume(SEMICOLON, "Expect ';' after loop condition.")
            val increment: Expr? = if (!check(RIGHT_PAREN)) expression(); else null
            consume(RIGHT_PAREN, "Expect ')' after for clauses.")

            var body = statement()
            if (increment != null) body = BlockStmt(listOf(body, ExprStmt(increment)))
            body = WhileStmt(condition, body)
            if (initializer != null) body = BlockStmt(listOf(initializer, body))

            return body
        }
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
        var expr = range()

        while (match(GREATER_GREATER, GREATER_GREATER_GREATER, LESS_LESS)) {
            val op = previous()
            val right = range()
            expr = BinaryExpr(expr, op, right)
        }

        return expr
    }

    private fun range(): Expr {
        var expr = term()
        while (match(DOT_DOT)) {
            val op = previous()
            val right = factor()
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
        var expr = exponent()
        while (match(SLASH, STAR, PERCENT)) {
            val op = previous()
            val right = exponent()
            expr = BinaryExpr(expr, op, right)
        }
        return expr
    }

    private fun exponent(): Expr {
        var expr = prefix()
        while (match(STAR_STAR)) {
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
        match(FUN) -> {
            if (check(IDENTIFIER)) throw error(peek(), "Named function not allowed here.")
            functionBody(ModifierFlag.empty(), functionParameters())
        }
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
