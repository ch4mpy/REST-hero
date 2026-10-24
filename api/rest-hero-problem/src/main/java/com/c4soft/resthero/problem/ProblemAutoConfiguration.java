package com.c4soft.resthero.problem;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Registers the components of this module in the applications that depend on it, and in the
 * {@code @WebMvcTest} slices (see the {@code AutoConfigureWebMvc.imports} file of this module).
 *
 * <p>
 * The applications of this project are rooted in {@code com.c4soft.resthero.<service>}, so that
 * {@code com.c4soft.resthero.problem} is none of their component scans: unlike a plain library, this
 * module is a starter.
 * </p>
 *
 * <p>
 * Runs before Spring Boot's error handling, so that {@link ApiErrorController} is there when
 * {@code BasicErrorController} checks whether an {@code ErrorController} exists and backs off.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@AutoConfiguration(
    beforeName = "org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration")
@Import({ProblemWebMvcConfiguration.class, ApiErrorController.class, ApiExceptionHandler.class,
    DataAccessProblemHandler.class})
public class ProblemAutoConfiguration {

  /** Only with springdoc on the classpath (the {@code openapi} Maven profile). */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(OpenApiCustomizer.class)
  static class OpenApiConfiguration {
    @Bean
    ProblemTypeOpenApiCustomizer problemTypeOpenApiCustomizer() {
      return new ProblemTypeOpenApiCustomizer();
    }
  }
}
