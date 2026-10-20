package dev.techmart.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Project này không làm frontend, nên Swagger UI chính là giao diện sản phẩm.
 * Đầu tư vào mô tả ở đây đáng giá hơn nhiều thứ khác (docs/03-api-contract.md mục 13).
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI techMartOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TechMart API")
                        .version("v1")
                        .description("""
                                Backend thương mại điện tử thiết bị điện tử, tập trung vào tính đúng đắn
                                của tồn kho dưới tải đồng thời: giữ hàng có hạn dùng khi checkout,
                                chống bán quá tồn, mở bán giới hạn có rate limiting, thanh toán idempotent.

                                **Tài khoản demo**
                                - ADMIN: `admin@techmart.dev` / `Admin@123`
                                - STAFF: `staff@techmart.dev` / `Staff@123`
                                - CUSTOMER (có sẵn đơn hàng): `customer@techmart.dev` / `Customer@123`
                                - CUSTOMER (mới tinh): `new@techmart.dev` / `Customer@123`

                                **Cách dùng:** gọi `POST /api/v1/auth/login`, copy `accessToken`,
                                bấm Authorize ở góc trên bên phải và dán vào.
                                """))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Dán accessToken lấy từ /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
