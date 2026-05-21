package com.devportal.paymentsystem.dto;

public class PaymentResponse {

	private String transactionId;
	private String status;
	private String errorMessage;

	public PaymentResponse() {
		super();
	}

	public PaymentResponse(String transactionId, String status) {
		super();
		this.transactionId = transactionId;
		this.status = status;
	}
	
	public PaymentResponse(String errorMessage) {
		super();
		this.errorMessage = errorMessage;
	}

	public String getTransactionId() {
		return transactionId;
	}

	public void setTransactionId(String transactionId) {
		this.transactionId = transactionId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

}