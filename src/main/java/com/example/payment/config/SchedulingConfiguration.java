package com.example.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 앱 전체의 @Scheduled 작업을 켠다. 결제 복구와 미결제 주문 취소는 각자의 설정값으로 따로 켜고 끈다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfiguration {
}
