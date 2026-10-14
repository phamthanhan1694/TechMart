package dev.techmart.identity;

import dev.techmart.address.dto.AddressDtos.AddressRequest;
import dev.techmart.address.dto.AddressDtos.AddressResponse;
import dev.techmart.address.service.AddressService;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.identity.domain.User;
import dev.techmart.identity.dto.AccountDtos.ChangePasswordRequest;
import dev.techmart.identity.dto.AccountDtos.UpdateProfileRequest;
import dev.techmart.identity.dto.AuthDtos.LoginRequest;
import dev.techmart.identity.dto.AuthDtos.RefreshRequest;
import dev.techmart.identity.dto.AuthDtos.TokenResponse;
import dev.techmart.identity.service.AccountService;
import dev.techmart.identity.service.AuthService;
import dev.techmart.support.DatabaseCleaner;
import dev.techmart.support.IntegrationTestBase;
import dev.techmart.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Hồ sơ, đổi mật khẩu và sổ địa chỉ của khách. */
@DisplayName("Tai khoan ca nhan")
class AccountTest extends IntegrationTestBase {

    private static final String PASSWORD = "Password@123";

    @Autowired AccountService accountService;
    @Autowired AuthService authService;
    @Autowired AddressService addressService;
    @Autowired TestDataFactory fixtures;
    @Autowired DatabaseCleaner cleaner;

    private User user;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        user = fixtures.createCustomer();
    }

    // ------------------------------------------------------------------ mật khẩu

    @Test
    @DisplayName("Sai mat khau hien tai -> 422, khong phai 401 (client coi 401 la het phien)")
    void changePassword_wrongCurrent() {
        assertThatThrownBy(() -> accountService.changePassword(user.getId(),
                new ChangePasswordRequest("SaiRoi@123", "MatKhauMoi@1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .satisfies(code -> {
                    assertThat(code).isEqualTo(ErrorCode.CURRENT_PASSWORD_INCORRECT);
                    assertThat(code.status().value()).isEqualTo(422);
                });
    }

    @Test
    @DisplayName("Doi mat khau -> moi phien cu bi thu hoi, mat khau moi dung duoc")
    void changePassword_revokesAllSessions() {
        TokenResponse before = authService.login(new LoginRequest(user.getEmail(), PASSWORD));

        accountService.changePassword(user.getId(), new ChangePasswordRequest(PASSWORD, "MatKhauMoi@1"));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(before.refreshToken())))
                .as("Nguoi doi mat khau thuong vi nghi bi lo - phien cua ke gian phai chet")
                .isInstanceOf(BusinessException.class);
        assertThat(authService.login(new LoginRequest(user.getEmail(), "MatKhauMoi@1")).accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("Mat khau moi trung mat khau cu -> 422")
    void changePassword_sameAsCurrent() {
        assertThatThrownBy(() -> accountService.changePassword(user.getId(), new ChangePasswordRequest(PASSWORD, PASSWORD)))
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.PASSWORD_UNCHANGED);
    }

    @Test
    @DisplayName("Cap nhat ho so: so dien thoai rong luu thanh null")
    void updateProfile() {
        var updated = accountService.updateProfile(user.getId(), new UpdateProfileRequest("  Ten Moi ", ""));
        assertThat(updated.fullName()).isEqualTo("Ten Moi");
        assertThat(updated.phone()).isNull();
    }

    // ------------------------------------------------------------------ sổ địa chỉ

    @Test
    @DisplayName("Dia chi dau tien tu thanh mac dinh; dat mac dinh moi thi cai cu tu bo co")
    void addresses_exactlyOneDefault() {
        AddressResponse home = addressService.create(user.getId(), address("Nha", false));
        assertThat(home.isDefault()).as("Dia chi dau tien").isTrue();

        AddressResponse office = addressService.create(user.getId(), address("Cong ty", true));
        assertThat(office.isDefault()).isTrue();

        List<AddressResponse> all = addressService.list(user.getId());
        assertThat(all).filteredOn(AddressResponse::isDefault).singleElement()
                .extracting(AddressResponse::id).isEqualTo(office.id());
        assertThat(all.get(0).id()).as("Mac dinh dung dau danh sach").isEqualTo(office.id());

        addressService.setDefault(user.getId(), home.id());
        assertThat(addressService.list(user.getId())).filteredOn(AddressResponse::isDefault).singleElement()
                .extracting(AddressResponse::id).isEqualTo(home.id());
    }

    @Test
    @DisplayName("Xoa dia chi mac dinh -> dia chi con lai len lam mac dinh")
    void deleteDefault_promotesAnother() {
        AddressResponse first = addressService.create(user.getId(), address("Nha", false));
        AddressResponse second = addressService.create(user.getId(), address("Cong ty", false));

        addressService.delete(user.getId(), first.id());

        assertThat(addressService.list(user.getId())).singleElement().satisfies(a -> {
            assertThat(a.id()).isEqualTo(second.id());
            assertThat(a.isDefault()).isTrue();
        });
    }

    @Test
    @DisplayName("Toi da 10 dia chi")
    void addresses_limit() {
        for (int i = 0; i < AddressService.MAX_ADDRESSES; i++) {
            addressService.create(user.getId(), address("Dia chi " + i, false));
        }
        assertThatThrownBy(() -> addressService.create(user.getId(), address("Thu 11", false)))
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.ADDRESS_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("Dia chi cua nguoi khac -> 404, va khong dat mac dinh ho duoc")
    void addresses_ofAnotherUser_return404() {
        AddressResponse mine = addressService.create(user.getId(), address("Nha", false));
        User stranger = fixtures.createCustomer();

        assertThatThrownBy(() -> addressService.setDefault(stranger.getId(), mine.id()))
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.ADDRESS_NOT_FOUND);
        assertThatThrownBy(() -> addressService.delete(stranger.getId(), mine.id()))
                .extracting(e -> ((BusinessException) e).code().status().value())
                .isEqualTo(404);
    }

    private static AddressRequest address(String label, boolean makeDefault) {
        return new AddressRequest(label, "Nguyen Van A", "0901234567", "12 Nguyen Trai",
                "Ben Thanh", "Quan 1", "TP Ho Chi Minh", makeDefault);
    }
}
