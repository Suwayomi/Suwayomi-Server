package xyz.nulldev.androidcompat.res

class DrawableResource(val location: String) : Resource {
    override fun getType() = DrawableResource::class.java

    override fun getValue() = javaClass.getResourceAsStream(location)
}
