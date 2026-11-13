package com.api.mpesa.service;

import com.api.mpesa.dto.PaymentStatusResponse;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Remembers each payment's state by CheckoutRequestID, so the callback (written by Safaricom)
 * and the status endpoint (read by the browser) can meet in the middle.
 *
 * <p>In memory for now: it's empty again after a restart. Swap this for a JPA repository
 * when the service gets a database. ConcurrentHashMap because the callback and the
 * polling requests run on different threads at the same time.
 */
@Component
public class PaymentStore {
  private final Map<String, PaymentStatusResponse> payments = new ConcurrentHashMap<>();

  public void save(PaymentStatusResponse payment) {
    payments.put(payment.checkoutRequestId(), payment);
  }

  public Optional<PaymentStatusResponse> find(String checkoutRequestId) {
    return Optional.ofNullable(payments.get(checkoutRequestId));
  }
}
