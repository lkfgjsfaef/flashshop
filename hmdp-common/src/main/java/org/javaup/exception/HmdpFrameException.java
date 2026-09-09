package org.javaup.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.javaup.enums.BaseCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class HmdpFrameException extends BaseException {
	
	private Integer code;
	
	private String message;

	public HmdpFrameException() {
		super();
	}

	/**
	 * 注意：@Data 会生成 getMessage() 并覆盖 Throwable 的同名方法，
	 * 所以每个构造函数都必须同步给 this.message 赋值，
	 * 否则异常消息会恒为 null（线上日志看不到任何原因）。
	 */
	public HmdpFrameException(String message) {
		super(message);
		this.message = message;
	}
	
	public HmdpFrameException(Integer code, String message) {
		super(message);
		this.code = code;
		this.message = message;
	}
	
	public HmdpFrameException(BaseCode baseCode) {
		super(baseCode.getMsg());
		this.code = baseCode.getCode();
		this.message = baseCode.getMsg();
	}

	public HmdpFrameException(Throwable cause) {
		super(cause);
	}

	public HmdpFrameException(String message, Throwable cause) {
		super(message, cause);
		this.message = message;
	}
}
