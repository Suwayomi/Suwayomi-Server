package suwayomi.tachidesk.graphql.server.primitives

import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.FloatValue
import graphql.language.Value
import graphql.scalar.CoercingUtil
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import java.util.Locale

val GraphQLFloatAsDouble: GraphQLScalarType =
    GraphQLScalarType
        .newScalar()
        .name(
            "Float32",
        ).description("32-bit float aka kotlin.lang.Float")
        .coercing(GraphqlActualFloatCoercing())
        .build()

private class GraphqlActualFloatCoercing : Coercing<Float, Double> {
    private fun toDoubleImpl(input: Any): Double? =
        when (input) {
            is Float -> input.toDouble()
            is Double -> input
            is Int -> input.toDouble()
            else -> null
        }

    private fun parseValueImpl(
        input: Any,
        locale: Locale,
    ): Float {
        if (input is Int) {
            return input.toFloat()
        }
        if (input !is Double) {
            throw CoercingParseValueException(
                CoercingUtil.i18nMsg(
                    locale,
                    "String.unexpectedRawValueType",
                    CoercingUtil.typeName(input),
                ),
            )
        }
        return input.toFloat()
    }

    private fun parseLiteralImpl(
        input: Any,
        locale: Locale,
    ): Float {
        if (input !is FloatValue) {
            throw CoercingParseLiteralException(
                CoercingUtil.i18nMsg(
                    locale,
                    "Scalar.unexpectedAstType",
                    "StringValue",
                    CoercingUtil.typeName(input),
                ),
            )
        }
        return input.value.toFloat()
    }

    private fun valueToLiteralImpl(input: Any): FloatValue =
        FloatValue
            .newFloatValue()
            .value(
                toDoubleImpl(input) ?: throw CoercingSerializeException(
                    CoercingUtil.i18nMsg(
                        Locale.getDefault(),
                        "String.unexpectedRawValueType",
                        CoercingUtil.typeName(input),
                    ),
                ),
            ).build()

    @Deprecated("")
    override fun serialize(dataFetcherResult: Any): Double =
        toDoubleImpl(dataFetcherResult) ?: throw CoercingSerializeException(
            CoercingUtil.i18nMsg(
                Locale.getDefault(),
                "String.unexpectedRawValueType",
                CoercingUtil.typeName(dataFetcherResult),
            ),
        )

    @Throws(CoercingSerializeException::class)
    override fun serialize(
        dataFetcherResult: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Double =
        toDoubleImpl(dataFetcherResult) ?: throw CoercingSerializeException(
            CoercingUtil.i18nMsg(
                locale,
                "String.unexpectedRawValueType",
                CoercingUtil.typeName(dataFetcherResult),
            ),
        )

    @Deprecated("")
    override fun parseValue(input: Any): Float = parseValueImpl(input, Locale.getDefault())

    @Throws(CoercingParseValueException::class)
    override fun parseValue(
        input: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Float = parseValueImpl(input, locale)

    @Deprecated("")
    override fun parseLiteral(input: Any): Float = parseLiteralImpl(input, Locale.getDefault())

    @Throws(CoercingParseLiteralException::class)
    override fun parseLiteral(
        input: Value<*>,
        variables: CoercedVariables,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Float = parseLiteralImpl(input, locale)

    @Deprecated("")
    override fun valueToLiteral(input: Any): Value<*> = valueToLiteralImpl(input)

    override fun valueToLiteral(
        input: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Value<*> = valueToLiteralImpl(input)
}
