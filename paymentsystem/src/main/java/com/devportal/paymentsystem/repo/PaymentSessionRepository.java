package com.devportal.paymentsystem.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devportal.paymentsystem.model.PaymentSession;

public interface PaymentSessionRepository extends JpaRepository<PaymentSession, Long> {

	Optional<PaymentSession> findByPaymentSessionId(String paymentSessionId);
}