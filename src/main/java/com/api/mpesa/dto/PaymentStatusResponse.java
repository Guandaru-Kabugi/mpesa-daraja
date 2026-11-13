package com.api.mpesa.dto;

/** What the frontend polls: one payment's current state, with a message it can show as-is. */
public record PaymentStatusResponse(
    String checkoutRequestId,
    PaymentStatus status,
    Integer resultCode, // Safaricom's code; null while PENDING
    String message,
    String receiptNumber, // e.g. "NLJ7RT61SV"; only on SUCCESS
    String amount,
    String phoneNumber) {

  public static PaymentStatusResponse pending(String checkoutRequestId, String amount, String phone) {
    return new PaymentStatusResponse(checkoutRequestId, PaymentStatus.PENDING, null,
        "Waiting for you to enter your M-Pesa PIN.", null, amount, phone);
  }
}
