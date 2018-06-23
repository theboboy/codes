package com.theboboy.utils;

import java.util.regex.Pattern;

public class Patterns {

	public static Pattern digits = Pattern.compile("\\d+");

	public static Pattern url = Pattern.compile(
			"(http|https|ftp):\\/\\/[\\w\\-_]+(\\.[\\w\\-_]+)+([\\w\\-\\.,@?^=%&:/~\\+#]*[\\w\\-\\@?^=%&/~\\+#])?",
			Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);

	private Patterns() {
		super();
	}

}
