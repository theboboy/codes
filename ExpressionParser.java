package com.yoho.erp.stock.common.util;

import java.math.BigDecimal;
import java.util.ArrayList;

/**
 * 表达式解析类，只支持常规运算，不包括函数和自变量
 * @author bo.sun
 */
public final class ExpressionParser {
	
	private ExpressionParser() {
	}
	
	public static String parse(String exp) {
		return parse(convert(exp));
	}

	/**
	 * 解析并计算表达式的值，未做校验
	 * 
	 * 从左到右读后缀表达式，读到数字就将它转换为数值压入栈S中
	 * 读到运算符则从栈中依次弹出两个数分别到Y和X，然后以X运算符 Y的形式计算机出结果，再压入栈S中
	 * 如果后缀表达式未读完，就重复上面过程，最后输出栈顶的数值则为结束
	 * 
	 * @param list 拆分后的表达式
	 * @return 计算结果
	 */
	public static String parse(ArrayList<String> list) {
		
		ArrayList<String> segments = convert(list);
		
		int len = segments.size();
		
		NumberStack stack = new NumberStack(len);
		
		for (int i = 0; i < len; i++) {
			String str = segments.get(i);
			StringType type = getType(str);
			if(type == StringType.DIGIT) {
				stack.push(str);
			} else {
				String y = stack.pop();
				String x = stack.pop();
				stack.push(calc(x, y, str.charAt(0)));
			}
		}
		
		return stack.isNotEmpty() ? stack.pop() : null;
		
	}

	private static ArrayList<String> convert(String exp) {
		return split(exp);
	}

	/**
	 * 中缀转后缀
	 * 
	 * 读到数字直接送至输出队列中
	 * 读到运算符t时
	 * 		将栈中所有优先级高于或等于t的运算符弹出，送到输出队列中
	 * 		t进栈
	 * 读到左括号时总是将它压入栈中
	 * 读到右括号时，将靠近栈顶的第一个左括号上面的运算符全部依次弹出，送至输出队列后，再丢弃左括号
	 * 中缀表达式全部读完后，若栈中仍有运算符，将其送到输出队列中
	 * 
	 * @param list 拆分后的表达式
	 * @return 转换后的结果
	 */
	private static ArrayList<String> convert(ArrayList<String> list) {
		
		int len = list.size();
		
		OperatorStack stack = new OperatorStack(len);
		
		ArrayList<String> result = new ArrayList<>(len);
		
		char opr;
		for (int i = 0; i < len; i++) {
			String str = list.get(i);
			StringType type = getType(str);
			switch(type) {
			case DIGIT:
				result.add(str);
				break;
			case OPERATOR:
				opr = str.charAt(0);
				while(stack.isNotEmpty()) {
					char ch = stack.peek();
					if(getPriority(opr) > getPriority(ch)) {
						break;
					}
					result.add(Character.toString(stack.pop()));
				}
				stack.push(opr);
				break;
			case LEFT:
				opr = str.charAt(0);
				stack.push(opr);
				break;
			case RIGHT:
				opr = str.charAt(0);
				while(stack.isNotEmpty()) {
					char ch = stack.pop();
					if(ch == 40) {
						break;
					}
					result.add(Character.toString(ch));
				}
				break;
			}
		}
		
		while(stack.isNotEmpty()) {
			result.add(Character.toString(stack.pop()));
		}
		
		return result;
	}

	/**
	 * 获取字符串是什么类型，括号或运算符或数字
	 * @param str
	 * @return
	 */
	private static StringType getType(String str) {
		
		int len = str.length(), first = str.charAt(0);
		
		if(len == 1) {
			if(isOperator(first)) {
				return StringType.OPERATOR;
			}
			if(first == 40) {
				return StringType.LEFT;
			}
			if(first == 41) {
				return StringType.RIGHT;
			}
			if(first > 47 && first < 58) {
				return StringType.DIGIT;
			}
			throw new RuntimeException();
		}
		return StringType.DIGIT; 
	}

	/**
	 * 拆分字符串
	 * @param exp
	 * @return
	 */
	private static ArrayList<String> split(String exp) {
		
		String temp = new String(exp);
		
		int len = temp.length(), index = -1;
		
		ArrayList<String> list = new ArrayList<String>(len);
		
		StringBuilder builder = new StringBuilder();
		
		char ch;
		
		boolean isFollowBracket = true, isFollowNum = false;
		
		while(++index < len) {
			ch = temp.charAt(index);
			if((ch == 45 && isFollowBracket && !isFollowNum) || ch == 46 || (isFollowNum = (ch > 47 && ch < 58))) {	// '-'=45 '.'=46 '0'=48 '9'=57
				builder.append(ch);
			} else if(isOperator(ch) || (isFollowBracket = (ch == 40)) || (isFollowBracket = (ch == 41))) {
				if(isFollowNum = (builder.length() > 0)) {
					list.add(builder.toString());
					builder.setLength(0);
				}
				list.add(Character.toString(ch));
			} else {
				throw new RuntimeException();
			}
		}
		
		if(builder.length() > 0) {
			list.add(builder.toString());
		}
		
		return list;
	}

	/**
	 * 判断字符是否为运算符
	 * @param ch
	 * @return
	 */
	private static boolean isOperator(int ch) {
		return ch == 43 || ch == 45 || ch == 42 || ch == 47;
	}

	/**
	 * 判断运算符优先级，+-为1，x/为2
	 * @param ch
	 * @return
	 */
	private static int getPriority(int ch) {
		return ch == 43 || ch == 45 ? 1 : (ch == 42 || ch == 47 ? 2 : 0);
	}

	/**
	 * 根据两个数和运算符进行计算
	 * @param x
	 * @param y
	 * @param opr
	 * @return
	 */
	private static String calc(String x, String y, int opr) {
		BigDecimal bd1 = new BigDecimal(x), bd2 = new BigDecimal(y);
		switch(opr) {
		case 43:
			bd1 = bd1.add(bd2);
			break;
		case 45:
			bd1 = bd1.subtract(bd2);
			break;
		case 42:
			bd1 = bd1.multiply(bd2);
			break;
		case 47:
			bd1 = bd1.divide(bd2, 10, BigDecimal.ROUND_HALF_UP);
			break;
		}
		return bd1.toString();
	}

	/**
	 * 字符串类型
	 */
	private enum StringType {
		OPERATOR, DIGIT, LEFT, RIGHT
	}

	/**
	 * 运算符的栈，转后缀时用的
	 */
	private static class OperatorStack {
		char[] arr;
		int size = 0;
		int top = -1;
		public OperatorStack(int size) {
			this.arr = new char[this.size = size];
		}
		public int push(char ch) {
			arr[++top] = ch;
			return ++size;
		}
		public char pop() {
			char temp = arr[top--];
			size--;
			return temp;
		}
		public char peek() {
			return arr[top];
		}
		public boolean isNotEmpty() {
			return top > -1;
		}
	}

	/**
	 * 操作数的栈，计算时用的
	 */
	private static class NumberStack {
		String[] arr;
		int size = 0;
		int top = -1;
		public NumberStack(int size) {
			this.arr = new String[this.size = size];
		}
		public int push(String str) {
			arr[++top] = str;
			return ++size;
		}
		public String pop() {
			String temp = arr[top--];
			size--;
			return temp;
		}
		public boolean isNotEmpty() {
			return top > -1;
		}
	}

}
