package com.alphabank.backend.statemachine;

import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.TransitionInitiator;

public record Transition(int number, ApplicationStatus from, ApplicationStatus to, TransitionInitiator initiator) {
}
