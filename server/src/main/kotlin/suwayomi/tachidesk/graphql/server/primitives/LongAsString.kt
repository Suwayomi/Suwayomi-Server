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

val GraphQLLongAsString: GraphQLScalarType =
    GraphQLScalarType
        .newScalar()
        .name("LongString")
        .description("A 64-bit signed integer as a String")
        .coercing(GraphqlLongAsStringCoercing())
        .build()

private class GraphqlLongAsStringCoercing : Coercing<Long, String> {
    private fun toStringImpl(input: Any): String = input.toString()

    private fun parseValueImpl(
        input: Any,
        locale: Locale,
    ): Long {
        if (input !is String) {
            throw CoercingParseValueException(
                CoercingUtil.i18nMsg(
                    locale,
                    "String.unexpectedRawValueType",
                    CoercingUtil.typeName(input),
                ),
            )
        }
        return input.toLong()
    }

    private fun parseLiteralImpl(
        input: Any,
        locale: Locale,
    ): Long {
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
        return input.value.toLong()
    }

    private fun valueToLiteralImpl(input: Any): StringValue = StringValue.newStringValue(input.toString()).build()

    @Deprecated("")
    override fun serialize(dataFetcherResult: Any): String = toStringImpl(dataFetcherResult)

    @Throws(CoercingSerializeException::class)
    override fun serialize(
        dataFetcherResult: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): String = toStringImpl(dataFetcherResult)

    @Deprecated("")
    override fun parseValue(input: Any): Long = parseValueImpl(input, Locale.getDefault())

    @Throws(CoercingParseValueException::class)
    override fun parseValue(
        input: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Long = parseValueImpl(input, locale)

    @Deprecated("")
    override fun parseLiteral(input: Any): Long = parseLiteralImpl(input, Locale.getDefault())

    @Throws(CoercingParseLiteralException::class)
    override fun parseLiteral(
        input: Value<*>,
        variables: CoercedVariables,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Long = parseLiteralImpl(input, locale)

    @Deprecated("")
    override fun valueToLiteral(input: Any): Value<*> = valueToLiteralImpl(input)

    override fun valueToLiteral(
        input: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Value<*> = valueToLiteralImpl(input)
}
