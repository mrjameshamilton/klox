package eu.jameshamilton.klox.compile.composer

import proguard.classfile.util.ClassUtil
import proguard.classfile.editor.CompactCodeAttributeComposer as Composer

fun Composer.box(type: String): Composer {
    boxPrimitiveType(ClassUtil.internalPrimitiveTypeFromNumericClassName(type))
    return this
}

fun Composer.unbox(type: String): Composer {
    unboxPrimitiveType("Ljava/lang/Object;", ClassUtil.internalPrimitiveTypeFromNumericClassName(type).toString())
    return this
}

fun Composer.boxed(type: String, composer: Composer.() -> Composer): Composer {
    unbox(type)
    composer(this)
    box(type)
    return this
}
