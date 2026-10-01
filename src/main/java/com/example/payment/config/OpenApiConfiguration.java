package com.example.payment.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Payment Service API",
        version = "v1",
        description = "주문 생성·조회, 결제 승인·결과 재확인, 고객 1:1 상담을 제공하는 로컬 학습용 API. "
                + "주문·결제·상담 API는 Keycloak access token이 필요하고, /admin API는 shop-admin 역할이 필요하다."
), security = @SecurityRequirement(name = "keycloak"))
@SecurityScheme(name = "keycloak", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfiguration {
}
