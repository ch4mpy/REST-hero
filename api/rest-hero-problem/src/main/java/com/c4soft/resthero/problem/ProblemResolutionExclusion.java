package com.c4soft.resthero.problem;

/**
 * An exception {@link ProblemHandlerExceptionResolver} must leave alone, so that it propagates out
 * of the {@code DispatcherServlet} to the filter that handles it. Declared as a bean by the
 * application that needs one: the gateway's {@code ClientAuthorizationRequiredException}, thrown
 * by {@code TokenRelay=} inside the handler and answered {@code 401} by its
 * {@code RestfulOAuth2Filter}.
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@FunctionalInterface
public interface ProblemResolutionExclusion {

  boolean excludes(Exception ex);
}
