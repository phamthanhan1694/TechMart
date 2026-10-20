package dev.techmart.address.web;

import dev.techmart.address.dto.AddressDtos.AddressRequest;
import dev.techmart.address.dto.AddressDtos.AddressResponse;
import dev.techmart.address.service.AddressService;
import dev.techmart.identity.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/account/addresses")
@Tag(name = "Account - Địa chỉ", description = "Sổ địa chỉ giao hàng, tối đa 10 địa chỉ, đúng một mặc định")
@PreAuthorize("hasRole('CUSTOMER')")
public class AddressController {

    private final AddressService service;

    public AddressController(AddressService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Danh sách địa chỉ", description = "Địa chỉ mặc định đứng đầu.")
    public List<AddressResponse> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.list(principal.id());
    }

    @PostMapping
    @Operation(summary = "Thêm địa chỉ", description = "Địa chỉ đầu tiên tự thành mặc định. Qua 10 địa chỉ trả 422 ADDRESS_LIMIT_EXCEEDED.")
    public ResponseEntity<AddressResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @Valid @RequestBody AddressRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(principal.id(), req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa địa chỉ")
    public AddressResponse update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                  @Valid @RequestBody AddressRequest req) {
        return service.update(principal.id(), id, req);
    }

    @PostMapping("/{id}/default")
    @Operation(summary = "Đặt làm mặc định")
    public AddressResponse setDefault(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        return service.setDefault(principal.id(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá địa chỉ", description = "Xoá địa chỉ mặc định thì địa chỉ mới nhất còn lại lên làm mặc định.")
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        service.delete(principal.id(), id);
    }
}
