package com.alphabank.backend.enrichment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.alphabank.backend.enrichment.client.MockNotFoundException;
import com.alphabank.backend.enrichment.client.MockValidationException;
import com.alphabank.backend.enrichment.client.MocksHttpClient;
import com.alphabank.backend.enrichment.dto.AcquiringResponse;
import com.alphabank.backend.enrichment.dto.BkiResponse;
import com.alphabank.backend.enrichment.dto.EgrulResponse;
import com.alphabank.backend.enrichment.dto.FnsResponse;
import com.alphabank.backend.enrichment.dto.MarketplaceResponse;
import com.alphabank.backend.enrichment.dto.OfdResponse;
import com.alphabank.backend.support.IoRetry;

class MocksClientLiveTest {

    private MocksHttpClient client;

    @BeforeEach
    void setUp() {
        client = new MocksHttpClient(RestClient.builder(), "http://localhost:8002", new IoRetry(3, 100));
        client.reset();
    }

    @Test
    void egrulReturnsValidCompanyData() {
        EgrulResponse egrul = client.egrul("7701234560");
        assertNotNull(egrul);
        assertEquals("7701234560", egrul.inn());
        assertEquals("ООО «Кофейня»", egrul.name());
        assertEquals("ACTIVE", egrul.status());
        assertNotNull(egrul.director());
        assertEquals("Смирнова А. В.", egrul.director().fio());
        assertFalse(egrul.director().isMass());
        assertNotNull(egrul.address());
        assertFalse(egrul.address().isMass());
    }

    @Test
    void bkiReturnsReport() {
        BkiResponse bki = client.bki("7701234560");
        assertNotNull(bki);
        assertEquals(712, bki.creditScore());
        assertEquals(3, bki.closedCredits());
        assertEquals(1, bki.openCredits().size());
        assertEquals(1, bki.inquiries6m());
    }

    @Test
    void fnsDebtsAndPayScenario() {
        FnsResponse initial = client.fnsDebts("7725555556");
        assertEquals(3412, initial.taxDebt().amount());

        client.fnsPay("7725555556");

        FnsResponse afterPay = client.fnsDebts("7725555556");
        assertEquals(0, afterPay.taxDebt().amount());

        client.reset();

        FnsResponse afterReset = client.fnsDebts("7725555556");
        assertEquals(3412, afterReset.taxDebt().amount());
    }

    @Test
    void ofdRevenueAnd404Handling() {
        Optional<OfdResponse> coffeeOfd = client.ofdRevenue("7701234560");
        assertTrue(coffeeOfd.isPresent());
        assertEquals(12, coffeeOfd.get().months().size());

        Optional<OfdResponse> builderOfd = client.ofdRevenue("590234567822");
        assertTrue(builderOfd.isEmpty());
    }

    @Test
    void marketplaceSalesAndConnect() {
        Optional<MarketplaceResponse> wb = client.marketplaceSales("590987654351");
        assertTrue(wb.isPresent());
        assertEquals(6, wb.get().months().size());
        assertEquals(4.6, wb.get().rating());

        client.marketplaceConnect("590987654351");
    }

    @Test
    void acquiringTurnoverAndConnect() {
        Optional<AcquiringResponse> acq = client.acquiringTurnover("7725555556");
        assertTrue(acq.isPresent());
        assertEquals(12, acq.get().months().size());

        client.acquiringConnect("7725555556");
    }

    @Test
    void unknownInnReturns404() {
        assertThrows(MockNotFoundException.class, () -> client.egrul("7700000009"));
    }

    @Test
    void invalidChecksumInnReturns422() {
        assertThrows(MockValidationException.class, () -> client.egrul("7701234567"));
    }
}
