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

data class Cursor(val value: String)

val GraphQLCursor: GraphQLScalarType = GraphQLScalarType.newScalar()
    .name("Cursor").description("A location in a connection that can be used for resuming pagination.").coercing(GraphqlCursorCoercing()).build()

private class GraphqlCursorCoercing : Coercing<Cursor, String> {
    private fun toStringImpl(input: Any): String? {
        return (input as? Cursor)?.value
    }

    private fun parseValueImpl(input: Any, locale: Locale): Cursor {
        if (input !is String) {
            throw CoercingParseValueException(
                CoercingUtil.i18nMsg(
                    locale,
                    "String.unexpectedRawValueType",
                    CoercingUtil.typeName(input)
                )
            )
        }
        return Cursor(input)
    }

    private fun parseLiteralImpl(input: Any, locale: Locale): Cursor {
        if (input !is StringValue) {
            throw CoercingParseLiteralException(
                CoercingUtil.i18nMsg(
                    locale,
                    "Scalar.unexpectedAstType",
                    "StringValue",
                    CoercingUtil.typeName(input)
                )
            )
        }
        return Cursor(input.value)
    }

    private fun valueToLiteralImpl(input: Any): StringValue {
        return StringValue.newStringValue(input.toString()).build()
    }

    @Deprecated("")
    override fun serialize(dataFetcherResult: Any): String {
        return toStringImpl(dataFetcherResult) ?: throw CoercingSerializeException(
            CoercingUtil.i18nMsg(
                Locale.getDefault(),
                "String.unexpectedRawValueType",
                CoercingUtil.typeName(dataFetcherResult)
            )
        )
    }

    @Throws(CoercingSerializeException::class)
    override fun serialize(
        dataFetcherResult: Any,
        graphQLContext: GraphQLContext,
        locale: Locale
    ): String {
        return toStringImpl(dataFetcherResult) ?: throw CoercingSerializeException(
            CoercingUtil.i18nMsg(
                locale,
                "String.unexpectedRawValueType",
                CoercingUtil.typeName(dataFetcherResult)
            )
        )
    }

    @Deprecated("")
    override fun parseValue(input: Any): Cursor {
        return parseValueImpl(input, Locale.getDefault())
    }

    @Throws(CoercingParseValueException::class)
    override fun parseValue(input: Any, graphQLContext: GraphQLContext, locale: Locale): Cursor {
        return parseValueImpl(input, locale)
    }

    @Deprecated("")
    override fun parseLiteral(input: Any): Cursor {
        return parseLiteralImpl(input, Locale.getDefault())
    }

    @Throws(CoercingParseLiteralException::class)
    override fun parseLiteral(
        input: Value<*>,
        variables: CoercedVariables,
        graphQLContext: GraphQLContext,
        locale: Locale
    ): Cursor {
        return parseLiteralImpl(input, locale)
    }

    @Deprecated("")
    override fun valueToLiteral(input: Any): Value<*> {
        return valueToLiteralImpl(input)
    }

    override fun valueToLiteral(
        input: Any,
        graphQLContext: GraphQLContext,
        locale: Locale
    ): Value<*> {
        return valueToLiteralImpl(input)
    }
}
