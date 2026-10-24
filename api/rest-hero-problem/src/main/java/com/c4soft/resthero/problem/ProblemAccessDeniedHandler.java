package com.c4soft.resthero.problem;

import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Answers an authenticated request an access rule refused, or a request the CSRF protection
 * rejected, with a {@code 403} {@link ProblemType#ACCESS_DENIED}.
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

  private final ProblemResponses problems;

  public ProblemAccessDeniedHandler(ProblemResponses problems) {
    this.problems = problems;
  }

  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response,
      AccessDeniedException accessDeniedException) throws IOException {
    problems.write(request, response, ProblemType.ACCESS_DENIED);
  }
}
