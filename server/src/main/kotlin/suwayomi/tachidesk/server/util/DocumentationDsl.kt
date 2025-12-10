package suwayomi.tachidesk.server.util

import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.HttpStatus

fun <T> getSimpleParamItem(
    ctx: Context,
    param: Param<T>,
): String? =
    when (param) {
        is Param.FormParam -> ctx.formParam(param.key)
        is Param.PathParam -> ctx.pathParam(param.key)
        is Param.QueryParam -> ctx.queryParam(param.key)
        else -> throw IllegalStateException("Invalid param")
    }

@Suppress("UNCHECKED_CAST")
fun <T> getParam(
    ctx: Context,
    param: Param<T>,
): T {
    if (param is Param.QueryParams<*, *>) {
        val item = ctx.queryParams(param.key).filter(String::isNotBlank)
        val typedItem: List<Any?> =
            when (param.clazz) {
                String::class.java, java.lang.String::class.java -> item
                Int::class.java, java.lang.Integer::class.java -> item.map { it.toIntOrNull() }
                Long::class.java, java.lang.Long::class.java -> item.map { it.toLongOrNull() }
                Boolean::class.java, java.lang.Boolean::class.java -> item.map { it.toBoolean() }
                Float::class.java, java.lang.Float::class.java -> item.map { it.toFloatOrNull() }
                Double::class.java, java.lang.Double::class.java -> item.map { it.toDoubleOrNull() }
                else -> throw IllegalStateException("Unknown class ${param.clazz.simpleName}")
            }.let {
                if (param.nullable) {
                    it
                } else {
                    it.filterNotNull()
                }
            }.ifEmpty { param.defaultValue }
        return typedItem as T
    }
    val typedItem: Any? =
        when (val clazz = param.clazz as Class<T>) {
            String::class.java, java.lang.String::class.java -> {
                getSimpleParamItem(ctx, param) ?: param.defaultValue
            }

            Int::class.java, java.lang.Integer::class.java -> {
                getSimpleParamItem(ctx, param)?.toIntOrNull() ?: param.defaultValue
            }

            Long::class.java, java.lang.Long::class.java -> {
                getSimpleParamItem(ctx, param)?.toLongOrNull() ?: param.defaultValue
            }

            Boolean::class.java, java.lang.Boolean::class.java -> {
                getSimpleParamItem(ctx, param)?.toBoolean() ?: param.defaultValue
            }

            Float::class.java, java.lang.Float::class.java -> {
                getSimpleParamItem(ctx, param)?.toFloatOrNull() ?: param.defaultValue
            }

            Double::class.java, java.lang.Double::class.java -> {
                getSimpleParamItem(ctx, param)?.toDoubleOrNull() ?: param.defaultValue
            }

            else -> {
                when (param) {
                    is Param.FormParam -> ctx.formParamAsClass(param.key, clazz)
                    is Param.PathParam -> ctx.pathParamAsClass(param.key, clazz)
                    is Param.QueryParam -> ctx.queryParamAsClass(param.key, clazz)
                    else -> throw IllegalStateException("Invalid param")
                }.let {
                    if (param.nullable) {
                        it.allowNullable().get() ?: param.defaultValue
                    } else {
                        if (param.defaultValue != null) {
                            it.getOrDefault(param.defaultValue!!)
                        } else {
                            it.get()
                        }
                    }
                }
            }
        }

    return if (param.nullable) {
        typedItem as T
    } else {
        typedItem!! as T
    }
}

inline fun getDocumentation(
    documentWith: OpenApiDocumentation.() -> Unit,
    noinline withResults: ResultsBuilder.() -> Unit,
    vararg params: Param<*>,
): OpenApiDocumentation =
    OpenApiDocumentation().apply(documentWith).apply {
        applyResults(withResults)
        params.forEach {
            when (it) {
                is Param.FormParam -> formParam(it.key, it.clazz, !it.nullable && it.defaultValue == null)
                is Param.PathParam -> pathParam(it.key, it.clazz)
                is Param.QueryParam -> queryParam(it.key, it.clazz)
                is Param.QueryParams<*, *> -> queryParam(it.key, it.clazz, isRepeatable = true)
            }
        }
    }

fun OpenApiDocumentation.applyResults(withResults: ResultsBuilder.() -> Unit) {
    ResultsBuilder().apply(withResults).results.forEach {
        it.applyTo(this)
    }
}

fun OpenApiDocumentation.withOperation(block: Operation.() -> Unit) {
    operation(block)
}

inline fun <reified T> formParam(
    key: String,
    defaultValue: T? = null,
): Param.FormParam<T> = Param.FormParam(key, T::class.java, defaultValue, null is T)

