package com.api.mpesa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * What Safaricom POSTs to our callback URL once the customer acts on the PIN prompt.
 *
 * <pre>
 * { "Body": { "stkCallback": {
 *     "MerchantRequestID": "...", "CheckoutRequestID": "ws_CO_...",
 *     "ResultCode": 0, "ResultDesc": "The service request is processed successfully.",
 *     "CallbackMetadata": { "Item": [            // only present when ResultCode is 0
 *       { "Name": "Amount", "Value": 1.00 },
 *       { "Name": "MpesaReceiptNumber", "Value": "NLJ7RT61SV" },
 *       { "Name": "TransactionDate", "Value": 20191219102115 },
 *       { "Name": "PhoneNumber", "Value": 254708374149 } ] } } } }
 * </pre>
 */
public record StkCallbackRequest(@JsonProperty("Body") Body body) {

  public record Body(@JsonProperty("stkCallback") StkCallback stkCallback) {}

  public record StkCallback(
      @JsonProperty("MerchantRequestID") String merchantRequestId,
      @JsonProperty("CheckoutRequestID") String checkoutRequestId,
      @JsonProperty("ResultCode") int resultCode,
      @JsonProperty("ResultDesc") String resultDesc,
      @JsonProperty("CallbackMetadata") CallbackMetadata callbackMetadata) {

    /** Looks up one metadata value, e.g. item("MpesaReceiptNumber"); null if absent. */
    public String item(String name) {
      if (callbackMetadata == null || callbackMetadata.items() == null) {
        return null;
      }
      return callbackMetadata.items().stream()
          .filter(i -> name.equals(i.name()) && i.value() != null)
          .map(i -> String.valueOf(i.value()))
          .findFirst()
          .orElse(null);
    }
  }

  public record CallbackMetadata(@JsonProperty("Item") List<Item> items) {}

  // Value is a number for Amount/PhoneNumber/TransactionDate and a string for the receipt
  public record Item(@JsonProperty("Name") String name, @JsonProperty("Value") Object value) {}
}
