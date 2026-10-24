package com.alphabank.backend.service;

import com.alphabank.backend.dto.response.DemoTimeDto;

public interface DemoTimeService {

    DemoTimeDto now();

    DemoTimeDto advance(int days);

    DemoTimeDto reset();
}
