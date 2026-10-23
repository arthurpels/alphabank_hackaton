package com.alphabank.backend.stub;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.alphabank.backend.dto.response.CompanyDto;
import com.alphabank.backend.dto.response.MeDto;
import com.alphabank.backend.enums.LegalForm;
import com.alphabank.backend.enums.UserRole;

public final class StubUsers {

    private StubUsers() {
    }

    public static CompanyDto coffeeCompany() {
        return company(StubIds.COFFEE_COMPANY_ID, "7701234560", "ООО «Кофейня»", LegalForm.OOO,
                "г. Москва, ул. Покровка, 12", "Смирнова А. В.", "2019-04-15", "56.30");
    }

    public static List<MeDto> users() {
        return List.of(
                new MeDto(StubIds.COFFEE_USER_ID, "Смирнова А. В.", UserRole.CLIENT,
                        "ООО «Кофейня» — автоодобрение", coffeeCompany()),
                new MeDto(StubIds.BUILDER_USER_ID, "Петров С. Н.", UserRole.CLIENT,
                        "ИП Петров — андеррайтер, дозапрос, иные условия",
                        company(StubIds.id("202"), "590234567822", "ИП Петров Сергей Николаевич", LegalForm.IP,
                                "г. Пермь, ул. Ленина, 40", "Петров С. Н.", "2024-03-01", "41.20")),
                new MeDto(StubIds.SELLER_USER_ID, "Кузнецова М. И.", UserRole.CLIENT,
                        "ИП Кузнецова — иные условия от системы",
                        company(StubIds.id("203"), "590987654351", "ИП Кузнецова Мария Игоревна", LegalForm.IP,
                                "г. Пермь, ул. Сибирская, 15", "Кузнецова М. И.", "2024-05-20", "47.91")),
                new MeDto(StubIds.TAX_DEBT_USER_ID, "Орлов Д. А.", UserRole.CLIENT,
                        "ООО «Техснаб» — устранимый стоп-фактор",
                        company(StubIds.id("204"), "7725555556", "ООО «Техснаб»", LegalForm.OOO,
                                "г. Москва, Варшавское ш., 33", "Орлов Д. А.", "2021-02-10", "46.69")),
                new MeDto(StubIds.SHELL_USER_ID, "Иванов И. И.", UserRole.CLIENT,
                        "ООО «Однодневка» — неустранимые стоп-факторы",
                        company(StubIds.id("205"), "7719999994", "ООО «Однодневка»", LegalForm.OOO,
                                "г. Москва, ул. Примерная, 1", "Иванов И. И.", "2025-08-01", "46.90")),
                new MeDto(StubIds.UNDERWRITER_ID, "Андеррайтер Демо", UserRole.UNDERWRITER, "Андеррайтер", null));
    }

    private static CompanyDto company(UUID id, String inn, String name, LegalForm form, String address,
                                      String director, String registeredAt, String okved) {
        return new CompanyDto(id, inn, name, form, address, director, LocalDate.parse(registeredAt), okved);
    }
}
