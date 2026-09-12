/* 파일 역할: 주문·결제 REST API를 제공하는 Spring Boot 애플리케이션의 실행 진입점이다. */
package com.example.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 자동 설정과 이 패키지 아래의 컴포넌트 탐색을 활성화하는 애플리케이션 시작 클래스다. */
@SpringBootApplication
public class PaymentServiceApplication {

	/** Spring 컨테이너와 내장 웹 서버를 시작하여 Controller가 HTTP 요청을 받을 수 있게 한다. */
	public static void main(String[] args) {
		SpringApplication.run(PaymentServiceApplication.class, args);
	}
}
