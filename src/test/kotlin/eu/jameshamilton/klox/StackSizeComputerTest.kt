package eu.jameshamilton.klox

import eu.jameshamilton.klox.compile.StackSizeComputer
import eu.jameshamilton.klox.parse.FunctionStmt
import eu.jameshamilton.klox.util.parse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class StackSizeComputerTest : FunSpec({
    test("Hello World should produce empty stack") {
        val program = """
            print "Hello World";
        """.parse()

        program.statementAccept(StackSizeComputer()).sum() shouldBe 0
    }

    test("Hello World with var should produce empty stack") {
        val program = """
            var foo = "Hello World";
            print foo;
        """.parse()

        program.statementAccept(StackSizeComputer()).sum() shouldBe 0
    }

    test("Printing add should produce empty stack") {
        val program = """
            var foo = 1 + 1 + 2;
            print foo;
        """.parse()

        program.statementAccept(StackSizeComputer()).sum() shouldBe 0
    }

    test("Printing assign should produce non-empty stack") {
        val program = """
            var foo = (bar = 1);
            print foo;
        """.parse()

        program.statementAccept(StackSizeComputer()).sum() shouldBe 0
    }

    test("Assignment should produce non-empty stack") {
        val program = """
            var bar;
            bar = 1;
        """.parse()

        program.statementAccept(StackSizeComputer()).sum() shouldBe 1
    }

    test("Logical expression should produce non-empty stack") {
        val program = """
            1 or 2;
        """.parse()

        program.statementAccept(StackSizeComputer()).sum() shouldBe 1
    }

    test("If statement with print should produce empty stack") {
        val program = """
            if (true) print "foo";
        """.parse()

        program.statementAccept(StackSizeComputer()).sum() shouldBe 0
    }

    test("Unary should produce non-empty stack") {
        val program = """
            var a = 1;
            -a;
        """.parse()

        program.statementAccept(StackSizeComputer()).sum() shouldBe 1
    }

    test("Print unary should produce empty stack") {
        val program = """
            var a = 1;
            print -a;
        """.parse()

        program.statementAccept(StackSizeComputer()).sum() shouldBe 0
    }

    test("Function definition should produce empty stack") {
        val program = """
            fun foo() { return 1; }
        """.parse()

        program.statementAccept(StackSizeComputer()).sum() shouldBe 0
    }

    test("Function call should produce non-empty stack") {
        val program = """
            fun foo() { return 1; }
            foo();
        """.parse()

        program.statementAccept(StackSizeComputer()).sum() shouldBe 1
    }

    test("Printing a function call should produce an empty stack") {
        val program = """
            fun foo() { return 1; }
            print foo();
        """.parse()

        program.statementAccept(StackSizeComputer()).sum() shouldBe 0
    }

    test("A function call with arguments should produce an non-empty stack") {
        val program = """
            fun foo(a, b) { return a + b; }
            foo(1 + 1, 2);
        """.parse()

        program.statementAccept(StackSizeComputer()).sum() shouldBe 1
    }

    test("Printing a function call with arguments should produce an empty stack") {
        val program = """
            fun foo(a, b) { return a + b; }
            print foo(1 + 1, 2);
        """.parse()

        program.statementAccept(StackSizeComputer()).sum() shouldBe 0
    }

    test("A class definition should produce an empty stack") {
        val program = """
            class Foo { foo() { return 1; } } 
        """.parse()

        program.statementAccept(StackSizeComputer()).sum() shouldBe 0
    }

    test("A class definition with a super class should produce an empty stack") {
        val program = """
            class Super { }
            class Foo < Super { foo() { return 1; } } 
        """.parse()

        program.statementAccept(StackSizeComputer()).sum() shouldBe 0
    }

    test("Setting a field value should not produce an empty stack") {
        val program = """
            class Foo { } 
            foo.test = 1;
        """.parse()

        program.statementAccept(StackSizeComputer()).sum() shouldBe 1
    }

    test("An assignment should produce a non-empty stack") {
        val program = """
            var x = 1;
            x = false;
        """.parse()

        program.statementAccept(StackSizeComputer()).sum() shouldBe 1
    }

    test("Logical short circuiting with assignment should produce a non-empty stack") {
        val program = """
            var a = "before";
            var b = "before";
            (a = false) or
                (b = true) or
                (a = "bad");
            // stacksize: 1
        """.parse()

        program.statementAccept(StackSizeComputer()).sum() shouldBe 1
    }

    test("Void return should produce empty stack") {
        val program = """
            fun foo() {
                return;
            }
        """.parse()

        program.stmts.find { it is FunctionStmt }?.accept(StackSizeComputer()) shouldBe 0
    }

    test("Non-void return should produce empty stack") {
        val program = """
            fun foo() {
                return 1 + 1;
            }
        """.parse()

        program.stmts.find { it is FunctionStmt }?.accept(StackSizeComputer()) shouldBe 0
    }

    test("If should produce empty stack") {
        val program = """
            if (1 + 1) { print "foo"; }
        """.parse()

        program.statementAccept(StackSizeComputer()).sum() shouldBe 0
    }

    test("If-else should produce empty stack") {
        val program = """
            if (1 + 1) {
                print "foo";
            } else {
               print "bar";
            }
        """.parse()

        program.statementAccept(StackSizeComputer()).sum() shouldBe 0
    }
})
