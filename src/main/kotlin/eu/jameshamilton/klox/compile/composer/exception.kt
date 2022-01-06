package eu.jameshamilton.klox.compile.composer

import proguard.classfile.editor.CompactCodeAttributeComposer as Composer

fun Composer.try_(composer: Composer.() -> Composer): Pair<Composer.Label, Composer.Label> {
    val (tryStart, tryEnd) = labels(2)
    label(tryStart)
    composer(this)
    label(tryEnd)
    return Pair(tryStart, tryEnd)
}

fun Composer.catch_(start: Composer.Label, end: Composer.Label, type: String, composer: Composer.() -> Composer): Composer {
    val (skip, handler) = labels(2)
    catch_(start, end, handler, type, null)
    goto_(skip)
    label(handler)
    composer(this)
    label(skip)
    return this
}

fun Composer.catchAll(start: Composer.Label, end: Composer.Label, composer: Composer.() -> Composer): Composer {
    val (skip, handler) = labels(2)
    goto_(skip)
    catchAll(start, end, handler)
    label(handler)
    composer(this)
    label(skip)
    return this
}

fun Composer.throw_(type: String, message: String): Composer = throw_(type) { ldc(message) }
fun Composer.throw_(type: String, message: Composer.() -> Composer): Composer {
    message()
    new_(type)
    dup_x1()
    swap()
    invokespecial(type, "<init>", "(Ljava/lang/String;)V")
    athrow()
    return this
}