inline fun <reified T> queryParam(
    key: String,
    defaultValue: T? = null,
): Param.QueryParam<T> = Param.QueryParam(key, T::class.java, defaultValue, null is T)

inline fun <reified T> queryParams(
    key: String,
    defaultValue: List<T> = emptyList(),
): Param.QueryParams<T, List<T>> = Param.QueryParams(key, T::class.java, defaultValue, null is T)

inline fun <reified T> pathParam(key: String): Param.PathParam<T> = Param.PathParam(key, T::class.java, null, false)

sealed class Param<T> {
    abstract val key: String
    abstract val clazz: Class<*>
    abstract val defaultValue: T?
    abstract val nullable: Boolean

    data class FormParam<T>(
        override val key: String,
        override val clazz: Class<*>,
        override val defaultValue: T?,
        override val nullable: Boolean,
    ) : Param<T>()

    data class QueryParam<T>(
        override val key: String,
        override val clazz: Class<*>,
        override val defaultValue: T?,
        override val nullable: Boolean,
    ) : Param<T>()

    data class QueryParams<R, T : List<R>>(
        override val key: String,
        override val clazz: Class<R>,
        override val defaultValue: T,
        override val nullable: Boolean,
    ) : Param<T>()

    data class PathParam<T>(
        override val key: String,
        override val clazz: Class<*>,
        override val defaultValue: T?,
        override val nullable: Boolean,
    ) : Param<T>()
}

class ResultsBuilder {
    val results = mutableListOf<ResultType>()

    inline fun <reified T> json(code: HttpStatus) {
        results += ResultType.MimeType(code, "application/json", T::class.java)
    }

    fun plainText(code: HttpStatus) {
        results += ResultType.MimeType(code, "text/plain", String::class.java)
    }

    fun image(code: HttpStatus) {
        results += ResultType.MimeType(code, "image/*", ByteArray::class.java)
    }

    fun stream(code: HttpStatus) {
        results += ResultType.MimeType(code, "application/octet-stream", ByteArray::class.java)
    }

    inline fun <reified T> mime(
        code: HttpStatus,
        mime: String,
    ) {
        results += ResultType.MimeType(code, mime, T::class.java)
    }

    fun httpCode(code: HttpStatus) {
        results += ResultType.StatusCode(code)
    }
}

sealed class ResultType {
    abstract fun applyTo(documentation: OpenApiDocumentation)

    data class MimeType(
        val code: HttpStatus,
        val mime: String,
        private val clazz: Class<*>,
    ) : ResultType() {
        override fun applyTo(documentation: OpenApiDocumentation) {
            documentation.result(code.code.toString(), clazz, mime)
        }
    }

    data class StatusCode(
        val code: HttpStatus,
    ) : ResultType() {
        override fun applyTo(documentation: OpenApiDocumentation) {
            documentation.result<Unit>(code.code.toString())
        }
    }
}

inline fun handler(
    documentWith: OpenApiDocumentation.() -> Unit = {},
    noinline behaviorOf: (ctx: Context) -> Unit,
    noinline withResults: ResultsBuilder.() -> Unit,
): DocumentedHandler =
    documented(
        documentation = getDocumentation(documentWith, withResults),
        handle = behaviorOf,
    )

inline fun <reified P1> handler(
    param1: Param<P1>,
    documentWith: OpenApiDocumentation.() -> Unit,
    noinline behaviorOf: (ctx: Context, P1) -> Unit,
    noinline withResults: ResultsBuilder.() -> Unit,
): DocumentedHandler =
    documented(
        documentation = getDocumentation(documentWith, withResults, param1),
        handle = {
            behaviorOf(
                it,
                getParam(it, param1),
            )
        },
    )

inline fun <reified P1, reified P2> handler(
    param1: Param<P1>,
    param2: Param<P2>,
    documentWith: OpenApiDocumentation.() -> Unit = {},
    crossinline behaviorOf: (ctx: Context, P1, P2) -> Unit,
    noinline withResults: ResultsBuilder.() -> Unit,
): DocumentedHandler =
    documented(
        documentation = getDocumentation(documentWith, withResults, param1, param2),
        handle = {
            behaviorOf(
                it,
                getParam(it, param1),
                getParam(it, param2),
            )
        },
    )

