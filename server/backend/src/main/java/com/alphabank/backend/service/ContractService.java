package com.alphabank.backend.service;

import com.alphabank.backend.dto.response.ContractDto;
import com.alphabank.backend.repository.model.ApplicationRecord;

public interface ContractService {

    ContractDto find(ApplicationRecord application);
}
