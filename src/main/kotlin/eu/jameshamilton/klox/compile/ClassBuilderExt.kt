package eu.jameshamilton.klox.compile

import proguard.classfile.AccessConstants.PUBLIC
import proguard.classfile.Clazz
import proguard.classfile.Method
import proguard.classfile.ProgramClass
import proguard.classfile.VersionConstants.CLASS_VERSION_1_6
import proguard.classfile.attribute.BootstrapMethodInfo
import proguard.classfile.editor.BootstrapMethodsAttributeAdder
import proguard.classfile.editor.ClassBuilder
import proguard.classfile.editor.ConstantPoolEditor
import proguard.classfile.editor.CompactCodeAttributeComposer as Composer

private val EMPTY_CLASS: ProgramClass = ClassBuilder(
    CLASS_VERSION_1_6,
    PUBLIC,
    "EMPTY",
    "java/lang/Object"
).programClass

fun ClassBuilder.addMethod(u2accessFlags: Int, name: String, descriptor: String, composer: Composer.() -> Composer): ClassBuilder {
    // Inefficient, since the composition has to be done twice (first to compute the codeLength) but API is improved.
    // It also means that any code in the composer should not have side-effects, since it will be
    // executed twice!
    val countingComposer = composer(object : Composer(EMPTY_CLASS) {
        override fun label(label: Label): proguard.classfile.editor.CompactCodeAttributeComposer {
            // Adding a label without specifying a maxCodeFragmentSize would
            // normally throw an ArrayIndexOutOfBoundsException.
            // Since this first composer execution is only needed for
            // counting the size, it doesn't matter about adding labels.
            return this
        }

        // Avoid out of bounds exceptions for exception labels.
        override fun catch_(startLabel: Label, endLabel: Label, catchType: String, referencedClass: Clazz?): Composer = this
        override fun catch_(startLabel: Label, endLabel: Label, handlerLabel: Label, catchType: String, referencedClass: Clazz?): Composer = this
        override fun catchAll(startLabel: Label, endLabel: Label): Composer = this
        override fun catchAll(startLabel: Label, endLabel: Label, handlerLabel: Label): Composer = this
    })

    return addMethod(u2accessFlags, name, descriptor, countingComposer.codeLength) {
        composer(it)
    }
}

inline fun <reified T : Method> ClassBuilder.addAndReturnMethod(u2accessFlags: Int, name: String, descriptor: String, noinline composer: Composer.() -> Composer): T {
    addMethod(u2accessFlags, name, descriptor, composer)
    return programClass.findMethod(name, descriptor) as T
}

// TODO how to get bootstrap method ID when needed? invokedynamic(0, "invoke")
fun ClassBuilder.addBootstrapMethod(kind: Int, className: String, name: String, descriptor: String, arguments: (ConstantPoolEditor) -> Array<Int> = { emptyArray() }): ClassBuilder {
    val constantPoolEditor = ConstantPoolEditor(programClass)
    val bootstrapMethodsAttributeAdder = BootstrapMethodsAttributeAdder(programClass)

    val args = arguments(constantPoolEditor)

    val bootstrapMethodInfo = BootstrapMethodInfo(
        constantPoolEditor.addMethodHandleConstant(
            kind,
            constantPoolEditor.addMethodrefConstant(className, name, descriptor, null, null)
        ),
        args.size,
        args.toIntArray()
    )

    bootstrapMethodsAttributeAdder.visitBootstrapMethodInfo(programClass, bootstrapMethodInfo)

    return this
}
