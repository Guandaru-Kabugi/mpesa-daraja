package com.api.mpesa.service;

import com.api.mpesa.config.MpesaTokenProvider;
import com.api.mpesa.dto.PaymentStatus;
import com.api.mpesa.dto.PaymentStatusResponse;
import com.api.mpesa.dto.STKPushRequest;
import com.api.mpesa.dto.STKPushResponse;
import com.api.mpesa.dto.StkCallbackRequest;
import com.api.mpesa.util.Helper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
@Service
@Slf4j
public class MpesaServiceImpl implements MpesaServiceInterface {
  private final RestClient restClient;
  private final MpesaTokenProvider mpesaTokenProvider;
  private final PaymentStore paymentStore;

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

    STKPushResponse response = restClient.post()
        .uri("/mpesa/stkpush/v1/processrequest")
        .headers(httpHeaders -> httpHeaders.setBearerAuth(mpesaTokenProvider.getToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(stkPushRequest)
        .retrieve()
        .body(STKPushResponse.class);

    // "0" = the prompt was sent. Record it as PENDING so the status endpoint knows this
    // CheckoutRequestID before the callback arrives.
    if (response != null && "0".equals(response.getResponseCode())) {
      paymentStore.save(
          PaymentStatusResponse.pending(response.getCheckoutRequestID(), amount, phoneNumber));
    }
    return response;
  }

  @Override
  public void handleCallback(StkCallbackRequest callback) {
    StkCallbackRequest.StkCallback cb = callback.body().stkCallback();
    String id = cb.checkoutRequestId();
    // Prefer the amount/phone we saved at initiation: the callback only carries them on
    // success, and sends the amount as a number (1.00 → "1.0")
    PaymentStatusResponse previous = paymentStore.find(id).orElse(null);
    String amount = previous != null ? previous.amount() : cb.item("Amount");
    String phone = previous != null ? previous.phoneNumber() : cb.item("PhoneNumber");

    paymentStore.save(new PaymentStatusResponse(
        id, statusFor(cb.resultCode()), cb.resultCode(), messageFor(cb.resultCode(), cb.resultDesc()),
        cb.item("MpesaReceiptNumber"), amount, phone));
    log.info("STK callback {}: ResultCode={} ({})", id, cb.resultCode(), cb.resultDesc());
  }

  @Override
  public Optional<PaymentStatusResponse> getStatus(String checkoutRequestId) {
    return paymentStore.find(checkoutRequestId);
  }

  // Daraja's ResultCodes for STK push. The common ones get friendlier wording than ResultDesc.
  private static PaymentStatus statusFor(int resultCode) {
    return switch (resultCode) {
      case 0 -> PaymentStatus.SUCCESS;
      case 1032 -> PaymentStatus.CANCELLED;
      case 1037, 1019 -> PaymentStatus.TIMEOUT;
      default -> PaymentStatus.FAILED;
    };
  }

  private static String messageFor(int resultCode, String resultDesc) {
    return switch (resultCode) {
      case 0 -> "Payment received. Thank you!";
      case 1032 -> "You cancelled the payment on your phone.";
      case 1037 -> "We couldn't reach your phone, or the prompt timed out. Please try again.";
      case 1019 -> "The payment request expired. Please try again.";
      case 2001 -> "The M-Pesa PIN entered was wrong.";
      case 1 -> "Your M-Pesa balance is too low for this payment.";
      case 1001 -> "Another M-Pesa transaction is in progress on this phone. Try again in a minute.";
      default -> resultDesc;
    };
  }
}
