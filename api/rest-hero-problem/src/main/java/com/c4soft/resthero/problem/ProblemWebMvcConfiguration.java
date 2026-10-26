package com.c4soft.resthero.problem;

import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;
import tools.jackson.databind.json.JsonMapper;

/**
 * Inserts {@link ProblemHandlerExceptionResolver} right before Spring's
 * {@link DefaultHandlerExceptionResolver}, which would otherwise answer framework rejections with a
 * bare {@code sendError}.
 *
 * <p>
 * Not a {@link HandlerExceptionResolver} bean: the {@code DispatcherServlet} would run it at its own
 * order, before or after the whole composite, not between its resolvers.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Configuration(proxyBeanMethods = false)
public class ProblemWebMvcConfiguration implements WebMvcConfigurer {

  private final ProblemResponses problems;

  private final List<ProblemResolutionExclusion> exclusions;

  public ProblemWebMvcConfiguration(JsonMapper jsonMapper,
      ObjectProvider<ProblemResolutionExclusion> exclusions) {
    this.problems = new ProblemResponses(jsonMapper);
    this.exclusions = exclusions.orderedStream().toList();
  }

  /** Declared here, rather than scanned, to be part of the {@code @WebMvcTest} slices too. */
  @Bean
  ProblemResponses problemResponses() {
    return problems;
  }

  @Override
  public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
    int index = resolvers.size();
    for (int i = 0; i < resolvers.size(); ++i) {
      if (resolvers.get(i) instanceof DefaultHandlerExceptionResolver) {
        index = i;
        break;
      }
    }
    resolvers.add(index, new ProblemHandlerExceptionResolver(problems, exclusions));
  }
}
