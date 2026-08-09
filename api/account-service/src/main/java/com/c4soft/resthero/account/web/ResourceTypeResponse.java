package com.c4soft.resthero.account.web;

import com.c4soft.resthero.commons.events.ResourceType;
import jakarta.validation.constraints.NotNull;

public record ResourceTypeResponse(@NotNull ResourceType resourceType) {

}
