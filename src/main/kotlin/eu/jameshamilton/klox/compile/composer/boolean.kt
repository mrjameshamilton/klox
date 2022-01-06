package eu.jameshamilton.klox.compile.composer

import proguard.classfile.editor.CompactCodeAttributeComposer as Composer

fun Composer.TRUE(): Composer =
    getstatic("java/lang/Boolean", "TRUE", "Ljava/lang/Boolean;")

fun Composer.FALSE(): Composer =
    getstatic("java/lang/Boolean", "FALSE", "Ljava/lang/Boolean;")
