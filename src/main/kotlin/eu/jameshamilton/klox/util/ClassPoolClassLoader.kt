package eu.jameshamilton.klox.util

import eu.jameshamilton.klox.error
import proguard.classfile.ClassPool
import proguard.classfile.io.ProgramClassWriter
import proguard.classfile.util.ClassUtil.internalClassName
import proguard.classfile.visitor.ProgramClassFilter
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * A ClassLoader that can load classes from a ProGuardCORE ClassPool.
 */
class ClassPoolClassLoader(private val classPool: ClassPool) : ClassLoader() {
    override fun findClass(name: String): Class<*> {
        val clazz = classPool.getClass(internalClassName(name))
        val byteArrayOutputStream = ByteArrayOutputStream()
        try {
            clazz.accept(ProgramClassFilter(ProgramClassWriter(DataOutputStream(byteArrayOutputStream))))
        } catch (e: Exception) {
            error(-1, e.message.toString())
        }
        val bytes = byteArrayOutputStream.toByteArray()
        if (clazz != null) return defineClass(name, bytes, 0, bytes.size)
        return super.findClass(name)
    }
}
