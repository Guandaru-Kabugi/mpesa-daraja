package com.api.mpesa.service;

import com.api.mpesa.config.MpesaTokenProvider;
import com.api.mpesa.dto.PaymentStatus;
import com.api.mpesa.dto.PaymentStatusResponse;
import com.api.mpesa.dto.STKPushRequest;
import com.api.mpesa.dto.STKPushResponse;
import com.api.mpesa.dto.StkCallbackRequest;
import com.api.mpesa.dto.StkQueryRequest;
import com.api.mpesa.dto.StkQueryResponse;
import com.api.mpesa.exceptions.ApiException;
import com.api.mpesa.util.Helper;
import java.time.Duration;
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
    STKPushRequest stkPushRequest = new STKPushRequest(
        shortCode,password(timeStamp),timeStamp,"CustomerPayBillOnline",
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

    // save, not saveIfPending: the callback is the most complete answer (it has the receipt),
    // so it wins even if an STK query already settled the payment
    paymentStore.save(result(id, cb.resultCode(), cb.resultDesc(),
        cb.item("MpesaReceiptNumber"), amount, phone));
    log.info("STK callback {}: ResultCode={} ({})", id, cb.resultCode(), cb.resultDesc());
  }

  /**
   * Normally the callback settles a payment. But if it never arrives (sandbox hiccup, ngrok
   * down, wrong PUBLIC_BASE_URL, app restarted mid-payment), the page would wait forever.
   * So once a payment has been PENDING for a while, each status check may also ask Safaricom
   * directly. The waits keep us under Daraja's rate limit on the query API.
   */
  private static final Duration QUERY_AFTER = Duration.ofSeconds(20); // give the callback a chance
  private static final Duration QUERY_EVERY = Duration.ofSeconds(10); // however often the page polls
  /**
   * The hard stop. The prompt on the phone expires after about a minute, so if neither the
   * callback nor the query has an answer by now, the prompt most likely never arrived (e.g.
   * the sandbox is having problems). A callback that still turns up later overrides this,
   * because handleCallback uses save(), so a late payment isn't lost.
   */
  private static final Duration GIVE_UP_AFTER = Duration.ofSeconds(90);

  @Override
  public Optional<PaymentStatusResponse> getStatus(String checkoutRequestId) {
    if (paymentStore.claimQuery(checkoutRequestId, QUERY_AFTER, QUERY_EVERY)) {
      queryDaraja(checkoutRequestId);
    }
    if (paymentStore.isPendingLongerThan(checkoutRequestId, GIVE_UP_AFTER)) {
      paymentStore.find(checkoutRequestId).ifPresent(p -> {
        paymentStore.saveIfPending(new PaymentStatusResponse(p.checkoutRequestId(),
            PaymentStatus.TIMEOUT, null,
            "We didn't get a response from M-Pesa. If no prompt appeared on your phone, "
                + "please try again in a few minutes.",
            null, p.amount(), p.phoneNumber()));
        log.info("STK {} timed out: no answer from Safaricom after {}s", checkoutRequestId,
            GIVE_UP_AFTER.toSeconds());
      });
    }
    return paymentStore.find(checkoutRequestId);
  }

  /** Asks Daraja what happened to a push, and records the answer if it's final. */
  private void queryDaraja(String checkoutRequestId) {
    String timeStamp = Helper.getTimeStamp();
    StkQueryResponse res;
    try {
      res = restClient.post()
          .uri("/mpesa/stkpushquery/v1/query")
          .headers(h -> h.setBearerAuth(mpesaTokenProvider.getToken()))
          .contentType(MediaType.APPLICATION_JSON)
          .body(new StkQueryRequest(shortCode, password(timeStamp), timeStamp, checkoutRequestId))
          .retrieve()
          .body(StkQueryResponse.class);
    } catch (ApiException e) {
      // "500.001.1001" = the prompt is still open on the phone: not an error, just not done.
      // Anything else (rate limit, sandbox outage) we also treat as "ask again later".
      String body = e.getResponseBody();
      if (body != null && body.contains("500.001.1001")) {
        log.debug("STK query {}: still being processed", checkoutRequestId);
      } else {
        log.warn("STK query {} failed: {}", checkoutRequestId, e.getMessage());
      }
      return;
    } catch (RuntimeException e) { // network errors, timeouts
      log.warn("STK query {} failed: {}", checkoutRequestId, e.getMessage());
      return;
    }

    if (res == null || res.resultCode() == null) {
      return;
    }
    int resultCode;
    try {
      resultCode = Integer.parseInt(res.resultCode().trim());
    } catch (NumberFormatException e) {
      log.warn("STK query {}: unexpected ResultCode '{}'", checkoutRequestId, res.resultCode());
      return;
    }

    PaymentStatusResponse current = paymentStore.find(checkoutRequestId).orElse(null);
    if (current == null) {
      return;
    }
    // No receipt number: the query API doesn't return one
    paymentStore.saveIfPending(result(checkoutRequestId, resultCode, res.resultDesc(), null,
        current.amount(), current.phoneNumber()));
    log.info("STK query {}: ResultCode={} ({})", checkoutRequestId, resultCode, res.resultDesc());
  }

  private String password(String timeStamp) {
    return Helper.toBase64(shortCode + passKey + timeStamp);
  }

  private static PaymentStatusResponse result(String checkoutRequestId, int resultCode,
      String resultDesc, String receipt, String amount, String phone) {
    return new PaymentStatusResponse(checkoutRequestId, statusFor(resultCode), resultCode,
        messageFor(resultCode, resultDesc), receipt, amount, phone);
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
