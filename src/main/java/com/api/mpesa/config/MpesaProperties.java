package com.api.mpesa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mpesa.daraja")
public record MpesaProperties(String baseUrl, String consumerKey, String consumerSecret) {}