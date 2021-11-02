package eu.jameshamilton.klox.parse

/**
 * KLox `Error` class which indicates an error occurred.
 *
 * Can be used with `is` for error checking e.g.
 *
 * fun foo(a, b) {
 *     if (b == 0) return Error("Cannot divide by zero");
 *     else return a / b;
 * }
 *
 * var result = foo(1, 0);
 *
 * if (result is Error)
 *    print result.message;
 * else
 *    print result;
 */
val errorClass = ClassStmt(
    name = Token(TokenType.IDENTIFIER, "Error"),
    superClass = null,
    methods = listOf(
        FunctionStmt(
            name = Token(TokenType.IDENTIFIER, "init"),
            kind = FunctionType.INITIALIZER,
            params = listOf(Parameter(Token(TokenType.IDENTIFIER, "message"))),
            body = listOf(
                ExprStmt(
                    SetExpr(
                        ThisExpr(Token(TokenType.IDENTIFIER, "this")),
                        Token(TokenType.IDENTIFIER, "message"),
                        VariableExpr(Token(TokenType.IDENTIFIER, "message"))
                    )
                )
            )
        ),
    )
)
