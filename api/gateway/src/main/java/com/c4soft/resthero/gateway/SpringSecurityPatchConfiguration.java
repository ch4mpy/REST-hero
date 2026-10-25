package com.c4soft.resthero.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.util.ThrowableAnalyzer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.c4_soft.springaddons.security.oidc.starter.properties.InvalidRedirectionUriException;
import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcClientProperties;
import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcProperties;
import lombok.extern.slf4j.Slf4j;
import com.c4soft.resthero.problem.ProblemResponses;
import com.c4soft.resthero.problem.ProblemType;

/**
 * Workaround https://github.com/spring-projects/spring-security/issues/17754
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Component
public class SpringSecurityPatchConfiguration implements BeanPostProcessor {

  private final ObjectProvider<ProblemResponses> problems;

  private final ObjectProvider<SpringAddonsOidcProperties> addonsProperties;

  public SpringSecurityPatchConfiguration(ObjectProvider<ProblemResponses> problems,
      ObjectProvider<SpringAddonsOidcProperties> addonsProperties) {
    this.problems = problems;
    this.addonsProperties = addonsProperties;
  }

  @Override
  public @Nullable Object postProcessBeforeInitialization(
      @Nullable Object bean,
      @Nullable String beanName) throws BeansException {
    if (bean instanceof OAuth2AuthorizationRequestRedirectFilter oauth2AuthorizationRequestRedirectFilter) {
      return new RestfulOAuth2Filter(oauth2AuthorizationRequestRedirectFilter, problems::getObject,
          () -> addonsProperties.getObject().getClient().getPostLoginAllowedUriPatterns());
    }
    return bean;
  }

  @Slf4j
  public static class RestfulOAuth2Filter extends OAuth2AuthorizationRequestRedirectFilter {

    private final ThrowableAnalyzer throwableAnalyzer;

    private final RedirectStrategy authorizationRedirectStrategy;

    /** Resolved at request time: this filter is built by a {@link BeanPostProcessor}. */
    private final Supplier<ProblemResponses> problems;

    private final OAuth2AuthorizationRequestResolver authorizationRequestResolver;

    private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository;

    private final AuthenticationFailureHandler authenticationFailureHandler;

    /**
     * Constructs an {@code OAuth2AuthorizationRequestRedirectFilter} using the provided
     * parameters.
     *
     * @param authorizationRequestResolver the resolver used for resolving
     *                                     authorization requests
     */
    public RestfulOAuth2Filter(
        OAuth2AuthorizationRequestResolver authorizationRequestResolver,
        AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository,
        RedirectStrategy authorizationRedirectStrategy,
        AuthenticationFailureHandler authenticationFailureHandler,
        ThrowableAnalyzer throwableAnalyzer,
        Supplier<ProblemResponses> problems) {
      super(authorizationRequestResolver);
      this.problems = problems;
      this.authorizationRequestResolver = authorizationRequestResolver;

      this.throwableAnalyzer = throwableAnalyzer;

      this.authorizationRedirectStrategy = authorizationRedirectStrategy;
      super.setAuthorizationRedirectStrategy(authorizationRedirectStrategy);

      this.authorizationRequestRepository = authorizationRequestRepository;
      super.setAuthorizationRequestRepository(authorizationRequestRepository);

      this.authenticationFailureHandler = authenticationFailureHandler;
      super.setAuthenticationFailureHandler(authenticationFailureHandler);
    }

    /**
     * Replaces {@code other}, its resolver checking the login's return addresses first
     * ({@link PostLoginUriCheckingResolver}).
     *
     * @param postLoginAllowedUriPatterns spring-addons' {@code post-login-allowed-uri-patterns},
     *                                    resolved at request time
     */
    public RestfulOAuth2Filter(OAuth2AuthorizationRequestRedirectFilter other,
        Supplier<ProblemResponses> problems, Supplier<List<Pattern>> postLoginAllowedUriPatterns) {
      this(
          new PostLoginUriCheckingResolver(
              getField("authorizationRequestResolver", other), postLoginAllowedUriPatterns),
          getField("authorizationRequestRepository", other),
          getField("authorizationRedirectStrategy", other),
          getField("authenticationFailureHandler", other),
          getField("throwableAnalyzer", other),
          problems);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(String fieldName, Object target) {
      try {
        final var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(target);
      } catch (NoSuchFieldException | IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
      try {
        OAuth2AuthorizationRequest authorizationRequest = this.authorizationRequestResolver.resolve(request);
        if (authorizationRequest != null) {
          this.sendRedirectForAuthorization(request, response, authorizationRequest);
          return;
        }
      } catch (Exception ex) {
        // A return address (X-POST-LOGIN-SUCCESS-URI / -FAILURE-URI, or their parameters) outside
        // the allowed patterns: the client's request is invalid, not a server failure. Refused
        // without Location, failure handler nor saved authorization request, and, refused by
        // PostLoginUriCheckingResolver, before spring-addons' resolver opens or changes the
        // session; the address, which the client chose, isn't logged.
        if (this.throwableAnalyzer.getFirstThrowableOfType(InvalidRedirectionUriException.class,
            this.throwableAnalyzer.determineCauseChain(ex)) != null) {
          log.debug("Login refused on {}: return address not allowed", request.getRequestURI());
          this.problems.get().write(request, response, ProblemType.INVALID_REQUEST);
          return;
        }
        AuthenticationException wrappedException = new OAuth2AuthorizationRequestException(ex);
        this.authenticationFailureHandler.onAuthenticationFailure(
            request,
            response,
            wrappedException);
        return;
      }
      try {
        filterChain.doFilter(request, response);
      } catch (IOException ex) {
        throw ex;
      } catch (Throwable ex) {
        // The user must log in again: no authorized client any more
        // (ClientAuthorizationRequiredException), or its refresh refused by the authorization
        // server (a plain ClientAuthorizationException, invalid_grant: SSO session or refresh token
        // expired; the failure handler has already removed the client). Both answer 401, without
        // resolving an authorization request the SPA would not follow.
        Throwable[] causeChain = this.throwableAnalyzer.determineCauseChain(ex);
        ClientAuthorizationException authzEx = (ClientAuthorizationException) this.throwableAnalyzer
            .getFirstThrowableOfType(
                ClientAuthorizationException.class,
                causeChain);
        if (authzEx != null) {
          if ("401".equals(authzEx.getError().getErrorCode())) {
            SecurityContextHolder
                .getContext()
                .setAuthentication(new AnonymousAuthenticationToken("anonymous", "", List.of()));
          }
          this.sendUnauthorized(request, response);
          return;
        }
        if (ex instanceof ServletException) {
          throw (ServletException) ex;
        }
        if (ex instanceof RuntimeException) {
          throw (RuntimeException) ex;
        }
        throw new RuntimeException(ex);
      }
    }

    /**
     * The user must log in again (refresh token expired or revoked): a {@code 401}
     * {@link ProblemType#UNAUTHENTICATED}, without the authorization request's URI, which the SPA
     * never follows (it logs in through {@code /oauth2/authorization}).
     */
    private void sendUnauthorized(
        HttpServletRequest request,
        HttpServletResponse response) throws IOException {
      this.problems.get().write(request, response, ProblemType.UNAUTHENTICATED);
    }

    private void sendRedirectForAuthorization(
        HttpServletRequest request,
        HttpServletResponse response,
        OAuth2AuthorizationRequest authorizationRequest) throws IOException {
      if (AuthorizationGrantType.AUTHORIZATION_CODE.equals(authorizationRequest.getGrantType())) {
        this.authorizationRequestRepository.saveAuthorizationRequest(
            authorizationRequest,
            request,
            response);
      }
      this.authorizationRedirectStrategy.sendRedirect(
          request,
          response,
          authorizationRequest.getAuthorizationRequestUri());
    }

    private static final class OAuth2AuthorizationRequestException extends AuthenticationException {
      private static final long serialVersionUID = 407359823633913542L;

      OAuth2AuthorizationRequestException(Throwable cause) {
        super(cause.getMessage(), cause);
      }

    }

  }

  /**
   * Refuses, with spring-addons' {@link InvalidRedirectionUriException}, a login whose success or
   * failure address the post-login patterns don't allow, before spring-addons' resolver runs: on
   * every request, that one opens a session, then checks the success address and stores it before
   * it checks the failure address, so a login it refuses would leave the session opened or
   * changed. The addresses are picked as it picks them (the header when present, otherwise the
   * first parameter; a blank one ignored) and checked with its own method and patterns: what
   * passes here passes there.
   */
  static final class PostLoginUriCheckingResolver implements OAuth2AuthorizationRequestResolver {

    private final OAuth2AuthorizationRequestResolver delegate;

    private final Supplier<List<Pattern>> allowedUriPatterns;

    PostLoginUriCheckingResolver(OAuth2AuthorizationRequestResolver delegate,
        Supplier<List<Pattern>> allowedUriPatterns) {
      this.delegate = delegate;
      this.allowedUriPatterns = allowedUriPatterns;
    }

    @Override
    public @Nullable OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
      checkPostLoginUris(request);
      return delegate.resolve(request);
    }

    @Override
    public @Nullable OAuth2AuthorizationRequest resolve(HttpServletRequest request,
        String clientRegistrationId) {
      checkPostLoginUris(request);
      return delegate.resolve(request, clientRegistrationId);
    }

    private void checkPostLoginUris(HttpServletRequest request) {
      check(request, SpringAddonsOidcClientProperties.POST_AUTHENTICATION_SUCCESS_URI_HEADER,
          SpringAddonsOidcClientProperties.POST_AUTHENTICATION_SUCCESS_URI_PARAM);
      check(request, SpringAddonsOidcClientProperties.POST_AUTHENTICATION_FAILURE_URI_HEADER,
          SpringAddonsOidcClientProperties.POST_AUTHENTICATION_FAILURE_URI_PARAM);
    }

    private void check(HttpServletRequest request, String header, String param) {
      @Nullable String uri = request.getHeader(header);
      if (uri == null) {
        final var values = request.getParameterValues(param);
        uri = values == null || values.length < 1 ? null : values[0];
      }
      if (uri != null && StringUtils.hasText(uri)
          && !SpringAddonsOidcClientProperties.isAllowedRedirectionUri(uri,
              allowedUriPatterns.get())) {
        throw new InvalidRedirectionUriException(uri);
      }
    }
  }
}
