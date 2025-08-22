package suwayomi.tachidesk.graphql.server.primitives

import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.StringValue
import graphql.language.Value
import graphql.scalar.CoercingUtil
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import java.util.Locale
import kotlin.time.Duration

val GraphQLDurationAsString: GraphQLScalarType =
    GraphQLScalarType
        .newScalar()
        .name("Duration")
        .description("An ISO-8601 encoded duration string")
        .coercing(GraphqlDurationAsStringCoercing())
        .build()

private class GraphqlDurationAsStringCoercing : Coercing<Duration, String> {
    private fun toStringImpl(input: Any): String =
        when (input) {
            is Duration -> input.toIsoString()
            is String -> Duration.parse(input).toIsoString()
            else -> throw CoercingSerializeException(
                "Expected a Duration or String but was ${CoercingUtil.typeName(input)}",
            )
        }

    private fun parseValueImpl(
        input: Any,
        locale: Locale,
    ): Duration {
        if (input !is String) {
            throw CoercingParseValueException(
                CoercingUtil.i18nMsg(
                    locale,
                    "String.unexpectedRawValueType",
                    CoercingUtil.typeName(input),
                ),
            )
        }
        return try {
            Duration.parse(input)
        } catch (e: IllegalArgumentException) {
            throw CoercingParseValueException(
                "Invalid duration format: $input. Expected ISO-8601 duration string (e.g., 'PT30M', 'P1D')",
                e,
            )
        }
    }

    private fun parseLiteralImpl(
        input: Any,
        locale: Locale,
    ): Duration {
        if (input !is StringValue) {
            throw CoercingParseLiteralException(
                CoercingUtil.i18nMsg(
                    locale,
                    "Scalar.unexpectedAstType",
                    "StringValue",
                    CoercingUtil.typeName(input),
                ),
            )
        }
        return try {
            Duration.parse(input.value)
        } catch (e: IllegalArgumentException) {
            throw CoercingParseLiteralException(
                "Invalid duration format: ${input.value}. Expected ISO-8601 duration string (e.g., 'PT30M', 'P1D')",
                e,
            )
        }
    }

    private fun valueToLiteralImpl(input: Any): StringValue = StringValue.newStringValue(toStringImpl(input)).build()

    @Deprecated("")
    override fun serialize(dataFetcherResult: Any): String = toStringImpl(dataFetcherResult)

    @Throws(CoercingSerializeException::class)
    override fun serialize(
        dataFetcherResult: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): String = toStringImpl(dataFetcherResult)

    @Deprecated("")
    override fun parseValue(input: Any): Duration = parseValueImpl(input, Locale.getDefault())

    @Throws(CoercingParseValueException::class)
    override fun parseValue(
        input: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Duration = parseValueImpl(input, locale)

    @Deprecated("")
    override fun parseLiteral(input: Any): Duration = parseLiteralImpl(input, Locale.getDefault())

    @Throws(CoercingParseLiteralException::class)
    override fun parseLiteral(
        input: Value<*>,
        variables: CoercedVariables,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Duration = parseLiteralImpl(input, locale)

    @Deprecated("")
    override fun valueToLiteral(input: Any): Value<*> = valueToLiteralImpl(input)

    override fun valueToLiteral(
        input: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Value<*> = valueToLiteralImpl(input)
}
