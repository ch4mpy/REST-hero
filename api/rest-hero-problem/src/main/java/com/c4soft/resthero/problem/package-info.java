/**
 * RFC 9457 problem details with a closed set of {@link com.c4soft.resthero.problem.ProblemType types}, so
 * that the frontend can localize errors from the generated client's enum instead of displaying
 * server-side messages, and the components writing every error of the REST API and the BFF as one
 * of them: {@link com.c4soft.resthero.problem.ProblemAuthenticationEntryPoint},
 * {@link com.c4soft.resthero.problem.ProblemAccessDeniedHandler}, wired by each application's security
 * configuration, and, picked up by the component scan of the applications rooted in
 * {@code com.c4soft.resthero}, {@link com.c4soft.resthero.problem.ProblemHandlerExceptionResolver} (the exceptions no
 * {@code @ExceptionHandler} handled) and {@link com.c4soft.resthero.problem.ApiErrorController} (what
 * still reaches {@code /error}).
 */
@NullMarked
package com.c4soft.resthero.problem;

import org.jspecify.annotations.NullMarked;
