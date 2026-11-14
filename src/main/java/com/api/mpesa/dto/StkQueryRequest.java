package com.api.mpesa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body for Daraja's STK Push Query (POST /mpesa/stkpushquery/v1/query): "what happened to
 * this push?". Password and Timestamp are built the same way as for the push itself.
 */
public record StkQueryRequest(
    @JsonProperty("BusinessShortCode") String businessShortCode,
    @JsonProperty("Password") String password,
    @JsonProperty("Timestamp") String timestamp,
    @JsonProperty("CheckoutRequestID") String checkoutRequestId) {}
