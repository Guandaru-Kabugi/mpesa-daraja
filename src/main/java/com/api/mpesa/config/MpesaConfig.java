package com.api.mpesa.config;

import com.api.mpesa.exceptions.ApiException;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class MpesaConfig {
  @Bean
  public RestClient mpesaRestClient(RestClient.Builder builder, MpesaProperties mpesaProperties) {
    var jdkClient = java.net.http.HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    var factory = new JdkClientHttpRequestFactory(jdkClient);
    factory.setReadTimeout(Duration.ofSeconds(30));
    return builder
        .baseUrl(mpesaProperties.baseUrl())
        .requestFactory(factory)
        .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
          String body = new String(response.getBody().readAllBytes());
          throw new ApiException(
              request.getMethod() + " " + request.getURI() + " failed",
              response.getStatusCode().value(), body
          );
        }).build();
  }
  @Bean
  public ObjectMapper objectMapper() {
    return JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        .build();
  }

}
