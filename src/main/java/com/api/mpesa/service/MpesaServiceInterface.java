package com.api.mpesa.service;


import com.api.mpesa.dto.PaymentStatusResponse;
import com.api.mpesa.dto.STKPushResponse;
import com.api.mpesa.dto.StkCallbackRequest;
import java.util.Optional;


public interface MpesaServiceInterface {
  STKPushResponse initiateSTKPush(String phoneNumber, String amount);

  void handleCallback(StkCallbackRequest callback);

  Optional<PaymentStatusResponse> getStatus(String checkoutRequestId);
}
