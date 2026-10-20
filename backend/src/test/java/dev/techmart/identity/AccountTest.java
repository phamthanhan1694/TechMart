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
@DisplayName("Tài khoản cá nhân")
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
    @DisplayName("Sai mật khẩu hiện tại -> 422, không phải 401 (client coi 401 là hết phiên)")
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
    @DisplayName("Đổi mật khẩu -> mọi phiên cũ bị thu hồi, mật khẩu mới dùng được")
    void changePassword_revokesAllSessions() {
        TokenResponse before = authService.login(new LoginRequest(user.getEmail(), PASSWORD));

        accountService.changePassword(user.getId(), new ChangePasswordRequest(PASSWORD, "MatKhauMoi@1"));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(before.refreshToken())))
                .as("Người đổi mật khẩu thường vì nghi bị lộ - phiên của kẻ gian phải chết")
                .isInstanceOf(BusinessException.class);
        assertThat(authService.login(new LoginRequest(user.getEmail(), "MatKhauMoi@1")).accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("Mật khẩu mới trùng mật khẩu cũ -> 422")
    void changePassword_sameAsCurrent() {
        assertThatThrownBy(() -> accountService.changePassword(user.getId(), new ChangePasswordRequest(PASSWORD, PASSWORD)))
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.PASSWORD_UNCHANGED);
    }

    @Test
    @DisplayName("Cập nhật hồ sơ: số điện thoại rỗng lưu thành null")
    void updateProfile() {
        var updated = accountService.updateProfile(user.getId(), new UpdateProfileRequest("  Tên Mới ", ""));
        assertThat(updated.fullName()).isEqualTo("Tên Mới");
        assertThat(updated.phone()).isNull();
    }

    // ------------------------------------------------------------------ sổ địa chỉ

    @Test
    @DisplayName("Địa chỉ đầu tiên tự thành mặc định; đặt mặc định mới thì cái cũ tự bỏ cờ")
    void addresses_exactlyOneDefault() {
        AddressResponse home = addressService.create(user.getId(), address("Nha", false));
        assertThat(home.isDefault()).as("Địa chỉ đầu tiên").isTrue();

        AddressResponse office = addressService.create(user.getId(), address("Cong ty", true));
        assertThat(office.isDefault()).isTrue();

        List<AddressResponse> all = addressService.list(user.getId());
        assertThat(all).filteredOn(AddressResponse::isDefault).singleElement()
                .extracting(AddressResponse::id).isEqualTo(office.id());
        assertThat(all.get(0).id()).as("Mặc định đứng đầu danh sách").isEqualTo(office.id());

        addressService.setDefault(user.getId(), home.id());
        assertThat(addressService.list(user.getId())).filteredOn(AddressResponse::isDefault).singleElement()
                .extracting(AddressResponse::id).isEqualTo(home.id());
    }

    @Test
    @DisplayName("Xoá địa chỉ mặc định -> địa chỉ còn lại lên làm mặc định")
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
    @DisplayName("Tối đa 10 địa chỉ")
    void addresses_limit() {
        for (int i = 0; i < AddressService.MAX_ADDRESSES; i++) {
            addressService.create(user.getId(), address("Địa chỉ " + i, false));
        }
        assertThatThrownBy(() -> addressService.create(user.getId(), address("Thu 11", false)))
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.ADDRESS_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("Địa chỉ của người khác -> 404, và không đặt mặc định hộ được")
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
        return new AddressRequest(label, "Nguyễn Văn A", "0901234567", "12 Nguyen Trai",
                "Ben Thanh", "Quận 1", "TP Hồ Chí Minh", makeDefault);
    }
}
