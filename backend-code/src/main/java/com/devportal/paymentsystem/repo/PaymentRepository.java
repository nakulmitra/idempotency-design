package com.devportal.paymentsystem.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devportal.paymentsystem.model.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {}