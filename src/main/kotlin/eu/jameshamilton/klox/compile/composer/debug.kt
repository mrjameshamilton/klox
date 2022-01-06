package eu.jameshamilton.klox.compile.composer

import proguard.classfile.attribute.CodeAttribute
import proguard.classfile.editor.CodeAttributeComposer
import proguard.classfile.editor.CompactCodeAttributeComposer

// Useful for Debugging

val CompactCodeAttributeComposer.codeAttribute: CodeAttribute
    get() {
        return javaClass.getDeclaredField("codeAttributeComposer").let { field ->
            field.isAccessible = true
            val codeAttributeComposer = field.get(this) as CodeAttributeComposer
            val code = codeAttributeComposer.javaClass.getDeclaredField("code").let {
                it.isAccessible = true
                it.get(codeAttributeComposer) as ByteArray
            }
            val codeLength = codeAttributeComposer.javaClass.getDeclaredField("codeLength").let {
                it.isAccessible = true
                it.getInt(codeAttributeComposer)
            }
            CodeAttribute(0, 0, 0, codeLength, code, 0, null, 0, null)
        }
    }
