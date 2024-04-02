package fr.sunderia.bomberman.utils

import net.minestom.server.tag.Tag

enum class PowerupTags(val tag: Tag<*>) {
    BOXING_GLOVE(Tag.Boolean("boxing_glove")),
    ;

    @Suppress("UNCHECKED_CAST")
    fun <T> getTag(clazz: Class<T>): Tag<T> {
        return this.tag as Tag<T>
    }
}