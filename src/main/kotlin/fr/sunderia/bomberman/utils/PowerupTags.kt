package fr.sunderia.bomberman.utils

import net.minestom.server.tag.Tag

enum class PowerupTags(private val tag: Tag<*>) {
    BOXING_GLOVE(Tag.Boolean("boxing_glove")),
    PIERCE(Tag.Boolean("pierce")),
    ;

    @Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
    private fun <T> getTag(clazz: Class<T>): Tag<T> {
        return this.tag as Tag<T>
    }

    fun getBool(): Tag<Boolean> {
        return this.getTag(Boolean::class.java)
    }
}