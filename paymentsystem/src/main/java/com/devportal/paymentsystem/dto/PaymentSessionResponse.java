package com.devportal.paymentsystem.dto;

public class PaymentSessionResponse {

	private String paymentSessionId;

	public PaymentSessionResponse() {
		super();
	}

	public PaymentSessionResponse(String paymentSessionId) {
		super();
		this.paymentSessionId = paymentSessionId;
	}

	public String getPaymentSessionId() {
		return paymentSessionId;
	}

	public void setPaymentSessionId(String paymentSessionId) {
		this.paymentSessionId = paymentSessionId;
	}

}
