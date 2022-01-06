package eu.jameshamilton.klox.compile.composer

import proguard.classfile.editor.CompactCodeAttributeComposer as Composer

fun Composer.anewarray(type: String): Composer =
    anewarray(type, null)

fun Composer.packarray(n: Int, type: String = "java/lang/Object"): Composer {
    iconst(n)
    anewarray(type)
    for (i in 0 until n) {
        dup_x1()
        swap()
        iconst(i)
        swap()
        aastore()
    }
    return this
}

fun Composer.unpackarray(n: Int, action: (Composer.(i: Int) -> Composer)? = null): Composer {
    for (i in 0 until n) {
        if (i != n - 1) dup()
        iconst(i)
        aaload()
        if (action == null) {
            if (i != n - 1) swap()
        } else {
            action.invoke(this, i)
        }
    }
    return this
}
