package com.alphabank.backend.enrichment.client;

import java.util.Optional;

import com.alphabank.backend.enrichment.dto.AcquiringResponse;
import com.alphabank.backend.enrichment.dto.BkiResponse;
import com.alphabank.backend.enrichment.dto.EgrulResponse;
import com.alphabank.backend.enrichment.dto.FnsResponse;
import com.alphabank.backend.enrichment.dto.MarketplaceResponse;
import com.alphabank.backend.enrichment.dto.OfdResponse;

public interface MocksClient {

    EgrulResponse egrul(String inn);

    BkiResponse bki(String inn);

    FnsResponse fnsDebts(String inn);

    int CURRENT_MONTHS = 12;
    int MARKETPLACE_MONTHS = 6;
    int HISTORY_MONTHS = 24;

    default Optional<OfdResponse> ofdRevenue(String inn) {
        return ofdRevenue(inn, CURRENT_MONTHS);
    }

    Optional<OfdResponse> ofdRevenue(String inn, int months);

    default Optional<MarketplaceResponse> marketplaceSales(String inn) {
        return marketplaceSales(inn, MARKETPLACE_MONTHS);
    }

    Optional<MarketplaceResponse> marketplaceSales(String inn, int months);

    default Optional<AcquiringResponse> acquiringTurnover(String inn) {
        return acquiringTurnover(inn, CURRENT_MONTHS);
    }

    Optional<AcquiringResponse> acquiringTurnover(String inn, int months);

    void ofdConnect(String inn);

    void marketplaceConnect(String inn);

    void acquiringConnect(String inn);

    void fnsPay(String inn);

    void reset();
}
