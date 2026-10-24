package com.c4soft.resthero.problem;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.customizers.OpenApiCustomizer;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

/**
 * Documents {@link ProblemType} as a named enum schema of its {@code type} URIs, with
 * {@code x-enum-varnames} so that openapi-generator names the client enum's members after the
 * Java constants rather than after the URN values. {@link ApiProblemDetail#getType()} refers
 * to it.
 *
 * <p>
 * Also trims the {@link ApiProblemDetail} schemas: the {@code properties} map inherited from
 * {@link org.springframework.http.ProblemDetail} is never on the wire (extension members are
 * dedicated getters here), and the standard members, which can't be annotated on the parent's
 * getters, are always set.
 * </p>
 *
 * <p>
 * Only picked up when springdoc's web integration is on the classpath (the {@code openapi}
 * Maven profile).
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public class ProblemTypeOpenApiCustomizer implements OpenApiCustomizer {

  static final String ENUM_VARNAMES_EXTENSION = "x-enum-varnames";

  static final List<String> PROBLEM_DETAIL_SCHEMAS = List.of(
      ApiProblemDetail.class.getSimpleName(), ValidationProblemDetail.class.getSimpleName());

  static final List<String> STANDARD_MEMBERS = List.of("type", "title", "status", "detail", "instance");

  @Override
  public void customise(OpenAPI openApi) {
    final var schemas = openApi.getComponents().getSchemas();
    if (schemas == null) {
      return;
    }
    schemas.put(ApiProblemDetail.PROBLEM_TYPE_SCHEMA, problemTypeSchema());
    PROBLEM_DETAIL_SCHEMAS.stream().map(schemas::get).filter(Objects::nonNull)
        .forEach(ProblemTypeOpenApiCustomizer::trim);
  }

  private static Schema<String> problemTypeSchema() {
    final var types = Arrays.asList(ProblemType.values());
    final var schema = new StringSchema()
        .description("The closed set of problems this API reports: the RFC 9457 `type` URI "
            + "identifies which one, `parameters` holds the values named on each type.");
    schema.setEnum(types.stream().map(type -> type.uri().toString()).toList());
    schema.addExtension(ENUM_VARNAMES_EXTENSION, types.stream().map(ProblemType::name).toList());
    return schema;
  }

  private static void trim(Schema<?> schema) {
    schema.getProperties().remove("properties");
    final var required = Optional.ofNullable(schema.getRequired()).orElse(List.of());
    STANDARD_MEMBERS.stream().filter(member -> !required.contains(member))
        .forEach(schema::addRequiredItem);
  }
}
