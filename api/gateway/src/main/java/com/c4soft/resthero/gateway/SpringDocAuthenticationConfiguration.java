/**
 *
 */
package com.c4soft.resthero.gateway;

import java.util.List;
import java.util.Objects;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.Constants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcClientProperties;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import com.c4soft.resthero.problem.ProblemType;

/**
 * Add to the OpenAPI specification the end-points to:
 * <ul>
 * <li>initiate an OAuth2 Authorization Code flow for each registration with
 * authorization_code</li>
 * <li>perform <a href=
 * "https://openid.net/specs/openid-connect-rpinitiated-1_0.html">RP-Intiated
 * Logout</a></li>
 * </ul>
 *
 * <p>
 * Both declare the optional headers spring-addons reads for the return address
 * ({@code X-POST-LOGIN-SUCCESS-URI} and {@code X-POST-LOGIN-FAILURE-URI}, then
 * {@code X-POST-LOGOUT-SUCCESS-URI}) and the {@code Location} header of their answer: the
 * generated client takes the headers as arguments, with no interceptor, and the frontend
 * navigates to that {@code Location}.
 * </p>
 *
 * <p>
 * The spec declares the gateway's public path as its only server: fixed, so the generated spec
 * doesn't change with the ports of the build. With this annotation, springdoc no longer sets its
 * default {@code info}, which the spec requires: it is declared here, with the same values.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = Constants.DEFAULT_TITLE, version = Constants.DEFAULT_VERSION),
        servers = @Server(url = "/gateway"))
class SpringDocAuthenticationConfiguration {

    /** The problem a refused return address gets, on login and logout. */
    static final String INVALID_REQUEST_PROBLEM_SCHEMA = "InvalidRequestProblem";

    private static final String BAD_REQUEST = Integer.toString(HttpStatus.BAD_REQUEST.value());

    @Bean
    OpenApiCustomizer springDocAuthCustomizer(
            InMemoryClientRegistrationRepository clientRegistrationRepository) {
        return openapi -> {
            if (openapi.getComponents() == null) {
                openapi.setComponents(new Components());
            }
            openapi.getComponents().addSchemas(INVALID_REQUEST_PROBLEM_SCHEMA, invalidRequestProblemSchema());

            // Add a "path" for each registration with the authorization_code grant type
            clientRegistrationRepository.forEach(registration -> {
                if (Objects
                        .equals(
                                AuthorizationGrantType.AUTHORIZATION_CODE,
                                registration.getAuthorizationGrantType())) {
                    openapi
                            .getPaths()
                            .addPathItem(
                                    "/oauth2/authorization/%s".formatted(registration.getRegistrationId()),
                                    new PathItem()
                                            .get(
                                                    new Operation()
                                                            .tags(List.of("Gateway"))
                                                            .operationId(
                                                                    "startLoginWith%s%s"
                                                                            .formatted(
                                                                                    registration
                                                                                            .getRegistrationId()
                                                                                            .substring(0, 1)
                                                                                            .toUpperCase(),
                                                                                    registration
                                                                                            .getRegistrationId()
                                                                                            .substring(1)
                                                                                            .replaceAll("-", "")))
                                                            .addParametersItem(returnAddressHeader(
                                                                    SpringAddonsOidcClientProperties.POST_AUTHENTICATION_SUCCESS_URI_HEADER,
                                                                    "Where the browser is sent once logged in"))
                                                            .addParametersItem(returnAddressHeader(
                                                                    SpringAddonsOidcClientProperties.POST_AUTHENTICATION_FAILURE_URI_HEADER,
                                                                    "Where the browser is sent when the login fails"))
                                                            .responses(
                                                                    new ApiResponses()
                                                                            .addApiResponse(
                                                                                    Integer.valueOf(
                                                                                            HttpStatus.OK.value())
                                                                                            .toString(),
                                                                                    new ApiResponse()
                                                                                            .description(HttpStatus.OK.name())
                                                                                            .addHeaderObject(
                                                                                                    HttpHeaders.LOCATION,
                                                                                                    locationHeader()))
                                                                            .addApiResponse(
                                                                                    BAD_REQUEST,
                                                                                    invalidReturnAddressResponse()))
                                                            .description(
                                                                    "Initiate an authorization code flow with the URI to follow in the response's Location header")));
                }
            });

            // Add a "path" for logout
            openapi
                    .getPaths()
                    .addPathItem(
                            "/logout",
                            new PathItem()
                                    .post(
                                            new Operation()
                                                    .tags(List.of("Gateway"))
                                                    .operationId("logout")
                                                    .addParametersItem(returnAddressHeader(
                                                            SpringAddonsOidcClientProperties.POST_LOGOUT_SUCCESS_URI_HEADER,
                                                            "Where the browser is sent once logged out"))
                                                    .responses(
                                                            new ApiResponses()
                                                                    .addApiResponse(
                                                                            Integer.valueOf(HttpStatus.ACCEPTED.value())
                                                                                    .toString(),
                                                                            new ApiResponse()
                                                                                    .description(HttpStatus.ACCEPTED.name())
                                                                                    .addHeaderObject(
                                                                                            HttpHeaders.LOCATION,
                                                                                            locationHeader()))
                                                                    .addApiResponse(
                                                                            BAD_REQUEST,
                                                                            invalidReturnAddressResponse()))
                                                    .description("logout")));
        };
    }

    /**
     * An optional request header holding a return address, which the gateway checks against its
     * allowed patterns ({@code post-login-allowed-uri-patterns}, {@code post-logout-allowed-uri-patterns}).
     */
    private static Parameter returnAddressHeader(String name, String redirection) {
        return new Parameter()
                .in("header")
                .name(name)
                .required(false)
                .description("%s. Refused with %s %s outside the gateway's allowed patterns."
                        .formatted(redirection, HttpStatus.BAD_REQUEST.value(), ProblemType.INVALID_REQUEST.uri()))
                .schema(new StringSchema().format("uri"));
    }

    /** The {@code Location} of a login or logout answer. */
    private static Header locationHeader() {
        return new Header()
                .description("The URI to navigate to: a new navigation of the browser, not a request of its HTTP client")
                .schema(new StringSchema());
    }

    /**
     * A return address ({@code X-POST-LOGIN-SUCCESS-URI}, {@code X-POST-LOGOUT-SUCCESS-URI}...)
     * outside the allowed patterns: refused, without {@code Location}, nothing changed.
     */
    private static ApiResponse invalidReturnAddressResponse() {
        return new ApiResponse()
                .description("%s: the return address isn't allowed (problem %s)"
                        .formatted(HttpStatus.BAD_REQUEST.name(), ProblemType.INVALID_REQUEST.uri()))
                .content(new Content()
                        .addMediaType(
                                MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                new io.swagger.v3.oas.models.media.MediaType()
                                        .schema(new Schema<>().$ref(
                                                Components.COMPONENTS_SCHEMAS_REF
                                                        + INVALID_REQUEST_PROBLEM_SCHEMA))));
    }

    /** The RFC 9457 members the gateway writes, its {@code type} limited to that one problem. */
    private static Schema<?> invalidRequestProblemSchema() {
        final var type = new StringSchema();
        type.setEnum(List.of(ProblemType.INVALID_REQUEST.uri().toString()));
        return new ObjectSchema()
                .description("The gateway refused the request (problem %s)"
                        .formatted(ProblemType.INVALID_REQUEST.uri()))
                .addProperty("type", type)
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema())
                .required(List.of("type", "title", "status", "detail", "instance"));
    }
}
