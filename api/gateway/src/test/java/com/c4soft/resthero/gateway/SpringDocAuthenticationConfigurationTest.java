package com.c4soft.resthero.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * The login and logout operations the gateway adds to its OpenAPI description declare the
 * {@code 400} {@code invalid-request} problem a refused return address gets, the optional headers
 * that set the return addresses, and the {@code Location} header of their answer, so that the
 * generated client knows them and takes the headers as arguments. Checked on the customizer
 * itself: springdoc's web integration is only on the classpath with the {@code openapi} Maven
 * profile.
 */
class SpringDocAuthenticationConfigurationTest {

  private static final String COMPONENT_SCHEMA_REF = "#/components/schemas/";

  @Test
  void whenCustomise_thenLoginAndLogoutDeclareTheInvalidRequestProblem() {
    final var openApi = customisedOpenApi();

    assertDeclaresTheInvalidRequestProblem(
        openApi, openApi.getPaths().get("/oauth2/authorization/gateway").getGet());
    assertDeclaresTheInvalidRequestProblem(openApi, openApi.getPaths().get("/logout").getPost());
  }

  @Test
  void whenCustomise_thenLoginAndLogoutDeclareTheReturnAddressHeadersAndLocation() {
    final var openApi = customisedOpenApi();

    final var login = openApi.getPaths().get("/oauth2/authorization/gateway").getGet();
    assertDeclaresTheReturnAddressHeaders(
        login, "X-POST-LOGIN-SUCCESS-URI", "X-POST-LOGIN-FAILURE-URI");
    assertDeclaresTheLocationHeader(login, "200");

    final var logout = openApi.getPaths().get("/logout").getPost();
    assertDeclaresTheReturnAddressHeaders(logout, "X-POST-LOGOUT-SUCCESS-URI");
    assertDeclaresTheLocationHeader(logout, "202");
  }

  /** The customizer applied to an empty description, with the {@code gateway} registration. */
  private static OpenAPI customisedOpenApi() {
    final var registration =
        ClientRegistration.withRegistrationId("gateway")
            .clientId("gateway")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .authorizationUri("https://idp.example/auth")
            .tokenUri("https://idp.example/token")
            .build();
    final var openApi = new OpenAPI().paths(new Paths());

    new SpringDocAuthenticationConfiguration()
        .springDocAuthCustomizer(new InMemoryClientRegistrationRepository(registration))
        .customise(openApi);
    return openApi;
  }

  /** The operation's parameters are these optional headers, in this order, each a URI string. */
  private static void assertDeclaresTheReturnAddressHeaders(Operation operation, String... names) {
    assertThat(operation.getParameters())
        .as("the parameters of %s", operation.getOperationId())
        .isNotNull()
        .extracting(Parameter::getName)
        .containsExactly(names);
    for (final var parameter : operation.getParameters()) {
      final var name = parameter.getName();
      assertThat(parameter.getIn()).as("where %s is", name).isEqualTo("header");
      assertThat(parameter.getRequired()).as("%s required", name).isNotEqualTo(Boolean.TRUE);
      assertThat(parameter.getSchema()).as("the schema of %s", name).isNotNull();
      assertThat(parameter.getSchema().getType()).as("its type").isEqualTo("string");
      assertThat(parameter.getSchema().getFormat()).as("its format").isEqualTo("uri");
      assertThat(parameter.getDescription()).as("the description of %s", name).isNotBlank();
    }
  }

  /** The response with this status declares the {@code Location} header, a string. */
  private static void assertDeclaresTheLocationHeader(Operation operation, String status) {
    final var response = operation.getResponses().get(status);
    assertThat(response).as("the %s response of %s", status, operation.getOperationId()).isNotNull();
    assertThat(response.getHeaders()).as("its headers").isNotNull().containsKey("Location");
    final var location = response.getHeaders().get("Location");
    assertThat(location.getSchema()).as("the schema of its Location header").isNotNull();
    assertThat(location.getSchema().getType()).as("its type").isEqualTo("string");
    assertThat(location.getDescription()).as("the description of Location").isNotBlank();
  }

  private static void assertDeclaresTheInvalidRequestProblem(OpenAPI openApi, Operation operation) {
    final var response = operation.getResponses().get("400");
    assertThat(response).as("the 400 response of %s", operation.getOperationId()).isNotNull();
    final var content = response.getContent().get("application/problem+json");
    assertThat(content).as("its application/problem+json content").isNotNull();
    Schema<?> schema = content.getSchema();
    if (schema.get$ref() != null) {
      schema =
          openApi
              .getComponents()
              .getSchemas()
              .get(schema.get$ref().substring(COMPONENT_SCHEMA_REF.length()));
    }
    assertThat(schema).as("its schema").isNotNull();
    assertThat(schema.getProperties().get("type").getEnum())
        .containsExactly("urn:rest-hero:problem:invalid-request");
  }
}
