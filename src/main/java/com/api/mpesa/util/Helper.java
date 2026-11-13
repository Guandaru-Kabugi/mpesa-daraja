package com.api.mpesa.util;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import org.springframework.stereotype.Component;

@Component
public class Helper {
  public static String toBase64(String value){
    return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.ISO_8859_1));
  }
  public static String getTimeStamp(){
    return new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
  }
}
