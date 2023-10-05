package suwayomi.tachidesk.graphql.server.primitives

import graphql.GraphQLContext
import graphql.scalar.CoercingUtil
import graphql.schema.Coercing
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import io.javalin.http.UploadedFile
import java.util.Locale

val GraphQLUpload =
    GraphQLScalarType.newScalar()
        .name("Upload")
        .description("A file part in a multipart request")
        .coercing(GraphqlUploadCoercing())
        .build()

private class GraphqlUploadCoercing : Coercing<UploadedFile, Void?> {
    private fun parseValueImpl(
        input: Any,
        locale: Locale,
    ): UploadedFile {
        if (input !is UploadedFile) {
            throw CoercingParseValueException(
                CoercingUtil.i18nMsg(
                    locale,
                    "String.unexpectedRawValueType",
                    CoercingUtil.typeName(input),
                ),
            )
        }
        return input
    }

    @Deprecated("")
    override fun serialize(dataFetcherResult: Any): Void? {
        throw CoercingSerializeException("Upload is an input-only type")
    }

    @Throws(CoercingSerializeException::class)
    override fun serialize(
        dataFetcherResult: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Void? {
        throw CoercingSerializeException("Upload is an input-only type")
    }

    @Deprecated("")
    override fun parseValue(input: Any): UploadedFile {
        return parseValueImpl(input, Locale.getDefault())
    }

    @Throws(CoercingParseValueException::class)
    override fun parseValue(
        input: Any,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): UploadedFile {
        return parseValueImpl(input, locale)
    }

    @Deprecated("")
    override fun parseLiteral(input: Any): UploadedFile {
        return parseValueImpl(input, Locale.getDefault())
    }
}
