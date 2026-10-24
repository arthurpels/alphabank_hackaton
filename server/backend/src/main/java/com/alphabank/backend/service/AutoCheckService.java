package com.alphabank.backend.service;

import com.alphabank.backend.enums.ApplicationStatus;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.ProductRecord;

public interface AutoCheckService {

    ApplicationStatus run(ApplicationRecord application, ProductRecord product, String inn);
}
