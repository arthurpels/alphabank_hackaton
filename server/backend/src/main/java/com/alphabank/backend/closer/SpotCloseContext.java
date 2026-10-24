package com.alphabank.backend.closer;

import com.alphabank.backend.dto.request.SpotCloseRequest;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.ProductRecord;
import com.alphabank.backend.repository.model.SpotRecord;
import com.alphabank.backend.security.CurrentUser;

public record SpotCloseContext(
        ApplicationRecord application,
        ProductRecord product,
        String inn,
        SpotRecord spot,
        SpotCloseRequest request,
        CurrentUser user) {
}
