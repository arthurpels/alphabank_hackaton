package com.alphabank.backend.enrichment.client;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import com.alphabank.backend.enrichment.dto.AcquiringResponse;
import com.alphabank.backend.enrichment.dto.BkiResponse;
import com.alphabank.backend.enrichment.dto.EgrulResponse;
import com.alphabank.backend.enrichment.dto.FnsResponse;
import com.alphabank.backend.enrichment.dto.MarketplaceResponse;
import com.alphabank.backend.enrichment.dto.OfdResponse;
import com.alphabank.backend.support.IoRetry;

@Component
public class MocksHttpClient implements MocksClient {

    private final RestClient restClient;
    private final IoRetry ioRetry;

    public MocksHttpClient(RestClient.Builder builder, @Value("${app.mocks-url}") String mocksUrl, IoRetry ioRetry) {
        this.restClient = builder.baseUrl(mocksUrl).build();
        this.ioRetry = ioRetry;
    }

    @Override
    public EgrulResponse egrul(String inn) {
        return ioRetry.execute("EGRUL", () -> {
            try {
                return restClient.get()
                        .uri("/mock/egrul/v1/company?inn={inn}", inn)
                        .retrieve()
                        .body(EgrulResponse.class);
            } catch (HttpStatusCodeException e) {
                throw handleStatus(e, inn, "EGRUL");
            }
        });
    }

    @Override
    public BkiResponse bki(String inn) {
        return ioRetry.execute("BKI", () -> {
            try {
                return restClient.get()
                        .uri("/mock/bki/v1/report?inn={inn}", inn)
                        .retrieve()
                        .body(BkiResponse.class);
            } catch (HttpStatusCodeException e) {
                throw handleStatus(e, inn, "BKI");
            }
        });
    }

    @Override
    public FnsResponse fnsDebts(String inn) {
        return ioRetry.execute("FNS", () -> {
            try {
                return restClient.get()
                        .uri("/mock/fns/v1/debts?inn={inn}", inn)
                        .retrieve()
                        .body(FnsResponse.class);
            } catch (HttpStatusCodeException e) {
                throw handleStatus(e, inn, "FNS");
            }
        });
    }

    @Override
    public Optional<OfdResponse> ofdRevenue(String inn, int months) {
        return ioRetry.execute("OFD", () -> {
            try {
                OfdResponse res = restClient.get()
                        .uri("/mock/ofd/v1/revenue?inn={inn}&months={months}", inn, months)
                        .retrieve()
                        .body(OfdResponse.class);
                return Optional.ofNullable(res);
            } catch (HttpStatusCodeException e) {
                if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                    return Optional.empty();
                }
                throw handleStatus(e, inn, "OFD");
            }
        });
    }

    @Override
    public Optional<MarketplaceResponse> marketplaceSales(String inn, int months) {
        return ioRetry.execute("MARKETPLACE", () -> {
            try {
                MarketplaceResponse res = restClient.get()
                        .uri("/mock/marketplace/v1/sales?inn={inn}&months={months}", inn, months)
                        .retrieve()
                        .body(MarketplaceResponse.class);
                return Optional.ofNullable(res);
            } catch (HttpStatusCodeException e) {
                if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                    return Optional.empty();
                }
                throw handleStatus(e, inn, "MARKETPLACE");
            }
        });
    }

    @Override
    public Optional<AcquiringResponse> acquiringTurnover(String inn, int months) {
        return ioRetry.execute("ACQUIRING", () -> {
            try {
                AcquiringResponse res = restClient.get()
                        .uri("/mock/acquiring/v1/turnover?inn={inn}&months={months}", inn, months)
                        .retrieve()
                        .body(AcquiringResponse.class);
                return Optional.ofNullable(res);
            } catch (HttpStatusCodeException e) {
                if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                    return Optional.empty();
                }
                throw handleStatus(e, inn, "ACQUIRING");
            }
        });
    }

    @Override
    public void ofdConnect(String inn) {
        ioRetry.run("OFD", () -> {
            try {
                restClient.post()
                        .uri("/mock/ofd/v1/connect")
                        .body(Map.of("inn", inn, "consent", true))
                        .retrieve()
                        .toBodilessEntity();
            } catch (HttpStatusCodeException e) {
                throw handleStatus(e, inn, "OFD");
            }
        });
    }

    @Override
    public void marketplaceConnect(String inn) {
        ioRetry.run("MARKETPLACE", () -> {
            try {
                restClient.post()
                        .uri("/mock/marketplace/v1/connect")
                        .body(Map.of("inn", inn, "api_key", "demo"))
                        .retrieve()
                        .toBodilessEntity();
            } catch (HttpStatusCodeException e) {
                throw handleStatus(e, inn, "MARKETPLACE");
            }
        });
    }

    @Override
    public void acquiringConnect(String inn) {
        ioRetry.run("ACQUIRING", () -> {
            try {
                restClient.post()
                        .uri("/mock/acquiring/v1/connect")
                        .body(Map.of("inn", inn, "consent", true))
                        .retrieve()
                        .toBodilessEntity();
            } catch (HttpStatusCodeException e) {
                throw handleStatus(e, inn, "ACQUIRING");
            }
        });
    }

    @Override
    public void fnsPay(String inn) {
        ioRetry.run("FNS", () -> {
            try {
                restClient.post()
                        .uri("/mock/fns/v1/debts/pay")
                        .body(Map.of("inn", inn))
                        .retrieve()
                        .toBodilessEntity();
            } catch (HttpStatusCodeException e) {
                throw handleStatus(e, inn, "FNS");
            }
        });
    }

    @Override
    public void reset() {
        ioRetry.run("mocks", () -> {
            try {
                restClient.post()
                        .uri("/mock/reset")
                        .retrieve()
                        .toBodilessEntity();
            } catch (HttpStatusCodeException e) {
                throw new MockClientException("Ошибка сброса моков", e);
            }
        });
    }

    private RuntimeException handleStatus(HttpStatusCodeException e, String inn, String source) {
        if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
            return new MockNotFoundException("Источник " + source + " не нашёл данных для ИНН " + inn);
        }
        if (e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
            return new MockValidationException("ИНН " + inn + " не проходит проверку контрольной суммы");
        }
        return new MockClientException("Ошибка запроса к моку " + source + ": " + e.getStatusCode(), e);
    }
}
