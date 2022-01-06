package eu.jameshamilton.klox.compile.composer

import proguard.classfile.editor.CompactCodeAttributeComposer as Composer

/**
 * Concatenate strings produced by each composer. Each composer should
 * leave one object on the stack, toString will be called on the object.
 * Primitives are not supported.
 */
fun Composer.concat(vararg composers: Composer.() -> Composer): Composer {
    // TODO invokedynamic
    new_("java/lang/StringBuilder")
    dup()
    invokespecial("java/lang/StringBuilder", "<init>", "()V")
    composers.forEach { composer ->
        composer(this@concat)
        invokevirtual("java/lang/Object", "toString", "()Ljava/lang/String;")
        invokevirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;")
    }
    invokevirtual("java/lang/StringBuilder", "toString", "()Ljava/lang/String;")
    return this
}
