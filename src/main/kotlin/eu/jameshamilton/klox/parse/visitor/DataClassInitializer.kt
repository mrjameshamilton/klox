package eu.jameshamilton.klox.parse.visitor

import eu.jameshamilton.klox.parse.AssignExpr
import eu.jameshamilton.klox.parse.BinaryExpr
import eu.jameshamilton.klox.parse.CallExpr
import eu.jameshamilton.klox.parse.ClassStmt
import eu.jameshamilton.klox.parse.Expr
import eu.jameshamilton.klox.parse.ExprStmt
import eu.jameshamilton.klox.parse.FunctionExpr
import eu.jameshamilton.klox.parse.FunctionStmt
import eu.jameshamilton.klox.parse.GetExpr
import eu.jameshamilton.klox.parse.GroupingExpr
import eu.jameshamilton.klox.parse.IfStmt
import eu.jameshamilton.klox.parse.LiteralExpr
import eu.jameshamilton.klox.parse.LogicalExpr
import eu.jameshamilton.klox.parse.ModifierFlag
import eu.jameshamilton.klox.parse.ModifierFlag.INITIALIZER
import eu.jameshamilton.klox.parse.Parameter
import eu.jameshamilton.klox.parse.ReturnStmt
import eu.jameshamilton.klox.parse.Stmt
import eu.jameshamilton.klox.parse.ThisExpr
import eu.jameshamilton.klox.parse.Token
import eu.jameshamilton.klox.parse.TokenType
import eu.jameshamilton.klox.parse.TokenType.EQUAL_EQUAL
import eu.jameshamilton.klox.parse.TokenType.IDENTIFIER
import eu.jameshamilton.klox.parse.TokenType.IS
import eu.jameshamilton.klox.parse.TokenType.LEFT_PAREN
import eu.jameshamilton.klox.parse.TokenType.OR
import eu.jameshamilton.klox.parse.TokenType.PLUS
import eu.jameshamilton.klox.parse.TokenType.RETURN
import eu.jameshamilton.klox.parse.TokenType.STAR
import eu.jameshamilton.klox.parse.VarStmt
import eu.jameshamilton.klox.parse.VariableExpr

