package com.alphabank.backend.statemachine;

import static com.alphabank.backend.enums.ApplicationStatus.APPROVED;
import static com.alphabank.backend.enums.ApplicationStatus.AUTO_CHECK;
import static com.alphabank.backend.enums.ApplicationStatus.CANCELLED;
import static com.alphabank.backend.enums.ApplicationStatus.COLLECTING;
import static com.alphabank.backend.enums.ApplicationStatus.COUNTER_OFFER;
import static com.alphabank.backend.enums.ApplicationStatus.DRAFT;
import static com.alphabank.backend.enums.ApplicationStatus.INFO_REQUESTED;
import static com.alphabank.backend.enums.ApplicationStatus.ISSUED;
import static com.alphabank.backend.enums.ApplicationStatus.REJECTED;
import static com.alphabank.backend.enums.ApplicationStatus.SIGNING;
import static com.alphabank.backend.enums.ApplicationStatus.SUBMITTED;
import static com.alphabank.backend.enums.ApplicationStatus.UNDERWRITING;
import static com.alphabank.backend.enums.TransitionInitiator.CLIENT;
import static com.alphabank.backend.enums.TransitionInitiator.SYSTEM;
import static com.alphabank.backend.enums.TransitionInitiator.UNDERWRITER;

import java.util.List;
import java.util.Set;

import com.alphabank.backend.enums.ApplicationAction;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.UserRole;

public final class ApplicationTransitions {

    public static final ApplicationStatus INITIAL = DRAFT;

    public static final Set<ApplicationStatus> FINAL = Set.of(REJECTED, ISSUED, CANCELLED);

    public static final List<Transition> TRANSITIONS = List.of(
            new Transition(2, DRAFT, COLLECTING, CLIENT),
            new Transition(3, COLLECTING, COLLECTING, CLIENT),
            new Transition(4, COLLECTING, SUBMITTED, CLIENT),
            new Transition(5, SUBMITTED, AUTO_CHECK, SYSTEM),
            new Transition(6, AUTO_CHECK, APPROVED, SYSTEM),
            new Transition(7, AUTO_CHECK, COUNTER_OFFER, SYSTEM),
            new Transition(8, AUTO_CHECK, REJECTED, SYSTEM),
            new Transition(9, AUTO_CHECK, UNDERWRITING, SYSTEM),
            new Transition(10, UNDERWRITING, INFO_REQUESTED, UNDERWRITER),
            new Transition(11, INFO_REQUESTED, UNDERWRITING, SYSTEM),
            new Transition(12, UNDERWRITING, UNDERWRITING, UNDERWRITER),
            new Transition(13, UNDERWRITING, APPROVED, UNDERWRITER),
            new Transition(13, UNDERWRITING, COUNTER_OFFER, UNDERWRITER),
            new Transition(13, UNDERWRITING, REJECTED, UNDERWRITER),
            new Transition(14, APPROVED, SIGNING, CLIENT),
            new Transition(14, COUNTER_OFFER, SIGNING, CLIENT),
            new Transition(15, SIGNING, ISSUED, CLIENT),
            new Transition(16, DRAFT, CANCELLED, CLIENT),
            new Transition(16, COLLECTING, CANCELLED, CLIENT),
            new Transition(16, INFO_REQUESTED, CANCELLED, CLIENT),
            new Transition(16, APPROVED, CANCELLED, CLIENT),
            new Transition(16, COUNTER_OFFER, CANCELLED, CLIENT),
            new Transition(17, INFO_REQUESTED, CANCELLED, SYSTEM));

    public static final List<ActionRule> ACTIONS = List.of(
            new ActionRule(ApplicationAction.EDIT, UserRole.CLIENT, Set.of(DRAFT, COLLECTING)),
            new ActionRule(ApplicationAction.EVALUATE, UserRole.CLIENT, Set.of(DRAFT)),
            new ActionRule(ApplicationAction.CLOSE_SPOT, UserRole.CLIENT, Set.of(COLLECTING, INFO_REQUESTED)),
            new ActionRule(ApplicationAction.SUBMIT, UserRole.CLIENT, Set.of(COLLECTING)),
            new ActionRule(ApplicationAction.ACCEPT, UserRole.CLIENT, Set.of(APPROVED, COUNTER_OFFER)),
            new ActionRule(ApplicationAction.SIGN, UserRole.CLIENT, Set.of(SIGNING)),
            new ActionRule(ApplicationAction.CANCEL, UserRole.CLIENT,
                    Set.of(DRAFT, COLLECTING, INFO_REQUESTED, APPROVED, COUNTER_OFFER)),
            new ActionRule(ApplicationAction.REVIEW_SPOT, UserRole.UNDERWRITER, Set.of(UNDERWRITING)),
            new ActionRule(ApplicationAction.ADD_SPOT, UserRole.UNDERWRITER, Set.of(UNDERWRITING)),
            new ActionRule(ApplicationAction.DECIDE, UserRole.UNDERWRITER, Set.of(UNDERWRITING)));

    private ApplicationTransitions() {
    }
}
