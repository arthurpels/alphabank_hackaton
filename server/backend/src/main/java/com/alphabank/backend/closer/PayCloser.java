package com.alphabank.backend.closer;

import org.springframework.stereotype.Component;

import com.alphabank.backend.enrichment.client.MocksClient;
import com.alphabank.backend.enrichment.dto.FnsResponse;
import com.alphabank.backend.enums.CloseMethod;
import com.alphabank.backend.enums.SourceCode;
import com.alphabank.backend.exception.ApiException;

@Component
public class PayCloser implements SpotCloser {

    private final MocksClient mocksClient;
    private final SpotResolutionWriter writer;

    public PayCloser(MocksClient mocksClient, SpotResolutionWriter writer) {
        this.mocksClient = mocksClient;
        this.writer = writer;
    }

    @Override
    public CloseMethod method() {
        return CloseMethod.PAY;
    }

    @Override
    public void close(SpotCloseContext context) {
        if (context.spot().source() != SourceCode.FNS) {
            throw ApiException.validation("Погасить можно только долг в ФНС");
        }
        mocksClient.fnsPay(context.inn());
        FnsResponse fns = mocksClient.fnsDebts(context.inn());
        if (fns.taxDebt() != null && fns.taxDebt().amount() > 0) {
            throw ApiException.validation("ФНС ещё не видит оплату — повторите позже");
        }
        writer.close(context.spot(), context.user().id(), "Долг в ФНС погашен, повторный запрос в ФНС: долга нет");
    }
}
