package com.alphabank.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import com.alphabank.backend.enums.LegalForm;
import com.alphabank.backend.enums.SourceCode;
import com.alphabank.backend.repository.model.ApplicationSourceRecord;
import com.alphabank.backend.repository.model.CompanyRecord;

@SpringBootTest
@Transactional
class RepositoryIntegrationTest {

    private static final UUID EQUIPMENT_PRODUCT = UUID.fromString("00000000-0000-0000-0000-000000000302");

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ApplicationSourceRepository applicationSourceRepository;

    @Autowired
    private JdbcClient jdbc;

    private UUID applicationId;

    @BeforeEach
    void setUp() {
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        applicationId = UUID.randomUUID();

        jdbc.sql("INSERT INTO company (id, inn, name, legal_form) VALUES (:id, '7700000099', 'ООО «Интеграция»', 'OOO')")
                .param("id", companyId).update();
        jdbc.sql("INSERT INTO app_user (id, company_id, role, fio) VALUES (:id, :company, 'CLIENT', 'Иванов И. И.')")
                .param("id", userId).param("company", companyId).update();
        jdbc.sql("""
                INSERT INTO application (id, company_id, user_id, product_id, amount, term_months, goal, status)
                VALUES (:id, :company, :user, :product, 100000000, 12, 'цель', 'DRAFT')
                """)
                .param("id", applicationId)
                .param("company", companyId)
                .param("user", userId)
                .param("product", EQUIPMENT_PRODUCT)
                .update();
    }

    @Test
    void companyUpsertAndFind() {
        String inn = "7701234560";
        String snapshot = "{\"source\":\"EGRUL\",\"name\":\"ООО «Кофейня»\"}";

        UUID id = companyRepository.upsertCompany(inn, "ООО «Кофейня»", LegalForm.OOO, snapshot);
        assertThat(id).isNotNull();

        Optional<CompanyRecord> byInn = companyRepository.findByInn(inn);
        assertThat(byInn).isPresent();
        assertThat(byInn.get().name()).isEqualTo("ООО «Кофейня»");
        assertThat(byInn.get().legalForm()).isEqualTo(LegalForm.OOO);
        assertThat(byInn.get().egrulSnapshot()).contains("EGRUL");

        Optional<CompanyRecord> byId = companyRepository.findById(id);
        assertThat(byId).isPresent();
        assertThat(byId.get().inn()).isEqualTo(inn);

        String updatedSnapshot = "{\"source\":\"EGRUL\",\"name\":\"ООО «Кофейня Новая»\"}";
        UUID updatedId = companyRepository.upsertCompany(inn, "ООО «Кофейня Новая»", LegalForm.OOO, updatedSnapshot);
        assertThat(updatedId).isEqualTo(id);

        Optional<CompanyRecord> afterUpdate = companyRepository.findByInn(inn);
        assertThat(afterUpdate).isPresent();
        assertThat(afterUpdate.get().name()).isEqualTo("ООО «Кофейня Новая»");
    }

    @Test
    void applicationSourceRecordConsentAndQueries() {
        assertThat(applicationSourceRepository.isConsentGiven(applicationId, SourceCode.BKI)).isFalse();

        applicationSourceRepository.recordConsent(applicationId, SourceCode.BKI, true);
        assertThat(applicationSourceRepository.isConsentGiven(applicationId, SourceCode.BKI)).isTrue();

        applicationSourceRepository.recordConsent(applicationId, SourceCode.OFD, true);

        List<ApplicationSourceRecord> sources = applicationSourceRepository.findByApplicationId(applicationId);
        assertThat(sources).hasSize(2);
        assertThat(sources).extracting(ApplicationSourceRecord::sourceCode)
                .containsExactlyInAnyOrder(SourceCode.BKI, SourceCode.OFD);
        assertThat(sources).allMatch(ApplicationSourceRecord::consentGiven);

        applicationSourceRepository.recordConsent(applicationId, SourceCode.BKI, false);
        assertThat(applicationSourceRepository.isConsentGiven(applicationId, SourceCode.BKI)).isFalse();
    }
}
