package eu.jameshamilton.klox.compile.composer

import eu.jameshamilton.klox.debug
import proguard.classfile.editor.CompactCodeAttributeComposer as Composer

fun Composer.println(composer: Composer.() -> Composer): Composer {
    getstatic("java/lang/System", "out", "Ljava/io/PrintStream;")
    composer(this)
    invokevirtual("java/io/PrintStream", "println", "(Ljava/lang/Object;)V")
    return this
}

@Suppress("unused")
fun Composer.printlnerr(composer: Composer.() -> Composer): Composer {
    getstatic("java/lang/System", "err", "Ljava/io/PrintStream;")
    composer(this)
    invokevirtual("java/io/PrintStream", "println", "(Ljava/lang/Object;)V")
    return this
}

@Suppress("unused")
fun Composer.printlndebug(str: String): Composer = if (debug == true) {
    println { ldc(str) }
} else this

@Suppress("unused")
fun Composer.printlnpeek(prefix: String? = null): Composer {
    if (debug != true) return this

    dup()
    getstatic("java/lang/System", "out", "Ljava/io/PrintStream;")
    dup_x1()
    swap()
    if (prefix != null) {
        getstatic("java/lang/System", "out", "Ljava/io/PrintStream;")
        ldc(prefix)
        invokevirtual("java/io/PrintStream", "print", "(Ljava/lang/Object;)V")
    }
    invokevirtual("java/io/PrintStream", "print", "(Ljava/lang/Object;)V")
    ldc(" (stack top at offset ${this.codeLength})")
    invokevirtual("java/io/PrintStream", "println", "(Ljava/lang/Object;)V")
    return this
}
