package com.yoho.erp.fms.common.util;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;

/**
 * 自己的Collectors
 * @author bo.sun
 */
public final class YHCollectors {

	/**
	 * 强制toMap
	 * @param f1
	 * @param f2
	 * @param <T>
	 * @param <K>
	 * @param <V>
	 * @return
	 */
	public static <T, K, V> Collector<T, ?, Map<K, V>> toMap(Function<T, K> f1, Function<T, V> f2) {
		return new ForceToMapCollector<>(f1, f2);
	}

}
