package eu.jameshamilton.klox.compile.composer

import proguard.classfile.editor.CompactCodeAttributeComposer as Composer

fun Composer.invokedynamic(bootStrapMethodIndex: Int, name: String, descriptor: String): Composer =
    invokedynamic(bootStrapMethodIndex, name, descriptor, null)