inline fun <reified P1, reified P2, reified P3> handler(
    param1: Param<P1>,
    param2: Param<P2>,
    param3: Param<P3>,
    documentWith: OpenApiDocumentation.() -> Unit = {},
    crossinline behaviorOf: (ctx: Context, P1, P2, P3) -> Unit,
    noinline withResults: ResultsBuilder.() -> Unit,
): DocumentedHandler =
    documented(
        documentation = getDocumentation(documentWith, withResults, param1, param2, param3),
        handle = {
            behaviorOf(
                it,
                getParam(it, param1),
                getParam(it, param2),
                getParam(it, param3),
            )
        },
    )

inline fun <reified P1, reified P2, reified P3, reified P4> handler(
    param1: Param<P1>,
    param2: Param<P2>,
    param3: Param<P3>,
    param4: Param<P4>,
    documentWith: OpenApiDocumentation.() -> Unit = {},
    crossinline behaviorOf: (ctx: Context, P1, P2, P3, P4) -> Unit,
    noinline withResults: ResultsBuilder.() -> Unit,
): DocumentedHandler =
    documented(
        documentation = getDocumentation(documentWith, withResults, param1, param2, param3, param4),
        handle = {
            behaviorOf(
                it,
                getParam(it, param1),
                getParam(it, param2),
                getParam(it, param3),
                getParam(it, param4),
            )
        },
    )

inline fun <reified P1, reified P2, reified P3, reified P4, reified P5> handler(
    param1: Param<P1>,
    param2: Param<P2>,
    param3: Param<P3>,
    param4: Param<P4>,
    param5: Param<P5>,
    documentWith: OpenApiDocumentation.() -> Unit = {},
    crossinline behaviorOf: (ctx: Context, P1, P2, P3, P4, P5) -> Unit,
    noinline withResults: ResultsBuilder.() -> Unit,
): DocumentedHandler =
    documented(
        documentation =
            getDocumentation(
                documentWith,
                withResults,
                param1,
                param2,
                param3,
                param4,
                param5,
            ),
        handle = {
            behaviorOf(
                it,
                getParam(it, param1),
                getParam(it, param2),
                getParam(it, param3),
                getParam(it, param4),
                getParam(it, param5),
            )
        },
    )

inline fun <reified P1, reified P2, reified P3, reified P4, reified P5, reified P6> handler(
    param1: Param<P1>,
    param2: Param<P2>,
    param3: Param<P3>,
    param4: Param<P4>,
    param5: Param<P5>,
    param6: Param<P6>,
    documentWith: OpenApiDocumentation.() -> Unit = {},
    crossinline behaviorOf: (ctx: Context, P1, P2, P3, P4, P5, P6) -> Unit,
    noinline withResults: ResultsBuilder.() -> Unit,
): DocumentedHandler =
    documented(
        documentation =
            getDocumentation(
                documentWith,
                withResults,
                param1,
                param2,
                param3,
                param4,
                param5,
                param6,
            ),
        handle = {
            behaviorOf(
                it,
                getParam(it, param1),
                getParam(it, param2),
                getParam(it, param3),
                getParam(it, param4),
                getParam(it, param5),
                getParam(it, param6),
            )
        },
    )

inline fun <
    reified P1,
    reified P2,
    reified P3,
    reified P4,
    reified P5,
    reified P6,
    reified P7,
> handler(
    param1: Param<P1>,
    param2: Param<P2>,
    param3: Param<P3>,
    param4: Param<P4>,
    param5: Param<P5>,
    param6: Param<P6>,
    param7: Param<P7>,
    documentWith: OpenApiDocumentation.() -> Unit = {},
    crossinline behaviorOf: (ctx: Context, P1, P2, P3, P4, P5, P6, P7) -> Unit,
    noinline withResults: ResultsBuilder.() -> Unit,
): DocumentedHandler =
    documented(
        documentation =
            getDocumentation(
                documentWith,
                withResults,
                param1,
                param2,
                param3,
                param4,
                param5,
                param6,
                param7,
            ),
        handle = {
            behaviorOf(
                it,
                getParam(it, param1),
                getParam(it, param2),
                getParam(it, param3),
                getParam(it, param4),
                getParam(it, param5),
                getParam(it, param6),
                getParam(it, param7),
            )
        },
    )

inline fun <
    reified P1,
    reified P2,
    reified P3,
    reified P4,
    reified P5,
    reified P6,
    reified P7,
    reified P8,
