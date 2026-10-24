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

    Optional<OfdResponse> ofdRevenue(String inn);

    Optional<MarketplaceResponse> marketplaceSales(String inn);

    Optional<AcquiringResponse> acquiringTurnover(String inn);

    void ofdConnect(String inn);

    void marketplaceConnect(String inn);

    void acquiringConnect(String inn);

    void fnsPay(String inn);

    void reset();
}
