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
@Tag(name = "Account - Dia chi", description = "So dia chi giao hang, toi da 10 dia chi, dung mot mac dinh")
@PreAuthorize("hasRole('CUSTOMER')")
public class AddressController {

    private final AddressService service;

    public AddressController(AddressService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Danh sach dia chi", description = "Dia chi mac dinh dung dau.")
    public List<AddressResponse> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.list(principal.id());
    }

    @PostMapping
    @Operation(summary = "Them dia chi", description = "Dia chi dau tien tu thanh mac dinh. Qua 10 dia chi tra 422 ADDRESS_LIMIT_EXCEEDED.")
    public ResponseEntity<AddressResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @Valid @RequestBody AddressRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(principal.id(), req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sua dia chi")
    public AddressResponse update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                  @Valid @RequestBody AddressRequest req) {
        return service.update(principal.id(), id, req);
    }

    @PostMapping("/{id}/default")
    @Operation(summary = "Dat lam mac dinh")
    public AddressResponse setDefault(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        return service.setDefault(principal.id(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoa dia chi", description = "Xoa dia chi mac dinh thi dia chi moi nhat con lai len lam mac dinh.")
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        service.delete(principal.id(), id);
    }
}
