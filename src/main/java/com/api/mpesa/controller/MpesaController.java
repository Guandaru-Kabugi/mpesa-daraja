package com.api.mpesa.controller;


import com.api.mpesa.dto.STKPushResponse;
import com.api.mpesa.service.MpesaServiceInterface;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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
}
