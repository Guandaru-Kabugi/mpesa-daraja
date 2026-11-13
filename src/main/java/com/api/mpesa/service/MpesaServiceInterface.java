package com.api.mpesa.service;


import com.api.mpesa.dto.STKPushResponse;


public interface MpesaServiceInterface {
  STKPushResponse initiateSTKPush(String phoneNumber, String amount);
}
