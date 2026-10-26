package com.c4soft.resthero.card;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.c4_soft.springaddons.security.oidc.starter.synchronised.resourceserver.ResourceServerSynchronizedHttpSecurityPostProcessor;
import com.c4soft.resthero.problem.ProblemResourceServerSecurity;
import com.c4soft.resthero.problem.ProblemResponses;
import com.c4soft.resthero.problem.ProblemType;

/**
 * The resource server's {@code 401} and {@code 403} as problems ({@link ProblemType#UNAUTHENTICATED},
 * {@link ProblemType#ACCESS_DENIED}) instead of spring-addons' bare {@code sendError}, for no token
 * as for a rejected one ({@link ProblemResourceServerSecurity}, shared with the gateway). The
 * post-processor is applied last to the resource server's filter chain, so its exception handling
 * replaces spring-addons' entry point; the {@code Bearer} challenge is kept.
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Configuration(proxyBeanMethods = false)
public class ProblemSecurityConfiguration {

  @Bean
  ResourceServerSynchronizedHttpSecurityPostProcessor problemExceptionHandling(
      ProblemResponses problems) {
    return http -> ProblemResourceServerSecurity.apply(http, problems);
  }
}
