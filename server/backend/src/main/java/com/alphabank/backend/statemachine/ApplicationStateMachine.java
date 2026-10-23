package com.alphabank.backend.statemachine;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.alphabank.backend.enums.ApplicationAction;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.TransitionInitiator;
import com.alphabank.backend.enums.UserRole;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.exception.ErrorCode;

@Component
public class ApplicationStateMachine {

    public Transition transition(ApplicationStatus from, ApplicationStatus to, TransitionInitiator initiator) {
        return findTransition(from, to, initiator)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TRANSITION,
                        "Переход " + from + " → " + to + " недопустим для " + initiator, from));
    }

    public boolean canTransition(ApplicationStatus from, ApplicationStatus to, TransitionInitiator initiator) {
        return findTransition(from, to, initiator).isPresent();
    }

    public void checkAction(ApplicationStatus status, ApplicationAction action, UserRole role) {
        ActionRule rule = ruleFor(action);
        if (rule.role() != role) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    rule.role() == UserRole.UNDERWRITER
                            ? "Действие доступно только андеррайтеру"
                            : "Действие доступно только клиенту");
        }
        if (!rule.allowedIn().contains(status)) {
            throw ApiException.invalidTransition(status, action.name());
        }
    }

    public List<ApplicationAction> availableActions(ApplicationStatus status, UserRole role) {
        return Arrays.stream(ApplicationAction.values())
                .map(ApplicationStateMachine::ruleFor)
                .filter(rule -> rule.role() == role && rule.allowedIn().contains(status))
                .map(ActionRule::action)
                .toList();
    }

    public boolean isFinal(ApplicationStatus status) {
        return ApplicationTransitions.FINAL.contains(status);
    }

    private static Optional<Transition> findTransition(ApplicationStatus from, ApplicationStatus to,
                                                      TransitionInitiator initiator) {
        return ApplicationTransitions.TRANSITIONS.stream()
                .filter(t -> t.from() == from && t.to() == to && t.initiator() == initiator)
                .findFirst();
    }

    private static ActionRule ruleFor(ApplicationAction action) {
        return ApplicationTransitions.ACTIONS.stream()
                .filter(rule -> rule.action() == action)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Нет правила для действия " + action));
    }
}
