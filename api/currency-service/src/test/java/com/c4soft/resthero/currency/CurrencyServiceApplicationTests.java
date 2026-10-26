package com.c4soft.resthero.currency;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "server.ssl.enabled=false")
@AutoConfigureMockMvc
class CurrencyServiceApplicationTests {

  @Autowired
  MockMvc mockMvc;

  @Autowired
  ObjectMapper json;

  @Test
  void contextLoads() {}

}
