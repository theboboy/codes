package com.yoho.erp.sync.inventory.util;

import java.util.stream.Stream;

/**
 * @author bo.sun
 */
public final class ToStringUtils {

	/**
	 * 换行符
	 */
	private static final String lineSeparator = System.getProperty("line.separator", "\n");

	/**
	 * 获取异常对象的字符串表示
	 */
	private static String exceptionToString(Throwable e) {
		StringBuilder builder = new StringBuilder(1024);
		builder.append(e.getClass()).append(": ").append(e.getMessage());
		for (StackTraceElement trace : e.getStackTrace()) {
			builder.append("\tat ");
			builder.append(trace);
			builder.append(lineSeparator);
		}
		if (e.getSuppressed() != null) {
			builder.append("Suppressed: ");
			Stream.of(e.getSuppressed()).map(ToStringUtils::exceptionToString).forEach(builder::append);
		}
		if (e.getCause() != null) {
			builder.append("Caused by: ");
			builder.append(exceptionToString(e.getCause()));
		}
		return builder.toString();
	}

	/**
	 * 获取对象的字符串表示
	 */
	public static String objectToString(Object object) {
		if (Throwable.class.isAssignableFrom(object.getClass())) {
			return exceptionToString((Throwable) object);
		} else {
			return String.valueOf(object);
		}
	}

}
