package suwayomi.tachidesk.manga.impl.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import mu.KotlinLogging
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import suwayomi.tachidesk.manga.impl.util.storage.use
import java.io.File
import java.io.IOException
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object BytecodeEditor {
    private val logger = KotlinLogging.logger {}

    /**
     * Replace some java class references inside a jar with new ones that behave like Androids
     *
     * @param jarFile The JarFile to replace class references in
     */
    fun fixAndroidClasses(jarFile: File) {
        val nodes = loadClasses(jarFile)
            .mapValues { (className, classFileBuffer) ->
                logger.trace { "Processing class $className" }
                transform(classFileBuffer)
            } + loadNonClasses(jarFile)

        saveAsJar(nodes, jarFile)
    }

    /**
     * Load all classes inside the [jar] [File]
     *
     * @param jar The JarFile to load classes from
     *
     * @return [Map] with class names and [ByteArray]s of bytecode
     */
    private fun loadClasses(jar: File): Map<String, ByteArray> {
        return JarFile(jar).use { jarFile ->
            jarFile.entries()
                .asSequence()
                .mapNotNull {
                    readJar(jarFile, it)
                }
                .toMap()
        }
    }

    /**
     * Get class file in [jar] for [entry]
     *
     * @param jar The jar to get the class from
     * @param entry The entry in the jar
     *
     * @return [Pair] of the class name plus the class [ByteArray], or null if it's not a valid class
     */
    private fun readJar(jar: JarFile, entry: JarEntry): Pair<String, ByteArray>? {
        return try {
            jar.getInputStream(entry).use { stream ->
                if (entry.name.endsWith(".class")) {
                    val bytes = stream.readBytes()
                    if (bytes.size < 4) {
                        // Invalid class size
                        return@use null
                    }
                    val cafebabe = String.format(
                        "%02X%02X%02X%02X",
                        bytes[0],
                        bytes[1],
                        bytes[2],
                        bytes[3]
                    )
                    if (cafebabe.lowercase() != "cafebabe") {
                        // Corrupted class
                        return@use null
                    }

                    getNode(bytes).name to bytes
                } else null
            }
        } catch (e: IOException) {
            logger.error(e) { "Error loading jar file" }
            null
        }
    }

    private fun getNode(bytes: ByteArray): ClassNode {
        val cr = ClassReader(bytes)
        return ClassNode().also { cr.accept(it, ClassReader.EXPAND_FRAMES) }
    }

    /**
     * The path where replacement classes will reside
     */
    private const val replacementPath = "xyz/nulldev/androidcompat/replace"

    /**
     * List of classes that will be replaced
     */
    private val classesToReplace = listOf(
        "java/text/SimpleDateFormat"
    )

    /**
     * Replace direct references to the class, used on places
     * that don't have any other text then the class
     *
     * @return [String] of class or null if [String] was null
     */
    private fun String?.replaceDirectly() = when (this) {
        null -> this
        in classesToReplace -> "$replacementPath/$this"
        else -> this
    }

    /**
     * Replace references to the class, used in places that have
     * other text around the class references
     *
     * @return [String] with  class references replaced,
     *          or null if [String] was null
     */
    private fun String?.replaceIndirectly(): String? {
        var classReference = this
        if (classReference != null) {
            classesToReplace.forEach {
                classReference = classReference?.replace(it, "$replacementPath/$it")
            }
        }
        return classReference
    }

    /**
     * Replace all references to certain classes inside the class file
     * with ones that behave more like Androids
     *
     * @param classfileBuffer Class bytecode to load into ASM for ease of modification
     *
     * @return [ByteArray] with modified bytecode
     */
    private fun transform(classfileBuffer: ByteArray): ByteArray {
        // Read the class and prepare to modify it
        val cr = ClassReader(classfileBuffer)
        val cw = ClassWriter(cr, 0)
        // Modify the class
        cr.accept(
            object : ClassVisitor(Opcodes.ASM5, cw) {
                // Modify field descriptor, for example
                // class MangaYes {
                //     val format = SimpleDateFormat("YYYY-MM-dd")
                // }
                override fun visitField(
                    access: Int,
                    name: String?,
                    desc: String?,
                    signature: String?,
                    cst: Any?
                ): FieldVisitor? {
                    logger.trace { "CLass Field" to "${desc.replaceIndirectly()}: ${cst?.let { it::class.java.simpleName }}: $cst" }
                    return super.visitField(access, name, desc.replaceIndirectly(), signature, cst)
                }

                override fun visit(
                    version: Int,
                    access: Int,
                    name: String?,
                    signature: String?,
                    superName: String?,
                    interfaces: Array<out String>?
                ) {
                    logger.trace { "Visiting $name: $signature: $superName" }
                    super.visit(version, access, name, signature, superName, interfaces)
                }

                // Modify method bytecode, for example
                // class MangaYes {
                //     fun fetchChapterList() {
                //         SimpleDateFormat("YYYY-MM-dd")
                //     }
                // }
                override fun visitMethod(
                    access: Int,
                    name: String,
                    desc: String,
                    signature: String?,
                    exceptions: Array<String?>?
                ): MethodVisitor {
                    logger.trace { "Processing method $name: ${desc.replaceIndirectly()}: $signature" }
                    val mv: MethodVisitor? = super.visitMethod(
                        access, name, desc.replaceIndirectly(), signature, exceptions
                    )
                    return object : MethodVisitor(Opcodes.ASM5, mv) {
                        override fun visitLdcInsn(cst: Any?) {
                            logger.trace { "Ldc" to "${cst?.let { "${it::class.java.simpleName}: $it" }}" }
                            super.visitLdcInsn(cst)
                        }

                        // Replace method type, for example
                        // val format = DateFormat()
                        // fun fetchChapterList() {
                        //     if (format is SimpleDateFormat)
                        // }
                        override fun visitTypeInsn(opcode: Int, type: String?) {
                            logger.trace {
                                "Type" to "$opcode: ${type.replaceDirectly()}"
                            }
                            super.visitTypeInsn(
                                opcode,
                                type.replaceDirectly()
                            )
                        }

                        // Replace method field, for example
                        // fun fetchChapterList() {
                        //     val format = SimpleDateFormat("YYYY-MM-dd")
                        // }
                        override fun visitMethodInsn(
                            opcode: Int,
                            owner: String?,
                            name: String?,
                            desc: String?,
                            itf: Boolean
                        ) {
                            logger.trace {
                                "Method" to "$opcode: ${owner.replaceDirectly()}: $name: ${desc.replaceIndirectly()}"
                            }
                            super.visitMethodInsn(
                                opcode,
                                owner.replaceDirectly(),
                                name,
                                desc.replaceIndirectly(),
                                itf
                            )
                        }

                        // Replace class field call from method, for example
                        // val format = SimpleDateFormat("YYYY-MM-dd")
                        // fun fetchChapterList() {
                        //     format.format(Date())
                        // }
                        override fun visitFieldInsn(
                            opcode: Int,
                            owner: String?,
                            name: String?,
                            desc: String?
                        ) {
                            logger.trace { "Field" to "$opcode: $owner: $name: ${desc.replaceIndirectly()}" }
                            super.visitFieldInsn(opcode, owner, name, desc.replaceIndirectly())
                        }

                        override fun visitInvokeDynamicInsn(
                            name: String?,
                            desc: String?,
                            bsm: Handle?,
                            vararg bsmArgs: Any?
                        ) {
                            logger.trace { "InvokeDynamic" to "$name: $desc" }
                            super.visitInvokeDynamicInsn(name, desc, bsm, *bsmArgs)
                        }
                    }
                }
            },
            0
        )
        return cw.toByteArray()
    }

    /**
     * Load non-class files from the jar, such as icons and the manifest
     *
     * @param [jarFile] The file to load resources from
     *
     * @return [Map] of resources
     */
    private fun loadNonClasses(jarFile: File): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(jarFile.inputStream()).use { stream ->
            var nextEntry: ZipEntry?
            while (stream.nextEntry.also { nextEntry = it } != null) {
                nextEntry?.use(stream) { entry ->
                    // If it ends with class or is a directory ignore it
                    if (!entry.name.endsWith(".class") && !entry.isDirectory) {
                        val bytes = stream.readBytes()
                        entries[entry.name] = bytes
                    }
                }
            }
        }
        return entries
    }

    /**
     * Save jar with modified content
     *
     * @param outBytes [Map] of names and [ByteArray]s of content to save inside the jar
     * @param file JarFile to save to
     */
    private fun saveAsJar(outBytes: Map<String, ByteArray>, file: File) {
        JarOutputStream(file.outputStream()).use { out ->
            outBytes.forEach { (entry, value) ->
                // Append extension to class entries
                out.putNextEntry(
                    ZipEntry(
                        entry + if (entry.contains(".")) "" else ".class"
                    )
                )
                out.write(value)
                out.closeEntry()
            }
        }
    }
}
