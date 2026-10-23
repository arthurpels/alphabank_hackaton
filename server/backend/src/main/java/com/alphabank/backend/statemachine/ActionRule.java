package com.alphabank.backend.statemachine;

import java.util.Set;

import com.alphabank.backend.enums.ApplicationAction;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.UserRole;

public record ActionRule(ApplicationAction action, UserRole role, Set<ApplicationStatus> allowedIn) {
}
