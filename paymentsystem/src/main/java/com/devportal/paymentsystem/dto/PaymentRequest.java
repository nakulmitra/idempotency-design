package com.devportal.paymentsystem.dto;

public class PaymentRequest {

	private String paymentSessionId;
	private Double amount;
	private String receiver;

	public String getPaymentSessionId() {
		return paymentSessionId;
	}

	public void setPaymentSessionId(String paymentSessionId) {
		this.paymentSessionId = paymentSessionId;
	}

	public Double getAmount() {
		return amount;
	}

	public void setAmount(Double amount) {
		this.amount = amount;
	}

	public String getReceiver() {
		return receiver;
	}

	public void setReceiver(String receiver) {
		this.receiver = receiver;
	}

}