package com.c4soft.resthero.problem;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * The problems of a resource server filter chain, shared by the REST API and the gateway: a {@code 401} {@link ProblemType#UNAUTHENTICATED} with a {@code Bearer}
 * challenge, for no token (exception translation) as for a rejected one
 * ({@code BearerTokenAuthenticationFilter} has its own entry point), and a {@code 403}
 * {@link ProblemType#ACCESS_DENIED}. Each application applies it from its spring-addons
 * post-processor, applied last to the chain.
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public final class ProblemResourceServerSecurity {

  private ProblemResourceServerSecurity() {}

  public static HttpSecurity apply(HttpSecurity http, ProblemResponses problems) {
    final var entryPoint = new ProblemAuthenticationEntryPoint(problems, "Bearer");
    return http
        .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint)
            .accessDeniedHandler(new ProblemAccessDeniedHandler(problems)))
        .oauth2ResourceServer(resourceServer -> resourceServer.authenticationEntryPoint(entryPoint));
  }
}
