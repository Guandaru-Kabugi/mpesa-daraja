package com.api.mpesa.config;

import com.api.mpesa.dto.AccessTokenResponse;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class MpesaTokenProvider {
  private final RestClient restClient;
  private final MpesaProperties props;

  private volatile String token;
  private volatile Instant expiresAt = Instant.EPOCH;

  public synchronized String getToken() {
    //refresh 3 minutes early
    if (token == null || Instant.now().isAfter(expiresAt.minusSeconds(180))) {
      AccessTokenResponse res = restClient.get()
          .uri("/oauth/v1/generate?grant_type=client_credentials")
          .headers(h -> h.setBasicAuth(props.consumerKey(), props.consumerSecret()))
          .retrieve()
          .body(AccessTokenResponse.class);
      assert res != null;
      token = res.accessToken();
      expiresAt = Instant.now().plusSeconds(Long.parseLong(res.expiresIn()));

    }
    return token;
  }
}
