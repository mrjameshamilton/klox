package eu.jameshamilton.klox.composer

import eu.jameshamilton.klox.compile.VariableAllocator
import eu.jameshamilton.klox.compile.addMethod
import eu.jameshamilton.klox.compile.composer.break_
import eu.jameshamilton.klox.compile.composer.case
import eu.jameshamilton.klox.compile.composer.switch
import eu.jameshamilton.klox.util.ClassPoolClassLoader
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import proguard.classfile.AccessConstants.PUBLIC
import proguard.classfile.AccessConstants.STATIC
import proguard.classfile.ClassPool
import proguard.classfile.VersionConstants.CLASS_VERSION_1_6
import proguard.classfile.editor.ClassBuilder
import proguard.classfile.editor.CompactCodeAttributeComposer as Composer

class SwitchComposerTest : FunSpec({
    test("String switch 1") {
        execute {
            ldc("A")
            switch(
                DefaultVariableAllocator(),
                "A" case {
                    ldc("A!")
                    break_()
                },
                "B" case {
                    ldc("B!")
                    break_()
                },
                default = {
                    ldc("default")
                }
            )
            areturn()
        } shouldBe "A!"
    }

    test("String switch 2") {
        execute {
            ldc("B")
            switch(
                DefaultVariableAllocator(),
                "A" case {
                    ldc("A!")
                    break_()
                },
                "B" case {
                    ldc("B!")
                    break_()
                },
                default = {
                    ldc("default")
                }
            )
            areturn()
        } shouldBe "B!"
    }

    test("String switch 3") {
        execute {
            ldc("C")
            switch(
                DefaultVariableAllocator(),
                "A" case {
                    ldc("A!")
                    break_()
                },
                "B" case {
                    ldc("B!")
                    break_()
                },
                default = {
                    ldc("default")
                }
            )
            areturn()
        } shouldBe "default"
    }

    test("String switch fallthrough") {
        execute {
            ldc("A")
            switch(
                DefaultVariableAllocator(),
                "A" case {},
                "B" case {
                    ldc("B!")
                    break_()
                },
                default = {
                    ldc("default")
                }
            )
            areturn()
        } shouldBe "B!"
    }

    test("String switch hashCode clash") {
        execute {
            ldc("Ea")
            switch(
                DefaultVariableAllocator(),
                "FB" case {
                    ldc("FB!")
                    break_()
                },
                "Ea" case {
                    ldc("Ea!")
                    break_()
                },
                default = {
                    ldc("default")
                }
            )
            areturn()
        } shouldBe "Ea!"
    }

    test("String switch hashCode clash 2") {
        execute {
            ldc("FB")
            switch(
                DefaultVariableAllocator(),
                "FB" case {
                    ldc("FB!")
                    break_()
                },
                "Ea" case {
                    ldc("Ea!")
                    break_()
                },
                default = {
                    ldc("default")
                }
            )
            areturn()
        } shouldBe "FB!"
    }

    test("Integer switch 1") {
        execute {
            ldc(1)
            switch(
                DefaultVariableAllocator(),
                1 case {
                    ldc("A!")
                    break_()
                },
                2 case {
                    ldc("B!")
                    break_()
                },
                default = {
                    ldc("default")
                }
            )
            areturn()
        } shouldBe "A!"
    }

    test("Integer switch default") {
        execute {
            ldc(3)
            switch(
                DefaultVariableAllocator(),
                1 case {
                    ldc("A!")
                    break_()
                },
                2 case {
                    ldc("B!")
                    break_()
                },
                default = {
                    ldc("default")
                }
            )
            areturn()
        } shouldBe "default"
    }

    test("Integer switch 2") {
        execute {
            ldc(2)
            switch(
                DefaultVariableAllocator(),
                1 case {
                    ldc("A!")
                    break_()
                },
                2 case {
                    ldc("B!")
                    break_()
                },
                default = {
                    ldc("default")
                }
            )
            areturn()
        } shouldBe "B!"
    }

    test("Integer switch fallthrough") {
        execute {
            ldc(1)
            switch(
                DefaultVariableAllocator(),
                1 case { },
                2 case {
                    ldc("B!")
                    break_()
                },
                default = {
                    ldc("default")
                }
            )
            areturn()
        } shouldBe "B!"
    }
})

private fun execute(block: Composer.(Composer) -> Composer): Any? = with(
    ClassBuilder(CLASS_VERSION_1_6, PUBLIC, "Test", "java/lang/Object")
        .addMethod(PUBLIC or STATIC, "test", "()Ljava/lang/Object;") {
            block(this)
        }.programClass
) {
    return ClassPoolClassLoader(ClassPool().also { it.addClass(this) })
        .loadClass(this.name)
        .declaredMethods
        .single()
        .invoke(null)
}

private class DefaultVariableAllocator : VariableAllocator {
    private var nextIndex = 0

    override fun next(): Int {
        return nextIndex++
    }

    override fun free(index: Int) {
        // Do nothing
    }
}
