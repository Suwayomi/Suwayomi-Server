package suwayomi.tachidesk.manga.impl.util

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
    fun fixAndroidClasses(jarFile: File) {
        val nodes = loadClasses(jarFile)
            .mapValues { (className, classFileBuffer) ->
                logger.trace { "Processing class $className" }
                transform(classFileBuffer)
            } + loadNonClasses(jarFile)

        saveAsJar(nodes, jarFile)
    }

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
                    if (cafebabe.toLowerCase() != "cafebabe") {
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

    private const val replacementPath = "xyz/nulldev/androidcompat/replace"
    private const val simpleDateFormat = "java/text/SimpleDateFormat"
    private const val replacementSimpleDateFormat = "$replacementPath/$simpleDateFormat"

    private fun String?.replaceFormatFully() = if (this == simpleDateFormat) {
        replacementSimpleDateFormat
    } else this
    private fun String?.replaceFormat() = this?.replace(simpleDateFormat, replacementSimpleDateFormat)

    private fun transform(classfileBuffer: ByteArray): ByteArray {
        val cr = ClassReader(classfileBuffer)
        val cw = ClassWriter(cr, 0)
        cr.accept(
            object : ClassVisitor(Opcodes.ASM5, cw) {
                override fun visitField(
                    access: Int,
                    name: String?,
                    desc: String?,
                    signature: String?,
                    cst: Any?
                ): FieldVisitor? {
                    logger.trace { "CLass Field" to "${desc.replaceFormat()}: ${cst?.let { it::class.java.simpleName }}: $cst" }
                    return super.visitField(access, name, desc.replaceFormat(), signature, cst)
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

                override fun visitMethod(
                    access: Int,
                    name: String,
                    desc: String,
                    signature: String?,
                    exceptions: Array<String?>?
                ): MethodVisitor {
                    logger.trace { "Processing method $name: ${desc.replaceFormat()}: $signature" }
                    val mv: MethodVisitor? = super.visitMethod(
                        access, name, desc.replaceFormat(), signature, exceptions
                    )
                    return object : MethodVisitor(Opcodes.ASM5, mv) {
                        override fun visitLdcInsn(cst: Any?) {
                            logger.trace { "Ldc" to "${cst?.let { "${it::class.java.simpleName}: $it" }}" }
                            super.visitLdcInsn(cst)
                        }

                        override fun visitTypeInsn(opcode: Int, type: String?) {
                            logger.trace {
                                "Type" to "$opcode: ${type.replaceFormatFully()}"
                            }
                            super.visitTypeInsn(
                                opcode,
                                type.replaceFormatFully()
                            )
                        }

                        override fun visitMethodInsn(
                            opcode: Int,
                            owner: String?,
                            name: String?,
                            desc: String?,
                            itf: Boolean
                        ) {
                            logger.trace {
                                "Method" to "$opcode: ${owner.replaceFormatFully()}: $name: ${desc.replaceFormat()}"
                            }
                            super.visitMethodInsn(
                                opcode,
                                owner.replaceFormatFully(),
                                name,
                                desc.replaceFormat(),
                                itf
                            )
                        }

                        override fun visitFieldInsn(
                            opcode: Int,
                            owner: String?,
                            name: String?,
                            desc: String?
                        ) {
                            logger.trace { "Field" to "$opcode: $owner: $name: ${desc.replaceFormat()}" }
                            super.visitFieldInsn(opcode, owner, name, desc.replaceFormat())
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

    private fun loadNonClasses(jarFile: File): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(jarFile.inputStream()).use { stream ->
            var nextEntry: ZipEntry?
            while (stream.nextEntry.also { nextEntry = it } != null) {
                nextEntry?.use(stream) { entry ->
                    if (!entry.name.endsWith(".class") && !entry.isDirectory) {
                        val bytes = stream.readBytes()
                        entries[entry.name] = bytes
                    }
                }
            }
        }
        return entries
    }

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
