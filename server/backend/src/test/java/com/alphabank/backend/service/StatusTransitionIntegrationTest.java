package com.alphabank.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import com.alphabank.backend.dto.response.AuditEventDto;
import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.enums.AuditEventType;
import com.alphabank.backend.enums.TransitionInitiator;
import com.alphabank.backend.exception.ApiException;

@SpringBootTest
@Transactional
class StatusTransitionIntegrationTest {

    private static final UUID EQUIPMENT_PRODUCT = UUID.fromString("00000000-0000-0000-0000-000000000302");

    @Autowired
    private StatusTransitionService transitions;

    @Autowired
    private AuditService audit;

    @Autowired
    private JdbcClient jdbc;

    private UUID applicationId;
    private UUID userId;

    @BeforeEach
    void insertDraftApplication() {
        UUID companyId = UUID.randomUUID();
        userId = UUID.randomUUID();
        applicationId = UUID.randomUUID();
        jdbc.sql("INSERT INTO company (id, inn, name, legal_form) VALUES (:id, '7700000001', 'ООО «Тест»', 'OOO')")
                .param("id", companyId).update();
        jdbc.sql("INSERT INTO app_user (id, company_id, role, fio) VALUES (:id, :company, 'CLIENT', 'Тестов Т. Т.')")
                .param("id", userId).param("company", companyId).update();
        jdbc.sql("""
                        INSERT INTO application (id, company_id, user_id, product_id, amount, term_months, goal, status)
                        VALUES (:id, :company, :user, :product, 200000000, 24, 'витрина', 'DRAFT')
                        """)
                .param("id", applicationId).param("company", companyId).param("user", userId)
                .param("product", EQUIPMENT_PRODUCT).update();
    }

    @Test
    void transitionIsSavedToApplicationAndAuditLog() {
        transitions.start(applicationId, userId);
        transitions.apply(applicationId, ApplicationStatus.COLLECTING, TransitionInitiator.CLIENT, userId,
                "Рассчитать");

        String status = jdbc.sql("SELECT status FROM application WHERE id = :id")
                .param("id", applicationId).query(String.class).single();
        assertThat(status).isEqualTo("COLLECTING");

        List<AuditEventDto> history = audit.getHistory(applicationId);
        assertThat(history).extracting(AuditEventDto::event)
                .containsOnly(AuditEventType.STATUS_CHANGE);
        assertThat(history).extracting(AuditEventDto::toState).containsExactly("DRAFT", "COLLECTING");
        assertThat(history.get(1).fromState()).isEqualTo("DRAFT");
        assertThat(history.get(1).userId()).isEqualTo(userId);
    }

    @Test
    void forbiddenTransitionLeavesDatabaseUntouched() {
        assertThatThrownBy(() -> transitions.apply(applicationId, ApplicationStatus.APPROVED,
                TransitionInitiator.CLIENT, userId, null))
                .isInstanceOf(ApiException.class);

        String status = jdbc.sql("SELECT status FROM application WHERE id = :id")
                .param("id", applicationId).query(String.class).single();
        assertThat(status).isEqualTo("DRAFT");
        assertThat(audit.getHistory(applicationId)).isEmpty();
    }
}
