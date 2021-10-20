package eu.jameshamilton.klox.util

import eu.jameshamilton.klox.parse.Parser
import eu.jameshamilton.klox.parse.Program
import eu.jameshamilton.klox.parse.Scanner

fun String.parse(): Program = Parser(Scanner(this).scanTokens()).parse()
