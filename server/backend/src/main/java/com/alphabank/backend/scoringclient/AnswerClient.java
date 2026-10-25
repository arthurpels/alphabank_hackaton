package com.alphabank.backend.scoringclient;

import com.alphabank.backend.scoringclient.dto.AnswerClassifyRequest;
import com.alphabank.backend.scoringclient.dto.AnswerClassifyResponse;
import com.alphabank.backend.scoringclient.dto.AnswerVerifyRequest;
import com.alphabank.backend.scoringclient.dto.AnswerVerifyResponse;

public interface AnswerClient {

    AnswerClassifyResponse classify(AnswerClassifyRequest request);

    AnswerVerifyResponse verify(AnswerVerifyRequest request);
}