> handler(
    param1: Param<P1>,
    param2: Param<P2>,
    param3: Param<P3>,
    param4: Param<P4>,
    param5: Param<P5>,
    param6: Param<P6>,
    param7: Param<P7>,
    param8: Param<P8>,
    documentWith: OpenApiDocumentation.() -> Unit = {},
    crossinline behaviorOf: (ctx: Context, P1, P2, P3, P4, P5, P6, P7, P8) -> Unit,
    noinline withResults: ResultsBuilder.() -> Unit,
): DocumentedHandler =
    documented(
        documentation =
            getDocumentation(
                documentWith,
                withResults,
                param1,
                param2,
                param3,
                param4,
                param5,
                param6,
                param7,
                param8,
            ),
        handle = {
            behaviorOf(
                it,
                getParam(it, param1),
                getParam(it, param2),
                getParam(it, param3),
                getParam(it, param4),
                getParam(it, param5),
                getParam(it, param6),
                getParam(it, param7),
                getParam(it, param8),
            )
        },
    )

inline fun <
    reified P1,
    reified P2,
    reified P3,
    reified P4,
    reified P5,
    reified P6,
    reified P7,
    reified P8,
    reified P9,
> handler(
    param1: Param<P1>,
    param2: Param<P2>,
    param3: Param<P3>,
    param4: Param<P4>,
    param5: Param<P5>,
    param6: Param<P6>,
    param7: Param<P7>,
    param8: Param<P8>,
    param9: Param<P9>,
    documentWith: OpenApiDocumentation.() -> Unit = {},
    crossinline behaviorOf: (ctx: Context, P1, P2, P3, P4, P5, P6, P7, P8, P9) -> Unit,
    noinline withResults: ResultsBuilder.() -> Unit,
): DocumentedHandler =
    documented(
        documentation =
            getDocumentation(
                documentWith,
                withResults,
                param1,
                param2,
                param3,
                param4,
                param5,
                param6,
                param7,
                param8,
                param9,
            ),
        handle = {
            behaviorOf(
                it,
                getParam(it, param1),
                getParam(it, param2),
                getParam(it, param3),
                getParam(it, param4),
                getParam(it, param5),
                getParam(it, param6),
                getParam(it, param7),
                getParam(it, param8),
                getParam(it, param9),
            )
        },
    )

inline fun <
    reified P1,
    reified P2,
    reified P3,
    reified P4,
    reified P5,
    reified P6,
    reified P7,
    reified P8,
    reified P9,
    reified P10,
> handler(
    param1: Param<P1>,
    param2: Param<P2>,
    param3: Param<P3>,
    param4: Param<P4>,
    param5: Param<P5>,
    param6: Param<P6>,
    param7: Param<P7>,
    param8: Param<P8>,
    param9: Param<P9>,
    param10: Param<P10>,
    documentWith: OpenApiDocumentation.() -> Unit = {},
    crossinline behaviorOf: (ctx: Context, P1, P2, P3, P4, P5, P6, P7, P8, P9, P10) -> Unit,
    noinline withResults: ResultsBuilder.() -> Unit,
): DocumentedHandler =
    documented(
        documentation =
            getDocumentation(
                documentWith,
                withResults,
                param1,
                param2,
                param3,
                param4,
                param5,
                param6,
                param7,
                param8,
                param9,
                param10,
            ),
        handle = {
            behaviorOf(
                it,
                getParam(it, param1),
                getParam(it, param2),
                getParam(it, param3),
                getParam(it, param4),
                getParam(it, param5),
                getParam(it, param6),
                getParam(it, param7),
                getParam(it, param8),
                getParam(it, param9),
                getParam(it, param10),
            )
        },
    )

@Suppress("UNUSED")
class OpenApiDocumentation {
    fun operation(block: Operation.() -> Unit) {}

    fun result(
        toString: Any,
        clazz: Class<*>,
        mime: String,
    ) {
    }

    fun <T> result(toString: Any) {
    }

    fun <T> formParam(
        key: String,
        defaultValue: T? = null,
        isRequired: Boolean = false,
    ) {}

    fun <T> queryParam(
        key: String,
        defaultValue: T? = null,
        isRepeatable: Boolean = false,
    ) {}

    fun <T> pathParam(
        key: String,
        defaultValue: T? = null,
    ) {}

    fun <T> body() {}

    fun uploadedFile(
        name: String,
        block: (DocumentationFile) -> Unit,
    ) {}
}

class DocumentationFile {
    fun description(string: String) {}

    fun required(boolean: Boolean) {}
}

class DocumentedHandler(
    private val handler: (ctx: Context) -> Unit,
) : Handler {
    override fun handle(ctx: Context) {
        handler(ctx)
    }
}

fun documented(
    documentation: OpenApiDocumentation,
    handle: (ctx: Context) -> Unit,
): DocumentedHandler = DocumentedHandler(handle)

class Operation {
    fun summary(string: String) {}

    fun description(string: String) {}
}
