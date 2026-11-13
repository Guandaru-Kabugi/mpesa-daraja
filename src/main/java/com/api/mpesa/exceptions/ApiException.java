package com.api.mpesa.exceptions;

public class ApiException extends RuntimeException {
  private final int status;
  private final String responseBody;

  public ApiException(String message, int status, String responseBody) {
    super(message + " (HTTP " + status + "): " + responseBody);
    this.status = status;
    this.responseBody = responseBody;
  }

  public ApiException(String message, Throwable cause) {
    super(message, cause);
    this.status = -1;
    this.responseBody = null;
  }

  public int getStatus() { return status; }
  public String getResponseBody() { return responseBody; }
}