class DataClassInitializer : ClassStmt.Visitor<Unit> {
    override fun visitClassStmt(classStmt: ClassStmt) {
        if (classStmt.modifiers.contains(ModifierFlag.DATA_CLASS)) {
            val init = classStmt.methods.singleOrNull { it.modifiers.contains(INITIALIZER) }

            val parameters = init?.functionExpr?.params ?: emptyList()

            if (classStmt.methods.count { it.name.lexeme == "toString" && it.functionExpr.params.isEmpty() } == 0) {
                var expr: Expr = LiteralExpr("${classStmt.name.lexeme}(")

                for ((index, parameter) in parameters.withIndex()) {
                    expr = BinaryExpr(
                        expr,
                        Token(PLUS, "+"),
                        BinaryExpr(
                            LiteralExpr("${parameter.name.lexeme} = "),
                            Token(PLUS, "+"),
                            BinaryExpr(
                                GetExpr(
                                    ThisExpr(Token(IDENTIFIER, "this")),
                                    parameter.name,
                                    true
                                ),
                                Token(PLUS, "+"),
                                if (index == parameters.size - 1) LiteralExpr(")") else LiteralExpr(", ")
                            )
                        )
                    )
                }

                classStmt.methods.add(
                    FunctionStmt(
                        Token(IDENTIFIER, "toString"),
                        ModifierFlag.empty(),
                        FunctionExpr(
                            emptyList(),
                            listOf(ReturnStmt(Token(RETURN, "return"), expr))
                        ),
                        classStmt
                    )
                )
            }

            if (classStmt.methods.count { it.name.lexeme == "equals" && it.functionExpr.params.size == 1 } == 0) {
                val otherName = Parameter("other")
                var expr: Expr = LiteralExpr(false)
                for (parameter in parameters) {
                    expr = LogicalExpr(
                        expr,
                        Token(OR, "or"),
                        BinaryExpr(
                            GetExpr(
                                ThisExpr(Token(IDENTIFIER, "this")),
                                parameter.name
                            ),
                            Token(EQUAL_EQUAL, "=="),
                            GetExpr(
                                VariableExpr(otherName.name),
                                parameter.name
                            )
                        )
                    )
                }
                expr = LogicalExpr(
                    BinaryExpr(
                        VariableExpr(otherName.name),
                        Token(IS, "is"),
                        VariableExpr(classStmt.name)
                    ),
                    Token(TokenType.AND, "and"),
                    GroupingExpr(expr)
                )
                classStmt.methods.add(
                    FunctionStmt(
                        Token(IDENTIFIER, "equals"),
                        ModifierFlag.empty(),
                        FunctionExpr(
                            listOf(otherName),
                            body = listOf(
                                ReturnStmt(Token(RETURN, "return"), expr)
                            )
                        ),
                        classStmt
                    )
                )
            }

            if (classStmt.methods.count { it.name.lexeme == "hashCode" && it.functionExpr.params.isEmpty() } == 0) {
                val hashCodes: List<Stmt> = parameters.flatMap {
                    listOf(
                        VarStmt(it.name),
                        IfStmt(
                            BinaryExpr(
                                GetExpr(
                                    ThisExpr(Token(IDENTIFIER, "this")),
                                    it.name,
                                    true
                                ),
                                Token(EQUAL_EQUAL, "=="),
                                LiteralExpr(null)
                            ),
                            ExprStmt(AssignExpr(it.name, LiteralExpr(0.0))),
                            IfStmt(
                                BinaryExpr(
                                    GetExpr(
                                        ThisExpr(Token(IDENTIFIER, "this")),
                                        it.name,
                                    ),
                                    Token(IS, "is"),
                                    VariableExpr(Token(IDENTIFIER, "Object"))
                                ),
                                ExprStmt(
                                    AssignExpr(
                                        it.name,
                                        CallExpr(
                                            GetExpr(
                                                GetExpr(
                                                    ThisExpr(Token(IDENTIFIER, "this")),
                                                    it.name,
                                                ),
                                                Token(IDENTIFIER, "hashCode")
                                            ),
                                            Token(LEFT_PAREN, "("),
                                            emptyList()
                                        )
                                    )
                                ),
                                // TODO: need ability to check for numbers / strings / booleans
                                ExprStmt(
                                    AssignExpr(
                                        it.name,
                                        GetExpr(
                                            ThisExpr(Token(IDENTIFIER, "this")),
                                            it.name,
                                        ),
                                    )
                                )
                            )
                        )
                    )
                }

                var expr: Expr = LiteralExpr(1.0)
                for (parameter in parameters) {
                    expr =
                        BinaryExpr(
                            BinaryExpr(
                                LiteralExpr(31.0),
                                Token(STAR, "*"),
                                expr
                            ),
                            Token(PLUS, "+"),
                            VariableExpr(parameter.name)
                        )
                }

                classStmt.methods.add(
                    FunctionStmt(
                        Token(IDENTIFIER, "hashCode"),
                        ModifierFlag.empty(),
                        FunctionExpr(
                            emptyList(),
                            hashCodes + listOf(ReturnStmt(Token(RETURN, "return"), expr))
                        ),
                        classStmt
                    )
                )
            }

            if (classStmt.methods.count { it.name.lexeme == "get" && it.functionExpr.params.size == 1 } == 0) {
                val indexParam = Parameter(Token(IDENTIFIER, "index"))
                var ifStmt: Stmt = ReturnStmt(Token(RETURN, "return"), LiteralExpr(null))
                for ((i, parameter) in parameters.withIndex()) {
                    ifStmt = IfStmt(
                        BinaryExpr(
                            VariableExpr(indexParam.name),
                            Token(EQUAL_EQUAL, "=="),
                            LiteralExpr(i.toDouble())
                        ),
                        ReturnStmt(
                            Token(RETURN, "return"),
                            GetExpr(
                                ThisExpr(Token(IDENTIFIER, "this")),
                                parameter.name,
                            )
                        ),
                        ifStmt
                    )
                }

                classStmt.methods.add(
                    FunctionStmt(
                        Token(IDENTIFIER, "get"),
                        ModifierFlag.empty(),
                        FunctionExpr(
                            listOf(indexParam),
                            listOf(ifStmt)
                        ),
                        classStmt
                    )
                )
            }

            // TODO: implement named parameters to allow changing some fields when copying
            if (classStmt.methods.count { it.name.lexeme == "copy" && it.functionExpr.params.isEmpty() } == 0) {
                classStmt.methods.add(
                    FunctionStmt(
                        Token(IDENTIFIER, "copy"),
                        ModifierFlag.empty(),
                        FunctionExpr(
                            emptyList(),
                            listOf(
                                ReturnStmt(
                                    Token(RETURN, "return"),
                                    CallExpr(
                                        VariableExpr(classStmt.name), Token(IDENTIFIER, "("),
                                        parameters.map {
                                            GetExpr(
                                                ThisExpr(Token(IDENTIFIER, "this")),
                                                it.name,
                                            )
                                        }
                                    )
                                )
                            )
                        ),
                        classStmt
                    )
                )
            }
        }
    }
}
