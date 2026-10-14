package dev.techmart.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Project này không làm frontend, nên Swagger UI chính là giao dien sản phẩm.
 * Dau từ vào mo ta ở đây dạng giả hơn nhiều thu khác (docs/03-api-contract.md mục 13).
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
                                Backend thuong mai dien tu thiet bi dien tu, tap trung vao tinh dung dan
                                cua ton kho duoi tai dong thoi: giu hang co han dung khi checkout,
                                chong ban qua ton, mo ban gioi han co rate limiting, thanh toan idempotent.

                                **Tai khoan demo**
                                - ADMIN: `admin@techmart.dev` / `Admin@123`
                                - STAFF: `staff@techmart.dev` / `Staff@123`
                                - CUSTOMER (co san don hang): `customer@techmart.dev` / `Customer@123`
                                - CUSTOMER (moi tinh): `new@techmart.dev` / `Customer@123`

                                **Cach dung:** goi `POST /api/v1/auth/login`, copy `accessToken`,
                                bam Authorize o goc tren ben phai va dan vao.
                                """))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Dan accessToken lay tu /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
