package eu.jameshamilton.klox.parse

enum class TokenType {
    LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE, LEFT_BRACKET, RIGHT_BRACKET,
    COMMA, DOT, MINUS, MINUS_MINUS, PLUS, PLUS_PLUS, COLON, SEMICOLON, SLASH, STAR, STAR_STAR, PERCENT, UNDERSCORE,
    PIPE, AMPERSAND, CARET, TILDE, QUESTION_DOT, BANG_QUESTION,

    BANG, BANG_EQUAL, EQUAL, EQUAL_EQUAL,
    GREATER, GREATER_EQUAL, LESS, LESS_EQUAL,

    LESS_LESS, GREATER_GREATER, GREATER_GREATER_GREATER,

    IDENTIFIER, STRING, NUMBER,

    AND, CLASS, ELSE, FALSE, FUN, FOR, IF, NIL, OR,
    PRINT, RETURN, SUPER, THIS, TRUE, VAR, WHILE, BREAK, CONTINUE, IS,

    EOF
}

class Token(val type: TokenType, val lexeme: String, val literal: Any? = null, val line: Int = -1)
