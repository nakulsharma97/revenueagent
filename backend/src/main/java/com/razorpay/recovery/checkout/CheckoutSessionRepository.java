package com.razorpay.recovery.checkout;

import com.razorpay.recovery.checkout.CheckoutSession;
import com.razorpay.recovery.checkout.CheckoutSession.CheckoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheckoutSessionRepository extends JpaRepository<CheckoutSession, Long> {
    List<CheckoutSession> findByStatusIn(List<CheckoutStatus> statuses);
    List<CheckoutSession> findByStatus(CheckoutStatus status);
}
