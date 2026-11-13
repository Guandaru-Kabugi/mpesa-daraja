package com.api.mpesa.service;

import com.api.mpesa.config.MpesaTokenProvider;
import com.api.mpesa.dto.STKPushRequest;
import com.api.mpesa.dto.STKPushResponse;
import com.api.mpesa.util.Helper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
@Service
public class MpesaServiceImpl implements MpesaServiceInterface {
  private final RestClient restClient;
  private final MpesaTokenProvider mpesaTokenProvider;

  @Value("${mpesa.daraja.business-shortcode}")
  private String shortCode;
  @Value("${mpesa.daraja.passkey}")
  private String passKey;
  @Value("${mpesa.daraja.stk-push-callback-url}")
  private String callBackUrl;


  @Override
  public STKPushResponse initiateSTKPush(String phoneNumber, String amount) {
    phoneNumber = phoneNumber.startsWith("0") ? phoneNumber.replaceFirst("0", "254"): phoneNumber;
    String timeStamp = Helper.getTimeStamp();
    String password = Helper.toBase64(shortCode + passKey + timeStamp);
    STKPushRequest stkPushRequest = new STKPushRequest(
        shortCode,password,timeStamp,"CustomerPayBillOnline",
        amount,phoneNumber,shortCode,phoneNumber,callBackUrl,"Test", "Test"
    );

    return restClient.post()
        .uri("/mpesa/stkpush/v1/processrequest")
        .headers(httpHeaders -> httpHeaders.setBearerAuth(mpesaTokenProvider.getToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(stkPushRequest)
        .retrieve()
        .body(STKPushResponse.class);
  }
}
