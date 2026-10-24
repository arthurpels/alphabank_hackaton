package com.alphabank.backend.closer;

import com.alphabank.backend.enums.CloseMethod;

public interface SpotCloser {

    CloseMethod method();

    void close(SpotCloseContext context);
}
