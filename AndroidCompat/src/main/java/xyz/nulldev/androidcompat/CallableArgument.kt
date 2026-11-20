package xyz.nulldev.androidcompat

fun interface CallableArgument<A, R> {
    fun call(arg: A): R
}
