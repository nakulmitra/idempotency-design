package com.devportal.paymentsystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.devportal.paymentsystem.dto.PaymentRequest;
import com.devportal.paymentsystem.dto.PaymentResponse;
import com.devportal.paymentsystem.dto.PaymentSessionResponse;
import com.devportal.paymentsystem.service.PaymentService;

@RestController
@RequestMapping("/api")
public class PaymentController {

	@Autowired
	private PaymentService service;

	@PostMapping(value = "/payment-sessions")
	public PaymentSessionResponse createSession() {
		return service.createSession();
	}

	@PostMapping(value = "/payments")
	public PaymentResponse makePayment(@RequestHeader("Idempotency-Key") String idempotencyKey,
			@RequestBody PaymentRequest request) {
		try {
			return service.processPayment(request, idempotencyKey);
		} catch (Exception e) {
			System.err.println("Error during processing payment: " + e.getMessage());
			return new PaymentResponse(e.getMessage());
		}
	}

}
