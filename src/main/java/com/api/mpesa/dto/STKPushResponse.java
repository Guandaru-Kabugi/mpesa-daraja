package com.api.mpesa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class STKPushResponse {
  @JsonProperty("MerchantRequestID")
  private String merchantRequestID;
  @JsonProperty("CheckoutRequestID")
  private String checkoutRequestID;
  @JsonProperty("ResponseCode")
  private String responseCode;
  @JsonProperty("ResponseDescription")
  private String responseDescription;
  @JsonProperty("CustomerMessage")
  private String customerMessage;

}
