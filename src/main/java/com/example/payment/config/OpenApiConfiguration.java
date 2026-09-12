/* 파일 역할: Swagger UI와 OpenAPI 문서에 표시할 API 제목·버전·설명을 설정한다. */
package com.example.payment.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/** API 문서의 공통 메타데이터를 제공한다. 개별 API 설명은 각 Controller의 어노테이션에 있다. */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Payment Service API",
        version = "v1",
        description = "주문 생성·조회와 결제 승인·결과 재확인을 제공하는 로컬 학습용 API"
))
public class OpenApiConfiguration {
}
