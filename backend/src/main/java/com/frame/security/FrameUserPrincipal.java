package com.frame.security;

import java.util.UUID;

/** Authenticated-principal shape carried on the SecurityContext for every JWT-authenticated request. */
public record FrameUserPrincipal(UUID id, String email) {}
