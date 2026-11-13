package com.api.mpesa.dto;

public enum PaymentStatus {
  PENDING,   // prompt sent, waiting for Safaricom's callback
  SUCCESS,   // customer entered their PIN and the payment went through
  CANCELLED, // customer pressed Cancel on the prompt
  TIMEOUT,   // customer didn't respond, or the phone couldn't be reached
  FAILED     // anything else: wrong PIN, insufficient balance, ...
}
