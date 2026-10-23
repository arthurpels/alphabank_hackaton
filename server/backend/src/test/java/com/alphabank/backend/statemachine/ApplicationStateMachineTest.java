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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.alphabank.backend.enums.ApplicationAction;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.TransitionInitiator;
import com.alphabank.backend.enums.UserRole;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.exception.ErrorCode;

class ApplicationStateMachineTest {

    private final ApplicationStateMachine stateMachine = new ApplicationStateMachine();

    static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(2, DRAFT, COLLECTING, CLIENT),
                Arguments.of(3, COLLECTING, COLLECTING, CLIENT),
                Arguments.of(4, COLLECTING, SUBMITTED, CLIENT),
                Arguments.of(5, SUBMITTED, AUTO_CHECK, SYSTEM),
                Arguments.of(6, AUTO_CHECK, APPROVED, SYSTEM),
                Arguments.of(7, AUTO_CHECK, COUNTER_OFFER, SYSTEM),
                Arguments.of(8, AUTO_CHECK, REJECTED, SYSTEM),
                Arguments.of(9, AUTO_CHECK, UNDERWRITING, SYSTEM),
                Arguments.of(10, UNDERWRITING, INFO_REQUESTED, UNDERWRITER),
                Arguments.of(11, INFO_REQUESTED, UNDERWRITING, SYSTEM),
                Arguments.of(12, UNDERWRITING, UNDERWRITING, UNDERWRITER),
                Arguments.of(13, UNDERWRITING, APPROVED, UNDERWRITER),
                Arguments.of(13, UNDERWRITING, COUNTER_OFFER, UNDERWRITER),
                Arguments.of(13, UNDERWRITING, REJECTED, UNDERWRITER),
                Arguments.of(14, APPROVED, SIGNING, CLIENT),
                Arguments.of(14, COUNTER_OFFER, SIGNING, CLIENT),
                Arguments.of(15, SIGNING, ISSUED, CLIENT),
                Arguments.of(16, DRAFT, CANCELLED, CLIENT),
                Arguments.of(16, COLLECTING, CANCELLED, CLIENT),
                Arguments.of(16, INFO_REQUESTED, CANCELLED, CLIENT),
                Arguments.of(16, APPROVED, CANCELLED, CLIENT),
                Arguments.of(16, COUNTER_OFFER, CANCELLED, CLIENT),
                Arguments.of(17, INFO_REQUESTED, CANCELLED, SYSTEM));
    }

    @ParameterizedTest(name = "переход {0}: {1} → {2} ({3})")
    @MethodSource("allowedTransitions")
    void allowsTransitionFromDocument(int number, ApplicationStatus from, ApplicationStatus to,
                                      TransitionInitiator initiator) {
        Transition transition = stateMachine.transition(from, to, initiator);

        assertThat(transition.number()).isEqualTo(number);
    }

    @Test
    void tableHasExactlyTheDocumentedTransitions() {
        assertThat(ApplicationTransitions.TRANSITIONS).hasSize((int) allowedTransitions().count());
    }

    static Stream<Arguments> forbiddenTransitions() {
        return Stream.of(
                Arguments.of(SUBMITTED, COLLECTING, CLIENT),
                Arguments.of(DRAFT, SUBMITTED, CLIENT),
                Arguments.of(COLLECTING, APPROVED, CLIENT),
                Arguments.of(AUTO_CHECK, APPROVED, CLIENT),
                Arguments.of(UNDERWRITING, APPROVED, CLIENT),
                Arguments.of(UNDERWRITING, APPROVED, SYSTEM),
                Arguments.of(APPROVED, ISSUED, CLIENT),
                Arguments.of(SIGNING, CANCELLED, CLIENT),
                Arguments.of(UNDERWRITING, CANCELLED, CLIENT),
                Arguments.of(INFO_REQUESTED, UNDERWRITING, CLIENT));
    }

    @ParameterizedTest(name = "{0} → {1} ({2}) запрещён")
    @MethodSource("forbiddenTransitions")
    void rejectsTransitionNotInDocument(ApplicationStatus from, ApplicationStatus to, TransitionInitiator initiator) {
        assertThatThrownBy(() -> stateMachine.transition(from, to, initiator))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.code()).isEqualTo(ErrorCode.INVALID_TRANSITION);
                    assertThat(api.currentStatus()).isEqualTo(from);
                });
    }

    @ParameterizedTest
    @EnumSource(value = ApplicationStatus.class, names = {"REJECTED", "ISSUED", "CANCELLED"})
    void finalStatusesHaveNoWayOut(ApplicationStatus finalStatus) {
        assertThat(stateMachine.isFinal(finalStatus)).isTrue();
        for (ApplicationStatus to : ApplicationStatus.values()) {
            for (TransitionInitiator initiator : TransitionInitiator.values()) {
                assertThat(stateMachine.canTransition(finalStatus, to, initiator)).isFalse();
            }
        }
        assertThat(stateMachine.availableActions(finalStatus, UserRole.CLIENT)).isEmpty();
        assertThat(stateMachine.availableActions(finalStatus, UserRole.UNDERWRITER)).isEmpty();
    }

    @Test
    void everyActionHasRule() {
        Arrays.stream(ApplicationAction.values()).forEach(action ->
                assertThat(ApplicationTransitions.ACTIONS).anyMatch(rule -> rule.action() == action));
    }

    @Test
    void clientActionsInCollecting() {
        assertThat(stateMachine.availableActions(COLLECTING, UserRole.CLIENT)).containsExactly(
                ApplicationAction.EDIT, ApplicationAction.CLOSE_SPOT, ApplicationAction.SUBMIT,
                ApplicationAction.CANCEL);
        assertThat(stateMachine.availableActions(COLLECTING, UserRole.UNDERWRITER)).isEmpty();
    }

    @Test
    void underwriterActionsOnlyInUnderwriting() {
        assertThat(stateMachine.availableActions(UNDERWRITING, UserRole.UNDERWRITER)).containsExactly(
                ApplicationAction.REVIEW_SPOT, ApplicationAction.ADD_SPOT, ApplicationAction.DECIDE);
        assertThat(stateMachine.availableActions(INFO_REQUESTED, UserRole.UNDERWRITER)).isEmpty();
    }

    @Test
    void actionInWrongStatusIs409() {
        assertThatThrownBy(() -> stateMachine.checkAction(SUBMITTED, ApplicationAction.EDIT, UserRole.CLIENT))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.INVALID_TRANSITION);
    }

    @Test
    void actionOfAnotherRoleIs403() {
        assertThatThrownBy(() -> stateMachine.checkAction(UNDERWRITING, ApplicationAction.DECIDE, UserRole.CLIENT))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }
}
