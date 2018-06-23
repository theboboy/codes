package com.yoho.erp.fms.common.util;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * 强制toMap
 * @param <T>
 * @param <K>
 * @param <V>
 */
public class ForceToMapCollector<T, K, V> implements Collector<T, Map<K, V>, Map<K, V>> {

	private Function<? super T, ? extends K> keyMapper;

	private Function<? super T, ? extends V> valueMapper;

	public ForceToMapCollector(Function<? super T, ? extends K> keyMapper,
			Function<? super T, ? extends V> valueMapper) {
		super();
		this.keyMapper = keyMapper;
		this.valueMapper = valueMapper;
	}

	@Override
	public BiConsumer<Map<K, V>, T> accumulator() {
		return (map, element) -> map.put(keyMapper.apply(element), valueMapper.apply(element));
	}

	@Override
	public Supplier<Map<K, V>> supplier() {
		return HashMap::new;
	}

	@Override
	public BinaryOperator<Map<K, V>> combiner() {
		return (x, y) -> {
			x.putAll(y);
			return x;
		};
	}

	@Override
	public Function<Map<K, V>, Map<K, V>> finisher() {
		return null;
	}

	@Override
	public Set<Characteristics> characteristics() {
		return Collections.unmodifiableSet(EnumSet.of(Characteristics.IDENTITY_FINISH));
	}

}