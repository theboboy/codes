package com.theboboy.nirvana.collections;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.text.Collator;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.RandomAccess;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * 这个支持复合的查询条件
 * @author sunbo
 *
 * @param <E>
 */
public class Queryable<E> implements List<E>, RandomAccess, Serializable, Cloneable {

	/**
	 * default serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * 这个是真正存放数据的对象 use-a 关系
	 */
	private ArrayList<E> array;
	
	/**
	 * 用这个做锁保证线程安全 除了iterator方法
	 */
	private final ReentrantLock lock = new ReentrantLock();
	
	/**
	 * 用这个来拆分queryString
	 */
	private static final Pattern separatorPattern = Pattern.compile("(\\(|\\)|!=|<>|>=|<=|>|<|==|=|\\?|,)");
	
	/**
	 * 两个辅助变量
	 */
	private static final int AND = 1, OR = 2;
	
	/**
	 * 纯数字的正则
	 */
	private static final Pattern pureDigitPattern = Pattern.compile("\\d+");
	
	/**
	 * yyyy-MM-dd和yyyy/mm/dd的正则
	 */
	private static final Pattern yyyyMMddBarPattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2}"), yyyyMMddSlashPattern = Pattern.compile("\\d{4}/\\d{2}/\\d{2}");
	
	/**
	 * yyyy-MM-dd HH:mm:ss和yyyy/mm/dd HH:mm:ss的正则
	 */
	private static final Pattern yyyyMMddHHmmssBarPattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"), yyyyMMddHHmmssSlashPattern = Pattern.compile("\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}");
	
	/**
	 * yyyy-MM-dd HH:mm:ss.SSS和yyyy/mm/dd HH:mm:ss.SSS的正则
	 */
	private static final Pattern yyyyMMddHHmmssSSSBarPattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"), yyyyMMddHHmmssSSSSlashPattern = Pattern.compile("\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}");
	
	/**
	 * 数组字面量的正则 支持[] [1] [1, 2]
	 */
	private static final Pattern arrayPattern = Pattern.compile("\\[\\s*([^\\]]*?)(,\\s*.*)*\\s*\\]");
	
	/**
	 * 键值对字面量的正则 支持{} {a: 1} {a: 1, b: second}
	 */
	private static final Pattern mapPattern = Pattern.compile("\\{\\s*(,{0,1}[^:^\\}]*:[^,^\\}]*)*\\s*\\}");
	
	/**
	 * where和order by默认的字符串比较中汉字是按照首字母的顺序比较的
	 */
	private static final Comparator<Object> CHINESE_COMPARATOR = Collator.getInstance(Locale.CHINA);
	
	/**
	 * Usage: new Queryable();
	 */
	public Queryable() {
		array = new ArrayList<E>();
	}
	
	/**
	 * Usage: new Queryable(64);
	 * @param size 初始长度
	 */
	public Queryable(int size) {
		array = new ArrayList<E>(size);
	}
	
	public Queryable(Collection<? extends E> c) {
		array = new ArrayList<E>(c);
	}
	
	public Queryable(E... es) {
		if(es == null) {
			array = new ArrayList<E>(1);
			array.add(null);
		} else {
			array = new ArrayList<E>(es.length);
			for(int i = 0, len = es.length; i < len; i++) {
				array.add(es[i]);
			}
		}
	}
	
	/**
	 * Usage: q.query("select distinct where id > 0 and (isdel != 0 or test = true) and author = ? and version > ? order by date desc limit ?, ?", "theboboy", 1.0, 1, 10);
	 * @param queryString 表达式
	 * @param args 参数 替换问号
	 * @return
	 */
	public Queryable<E> query(String queryString, final Object... args) {
		String[] arr = separatorPattern.matcher(queryString).replaceAll(" $0 ").trim().split("\\s+");
		boolean distinctAware = false, whereAware = false, orderAware = false, limitAware = false;
		final ArrayList<OrderBy> orderBy = new ArrayList<OrderBy>(1);
		OrderBy ob = new OrderBy();
		ArrayList<Limit> limit = new ArrayList<Limit>(2);
		boolean isProp = true;
		int paramIndex = 0;
		int lastLogic = AND;
		Graph graph = new Graph();
		GraphNode begin = new GraphNode(true);
		graph.addNode(begin);
		LinkedList<GraphNode> virtualStack = new LinkedList<GraphNode>();
		LinkedList<GraphNode> currentNodes = new LinkedList<GraphNode>();
		currentNodes.add(begin);
		GraphNode node = new GraphNode(false);
		boolean isLeft = true;
		for(int i = 0, len = arr.length; i < len; i++) {
			String current = arr[i];
			if(!distinctAware && "distinct".equalsIgnoreCase(current)) {
				distinctAware = true;
				continue;
			}
			if(!whereAware && "where".equalsIgnoreCase(current)) {
				whereAware = true;
				continue;
			}
			if(!orderAware && "order".equalsIgnoreCase(current)) {
				orderAware = true;
				continue;
			}
			if(!limitAware && "limit".equalsIgnoreCase(current)) {
				limitAware = true;
				continue;
			}
			if(limitAware) {
				if(!",".equals(current)) {
					limit.add(new Limit(current, "?".equals(current) ? paramIndex : -1));
				}
			} else if(orderAware) {
				if("by".equalsIgnoreCase(current)) {
					continue;
				} else if(",".equals(current)) {
					ob = new OrderBy();
					isProp = true;
				} else {
					if(isProp) {
						orderBy.add(ob);
						ob.prop = current;
						ob.propIndex = "?".equals(current) ? paramIndex : -1;
						isProp = false;
					} else {
						ob.isDesc = "desc".equalsIgnoreCase(current);
						ob.orderIndex = "?".equals(current) ? paramIndex : -1;
						isProp = true;
					}
				}
			} else if(whereAware) {
				if("and".equalsIgnoreCase(current)) {
					lastLogic = AND;
					isLeft = true;
				} else if("or".equalsIgnoreCase(current)) {
					lastLogic = OR;
					isLeft = true;
				} else if(isOperator(current)) {
					if("(".equals(current)) {
						node.bracketStart = true;
						isLeft = true;
					} else if(")".equals(current)) {
						while(!virtualStack.isEmpty()) {
							GraphNode v = virtualStack.removeFirst();
							if(virtualStack.isEmpty() || v.bracketStart) {
								currentNodes.clear();
								currentNodes.add(v);
								break;
							}
						}
						isLeft = false;
					} else {
						if(node.condition != null) {
							if("in".equals(current) && "not".equals(node.condition.operator)) {
								node.condition.operator = "not in";
							} else if("like".equals(current) && "not".equals(node.condition.operator)) {
								node.condition.operator = "not like";
							} else {
								node.condition.operator = current;
							}
						}
					}
				} else {
					if(isLeft) {
						if(node.condition != null) {
							node.condition.left = current;
						}
						isLeft = false;
					} else {
						if(node.condition != null) {
							node.condition.right = current;
							node.condition.paramIndex = "?".equals(current) ? paramIndex : -1;
						}
						isLeft = true;
					}
					if(node.isFull()) {
						if(lastLogic == AND) {
							for(GraphNode src : currentNodes) {
								LinkedList<GraphNode> leaves = new LinkedList<GraphNode>();
								Graph.getLeaves(graph, src, leaves);
								for(GraphNode leaf : leaves) {
									graph.link(leaf, node);
								}
							}
						} else if(lastLogic == OR) {
							while(!virtualStack.isEmpty()) {
								GraphNode v = virtualStack.peekFirst();
								if(virtualStack.size() == 1 || v.bracketStart) {
									currentNodes.clear();
									currentNodes.add(v);
									break;
								}
								virtualStack.removeFirst();
							}
							for(GraphNode src : currentNodes) {
								for(GraphNode prev : graph.previousNodes.get(src)) {
									graph.link(prev, node);
								}
							}
						}
						currentNodes.clear();
						currentNodes.add(node);
						virtualStack.push(node);
						node = new GraphNode(false);
					}
				}
			}
			
			if("?".equals(current)) {
				++paramIndex;
				isLeft = true;
			}
			
		}
		
		//System.out.println(graph);
		
		if(paramIndex != args.length) {
			throw new RuntimeException("参数个数不一样");
		}
		
		Queryable<E> list = new Queryable<E>();
		
		try {
			lock.lock();
			
			if(distinctAware) {
				LinkedHashSet<E> set = new LinkedHashSet<E>(this);
				list = new Queryable<>(set);
				set.clear();
			}
			
			if(whereAware) {
				E e = null;
				for(int i = 0, len = array.size(); i < len; i++) {
					e = array.get(i);
					if(isTruth(graph, e, args)) {
						list.add(e);
					}
				}
			}
			
			if(orderAware) {
				Comparator<E> comparator = new Comparator<E>() {
					@Override
					public int compare(Object o1, Object o2) {
						OrderBy order = null;
						for(int i = 0, len = orderBy.size(); i < len; i++) {
							order = orderBy.get(i);
							String field = order.propIndex < 0 ? order.prop : String.valueOf(args[order.propIndex]);
							o1 = ReflectionUtils.getValue(field, o1);
							o2 = ReflectionUtils.getValue(field, o2);	
							if(isTruth(o1, "=", o2)) {
								continue;
							}
							break;
						}
						if(order.orderIndex >= 0) {
							order.isDesc = "desc".equalsIgnoreCase(String.valueOf(args[order.orderIndex]));
						}
						return (isTruth(o1, ">", o2) ? 1 : -1) * (order.isAsc() ? 1 : -1);
					}
				};
				Collections.sort(list, comparator);
			}
			
			if(limitAware) {
				String start, size;
				int first, end;
				if(limit.size() == 1) {
					Limit temp = limit.get(0);
					start = "0";
					size = temp.paramIndex < 0 ? temp.param : String.valueOf(args[temp.paramIndex]);
				} else {
					Limit temp = limit.get(0);
					start = temp.paramIndex < 0 ? temp.param : String.valueOf(args[temp.paramIndex]);
					temp = limit.get(limit.size() - 1);
					size = temp.paramIndex < 0 ? temp.param : String.valueOf(args[temp.paramIndex]);
				}
				first = Integer.parseInt(start);
				end = first + Integer.parseInt(size);
				list.array = new ArrayList<E>(list.array.subList(first, end));
			}
			
		} finally {
			lock.unlock();
		}
		
		return list;
	}
	
	private boolean isOperator(String str) {
		return !"?".equals(str) && separatorPattern.matcher(str).matches() || "not".equals(str) || "in".equals(str) || "like".equals(str);
	}
	
	private boolean isTruth(Graph graph, Object obj, Object[] params) {
		graph.copyLines();
		LinkedList<GraphNode> stack = new LinkedList<GraphNode>();
		GraphNode node = graph.nodes.getFirst(), top = null;
		for(;;) {
			if(node.alwaysTrue || isTruth(node, obj, params)) {
				stack.push(node);
				LinkedList<GraphLine> lines = graph.copies.get(node);
				if(lines == null || lines.isEmpty()) { // 成功并且没有后节点
					return true;
				}
				node = lines.getFirst().dist;
			} else {
				top = stack.peek();
				if(top == null) {
					stack.push(node);
					break;
				}
				LinkedList<GraphLine> lines = graph.copies.get(top);
				lines.removeFirst();
				if(lines.isEmpty()) {
					stack.push(node);
					break;
				}
				node = lines.getFirst().dist;
			}
		}
		return false;
	}
	
	private boolean isTruth(GraphNode node, Object obj, Object[] params) {
		if(node.alwaysTrue) {
			return true;
		}
		Object value = ReflectionUtils.getValue(node.condition.left, obj), param;
		if(node.condition.paramIndex < 0) {
			param = convertConst(node.condition.right, value != null ? value.getClass() : null);
		} else {
			param = params[node.condition.paramIndex];
		}
		return isTruth(value, node.condition.operator, param);
	}

	private boolean isTruth(Object value, String operator, Object param) {
		if("=".equals(operator) || "==".equals(operator) || "<>".equals(operator) || "is".equals(operator)) {
			if(value == null || "".equals(String.valueOf(value))) {
				return param == null || "".equals(String.valueOf(param));
			}
			if(param == null) {
				return false;
			}
			if(value == param || value.equals(param)) {
				return true;
			}
			if(Comparable.class.isAssignableFrom(value.getClass())) {
				if(((Comparable) value).compareTo(param) == 0) {
					return true;
				}
			}
			if(CharSequence.class.isAssignableFrom(value.getClass())) {
				return value.toString().equals(String.valueOf(param));
			}
			if(Number.class.isAssignableFrom(value.getClass())) {
				if(Number.class.isAssignableFrom(param.getClass())) {
					return ((Number) value).doubleValue() == ((Number) param).doubleValue();
				}
				if(CharSequence.class.isAssignableFrom(param.getClass())) {
					return ((Number) value).doubleValue() == Double.parseDouble(((CharSequence) param).toString());
				}
				return false;
			}
			if(Calendar.class.isAssignableFrom(value.getClass()) || Date.class.isAssignableFrom(value.getClass())) {
				long millis = Calendar.class.isAssignableFrom(value.getClass()) ? ((Calendar) value).getTimeInMillis() : ((Date) value).getTime();
				if(Calendar.class.isAssignableFrom(param.getClass())) {
					return millis == ((Calendar) param).getTimeInMillis();
				}
				if(Date.class.isAssignableFrom(param.getClass())) {
					return millis == ((Date) param).getTime();
				}
				if(Number.class.isAssignableFrom(param.getClass())) {
					return millis == ((Number) param).longValue();
				}
				if(CharSequence.class.isAssignableFrom(param.getClass())) {
					String s = ((CharSequence) param).toString();
					Date date = strToDate(s);
					if(date == null) {
						return false;
					}
					return millis == date.getTime();
				}
				return false;
			}
			if(value.getClass().isArray()) {
				int valueLen = Array.getLength(value), paramLen = -1;
				if(param.getClass().isArray()) {
					paramLen = Array.getLength(param);
					if(valueLen == paramLen) {
						LinkedList<Object> temp = new LinkedList<>();
						for(int i = 0; i < valueLen; i++) {
							temp.add(Array.get(value, i));
						}
						for(int i = 0; i < paramLen; i++) {
							Object paramI = Array.get(param, i);
							int size = temp.size();
							for(Iterator<Object> valueItr = temp.iterator(); valueItr.hasNext(); ) {
								if(isTruth(valueItr.next(), "=", paramI)) {
									valueItr.remove();
									break;
								}
							}
							if(temp.size() == size) {
								break;
							}
						}
						return temp.isEmpty();
					}
					return false;
				}
				if(Collection.class.isAssignableFrom(param.getClass())) {
					paramLen = ((Collection) param).size();
					if(valueLen == paramLen) {
						LinkedList<Object> temp = new LinkedList<>();
						for(int i = 0; i < valueLen; i++) {
							temp.add(Array.get(value, i));
						}
						for(Iterator<Object> itr = ((Collection) param).iterator(); itr.hasNext(); ) {
							Object paramI = itr.next();
							int size = temp.size();
							for(Iterator<Object> valueItr = temp.iterator(); valueItr.hasNext(); ) {
								if(isTruth(valueItr.next(), "=", paramI)) {
									valueItr.remove();
									break;
								}
							}
							if(temp.size() == size) {
								break;
							}
						}
						return temp.isEmpty();
					}
					return false;
				}
			}
			if(Collection.class.isAssignableFrom(value.getClass())) {
				int valueLen = ((Collection) value).size(), paramLen = -1;
				if(param.getClass().isArray()) {
					paramLen = Array.getLength(param);
					if(valueLen == paramLen) {
						LinkedList<Object> temp = new LinkedList<>(((Collection) value));
						for(int i = 0; i < paramLen; i++) {
							Object paramI = Array.get(param, i);
							int size = temp.size();
							for(Iterator<Object> valueItr = temp.iterator(); valueItr.hasNext(); ) {
								if(isTruth(valueItr.next(), "=", paramI)) {
									valueItr.remove();
									break;
								}
							}
							if(temp.size() == size) {
								break;
							}
						}
						return temp.isEmpty();
					}
					return false;
				}
				if(Collection.class.isAssignableFrom(param.getClass())) {
					paramLen = ((Collection) param).size();
					if(valueLen == paramLen) {
						LinkedList<Object> temp = new LinkedList<>(((Collection) value));
						for(Iterator<Object> itr = ((Collection) param).iterator(); itr.hasNext(); ) {
							Object paramI = itr.next();
							int size = temp.size();
							for(Iterator<Object> valueItr = temp.iterator(); valueItr.hasNext(); ) {
								if(isTruth(valueItr.next(), "=", paramI)) {
									valueItr.remove();
									break;
								}
							}
							if(temp.size() == size) {
								break;
							}
						}
						return temp.isEmpty();
					}
					return false;
				}
			}
			Map<Object, Object> valueMap = (Map<Object, Object>) (Map.class.isAssignableFrom(value.getClass()) ? value : ReflectionUtils.beanToHashMap(param));
			Map<Object, Object> paramMap = (Map<Object, Object>) (Map.class.isAssignableFrom(param.getClass()) ? param : ReflectionUtils.beanToHashMap(param));
			if(valueMap.size() != paramMap.size()) {
				return false;
			}
			Set<Map.Entry<Object, Object>> entries = valueMap.entrySet();
			for(Map.Entry<Object, Object> entry : entries) {
				if(!isTruth(entry.getValue(), "=", paramMap.get(entry.getKey()))) {
					return false;
				}
			}
			return true;
		} else if ("!=".equals(operator) || "is not".equals(operator)) {
			return !isTruth(value, "=", param);
		} else if ("in".equals(operator)) {
			if(value.getClass().isArray() || Collection.class.isAssignableFrom(value.getClass())) {
				if(isTruth(value, "<", param)) {
					return true;
				}
			}
			if(param.getClass().isArray()) {
				int paramLen = Array.getLength(param);
				for(int i = 0; i < paramLen; i++) {
					Object paramI = Array.get(param, i);
					if(isTruth(value, "=", paramI)) {
						return true;
					}
				}
				return false;
			}
			if(Collection.class.isAssignableFrom(param.getClass())) {
				int paramLen = ((Collection) param).size();
				for(Iterator<Object> itr = ((Collection) param).iterator(); itr.hasNext(); ) {
					Object paramI = itr.next();
					if(isTruth(value, "=", paramI)) {
						return true;
					}
				}
				return false;
			}
		} else if ("not in".equals(operator)) {
			return !isTruth(value, "in", param);
		} else if ("like".equals(operator)) {
			if(value == null || param == null) {
				return false;
			}
			Pattern pattern = null;
			if(Pattern.class.isAssignableFrom(param.getClass())) {
				pattern = (Pattern) param;
			} else if(CharSequence.class.isAssignableFrom(param.getClass())) {
				pattern = Pattern.compile(Matcher.quoteReplacement(param.toString()));
			}
			if(pattern == null) {
				return false;
			}
			if(CharSequence.class.isAssignableFrom(value.getClass())) {
				return pattern.matcher(value.toString()).matches();
			}
			if(Number.class.isAssignableFrom(value.getClass())) {
				return pattern.matcher(String.valueOf(((Number) value).longValue())).matches();
			}
			if(Calendar.class.isAssignableFrom(value.getClass()) || Date.class.isAssignableFrom(value.getClass())) {
				long millis = Calendar.class.isAssignableFrom(value.getClass()) ? ((Calendar) value).getTimeInMillis() : ((Date) value).getTime();
				String s = pattern.toString(), fmt = null;
				switch(s.length()) {
				case 8:
					if(pureDigitPattern.matcher(s).find()) {
						fmt = "yyyyMMdd";
					}
					break;
				case 10:
					if(yyyyMMddBarPattern.matcher(s).find()) {
						fmt = "yyyy-MM-dd";
					} else if(yyyyMMddSlashPattern.matcher(s).find()) {
						fmt = "yyyy/MM/dd";
					}
					break;
				case 14:
					if(pureDigitPattern.matcher(s).find()) {
						fmt = "yyyyMMddHHmmss";
					}
					break;
				case 19:
					if(yyyyMMddHHmmssBarPattern.matcher(s).find()) {
						fmt = "yyyy-MM-dd HH:mm:ss";
					} else if(yyyyMMddHHmmssSlashPattern.matcher(s).find()) {
						fmt = "yyyy/MM/dd HH:mm:ss";
					}
					break;
				case 18:
					if(pureDigitPattern.matcher(s).find()) {
						fmt = "yyyyMMddHHmmssSSS";
					}
					break;
				case 23:
					if(yyyyMMddHHmmssSSSBarPattern.matcher(s).find()) {
						fmt = "yyyy-MM-dd HH:mm:ss.SSS";
					} else if(yyyyMMddHHmmssSSSSlashPattern.matcher(s).find()) {
						fmt = "yyyy/MM/dd HH:mm:ss.SSS";
					}
					break;
				}
				if(fmt == null) {
					return false;
				}
				return pattern.matcher(new SimpleDateFormat(fmt).format(millis)).matches();
			}
		} else if ("not like".equals(operator)) {
			return !isTruth(value, "like", param);
		} else if (">".equals(operator)) {
			if(CharSequence.class.isAssignableFrom(value.getClass())) {
				return CHINESE_COMPARATOR.compare(value.toString(), String.valueOf(param)) > 0;
			}
			if(Number.class.isAssignableFrom(value.getClass())) {
				if(Number.class.isAssignableFrom(param.getClass())) {
					return Double.compare(((Number) value).doubleValue(), ((Number) param).doubleValue()) > 0;
				}
				if(CharSequence.class.isAssignableFrom(param.getClass())) {
					return Double.compare(((Number) value).doubleValue(), Double.parseDouble(((CharSequence) param).toString())) > 0;
				}
				return false;
			}
			if(Calendar.class.isAssignableFrom(value.getClass()) || Date.class.isAssignableFrom(value.getClass())) {
				long millis = Calendar.class.isAssignableFrom(value.getClass()) ? ((Calendar) value).getTimeInMillis() : ((Date) value).getTime();
				if(Calendar.class.isAssignableFrom(param.getClass())) {
					return millis > ((Calendar) param).getTimeInMillis();
				}
				if(Date.class.isAssignableFrom(param.getClass())) {
					return millis > ((Date) param).getTime();
				}
				if(Number.class.isAssignableFrom(param.getClass())) {
					return millis > ((Number) param).longValue();
				}
				if(CharSequence.class.isAssignableFrom(param.getClass())) {
					String s = ((CharSequence) param).toString();
					Date date = strToDate(s);
					if(date == null) {
						return false;
					}
					return millis > date.getTime();
				}
				return false;
			}
			if(value.getClass().isArray()) {
				int valueLen = Array.getLength(value), paramLen = -1;
				if(param.getClass().isArray()) {
					paramLen = Array.getLength(param);
					if(valueLen > paramLen) {
						LinkedList<Object> temp = new LinkedList<>();
						for(int i = 0; i < valueLen; i++) {
							temp.add(Array.get(value, i));
						}
						for(int i = 0; i < paramLen; i++) {
							Object paramI = Array.get(param, i);
							int size = temp.size();
							for(Iterator<Object> valueItr = temp.iterator(); valueItr.hasNext(); ) {
								if(isTruth(valueItr.next(), "=", paramI)) {
									valueItr.remove();
									break;
								}
							}
							if(temp.size() == size) {
								break;
							}
						}
						return !temp.isEmpty();
					}
					return false;
				}
				if(Collection.class.isAssignableFrom(param.getClass())) {
					paramLen = ((Collection) param).size();
					if(valueLen > paramLen) {
						LinkedList<Object> temp = new LinkedList<>();
						for(int i = 0; i < valueLen; i++) {
							temp.add(Array.get(value, i));
						}
						for(Iterator<Object> itr = ((Collection) param).iterator(); itr.hasNext(); ) {
							Object paramI = itr.next();
							int size = temp.size();
							for(Iterator<Object> valueItr = temp.iterator(); valueItr.hasNext(); ) {
								if(isTruth(valueItr.next(), "=", paramI)) {
									valueItr.remove();
									break;
								}
							}
							if(temp.size() == size) {
								break;
							}
						}
						return !temp.isEmpty();
					}
					return false;
				}
				for(int i = 0; i < valueLen; i++) {
					if(!isTruth(Array.get(value, i), ">", param)) {
						return false;
					}
				}
				return true;
			}
			if(Collection.class.isAssignableFrom(value.getClass())) {
				int valueLen = ((Collection) value).size(), paramLen = -1;
				if(param.getClass().isArray()) {
					paramLen = Array.getLength(param);
					if(valueLen > paramLen) {
						LinkedList<Object> temp = new LinkedList<>(((Collection) value));
						for(int i = 0; i < paramLen; i++) {
							Object paramI = Array.get(param, i);
							int size = temp.size();
							for(Iterator<Object> valueItr = temp.iterator(); valueItr.hasNext(); ) {
								if(isTruth(valueItr.next(), "=", paramI)) {
									valueItr.remove();
									break;
								}
							}
							if(temp.size() == size) {
								break;
							}
						}
						return !temp.isEmpty();
					}
					return false;
				}
				if(Collection.class.isAssignableFrom(param.getClass())) {
					paramLen = ((Collection) param).size();
					if(valueLen > paramLen) {
						LinkedList<Object> temp = new LinkedList<>(((Collection) value));
						for(Iterator<Object> itr = ((Collection) param).iterator(); itr.hasNext(); ) {
							Object paramI = itr.next();
							int size = temp.size();
							for(Iterator<Object> valueItr = temp.iterator(); valueItr.hasNext(); ) {
								if(isTruth(valueItr.next(), "=", paramI)) {
									valueItr.remove();
									break;
								}
							}
							if(temp.size() == size) {
								break;
							}
						}
						return !temp.isEmpty();
					}
					return false;
				}
				for(int i = 0; i < valueLen; i++) {
					if(!isTruth(Array.get(value, i), ">", param)) {
						return false;
					}
				}
				return true;
			}
			if(Comparable.class.isAssignableFrom(value.getClass()) && ((Comparable) value).compareTo(param) > 0) {
				return true;
			}
		} else if (">=".equals(operator)) {
			return isTruth(value, ">", param) || isTruth(value, "=", param);
		} else if ("<".equals(operator)) { // 不能使用isTruth(param, "<", value) 因为一定是右边往左边靠 这样就反了
			if(CharSequence.class.isAssignableFrom(value.getClass())) {
				return CHINESE_COMPARATOR.compare(value.toString(), String.valueOf(param)) < 0;
			}
			if(Number.class.isAssignableFrom(value.getClass())) {
				if(Number.class.isAssignableFrom(param.getClass())) {
					return Double.compare(((Number) value).doubleValue(), ((Number) param).doubleValue()) < 0;
				}
				if(CharSequence.class.isAssignableFrom(param.getClass())) {
					return Double.compare(((Number) value).doubleValue(), Double.parseDouble(((CharSequence) param).toString())) < 0;
				}
				return false;
			}
			if(Calendar.class.isAssignableFrom(value.getClass()) || Date.class.isAssignableFrom(value.getClass())) {
				long millis = Calendar.class.isAssignableFrom(value.getClass()) ? ((Calendar) value).getTimeInMillis() : ((Date) value).getTime();
				if(Calendar.class.isAssignableFrom(param.getClass())) {
					return millis < ((Calendar) param).getTimeInMillis();
				}
				if(Date.class.isAssignableFrom(param.getClass())) {
					return millis < ((Date) param).getTime();
				}
				if(Number.class.isAssignableFrom(param.getClass())) {
					return millis < ((Number) param).longValue();
				}
				if(CharSequence.class.isAssignableFrom(param.getClass())) {
					String s = ((CharSequence) param).toString();
					Date date = strToDate(s);
					if(date == null) {
						return false;
					}
					return millis < date.getTime();
				}
				return false;
			}
			if(value.getClass().isArray()) {
				int valueLen = Array.getLength(value), paramLen = -1;
				if(param.getClass().isArray()) {
					paramLen = Array.getLength(param);
					if(valueLen < paramLen) {
						LinkedList<Object> temp = new LinkedList<>();
						for(int i = 0; i < valueLen; i++) {
							temp.add(Array.get(value, i));
						}
						int i = 0;
						for(; i < paramLen; i++) {
							Object paramI = Array.get(param, i);
							int size = temp.size();
							for(Iterator<Object> valueItr = temp.iterator(); valueItr.hasNext(); ) {
								if(isTruth(valueItr.next(), "=", paramI)) {
									valueItr.remove();
									break;
								}
							}
							if(temp.isEmpty() || temp.size() == size) {
								break;
							}
						}
						return temp.isEmpty() && i < paramLen;
					}
					return false;
				}
				if(Collection.class.isAssignableFrom(param.getClass())) {
					paramLen = ((Collection) param).size();
					if(valueLen < paramLen) {
						LinkedList<Object> temp = new LinkedList<>();
						for(int i = 0; i < valueLen; i++) {
							temp.add(Array.get(value, i));
						}
						Iterator<Object> itr = ((Collection) param).iterator();
						for(; itr.hasNext(); ) {
							Object paramI = itr.next();
							int size = temp.size();
							for(Iterator<Object> valueItr = temp.iterator(); valueItr.hasNext(); ) {
								if(isTruth(valueItr.next(), "=", paramI)) {
									valueItr.remove();
									break;
								}
							}
							if(temp.isEmpty() || temp.size() == size) {
								break;
							}
						}
						return temp.isEmpty() && itr.hasNext();
					}
					return false;
				}
				for(int i = 0; i < valueLen; i++) {
					if(!isTruth(Array.get(value, i), "<", param)) {
						return false;
					}
				}
				return true;
			}
			if(Collection.class.isAssignableFrom(value.getClass())) {
				int valueLen = ((Collection) value).size(), paramLen = -1;
				if(param.getClass().isArray()) {
					paramLen = Array.getLength(param);
					if(valueLen < paramLen) {
						LinkedList<Object> temp = new LinkedList<>(((Collection) value));
						int i = 0;
						for(; i < paramLen; i++) {
							Object paramI = Array.get(param, i);
							int size = temp.size();
							for(Iterator<Object> valueItr = temp.iterator(); valueItr.hasNext(); ) {
								if(isTruth(valueItr.next(), "=", paramI)) {
									valueItr.remove();
									break;
								}
							}
							if(temp.isEmpty() || temp.size() == size) {
								break;
							}
						}
						return temp.isEmpty() && i < paramLen;
					}
					return false;
				}
				if(Collection.class.isAssignableFrom(param.getClass())) {
					paramLen = ((Collection) param).size();
					if(valueLen < paramLen) {
						LinkedList<Object> temp = new LinkedList<>(((Collection) value));
						Iterator<Object> itr = ((Collection) param).iterator();
						for(; itr.hasNext(); ) {
							Object paramI = itr.next();
							int size = temp.size();
							for(Iterator<Object> valueItr = temp.iterator(); valueItr.hasNext(); ) {
								if(isTruth(valueItr.next(), "=", paramI)) {
									valueItr.remove();
									break;
								}
							}
							if(temp.isEmpty() || temp.size() == size) {
								break;
							}
						}
						return temp.isEmpty() && itr.hasNext();
					}
					return false;
				}
				for(int i = 0; i < valueLen; i++) {
					if(!isTruth(Array.get(value, i), "<", param)) {
						return false;
					}
				}
				return true;
			}
			if(Comparable.class.isAssignableFrom(value.getClass()) && ((Comparable) value).compareTo(param) < 0) {
				return true;
			}
		} else if ("<=".equals(operator)) {
			return isTruth(value, "<", param) || isTruth(value, "=", param);
		}
		return false;
	}
	
	private Date strToDate(String s) {
		String fmt = null;
		switch(s.length()) {
		case 8:
			if(pureDigitPattern.matcher(s).matches()) {
				fmt = "yyyyMMdd";
			}
			break;
		case 10:
			if(yyyyMMddBarPattern.matcher(s).matches()) {
				fmt = "yyyy-MM-dd";
			} else if(yyyyMMddSlashPattern.matcher(s).matches()) {
				fmt = "yyyy/MM/dd";
			}
			break;
		case 14:
			if(pureDigitPattern.matcher(s).matches()) {
				fmt = "yyyyMMddHHmmss";
			}
			break;
		case 19:
			if(yyyyMMddHHmmssBarPattern.matcher(s).matches()) {
				fmt = "yyyy-MM-dd HH:mm:ss";
			} else if(yyyyMMddHHmmssSlashPattern.matcher(s).matches()) {
				fmt = "yyyy/MM/dd HH:mm:ss";
			}
			break;
		case 18:
			if(pureDigitPattern.matcher(s).matches()) {
				fmt = "yyyyMMddHHmmssSSS";
			}
			break;
		case 23:
			if(yyyyMMddHHmmssSSSBarPattern.matcher(s).matches()) {
				fmt = "yyyy-MM-dd HH:mm:ss.SSS";
			} else if(yyyyMMddHHmmssSSSSlashPattern.matcher(s).matches()) {
				fmt = "yyyy/MM/dd HH:mm:ss.SSS";
			}
			break;
		}
		if(fmt == null) {
			return null;
		}
		try {
			return new SimpleDateFormat(fmt).parse(s);
		} catch (ParseException e) {
		}
		return null;
	}
	
	/**
	 * 把右边的src往左边value的class转
	 * @param src
	 * @param targetClass
	 * @return
	 */
	private Object convertConst(String src, Class<?> targetClass) {
		if(src == null || targetClass == null) {
			return null;
		}
		if(CharSequence.class.isAssignableFrom(targetClass)) {
			return src;
		}
		if(Number.class.isAssignableFrom(targetClass)) {
			return new Double(ExpressionParser.parse(src));
		}
		if(Calendar.class.isAssignableFrom(targetClass) || Date.class.isAssignableFrom(targetClass)) {
			return strToDate(src);
		}
		if(targetClass.isArray() || Collections.class.isAssignableFrom(targetClass)) {
			if(arrayPattern.matcher(src).matches()) {
				String middle = src.substring(1, src.length() - 1).trim();
				if(middle.length() == 0) {
					return new Object[0];
				}
				return middle.split("\\s*,\\s*");
			}
			return null;
		}
		if(Map.class.isAssignableFrom(targetClass)) {
			if(mapPattern.matcher(src).matches()) {
				String middle = src.substring(1, src.length() - 1).trim();
				if(middle.length() == 0) {
					return new HashMap<Object, Object>(0);
				}
				String[] kvs = middle.split("\\s*,\\s*");
				int len = kvs.length;
				HashMap<String, String> map = new HashMap<String, String>(len, 1);
				for(int i = 0, index; i < len; i++) {
					index = kvs[i].indexOf(':');
					map.put(kvs[i].substring(0, index), kvs[i].substring(index + 1));
				}
				return map;
			}
		}
		return null;
	}
	
	public static class Condition {
		String left, operator, right;
		int paramIndex;
		boolean isComplete() {
			return left != null && operator != null && right != null;
		}
		@Override
		public String toString() {
			return left + ' ' + operator + ' ' + right + (paramIndex < 0 ? "" : ("(" + paramIndex + ')'));
		}
	}
	
	static class OrderBy {
		String prop;
		int propIndex;
		boolean isDesc;
		int orderIndex;
		boolean isAsc() {
			return !isDesc;
		}
	}
	
	static class Limit {
		String param;
		int paramIndex;
		Limit(String param, int paramIndex) {
			this.param = param;
			this.paramIndex = paramIndex;
		}
	}
	
	static class GraphNode implements Cloneable {
		Condition condition;
		String str;
		boolean alwaysTrue;
		boolean bracketStart;
		GraphNode startBracket;
		GraphNode(Condition condition) {
			super();
			this.condition = condition;
		}
		GraphNode(String str) {
			super();
			this.str = str;
			this.condition = new Condition();
		}
		GraphNode(boolean alwaysTrue) {
			super();
			this.alwaysTrue = alwaysTrue;
			this.condition = new Condition();
		}
		boolean isFull() {
			return alwaysTrue || (condition != null && condition.isComplete());
		}
		@Override
		public GraphNode clone() {
			try {
				super.clone();
			} catch (CloneNotSupportedException e) {
				throw new RuntimeException(e);
			}
			if(condition == null) {
				throw new RuntimeException(new CloneNotSupportedException());
			}
			GraphNode node = new GraphNode(new Condition());
			node.condition.left = condition.left;
			node.condition.operator = condition.operator;
			node.condition.right = condition.operator;
			node.condition.paramIndex = condition.paramIndex;
			return node;
		}
		@Override
		public String toString() {
			return "GraphNode[" + (str == null ? (alwaysTrue ? "TRUTH" : condition) : str) + "]";
		}
	}
	
	static class GraphLine {
		GraphNode src, dist;
		boolean walked = false;
		GraphLine(GraphNode src, GraphNode dist) {
			super();
			this.src = src;
			this.dist = dist;
		}
		@Override
		public String toString() {
			return src + " --> " + dist;
		}
	}
	
	public static class Graph {
		LinkedList<GraphNode> nodes = new LinkedList<GraphNode>();
		LinkedHashMap<GraphNode, LinkedList<GraphLine>> lines = new LinkedHashMap<GraphNode, LinkedList<GraphLine>>();
		LinkedHashMap<GraphNode, LinkedList<GraphNode>> previousNodes = new LinkedHashMap<GraphNode, LinkedList<GraphNode>>();
		LinkedHashMap<GraphNode, LinkedList<GraphLine>> copies = null;
		Graph addNode(GraphNode node) {
			nodes.add(node);
			return this;
		}
		Graph addNode(Condition condition) {
			return addNode(new GraphNode(condition));
		}
		Graph link(GraphNode src, GraphNode dist) {
			LinkedList<GraphLine> temp = lines.get(src);
			if(temp == null) {
				lines.put(src, temp = new LinkedList<GraphLine>());
			}
			temp.add(new GraphLine(src, dist));
			
			LinkedList<GraphNode> nodes = previousNodes.get(dist);
			if(nodes == null) {
				previousNodes.put(dist, nodes = new LinkedList<GraphNode>());
			}
			nodes.add(src);
			
			return this;
		}
		Graph copyLines() {
			copies = new LinkedHashMap<GraphNode, LinkedList<GraphLine>>(lines.size());
			Set<Map.Entry<GraphNode, LinkedList<GraphLine>>> entries = lines.entrySet();
			for(Map.Entry<GraphNode, LinkedList<GraphLine>> entry : entries) {
				LinkedList<GraphLine> lines = new LinkedList<GraphLine>();
				copies.put(entry.getKey(), lines);
				for(GraphLine l : entry.getValue()) {
					lines.add(l);
				}
			}
			return this;
		}
		static void getLeaves(Graph g, GraphNode node, LinkedList<GraphNode> list) {
			LinkedList<GraphLine> lines = g.lines.get(node);
			if(lines == null || lines.isEmpty()) {
				list.add(node);
				return;
			}
			for(GraphLine line : lines) {
				getLeaves(g, line.dist, list);
			}
		}
		static void getRoots(Graph g, GraphNode node, LinkedList<GraphNode> list) {
			LinkedList<GraphNode> nodes = g.previousNodes.get(node);
			if(nodes == null || nodes.isEmpty()) {
				list.add(node);
				return;
			}
			for(GraphNode n : nodes) {
				getRoots(g, n, list);
			}
		}
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder(lines.size() << 4);
			for(LinkedList<GraphLine> lines : lines.values()) {
				builder.append(lines.size());
				builder.append("\t");
				builder.append(lines);
				builder.append("\r\n");
			}
			return builder.toString();
		}
	}

	@Override
	public int size() {
		try {
			lock.lock();
			return array.size();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public boolean isEmpty() {
		try {
			lock.lock();
			return array.isEmpty();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public boolean contains(Object o) {
		try {
			lock.lock();
			return array.contains(o);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public Iterator<E> iterator() {
		return array.iterator();
	}

	@Override
	public Object[] toArray() {
		try {
			lock.lock();
			return array.toArray();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public <T> T[] toArray(T[] a) {
		try {
			lock.lock();
			return array.toArray(a);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public boolean add(E e) {
		try {
			lock.lock();
			return array.add(e);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public boolean remove(Object o) {
		try {
			lock.lock();
			return array.remove(o);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		try {
			lock.lock();
			return array.containsAll(c);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		try {
			lock.lock();
			return array.addAll(c);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		try {
			lock.lock();
			return array.addAll(index, c);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		try {
			lock.lock();
			return array.removeAll(c);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		try {
			lock.lock();
			return array.retainAll(c);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void clear() {
		try {
			lock.lock();
			array.clear();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public E get(int index) {
		try {
			lock.lock();
			return array.get(index);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public E set(int index, E element) {
		try {
			lock.lock();
			return array.set(index, element);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void add(int index, E element) {
		try {
			lock.lock();
			array.add(index, element);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public E remove(int index) {
		try {
			lock.lock();
			return array.remove(index);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public int indexOf(Object o) {
		try {
			lock.lock();
			return array.indexOf(o);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public int lastIndexOf(Object o) {
		try {
			lock.lock();
			return array.lastIndexOf(o);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public ListIterator<E> listIterator() {
		return array.listIterator();
	}

	@Override
	public ListIterator<E> listIterator(int index) {
		return array.listIterator(index);
	}

	@Override
	public List<E> subList(int fromIndex, int toIndex) {
		try {
			lock.lock();
			return array.subList(fromIndex, toIndex);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public String toString() {
		return array == null ? null : array.toString();
	}

}
