package com.alphabank.backend.decision;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.alphabank.backend.enums.OfferStatus;
import com.alphabank.backend.enums.ProductCode;
import com.alphabank.backend.repository.ProductRepository;
import com.alphabank.backend.repository.model.ProductRecord;
import com.alphabank.backend.scoringclient.OfferGridClient;
import com.alphabank.backend.scoringclient.ScoringClient;
import com.alphabank.backend.scoringclient.dto.Offer;
import com.alphabank.backend.scoringclient.dto.ScoreRequest;
import com.alphabank.backend.scoringclient.dto.ScoreResponse;
import com.alphabank.backend.scoringclient.enums.Collateral;

@Component
public class GridOfferWriter {

    private final OfferGridClient offerGridClient;
    private final ScoringClient scoringClient;
    private final ProductRepository productRepository;
    private final OfferWriter offerWriter;
    private final Clock clock;

    public GridOfferWriter(OfferGridClient offerGridClient, ScoringClient scoringClient,
                           ProductRepository productRepository, OfferWriter offerWriter, Clock clock) {
        this.offerGridClient = offerGridClient;
        this.scoringClient = scoringClient;
        this.productRepository = productRepository;
        this.offerWriter = offerWriter;
        this.clock = clock;
    }

    public int save(UUID applicationId, ScoreRequest request, OfferStatus status) {
        List<Offer> offers = offerGridClient.grid(request).offers();
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (int i = 0; i < offers.size(); i++) {
            Offer offer = offers.get(i);
            ScoreRequest offerRequest = offerRequest(request, offer.productCode(), offer.amount(), offer.termMonths(),
                    offer.collateralType());
            offerWriter.save(applicationId, product(offer), offer.amount(), offer.termMonths(), offer.collateralType(),
                    offerRequest, score(offerRequest), status, now.plusNanos(i * 1_000L));
        }
        return offers.size();
    }

    public ScoreRequest offerRequest(ScoreRequest request, ProductCode productCode, long amount,
                                     int termMonths, String collateralType) {
        Collateral collateral = collateralType == null ? Collateral.DECLINED : Collateral.valueOf(collateralType);
        return new ScoreRequest(request.asOf(), productCode, amount, termMonths,
                request.egrulStatus(), request.registeredAt(), request.directorSince(), request.massAddress(),
                request.creditScore(), request.delinq12m(), request.bkiCurrentOverdue(), request.taxDebt(),
                request.unfiledReports(), request.monthlyRevenue(), request.stability(), request.purpose(),
                collateral);
    }

    public ScoreResponse score(ScoreRequest request) {
        return scoringClient.score(request);
    }

    private ProductRecord product(Offer offer) {
        return productRepository.findByCode(offer.productCode())
                .orElseThrow(() -> new IllegalStateException("Продукт " + offer.productCode() + " не найден"));
    }
}
