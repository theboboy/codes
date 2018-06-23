package com.yoho.erp.stock.common.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 计算多表达式的值，可以互相引用，但不可以循环引用
 * <p>
 * 调用方法: new FormulaGroupCalculator(map).calc();
 * 或者: new FormulaGroupCalculater().calc(map);
 * 其中map的key为等式左边，value为等式右边
 *
 * @author bo.sun
 */
public class FormulaGroupCalculator {

	/**
	 * 指针指向传进来的map，直接替换value为计算后的值
	 */
	private Map<String, Object> result;
	/**
	 * 存放解析后的公式
	 */
	private LinkedHashMap<String, Formula> map;
	/**
	 * 空表，用做缓存
	 */
	private ArrayList<String> emptyList = new ArrayList<>(0);

	public FormulaGroupCalculator() {
		super();
	}

	public FormulaGroupCalculator(Map<String, Object> group) {
		super();
		init(group);
	}

	public static void main(String[] args) {

		FormulaGroupCalculator computer = new FormulaGroupCalculator();

		/*
		for(java.util.Scanner sc = new java.util.Scanner(System.in); System.out.format("请输入表达式：") != null; sc = new
		java.util.Scanner(System.in)) {

			String input = sc.next();

			System.out.println(computer.splitFormula(input));

		}
		//*/

		LinkedHashMap<String, Object> map = new LinkedHashMap<>();

		map.put("a", 2);
		map.put("b", "-3*(-2a+6)");
		map.put("c", "e-d/4");
		map.put("d", "(a+10)*2");
		map.put("e", "d-10");

		computer.calc(map);

		Set<Map.Entry<String, Object>> entries = map.entrySet();

		for (Map.Entry<String, Object> entry : entries) {

			System.out.println(entry.getKey() + '=' + entry.getValue());

		}

	}

	/**
	 * 判断字符是否为运算符
	 *
	 * @param ch 字符
	 */
	private static boolean isOperator(int ch) {
		return ch == 43 || ch == 45 || ch == 42 || ch == 47;
	}

	public void calc(Map<String, Object> group) {
		init(group);
		calc();
	}

	/**
	 * 初始化方法
	 *
	 * @param group 公式
	 */
	private void init(Map<String, Object> group) {

		this.result = group;

		// 公式个数
		int totalCount = group.size();

		map = new LinkedHashMap<>(totalCount * 4 / 3 + 1);

		Formula f;

		Set<Map.Entry<String, Object>> entries = result.entrySet();

		// 把map拆开放入公式中
		for (Map.Entry<String, Object> entry : entries) {
			String key = entry.getKey();
			String value = entry.getValue() == null ? null : String.valueOf(entry.getValue());
			f = new Formula(key, value);
			map.put(f.left, f);
		}

		String exp;

		Formula that;

		Collection<Formula> values = map.values();

		// 解析公式
		for (Iterator<Formula> itr = values.iterator(); itr.hasNext(); ) {
			f = itr.next();
			if (!f.isSimple()) {
				for (int i = 0, len = f.complexes.length; i < len; i++) {
					if (f.complexes[i]) {
						exp = f.segments.get(i);
						// 负号判断
						if (exp.charAt(0) == 45) {
							exp = exp.substring(1);
						}
						that = map.get(exp);
						if (that == null) {
							f.value = null;
						} else { // 存放依赖关系
							f.deps.add(that);
							that.exps.add(f);
						}
					}
				}
			}
		}

		// 对每个公式分别检测循环应用
		for (Iterator<Formula> itr = values.iterator(); itr.hasNext(); ) {
			f = itr.next();
			new LoopRefChecker(f);
		}

	}

	/**
	 * 计算
	 */
	public void calc() {
		Collection<Formula> values = map.values();
		Formula f;
		for (Iterator<Formula> itr = values.iterator(); itr.hasNext(); ) {
			f = itr.next();
			if (!f.isDone) {
				calcOneFormula(f);
			}
		}
	}

