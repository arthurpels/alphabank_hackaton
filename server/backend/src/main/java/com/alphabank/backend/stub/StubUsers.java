package com.alphabank.backend.stub;

import java.time.LocalDate;
import java.util.List;

import com.alphabank.backend.dto.response.CompanyDto;
import com.alphabank.backend.dto.response.MeDto;
import com.alphabank.backend.enums.LegalForm;
import com.alphabank.backend.enums.UserRole;

public final class StubUsers {

    private StubUsers() {
    }

    public static CompanyDto coffeeCompany() {
        return new CompanyDto(StubIds.COFFEE_COMPANY_ID, "7701234567", "ООО «Кофейня»", LegalForm.OOO,
                "г. Москва, ул. Примерная, 10", "Петрова Анна Сергеевна", LocalDate.of(2019, 4, 15), "56.10");
    }

    public static List<MeDto> users() {
        return List.of(
                new MeDto(StubIds.COFFEE_USER_ID, "Петрова Анна Сергеевна", UserRole.CLIENT,
                        "Кофейня — закрывает пятна и получает автоодобрение", coffeeCompany()),
                new MeDto(StubIds.BUILDER_USER_ID, "Соколов Дмитрий Игоревич", UserRole.CLIENT,
                        "ИП-строитель — андеррайтер и дозапрос",
                        company("202", "590123456789", "ИП Соколов Д. И.", LegalForm.IP, "43.39")),
                new MeDto(StubIds.SELLER_USER_ID, "Иванова Мария Олеговна", UserRole.CLIENT,
                        "ИП, продавец на WB — иные условия",
                        company("203", "590987654321", "ИП Иванова М. О.", LegalForm.IP, "47.91")),
                new MeDto(StubIds.TAX_DEBT_USER_ID, "Кузнецов Павел Андреевич", UserRole.CLIENT,
                        "ООО с долгом в ФНС — устранимый стоп-фактор",
                        company("204", "7725555555", "ООО «Мастерская»", LegalForm.OOO, "95.29")),
                new MeDto(StubIds.SHELL_USER_ID, "Иванов Иван Иванович", UserRole.CLIENT,
                        "ООО «Однодневка» — автоотказ",
                        company("205", "7719999999", "ООО «Однодневка»", LegalForm.OOO, "46.90")),
                new MeDto(StubIds.UNDERWRITER_ID, "Смирнов Олег Игоревич", UserRole.UNDERWRITER, "Андеррайтер", null));
    }

    private static CompanyDto company(String idSuffix, String inn, String name, LegalForm form, String okved) {
        return new CompanyDto(StubIds.id(idSuffix), inn, name, form, null, null, null, okved);
    }
}
