package com.example.payment.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Payment Service API",
        version = "v1",
        description = "주문 생성·조회와 결제 승인·결과 재확인을 제공하는 로컬 학습용 API"
))
public class OpenApiConfiguration {
}
