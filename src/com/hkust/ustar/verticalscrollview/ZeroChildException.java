package com.hkust.ustar.verticalscrollview;

public class ZeroChildException extends Exception {

	private static final long serialVersionUID = 1L;
	
	public ZeroChildException() {
	}
	
	public ZeroChildException(String errorMessage) {
		super(errorMessage);
	}
}