package com.devportal.paymentsystem.service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devportal.paymentsystem.constants.PaymentStatus;
import com.devportal.paymentsystem.dto.PaymentRequest;
import com.devportal.paymentsystem.dto.PaymentResponse;
import com.devportal.paymentsystem.dto.PaymentSessionResponse;
import com.devportal.paymentsystem.model.Payment;
import com.devportal.paymentsystem.model.PaymentSession;
import com.devportal.paymentsystem.repo.PaymentRepository;
import com.devportal.paymentsystem.repo.PaymentSessionRepository;

@Service
public class PaymentService {

	@Autowired
	private PaymentSessionRepository sessionRepository;

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;

	@Autowired
	private PaymentRepository paymentRepository;

	@Transactional
	public PaymentSessionResponse createSession() {
		String paymentSessionId = "ps" + UUID.randomUUID();

		PaymentSession session = new PaymentSession();
		session.setPaymentSessionId(paymentSessionId);
		;
		session.setStatus(PaymentStatus.PENDING);
		session.setCreatedAt(LocalDateTime.now());

		sessionRepository.save(session);

		return new PaymentSessionResponse(paymentSessionId);
	}

	@Transactional
	public PaymentResponse processPayment(PaymentRequest request, String idempotencyKey) {

		String redisKey = request.getPaymentSessionId() + ":" + idempotencyKey;

		// STEP 1 - CHECK REDIS
		PaymentResponse cachedResponse = (PaymentResponse) redisTemplate.opsForValue().get(redisKey);

		if (cachedResponse != null) {
			System.out.println("Returning cached response");
			return cachedResponse;
		}

		// STEP 2 - VALIDATE SESSION
		PaymentSession session = sessionRepository.findByPaymentSessionId(request.getPaymentSessionId())
				.orElseThrow(() -> new RuntimeException("Invalid session"));

		// STEP 3 - CHECK STATUS
		if(session.getStatus() == PaymentStatus.COMPLETED || session.getStatus() == PaymentStatus.FAILED) {
			throw new RuntimeException("Already transaction is " + session.getStatus());
		}

		// STEP 4 - PROCESS PAYMENT
		String transactionId = UUID.randomUUID().toString();
		
		Payment payment = new Payment();
		payment.setTransactionId(transactionId);
		payment.setReceiver(request.getReceiver());
		payment.setAmount(request.getAmount());
		payment.setPaymentSessionId(request.getPaymentSessionId());
		
		paymentRepository.save(payment);

		// STEP 5 - UPDATE SESSION STATUS
		
		session.setStatus(PaymentStatus.COMPLETED);
		sessionRepository.save(session);

		// STEP 6 - SAVE RESPONSE IN REDIS
		PaymentResponse response = new PaymentResponse(transactionId, "Success");
		
		redisTemplate.opsForValue().setIfAbsent(redisKey, response, 10, TimeUnit.MINUTES);

		return response;
	}

}
