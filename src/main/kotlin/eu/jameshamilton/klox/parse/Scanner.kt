package eu.jameshamilton.klox.parse

import eu.jameshamilton.klox.error
import eu.jameshamilton.klox.parse.TokenType.*
import org.apache.commons.text.StringEscapeUtils.*

class Scanner(private val source: String) {

    private var start = 0
    private var current = 0
    private var line = 1
    private val tokens = mutableListOf<Token>()

    fun scanTokens(): List<Token> {
        while (!isAtEnd()) {
            start = current
            scanToken()
        }

        tokens.add(Token(EOF, "", null, line))
        return tokens
    }

    private fun scanToken() {
        when (val c = advance()) {
            '(' -> addToken(LEFT_PAREN)
            ')' -> addToken(RIGHT_PAREN)
            '{' -> addToken(LEFT_BRACE)
            '}' -> addToken(RIGHT_BRACE)
            '[' -> addToken(LEFT_BRACKET)
            ']' -> addToken(RIGHT_BRACKET)
            ',' -> addToken(COMMA)
            '.' -> addToken(DOT)
            '-' -> addToken(MINUS)
            '+' -> addToken(PLUS)
            ':' -> addToken(COLON)
            ';' -> addToken(SEMICOLON)
            '*' -> addToken(STAR)
            '%' -> addToken(PERCENT)
            '_' -> addToken(UNDERSCORE)
            '!' -> addToken(if (match('=')) BANG_EQUAL else BANG)
            '=' -> addToken(if (match('=')) EQUAL_EQUAL else EQUAL)
            '<' -> addToken(if (match('=')) LESS_EQUAL else LESS)
            '>' -> addToken(if (match('=')) GREATER_EQUAL else GREATER)
            '/' -> {
                if (match('*')) {
                    var depth = 1 // Already at depth 1
                    while (!isAtEnd()) when {
                        match('/', '*') -> depth++
                        match('*', '/') -> if (--depth == 0) break
                        else -> advance()
                    }
                    if (depth != 0) error(line, "Unterminated multi-line comment.")
                } else if (match('/')) {
                    while (peek() != '\n' && !isAtEnd()) advance()
                } else {
                    addToken(SLASH)
                }
            }
            '"' -> string()
            ' ', '\t', '\r' -> { }
            '\n' -> line++
            else -> {
                if (isDigit(c)) number()
                else if (isAlpha(c)) identifier()
                else error(line, "Unexpected character.")
            }
        }
    }

    private fun identifier() {
        while (isAlphaNumeric(peek())) advance()

        addToken(KEYWORDS.getOrDefault(source.substring(start, current), IDENTIFIER))
    }

    private fun isAlpha(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c == '_'
    private fun isDigit(c: Char): Boolean = c in '0'..'9'
    private fun isAlphaNumeric(c: Char) = isAlpha(c) || isDigit(c)

    private fun number() {
        while (isDigit(peek())) advance()

        if (peek() == '.' && isDigit(peekNext())) {
            advance()

            while (isDigit(peek())) advance()
        }

        addToken(NUMBER, source.substring(start, current).toDouble())
    }

    private fun string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++
            advance()
        }

        if (isAtEnd()) {
            error(line, "Unterminated string.")
            return
        }

        advance()

        addToken(STRING, unescapeJava(source.substring(start + 1, current - 1)))
    }

    private fun peek(): Char = if (isAtEnd()) 0.toChar() else source[current]

    private fun peekNext(): Char = if (current + 1 >= source.length) 0.toChar() else source[current + 1]

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[current] != expected) return false

        current++
        return true
    }

    private fun match(expected: Char, expectedNext: Char): Boolean {
        if (isAtEnd()) return false
        if (peek() == expected && peekNext() == expectedNext) {
            current += 2
            return true
        }

        return false
    }

    private fun addToken(type: TokenType) = addToken(type, null)

    private fun addToken(type: TokenType, literal: Any?) =
        tokens.add(Token(type, source.substring(start, current), literal, line))

    private fun advance(): Char = source[current++]

    private fun isAtEnd() = current >= source.length

    companion object {
        val KEYWORDS: Map<String, TokenType> = mapOf(
            "and" to AND,
            "class" to CLASS,
            "else" to ELSE,
            "false" to FALSE,
            "for" to FOR,
            "fun" to FUN,
            "if" to IF,
            "nil" to NIL,
            "or" to OR,
            "print" to PRINT,
            "return" to RETURN,
            "super" to SUPER,
            "this" to THIS,
            "true" to TRUE,
            "var" to VAR,
            "while" to WHILE,
            "break" to BREAK,
            "continue" to CONTINUE,
            "is" to IS
        )
    }
}
