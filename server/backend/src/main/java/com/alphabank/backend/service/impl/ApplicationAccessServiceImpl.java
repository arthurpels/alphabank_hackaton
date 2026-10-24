package com.alphabank.backend.service.impl;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alphabank.backend.dto.response.MeDto;
import com.alphabank.backend.exception.ApiException;
import com.alphabank.backend.exception.ErrorCode;
import com.alphabank.backend.repository.AppUserRepository;
import com.alphabank.backend.repository.ApplicationRepository;
import com.alphabank.backend.repository.CompanyRepository;
import com.alphabank.backend.repository.model.ApplicationRecord;
import com.alphabank.backend.repository.model.CompanyRecord;
import com.alphabank.backend.security.CurrentUser;
import com.alphabank.backend.service.ApplicationAccessService;
import com.alphabank.backend.service.DemoUserService;

@Service
public class ApplicationAccessServiceImpl implements ApplicationAccessService {

    private final ApplicationRepository applicationRepository;
    private final CompanyRepository companyRepository;
    private final DemoUserService demoUserService;
    private final AppUserRepository appUserRepository;

    public ApplicationAccessServiceImpl(ApplicationRepository applicationRepository,
                                        CompanyRepository companyRepository, DemoUserService demoUserService,
                                        AppUserRepository appUserRepository) {
        this.applicationRepository = applicationRepository;
        this.companyRepository = companyRepository;
        this.demoUserService = demoUserService;
        this.appUserRepository = appUserRepository;
    }

    @Override
    public void registerUser(CurrentUser user) {
        appUserRepository.ensureExists(demoUserService.findById(user.id())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Пользователь не найден")));
    }

    @Override
    public ApplicationRecord load(CurrentUser user, UUID applicationId) {
        ApplicationRecord application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Заявка " + applicationId + " не найдена"));
        if (!user.isUnderwriter() && !resolveCompanyId(user).map(application.companyId()::equals).orElse(false)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Заявка принадлежит другой компании");
        }
        return application;
    }

    @Override
    public Optional<UUID> resolveCompanyId(CurrentUser user) {
        return demoUserService.findById(user.id())
                .map(MeDto::company)
                .filter(Objects::nonNull)
                .flatMap(company -> companyRepository.findByInn(company.inn()))
                .map(CompanyRecord::id);
    }

    @Override
    public String companyInn(ApplicationRecord application) {
        return companyRepository.findById(application.companyId())
                .map(CompanyRecord::inn)
                .orElseThrow(() -> new IllegalStateException("Компания заявки не найдена"));
    }
}
