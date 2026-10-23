package com.alphabank.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.alphabank.backend.dto.response.AuditEventDto;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.AuditEventType;
import com.alphabank.backend.enums.TransitionInitiator;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.exception.ErrorCode;
import com.alphabank.backend.repository.ApplicationStatusRepository;
import com.alphabank.backend.repository.AuditLogRepository;
import com.alphabank.backend.service.impl.AuditServiceImpl;
import com.alphabank.backend.service.impl.StatusTransitionServiceImpl;
import com.alphabank.backend.statemachine.ApplicationStateMachine;

class StatusTransitionServiceTest {

    private static final UUID APPLICATION = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private final FakeStatusRepository statuses = new FakeStatusRepository();
    private final FakeAuditLogRepository auditLog = new FakeAuditLogRepository();
    private final AuditService audit = new AuditServiceImpl(auditLog,
            Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC));
    private final StatusTransitionService transitions =
            new StatusTransitionServiceImpl(new ApplicationStateMachine(), statuses, audit);

    @BeforeEach
    void createDraft() {
        statuses.store.put(APPLICATION, ApplicationStatus.DRAFT);
    }

    @Test
    void startWritesCreationToJournal() {
        ApplicationStatus status = transitions.start(APPLICATION, USER);

        assertThat(status).isEqualTo(ApplicationStatus.DRAFT);
        AuditEventDto event = audit.getHistory(APPLICATION).get(0);
        assertThat(event.event()).isEqualTo(AuditEventType.STATUS_CHANGE);
        assertThat(event.fromState()).isNull();
        assertThat(event.toState()).isEqualTo("DRAFT");
        assertThat(event.userId()).isEqualTo(USER);
    }

    @Test
    void transitionSavesStatusAndJournal() {
        transitions.apply(APPLICATION, ApplicationStatus.COLLECTING, TransitionInitiator.CLIENT, USER, "Рассчитать");
        transitions.apply(APPLICATION, ApplicationStatus.SUBMITTED, TransitionInitiator.CLIENT, USER, null);
        transitions.apply(APPLICATION, ApplicationStatus.AUTO_CHECK, TransitionInitiator.SYSTEM, null, null);

        assertThat(statuses.store.get(APPLICATION)).isEqualTo(ApplicationStatus.AUTO_CHECK);
        List<AuditEventDto> history = audit.getHistory(APPLICATION);
        assertThat(history).extracting(AuditEventDto::fromState)
                .containsExactly("DRAFT", "COLLECTING", "SUBMITTED");
        assertThat(history).extracting(AuditEventDto::toState)
                .containsExactly("COLLECTING", "SUBMITTED", "AUTO_CHECK");
        assertThat(history.get(0).reason()).isEqualTo("Рассчитать");
        assertThat(history.get(2).userId()).isNull();
    }

    @Test
    void selfTransitionIsAllowedButNotJournaled() {
        statuses.store.put(APPLICATION, ApplicationStatus.COLLECTING);

        transitions.apply(APPLICATION, ApplicationStatus.COLLECTING, TransitionInitiator.CLIENT, USER, null);

        assertThat(audit.getHistory(APPLICATION)).isEmpty();
    }

    @Test
    void forbiddenTransitionChangesNothing() {
        assertThatThrownBy(() -> transitions.apply(APPLICATION, ApplicationStatus.SUBMITTED,
                TransitionInitiator.CLIENT, USER, null))
                .isInstanceOf(ApiException.class);

        assertThat(statuses.store.get(APPLICATION)).isEqualTo(ApplicationStatus.DRAFT);
        assertThat(audit.getHistory(APPLICATION)).isEmpty();
    }

    @Test
    void unknownApplicationIs404() {
        assertThatThrownBy(() -> transitions.apply(UUID.randomUUID(), ApplicationStatus.COLLECTING,
                TransitionInitiator.CLIENT, USER, null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private static class FakeStatusRepository implements ApplicationStatusRepository {

        private final Map<UUID, ApplicationStatus> store = new HashMap<>();

        @Override
        public Optional<ApplicationStatus> findStatusForUpdate(UUID applicationId) {
            return Optional.ofNullable(store.get(applicationId));
        }

        @Override
        public void updateStatus(UUID applicationId, ApplicationStatus status) {
            store.put(applicationId, status);
        }
    }

    private static class FakeAuditLogRepository implements AuditLogRepository {

        private final Map<UUID, List<AuditEventDto>> store = new HashMap<>();

        @Override
        public void save(UUID applicationId, AuditEventDto event) {
            store.computeIfAbsent(applicationId, id -> new ArrayList<>()).add(event);
        }

        @Override
        public List<AuditEventDto> findByApplicationId(UUID applicationId) {
            return store.getOrDefault(applicationId, List.of());
        }
    }
}
