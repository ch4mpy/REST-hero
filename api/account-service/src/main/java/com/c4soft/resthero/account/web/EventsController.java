package com.c4soft.resthero.account.web;

import java.util.Arrays;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.c4soft.resthero.commons.events.ResourceType;
import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Tag(name = "AccountServiceEvents")
@RestController
@RequestMapping(
    produces = {MediaType.APPLICATION_PROBLEM_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
@Observed
@Slf4j
public class EventsController {
  public static final String BASE_PATH = "/events";

  @GetMapping(BASE_PATH + "/resource-types")
  @PreAuthorize("isAuthenticated()")
  public List<ResourceType> listResourceTypes() {
    return List.of(ResourceType.values());
  }

}
