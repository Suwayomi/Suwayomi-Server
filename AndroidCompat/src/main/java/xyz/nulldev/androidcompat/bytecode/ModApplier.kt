package xyz.nulldev.androidcompat.bytecode

import javassist.CtClass
import mu.KotlinLogging

/**
 * Applies Javassist modifications
 */

class ModApplier {

    val logger = KotlinLogging.logger {}

    fun apply() {
        logger.info { "Applying Javassist mods..." }
        val modifiedClasses = mutableListOf<CtClass>()

        modifiedClasses.forEach {
            it.toClass()
        }
    }
}