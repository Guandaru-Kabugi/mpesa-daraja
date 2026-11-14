package com.api.mpesa.service;

import com.api.mpesa.dto.PaymentStatus;
import com.api.mpesa.dto.PaymentStatusResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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

  /** The payment plus bookkeeping the frontend doesn't need to see. */
  private record Entry(PaymentStatusResponse payment, Instant createdAt, Instant lastQueriedAt) {}

  private final Map<String, Entry> payments = new ConcurrentHashMap<>();

  /** Adds or replaces a payment, keeping its original createdAt. */
  public void save(PaymentStatusResponse payment) {
    payments.compute(payment.checkoutRequestId(), (id, old) -> old == null
        ? new Entry(payment, Instant.now(), null)
        : new Entry(payment, old.createdAt(), old.lastQueriedAt()));
  }

  /**
   * Like save, but only while the payment is still PENDING. Used for the STK Query's answer,
   * so it can't overwrite a callback that arrived in the meantime (the callback has the
   * receipt number; the query doesn't).
   */
  public void saveIfPending(PaymentStatusResponse payment) {
    payments.computeIfPresent(payment.checkoutRequestId(), (id, old) ->
        old.payment().status() == PaymentStatus.PENDING
            ? new Entry(payment, old.createdAt(), old.lastQueriedAt())
            : old);
  }

  /** True if the payment is still PENDING and was created more than {@code age} ago. */
  public boolean isPendingLongerThan(String checkoutRequestId, Duration age) {
    Entry e = payments.get(checkoutRequestId);
    return e != null
        && e.payment().status() == PaymentStatus.PENDING
        && Instant.now().isAfter(e.createdAt().plus(age));
  }

  public Optional<PaymentStatusResponse> find(String checkoutRequestId) {
    return Optional.ofNullable(payments.get(checkoutRequestId)).map(Entry::payment);
  }

  /**
   * Should we ask Safaricom about this payment now? True only if it's still PENDING, at least
   * {@code waitFirst} old, and nobody asked in the last {@code minGap}. Returning true also
   * records "asked now", in one atomic step: several browser tabs polling at once still make
   * only one Daraja call, which matters because Daraja rate-limits the query API.
   */
  public boolean claimQuery(String checkoutRequestId, Duration waitFirst, Duration minGap) {
    Instant now = Instant.now();
    AtomicBoolean claimed = new AtomicBoolean(false);
    payments.computeIfPresent(checkoutRequestId, (id, e) -> {
      boolean due = e.payment().status() == PaymentStatus.PENDING
          && now.isAfter(e.createdAt().plus(waitFirst))
          && (e.lastQueriedAt() == null || now.isAfter(e.lastQueriedAt().plus(minGap)));
      if (!due) {
        return e;
      }
      claimed.set(true);
      return new Entry(e.payment(), e.createdAt(), now);
    });
    return claimed.get();
  }
}
