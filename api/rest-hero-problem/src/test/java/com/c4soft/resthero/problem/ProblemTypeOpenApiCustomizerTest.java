package com.c4soft.resthero.problem;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

/**
 * The spec is read by the clients' generators: what this customizer leaves in the problem schemas
 * is what a Java or TypeScript client compiles.
 */
class ProblemTypeOpenApiCustomizerTest {

  /** The {@code type} property as springdoc writes it from the annotated getter. */
  private static Schema<?> springdocTypeProperty() {
    final var type = new StringSchema().format("uri")._default("");
    type.set$ref(ApiProblemDetail.PROBLEM_TYPE_SCHEMA_REF);
    type.setDescription("Same value as the parent's, documented as a ProblemType.");
    return type;
  }

  private static Schema<?> problemSchema() {
    final Map<String, Schema> properties = new LinkedHashMap<>();
    properties.put("type", springdocTypeProperty());
    properties.put("detail", new StringSchema());
    properties.put("properties", new MapSchema());
    properties.put("parameters", new MapSchema());
    final var schema = new ObjectSchema();
    schema.setProperties(properties);
    schema.setRequired(List.of("parameters"));
    return schema;
  }

  private static OpenAPI customised() {
    final var openApi = new OpenAPI().components(new Components()
        .addSchemas(ApiProblemDetail.class.getSimpleName(), problemSchema())
        .addSchemas(ValidationProblemDetail.class.getSimpleName(), problemSchema()));
    new ProblemTypeOpenApiCustomizer().customise(openApi);
    return openApi;
  }

  @Test
  void givenSpringdocsTypeProperty_whenCustomised_thenTypeIsABareReferenceToProblemType() {
    final var schemas = customised().getComponents().getSchemas();
    for (final var name : List.of("ApiProblemDetail", "ValidationProblemDetail")) {
      final Schema<?> type = (Schema<?>) schemas.get(name).getProperties().get("type");
      assertThat(type.get$ref()).as(name).isEqualTo(ApiProblemDetail.PROBLEM_TYPE_SCHEMA_REF);
      // what openapi-generator turned into "ProblemType type = ;"
      assertThat(type.getDefault()).as(name).isNull();
      assertThat(type.getType()).as(name).isNull();
      assertThat(type.getFormat()).as(name).isNull();
      assertThat(type.getDescription()).as(name).isNotBlank();
    }
  }

  @Test
  void givenProblemSchemas_whenCustomised_thenProblemTypeIsTheEnumOfTheUrisAndTheStandardMembersAreRequired() {
    final var schemas = customised().getComponents().getSchemas();
    final List<?> uris = schemas.get(ApiProblemDetail.PROBLEM_TYPE_SCHEMA).getEnum();
    assertThat(uris).hasSize(ProblemType.values().length)
        .anyMatch("urn:rest-hero:problem:account-not-found"::equals);
    assertThat(schemas.get("ApiProblemDetail").getProperties()).doesNotContainKey("properties");
    assertThat(schemas.get("ApiProblemDetail").getRequired())
        .contains("type", "title", "status", "detail", "instance", "parameters");
  }
}
