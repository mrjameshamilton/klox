package eu.jameshamilton.klox.compile.composer

import eu.jameshamilton.klox.compile.addAndReturnMethod
import eu.jameshamilton.klox.programClassPool
import proguard.classfile.AccessConstants
import proguard.classfile.ProgramClass
import proguard.classfile.VersionConstants
import proguard.classfile.editor.ClassBuilder
import proguard.classfile.editor.CompactCodeAttributeComposer as Composer

/**
 * Writes the wrapped instructions in a static util method.
 * The method will be created, if it doesn't already exist (uses the global programClassPool).
 * The stack should contain `stackInputSize` params which are
 * available as the local variables of the static util method.
 * The return value(s) will be placed on the stack after the helper
 * has executed.
 *
 * Example:
 *     * code is generated in method `Util.swap(Object, Object)[LObject`.
 *     * at the location of the original `composer` an invokestatic is generated.
 *
 * <code>
 *     // Stack before: Object A, Object B
 *     with (composer) {
 *         helper("Util", "swap", 2, 2) {
 *              aload_0()
 *              aload_1()
 *              swap()
 *         }
 *     }
 *     // Stack after: Object B, Object A
 * </code>
 */
fun Composer.helper(
    className: String,
    name: String,
    stackInputSize: Int,
    stackResultSize: Int = 1,
    composer: Composer.(Composer) -> Composer
): Composer {
    val utilClass = programClassPool.getClass(className) ?: ClassBuilder(
        VersionConstants.CLASS_VERSION_1_8,
        AccessConstants.PUBLIC,
        className,
        "java/lang/Object"
    ).programClass.apply { programClassPool.addClass(this) } as ProgramClass

    val returnType = when (stackResultSize) {
        0 -> "V"
        1 -> "Ljava/lang/Object;"
        else -> "[Ljava/lang/Object;"
    }

    val descriptor = """(${"Ljava/lang/Object;".repeat(stackInputSize)})$returnType"""
    invokestatic(
        utilClass,
        utilClass.findMethod(name, descriptor) ?: ClassBuilder(utilClass as ProgramClass)
            .addAndReturnMethod(AccessConstants.PUBLIC or AccessConstants.STATIC, name, descriptor) {
                composer(this)
                when (stackResultSize) {
                    0 -> this
                    1 -> areturn()
                    else -> packarray(stackResultSize).areturn()
                }
            }
    )

    if (stackResultSize > 1) unpackarray(stackResultSize)

    return this
}