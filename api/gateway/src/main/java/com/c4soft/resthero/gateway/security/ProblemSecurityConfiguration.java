package com.c4soft.resthero.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.session.InvalidSessionStrategy;
import org.springframework.security.web.util.ThrowableAnalyzer;
import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcProperties;
import com.c4_soft.springaddons.security.oidc.starter.synchronised.client.ClientSynchronizedHttpSecurityPostProcessor;
import com.c4_soft.springaddons.security.oidc.starter.synchronised.resourceserver.ResourceServerSynchronizedHttpSecurityPostProcessor;
import lombok.extern.slf4j.Slf4j;
import com.c4soft.resthero.problem.ProblemAccessDeniedHandler;
import com.c4soft.resthero.problem.ProblemAuthenticationEntryPoint;
import com.c4soft.resthero.problem.ProblemResolutionExclusion;
import com.c4soft.resthero.problem.ProblemResourceServerSecurity;
import com.c4soft.resthero.problem.ProblemResponses;
import com.c4soft.resthero.problem.ProblemType;

/**
 * The gateway's security errors as problems, in place of spring-addons' plain
 * text bodies, which its properties can't change:
 *
 * <ul>
 * <li>no session on a route that needs one: {@code 401} {@link ProblemType#UNAUTHENTICATED}, with
 * no login URL nor {@code Location} (the SPA logs in through {@code /oauth2/authorization});</li>
 * <li>a session cookie the gateway no longer knows, on any route but {@code /me}
 * ({@code invalid-session.pass-through-patterns}): the same {@code 401}. Unlike spring-addons'
 * strategy, no session is opened: the requests the SPA sends in parallel with a stale cookie don't
 * each leave an orphaned one behind, and the SPA's next call to {@code /me}, which a {@code 401}
 * triggers, opens the new one;</li>
 * <li>an access rule or the CSRF protection refusing an authenticated request: {@code 403}
 * {@link ProblemType#ACCESS_DENIED};</li>
 * <li>a logout whose return address the post-logout patterns don't allow: {@code 400}
 * {@link ProblemType#INVALID_REQUEST}, before anything is logged out
 * ({@link PostLogoutUriFilter}; a login's is refused by the {@code RestfulOAuth2Filter});</li>
 * <li>the resource server filter chain, which handles every route outside spring-addons' client
 * {@code security-matchers}: the same {@code 401} problem (with a {@code Bearer} challenge) for
 * what its {@code permit-all} doesn't list, an unknown route included. It ignores the session: a
 * logged-in user gets a {@code 404} on an unknown route under the client's prefixes only.</li>
 * </ul>
 *
 * <p>
 * What anonymous requests may reach is listed explicitly in the two chains' {@code permit-all}
 * ({@code application.yml}); nothing here widens it.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Configuration(proxyBeanMethods = false)
@Slf4j
public class ProblemSecurityConfiguration {

  /** Replaces spring-addons' default entry point of the client filter chain. */
  @Bean
  AuthenticationEntryPoint authenticationEntryPoint(ProblemResponses problems) {
    return new ProblemAuthenticationEntryPoint(problems, null);
  }

  /** Replaces spring-addons' invalid-session strategy. */
  @Bean
  InvalidSessionStrategy invalidSessionStrategy(ProblemResponses problems) {
    return (request, response) -> {
      log.debug("Invalid session on {}", request.getRequestURI());
      problems.write(request, response, ProblemType.UNAUTHENTICATED);
    };
  }

  /**
   * The client chain's only {@link ClientSynchronizedHttpSecurityPostProcessor} (spring-addons
   * takes a single one): its access-denied handler, and the {@link PostLogoutUriFilter} before
   * {@link LogoutFilter} (so after the CSRF protection).
   */
  @Bean
  ClientSynchronizedHttpSecurityPostProcessor clientChainProblems(ProblemResponses problems,
      SpringAddonsOidcProperties addonsProperties) {
    return http -> http
        .exceptionHandling(
            exceptions -> exceptions.accessDeniedHandler(new ProblemAccessDeniedHandler(problems)))
        .addFilterBefore(new PostLogoutUriFilter(problems, addonsProperties), LogoutFilter.class);
  }

  /**
   * {@code TokenRelay=} authorizes the client inside the handler: with no authorized client any more
   * ({@code ClientAuthorizationRequiredException}) or a refresh the authorization server refused
   * (a plain {@link ClientAuthorizationException}), the exception must reach the gateway's
   * {@code RestfulOAuth2Filter}, which answers {@code 401} {@code unauthenticated}, rather than be
   * written as a {@code 500} by the shared exception resolver.
   */
  @Bean
  ProblemResolutionExclusion clientAuthorizationFailures() {
    final var analyzer = new ThrowableAnalyzer();
    return ex -> analyzer.getFirstThrowableOfType(ClientAuthorizationException.class,
        analyzer.determineCauseChain(ex)) != null;
  }

  /**
   * The resource server chain's {@code 401}, for no token as for a rejected one, and its
   * {@code 403}, as problems instead of spring-addons' bare {@code sendError}: the same as
   * the REST API's ({@link ProblemResourceServerSecurity}).
   */
  @Bean
  ResourceServerSynchronizedHttpSecurityPostProcessor resourceServerProblems(
      ProblemResponses problems) {
    return http -> ProblemResourceServerSecurity.apply(http, problems);
  }
}
