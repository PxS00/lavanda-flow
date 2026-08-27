package com.ceudelavanda.lavandaflow.inventory.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InventoryAlertProperties.class)
public class InventoryAlertConfiguration {
}
