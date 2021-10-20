package eu.jameshamilton.klox.compile

import proguard.classfile.ClassPool

fun ClassPool.contains(className: String) = this.getClass(className) != null
