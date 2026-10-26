package com.c4soft.resthero.problem;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The module is a starter: nothing of it is in the component scan of a service, nor of this test,
 * whose bare {@code @SpringBootConfiguration} scans nothing (its controller is imported by name).
 * A {@code @WebMvcTest} slice gets its advices from the module's own
 * {@code AutoConfigureWebMvc.imports} file, which this test proves.
 */
@WebMvcTest(controllers = ProblemAutoConfigurationTest.ThrowingController.class)
@WithMockUser
@Import(ProblemAutoConfigurationTest.ThrowingController.class)
class ProblemAutoConfigurationTest {

  @SpringBootConfiguration
  static class TestApplication {
  }

  @RestController
  static class ThrowingController {
    @GetMapping("/business-rule")
    void businessRule() throws ApiProblemException {
      throw new ApiProblemException(
          ProblemType.ACCOUNT_NOT_FOUND,
          "No such account",
          Map.of("iban", "FR761111222233334441"));
    }

    @GetMapping("/concurrent-update")
    void concurrentUpdate() {
      throw new ObjectOptimisticLockingFailureException(String.class, 42L);
    }
  }

  @Autowired
  MockMvc mockMvc;

  @Test
  void givenAControllerThrowingAnApiProblem_whenGet_thenTheTypedProblemIsWritten() throws Exception {
    mockMvc
        .perform(get("/business-rule"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:rest-hero:problem:account-not-found"))
        .andExpect(jsonPath("$.parameters.iban").value("FR761111222233334441"));
  }

  @Test
  void givenAControllerLosingAnOptimisticLock_whenGet_thenConcurrentModificationIsWritten()
      throws Exception {
    mockMvc
        .perform(get("/concurrent-update"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:rest-hero:problem:concurrent-modification"))
        .andExpect(jsonPath("$.parameters.entity").value("String"));
  }
}
