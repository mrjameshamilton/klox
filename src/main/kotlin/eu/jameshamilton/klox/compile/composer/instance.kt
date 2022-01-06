package eu.jameshamilton.klox.compile.composer

import proguard.classfile.editor.CompactCodeAttributeComposer as Composer

fun Composer.instanceof_(type: String): Composer =
    instanceof_(type, null)
