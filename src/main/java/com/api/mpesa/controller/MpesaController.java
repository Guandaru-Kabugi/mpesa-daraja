package com.api.mpesa.controller;


import com.api.mpesa.dto.PaymentStatusResponse;
import com.api.mpesa.dto.STKPushResponse;
import com.api.mpesa.dto.StkCallbackRequest;
import com.api.mpesa.service.MpesaServiceInterface;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/payments")
@Slf4j
@RequiredArgsConstructor
public class MpesaController {
  private final MpesaServiceInterface mpesaService;


  @PostMapping("/stk-push")
  public ResponseEntity<STKPushResponse> stkPush(@RequestBody Map<String, String> payload) {
    return ResponseEntity.ok(
        mpesaService.initiateSTKPush(payload.get("phoneNumber"), payload.get("amount")));
  }

  /**
   * Called by Safaricom, not the browser (hence the public ngrok URL while developing).
   * Always answer 200 with ResultCode 0, even if we can't make sense of the body: anything
   * else makes Safaricom treat the delivery as failed.
   */
  @PostMapping("/callback")
  public ResponseEntity<Map<String, Object>> callback(@RequestBody StkCallbackRequest callback) {
    try {
      mpesaService.handleCallback(callback);
    } catch (RuntimeException e) {
      log.error("Could not process STK callback", e);
    }
    return ResponseEntity.ok(Map.of("ResultCode", 0, "ResultDesc", "Accepted"));
  }

  /** Polled by the frontend every few seconds after the push, until it's no longer PENDING. */
  @GetMapping("/{checkoutRequestId}/status")
  public ResponseEntity<PaymentStatusResponse> status(@PathVariable String checkoutRequestId) {
    return mpesaService.getStatus(checkoutRequestId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }
}
