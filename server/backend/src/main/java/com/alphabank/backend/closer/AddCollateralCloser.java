package com.alphabank.backend.closer;

import org.springframework.stereotype.Component;

import com.alphabank.backend.enums.CloseMethod;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.scoringclient.enums.Collateral;

@Component
public class AddCollateralCloser implements SpotCloser {

    private final SpotResolutionWriter writer;

    public AddCollateralCloser(SpotResolutionWriter writer) {
        this.writer = writer;
    }

    @Override
    public CloseMethod method() {
        return CloseMethod.ADD_COLLATERAL;
    }

    @Override
    public void close(SpotCloseContext context) {
        Collateral collateral = parse(context.request().collateralType());
        String comment = context.request().collateralComment();
        if (collateral == Collateral.PLEDGE) {
            writer.sendToReview(context.spot(), context.user().id(), collateral.name(), comment, null);
        } else {
            writer.acceptAnswer(context.spot(), context.user().id(), collateral.name(), comment, null,
                    collateral == Collateral.GUARANTEE ? "Добавлено поручительство собственника"
                            : "Клиент отказался от обеспечения");
        }
    }

    private static Collateral parse(String value) {
        try {
            return Collateral.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.validation("collateralType — GUARANTEE, PLEDGE или DECLINED");
        }
    }
}