	/**
	 * 计算单个表达式
	 *
	 * @param f 表达式
	 */
	private void calcOneFormula(Formula f) {

		if (f.isDone) { // 如果当前公式已完成计算，返回
			return;
		}

		if (f.isSimple()) { // 如果当前公式已经不含引用
			f.value = ExpressionParser.parse(f.segments);
			f.isDone = true;
			result.put(f.left, f.value);
		} else { // 还包含引用
			String exp;

			Formula that;

			boolean isNag = false;

			// 遍历每个元素
			for (int i = 0, len = f.complexes.length; i < len; i++) {
				if (f.complexes[i]) { // 如果该元素是引用
					exp = f.segments.get(i);

					// 负号检测
					if (exp.charAt(0) == 45) {
						exp = exp.substring(1);
						isNag = true;
					}

					// 取出引用的公式
					that = map.get(exp);

					// 如果不含该公式，当前公式值为空
					if (that == null) {
						result.put(f.left, null);
						f.isDone = true;
					} else {
						// 先计算引用的公式
						calcOneFormula(that);

						// 如果引用的公式值为空，当前公式值也为空
						if (that.value == null) {
							f.isDone = true;
							result.put(f.left, null);
						} else {
							// 用计算后的值替换指定引用
							f.segments.set(i, !isNag ? that.value : that.value.charAt(0) == 45 ? that.value.substring
									(1) : '-' + that.value);

							// 重新计算
							calcOneFormula(f);
						}
					}
				}
			}
		}
	}

	/**
	 * 拆分公式
	 *
	 * @param formula 公式
	 */
	private ArrayList<String> splitFormula(String formula) {
		if (formula == null) {
			return emptyList;
		}

		int len = formula.length();

		if (len == 0) {
			return emptyList;
		}

		ArrayList<String> list = new ArrayList<>(len * 4 / 3 + 1);

		char ch;

		// numberBuilder存放数字，letterBuilder存放字母
		StringBuilder numberBuilder = new StringBuilder(), letterBuilder = new StringBuilder();

		// isFollowBracket表示是否在'('后，isFollowNum表示是否在数字后，isFirstNag表示第一个字符是否是负号
		boolean isFollowBracket = true, isFollowNum = false, isFirstNag = false;

		// 遍历公式
		for (int i = 0; i < len; i++) {

			ch = formula.charAt(i);

			// 如果是负号，并且跟在'('后，并且没跟在数字后；或者是小数点，或者是数字
			// '-'=45 '.'=46 '0'=48 '9'=57
			if ((ch == 45 && isFollowBracket && !isFollowNum) || ch == 46 || (isFollowNum = (ch > 47 && ch < 58))) {
				// 如果是负号，并且出现在第一个，标记isFirstNag
				if (ch == 45) {
					if (isFollowBracket) {
						isFirstNag = true;
					}
				} else {
					if (letterBuilder.length() > 0) { //如果前面有字母，append到字母串
						letterBuilder.append(ch);
					} else { // 如果前面是数字，append到数字，如果有负号，先append负号
						if (isFirstNag) {
							numberBuilder.append('-');
							isFirstNag = false;
						}
						numberBuilder.append(ch);
					}
				}
			} else if (isOperator(ch) || (isFollowBracket = (ch == 40)) || (isFollowBracket = (ch == 41))) { //操作符
				// 先看前面有没有引用，如果有，表示前面的内容已经结束，先把前面的内容添加到元素集，再清空
				if (letterBuilder.length() > 0) {
					list.add(letterBuilder.toString().trim());
					letterBuilder.setLength(0);
				}

				// 先看前面有没有数字，如果有，表示前面的内容已经结束，先把前面的内容添加到元素集，再清空
				if (isFollowNum = (numberBuilder.length() > 0)) {
					list.add(numberBuilder.toString().trim());
					numberBuilder.setLength(0);
				}

				// 添加操作符到元素集
				list.add(Character.toString(ch));
			} else { // 当前读到的是字母
				// 先判断是否有负号
				if (isFirstNag) {
					letterBuilder.append('-');
					isFirstNag = false;
				}

				// 如果前面都是数字，要变为数字*字母
				if (numberBuilder.length() > 0) {
					list.add(numberBuilder.toString().trim());
					numberBuilder.setLength(0);
					list.add("*");
				}

				// append该字母
				letterBuilder.append(ch);
			}
		}

		// 如果最后包含内容，全取出，下面两个if只会执行一个
		if (numberBuilder.length() > 0) {
			list.add(numberBuilder.toString().trim());
		}

		if (letterBuilder.length() > 0) {
			list.add(letterBuilder.toString().trim());
		}

		return list;
	}

