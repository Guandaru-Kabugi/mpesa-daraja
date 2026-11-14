package com.api.mpesa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Daraja's answer to an STK Push Query once the push has finished, e.g.
 *
 * <pre>
 * { "ResponseCode": "0", "ResponseDescription": "The service request has been accepted successsfully",
 *   "MerchantRequestID": "...", "CheckoutRequestID": "ws_CO_...",
 *   "ResultCode": "1032", "ResultDesc": "Request cancelled by user" }
 * </pre>
 *
 * Unlike the callback, ResultCode is a string here, and there's no receipt number.
 * While the customer still has the prompt open, Daraja doesn't send this at all: it answers
 * HTTP 500 with errorCode "500.001.1001" ("The transaction is being processed").
 */
public record StkQueryResponse(
    @JsonProperty("ResponseCode") String responseCode,
    @JsonProperty("ResponseDescription") String responseDescription,
    @JsonProperty("MerchantRequestID") String merchantRequestId,
    @JsonProperty("CheckoutRequestID") String checkoutRequestId,
    @JsonProperty("ResultCode") String resultCode,
    @JsonProperty("ResultDesc") String resultDesc) {}
