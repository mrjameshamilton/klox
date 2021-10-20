package eu.jameshamilton.klox

import eu.jameshamilton.klox.compile.InvokeDynamicCounter
import eu.jameshamilton.klox.util.parse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class InvokeDynamicCounterTest : FunSpec({
    test("No call expressions") {
        val program = """
            print "Hello World";
        """.parse()

        program.statementAccept(InvokeDynamicCounter()).sum() shouldBe 0
    }

    test("Single call expr") {
        val program = """
            fun foo() { return "foo"; }
            print foo();
        """.parse()

        program.statementAccept(InvokeDynamicCounter()).sum() shouldBe 1
    }

    test("Two call expressions") {
        val program = """
            fun foo() { return "foo"; }
            print foo();
            print foo();
        """.parse()

        program.statementAccept(InvokeDynamicCounter()).sum() shouldBe 2
    }

    test("Call expression in loop") {
        val program = """
            fun foo() { bar(); }
            while (foo()) {
                print foo();
                print foo();
            }
        """.parse()

        program.statementAccept(InvokeDynamicCounter()).sum() shouldBe 3
    }

    test("Nested call expr") {
        val program = """
            fun bar() { return "bar"; }
            fun foo() { bar(); }
            print foo();
            print foo();
        """.parse()

        program.statementAccept(InvokeDynamicCounter()).sum() shouldBe 2
    }
})