	/**
	 * 获取字符是什么类型，括号或运算符或数字或字母
	 *
	 * @param ch 字符
	 */
	private CharType getCharacterType(char ch) {
		// 左括号
		if (ch == 40) {
			return CharType.LEFT;
		}

		// 右括号
		if (ch == 41) {
			return CharType.RIGHT;
		}

		// 操作符
		if (isOperator(ch)) {
			return CharType.OPERATOR;
		}

		// 数字
		if (ch > 47 && ch < 58) {
			return CharType.DIGIT;
		}

		// 字母
		return CharType.LETTER;
	}

	/**
	 * 字符类型
	 */
	private enum CharType {
		OPERATOR, DIGIT, LEFT, RIGHT, LETTER
	}

	/**
	 * 用于检测循环引用
	 */
	private class LoopRefChecker {

		private Formula root;

		public LoopRefChecker(Formula root) {
			this.root = root;
			check(root);
		}

		// 检测当前节点
		public void check(Formula node) {
			if (node.deps == null || node.deps.isEmpty()) {
				return;
			}
			for (Formula f : node.deps) {
				if (f == root) {
					throw new RuntimeException("Circular Reference Is Found at [" + root.left + '=' + root.right +
							']');
				}
				check(f);
			}
		}
	}

	/**
	 * 公式
	 */
	private class Formula {

		/**
		 * 左边
		 */
		public String left;

		/**
		 * 右边
		 */
		public String right;

		/**
		 * 拆分后的元素
		 */
		public ArrayList<String> segments = emptyList;

		/**
		 * 下标同上，用于区分哪些是可以直接计算的，哪些是引用
		 */
		public boolean[] complexes;

		/**
		 * 值
		 */
		public String value;

		/**
		 * 是否完成计算
		 */
		public boolean isDone = false;

		/**
		 * 依赖的公式
		 */
		public HashSet<Formula> deps = new HashSet<>();

		/**
		 * 被参照的公式
		 */
		public HashSet<Formula> exps = new HashSet<>();

		public Formula(String name, String value) {
			left = name;
			right = value;
			segments = splitFormula(right);
			System.out.println(segments);
		}

		// 判断是否为可以直接计算的公式，同时更新complexes
		public boolean isSimple() {
			boolean isSimple = true;

			int size = segments.size();

			complexes = new boolean[size];

			String current;

			char ch;

			for (int i = 0; i < size; i++) {
				current = segments.get(i);
				boolean complex = false;
				for (int j = 0, len = current.length(); j < len; j++) {
					ch = current.charAt(j);
					CharType type = getCharacterType(ch);
					if (type == CharType.LEFT || type == CharType.RIGHT) {
						continue;
					}

					if (type == CharType.DIGIT) {
						continue;
					}

					if (type == CharType.OPERATOR) {
						continue;
					}

					if (ch == 46) {
						continue;
					}

					isSimple = false;

					complex = true;

					break;
				}

				complexes[i] = complex;
			}

			return isSimple;
		}

		@Override
		public String toString() {
			return left + "=" + right + segments;
		}

	}

}
