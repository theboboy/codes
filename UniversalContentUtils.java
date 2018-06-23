package com.jrj.tougu.utils;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.web.util.HtmlUtils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * 
 * @author bo.sun
 * @since 2015-01-15
 * @version 1.0.0
 *
 */
public final class UniversalContentUtils {

	private static char HIDDEN_CHAR_BEGIN = '\ue40a', HIDDEN_CHAR_END = '\ue40b';
	
	private static Pattern emojiPattern = Pattern.compile("[\ud83c\udc00-\ud83c\udfff]|[\ud83d\udc00-\ud83d\udfff]|[\u2600-\u27ff]", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);
	
	//                                                                type             id   ($)         url                                   title ($)
	private static String linkFormat = "<span class=\"editor-insert-%s\" data-code=\"%s\">%s<a href=\"%s\" class=\"link\" target=\"_blank\">%s</a>%s<span></span></span>";

	//                                                               type             url             url
	private static String imgFormat = "<span class=\"editor-insert-%s\" data-code=\"%s\"><img src=\"%s\" width=\"26\" height=\"26\" /><span></span></span>";
	
	private static String pureLinkFormat = "<a href=\"%s\" class=\"link\" target=\"_blank\">%s</a>";
	
	private static Pattern urlPattern = Pattern.compile("(http|https|ftp):\\/\\/[\\w\\-_]+(\\.[\\w\\-_]+)+([\\w\\-\\.,@?^=%&:/~\\+#]*[\\w\\-\\@?^=%&/~\\+#])?", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);
	
	//                                                                                                          1                                                      2         3
	private static Pattern specialContentPattern = Pattern.compile("<span[^>]+data-code=\"[^\"^>]+\">\\${0,1}<(a href=\"[^\"^>]+\" class=\"link\" target=\"_blank\")>([^<^>]+)<(/a)>\\${0,1}<span[^<^>]*>[^<^>]*</span>[^<^>]*</span>");
	
	private static String domainName = "http://itougu.jrj.com.cn/";
	
	private static Pattern quesLinkPattern = Pattern.compile("<span class=\"editor-insert-link\" data-code=\"[^\"^>]+\"><a href=\"/ques/\\d+\\.jspa\" class=\"link\" target=\"_blank\">([^<^>]+)</a><span[^<^>]*>[^<^>]*</span>[^<^>]*</span>");
	private static Pattern liveLinkPattern = Pattern.compile("<span class=\"editor-insert-link\" data-code=\"[^\"^>]+\"><a href=\"/live/\\d+/\\d+\" class=\"link\" target=\"_blank\">([^<^>]+)</a><span[^<^>]*>[^<^>]*</span>[^<^>]*</span>");
	private static Pattern userLinkPattern = Pattern.compile("<span class=\"editor-insert-link\" data-code=\"[^\"^>]+\"><a href=\"/account/adviser/\\d+/\\d*\" class=\"link\" target=\"_blank\">([^<^>]+)</a><span[^<^>]*>[^<^>]*</span>[^<^>]*</span>");
	
	private static Pattern[] blockedUrls = new Pattern[] { liveLinkPattern };
	
	private static String replaceToHidden = HIDDEN_CHAR_BEGIN + "$1" + HIDDEN_CHAR_END + "$2" + HIDDEN_CHAR_BEGIN + "$3" + HIDDEN_CHAR_END;
	
	//                                                                                                       1
	private static Pattern specialContentPatternOfImg = Pattern.compile("<span[^>]+data-code=\"[^\"^>]+\"><(img src=\"[^\"^>]+\"[^<^>]*)/><span>[^<^>]*</span>[^<^>]*</span>");
	
	private static String replaceImgToHidden = HIDDEN_CHAR_BEGIN + "$1" + "/" + HIDDEN_CHAR_END;
	
	private static Pattern webStockPattern = Pattern.compile("<span[^>]+data-code=\"[^\"^>]+\">\\$<a href=\"[^\"^>]+\" class=\"link\" target=\"_blank\">([^<^>]+)\\(([^<^>]+)\\)</a>\\$[^<^>]*<span>[^<^>]*</span>[^<^>]*</span>");
	
	private static char[] separators = new char[] { '「', '」' };
	
	//                                                                                1                                                        2
	private static Pattern mobileOldStockPattern = Pattern.compile(separators[0] + "([" + "^" + separators[0] + "^" + separators[1] + "]+)\\(([" + "^" + separators[0] + "^" + separators[1] + "]+)\\)" + separators[1]);
	
	private static Pattern labelPattern = Pattern.compile("<.*?>");
	
	private static Pattern linkPattern = Pattern.compile("<a[^>]*>([^<^>]+)</a>");
	
	private static Pattern imgPattern = Pattern.compile("<img[^<^>]+/>");
	
	private UniversalContentUtils() {
	}

	/**
	 * 是否是内容贯通之前的版本
	 * @param appVersion
	 * @return
	 */
	public static boolean isOldVersionBeforeUniversal(String appVersion) {
		
		String theLatestVersionBeforeUniversal = "1.0.9";
		
		return isOldVersionBeforeCritical(theLatestVersionBeforeUniversal, appVersion);
		
	}
	
	/**
	 * 是否是小于等于临界版本号
	 * @param criticalVersion 临界版本号
	 * @param appVersion 当前版本号
	 * @return
	 */
	public static boolean isOldVersionBeforeCritical(String criticalVersion, String appVersion) {
		
		if(appVersion == null || appVersion.length() == 0) {
			return true;
		}
		
		for(int i1 = 0, i2 = 0, j1 = 0, j2 = 0, l1 = criticalVersion.length(), l2 = appVersion.length(), separator = '.'; i2 < l1 && j2 < l2; i2++, j2++) {
			i2 = (i2 = criticalVersion.indexOf(separator, i1 = i2)) >= 0 ? i2 : l1;
			j2 = (j2 = appVersion.indexOf(separator, j1 = j2)) >= 0 ? j2 : l2;
			int s1 = Integer.parseInt(criticalVersion.substring(i1, i2));
			int s2 = Integer.parseInt(appVersion.substring(j1, j2));
			if(s1 != s2) {
				return s1 > s2;
			}
		}
		
		return true;
		
	}

	/**
	 * 将客户端发送的内容转换成支持网页版的HTML
	 * @param content
	 * @param paramDesc
	 * @param images
	 * @param imageComments
	 * @param supportLink
	 * @return
	 */
	public static String convertSourceToUniversalContent(String content, String paramDesc, String[] images, String[] imageComments, boolean supportLink) {
		
		content = filterEmoji(content, true);
		
		content = HtmlUtils.htmlEscape(content);
		
		if(!supportLink) {
			
			String type = "stock";
			
			String id = "$2";
			
			String title = "$1($2)";
			
			content = mobileOldStockPattern.matcher(content).replaceAll(getLinkOrImg(type, id, title, true));
			
		} else {
			
			if(paramDesc != null && paramDesc.length() > 0) {
				
				int j = 0;
				
				for(int i = 0, len = content.length(), ch; i < len; i++) {
					
					ch = content.charAt(i);
					
					if(ch == HIDDEN_CHAR_BEGIN) {
						j++;
					}
					
				}
				
				if(j > 0) {
					
					StringBuilder builder = new StringBuilder(content.length() + j * 64);
					
					j = 0;
					
					JSONArray jsonArray = JSONArray.parseArray(paramDesc);
					
					if(!jsonArray.isEmpty()) {
						
						for(int i = 0, len = content.length(), ch; i < len; i++) {
							
							ch = content.charAt(i);
							
							if(ch == HIDDEN_CHAR_BEGIN) {
								
								if(j < jsonArray.size()) {
									
									JSONObject jsonObject = jsonArray.getJSONObject(j);
									
									String type = jsonObject.getString("type");
									
									String id = jsonObject.getString("id");
									
									String title = jsonObject.getString("title");
									
									builder.append(getLinkOrImg(type, id, title, false));
									
									j++;
									
								}
								
							} else {
								builder.append((char) ch);
							}
							
						}
						
						content = builder.toString();
						
					}
					
				}
				
			}
			
		}
		
		if(content.indexOf(HIDDEN_CHAR_BEGIN) >= 0) {
			content = content.replaceAll(String.valueOf(HIDDEN_CHAR_BEGIN), "");
		}
		
		content = parseMobileContentToWebContents(content, images, imageComments);
		
		content = "<p>" + content+ "</p>";
		
		content = processSiXin(content, true);
		
		return content;
		
	}
	
	private static String getLinkOrImg(String type, String id, String title, boolean includeDollar) {
		
		if("expression".equals(type)) {
			
			return String.format(imgFormat, type, id, id);
		}
		
		String url = "link".equals(type) ? id : "stock".equals(type) ? "http://stock.jrj.com.cn/share," + id + ".shtml" : "/" + type + "/" + ("live".equals(type) ? id : (new Double(id).longValue() + ".jspa"));
		
		int slashIndex = type.indexOf('/');
		
		return String.format(linkFormat, slashIndex < 0 ? type : type.substring(0, slashIndex), id, "stock".equals(type) ? includeDollar ? "\\$" : "$" : "", url, title, "stock".equals(type) ? includeDollar ? "\\$" : "$" : "");
		
	}

	/**
	 * 将HTML转换成客户端支持的文本和链接
	 * @param content
	 * @param supportLink
	 * @return
	 */
	public static String convertUniversalContentToPureTextAndLink(String content, boolean supportLink) {
		
		content = processSiXin(content, false);
		
		if(supportLink) {
			content = recognizeUrl(content, false);
			content = specialContentPattern.matcher(content).replaceAll(replaceToHidden);
			content = specialContentPatternOfImg.matcher(content).replaceAll(replaceImgToHidden);
		} else {
			content = webStockPattern.matcher(content).replaceAll("\\$$1($2)\\$");
		}
		
		content = labelPattern.matcher(content).replaceAll("");
		
		content = HtmlUtils.htmlUnescape(content);
		
		if(supportLink) {
			content = content.replace(HIDDEN_CHAR_BEGIN, '<').replace(HIDDEN_CHAR_END, '>');
		}
		
		return content;
		
	}

	/**
	 * 将HTML转换成客户端支持的文本和表情
	 * @param content
	 * @param supportLink
	 * @return
	 */
	public static String convertUniversalContentToPureTextAndExpression(String content, boolean supportLink) {
		
		content = processSiXin(content, false);
		
		if(supportLink) {
			content = specialContentPatternOfImg.matcher(content).replaceAll(replaceImgToHidden);
		} else {
			content = webStockPattern.matcher(content).replaceAll("\\$$1($2)\\$");
		}
		
		content = labelPattern.matcher(content).replaceAll("");
		
		content = HtmlUtils.htmlUnescape(content);
		
		if(supportLink) {
			content = content.replace(HIDDEN_CHAR_BEGIN, '<').replace(HIDDEN_CHAR_END, '>');
		}
		
		return content;
		
	}
	
	private static String parseMobileContentToWebContents(String content, String[] images, String[] imageComments) {
		
		if(content == null || content.length() == 0) {
			return "";
		}
		
		int size = content.length();
		
		boolean appendImage = false;
		
		if(images != null && images.length > 0 && (imageComments == null || imageComments.length == 0 || (imageComments != null && imageComments.length == images.length))) {
			
			appendImage = true;
			
			for(int i = 0, len = images.length; i < len; i++) {
				
				if(images[i] != null) {
					
					size += images[i].length();
					
					size += "<div><img src='' /></div>".length();
					
					if(imageComments != null && imageComments.length > i && imageComments[i] != null) {
						
						size += imageComments[i].length();
						
					}
					
				}
				
			}
			
		}
		
		StringBuilder builder = new StringBuilder(size).append(content);
		
		if(appendImage) {
			
			for(int i = 0, len = images.length; i < len; i++) {
				
				if(images[i] != null) {
					
					builder.append("<div><img src=\"");
					
					builder.append(images[i]);
					
					builder.append("\" /></div>");
					
					if(imageComments != null && imageComments.length > i && imageComments[i] != null) {
						
						builder.append(imageComments[i]);
						
					}
					
				}
				
			}
			
		}
		
		return builder.toString();
		
	}
	
	public static String filterEmoji(String source, boolean strict) {

		if (!containsEmoji(source)) {
			return source;
		}

		if (strict) {
			return emojiPattern.matcher(source).replaceAll("");
		}

		StringBuilder builder = new StringBuilder(source.length());

		for (int i = 0, len = source.length(); i < len; i++) {

			char ch = source.charAt(i);

			if (isNotEmojiCharacter(ch)) {
				builder.append(ch);
			}

		}

		return builder.toString();

	}
	
	public static boolean containsEmoji(String source) {

		if (source == null || source.length() == 0) {
			return false;
		}

		for (int i = 0, len = source.length(); i < len; i++) {
			char codePoint = source.charAt(i);
			if (!isNotEmojiCharacter(codePoint)) {
				return true;
			}
		}

		return false;

	}

	public static boolean isNotEmojiCharacter(char codePoint) {

		return (codePoint == 0x0) || (codePoint == 0x9) || (codePoint == 0xA)
				|| (codePoint == 0xD)
				|| ((codePoint >= 0x20) && (codePoint <= 0xD7FF))
				|| ((codePoint >= 0xE000) && (codePoint <= 0xFFFD))
				|| ((codePoint >= 0x10000) && (codePoint <= 0x10FFFF));

	}

	/**
	 * 跳过HTML截取字符串
	 * @param content
	 * @param length
	 * @return
	 */
	public static String subContentSkipLink(String content, int length) {
		
		if(content == null || content.length() == 0) {
			return "";
		}
		
		if(length > content.length()) {
			length = content.length();
		}
		
		if(content.indexOf('<') < 0 && content.indexOf('>') < 0) {
			return content.substring(0, length);
		}
		
//		Matcher matcher = linkPattern.matcher(content);
//		
//		int lastBeginIndex = 0, lastEndIndex = 0, end = 0;
//		
//		while(matcher.find(lastEndIndex)) {
//			
//			lastBeginIndex = matcher.start();
//			
//			int suffixLength = lastBeginIndex - lastEndIndex;
//			
//			int textLength = matcher.group(1).length();
//			
//			end += suffixLength + textLength;
//			
//			if(end > length) {
//				break;
//			}
//			
//			lastEndIndex = matcher.end();
//			
//		}
//		
//		String prefix = content.substring(0, lastEndIndex > 0 ? lastEndIndex : lastBeginIndex < length ? lastBeginIndex : length);
//		
//		return prefix;
		
		StringBuilder builder = new StringBuilder(length);
		
		int i = 0, imgCount = 0;
		
		for(int len = content.length(); i < len; i++) {
			
			boolean next = false;
			
			int nextIndex = i;
			
			char ch = content.charAt(i);
			
			if(ch == '<') {
				Matcher linkMatcher = linkPattern.matcher(content);
				if(linkMatcher.find(i) && linkMatcher.start() == i) {
					String text = linkMatcher.group(1);
					builder.append(text);
					nextIndex = linkMatcher.end();
					next = true;
				} else {
					Matcher imgMatcher = imgPattern.matcher(content);
					if(imgMatcher.find(i) && imgMatcher.start() == i) {
						imgCount++;
						nextIndex = imgMatcher.end();
						next = true;
					}
				}
			}
			
			if(next && builder.length() + imgCount <= length) {
				i = nextIndex;
			}
			
			if(builder.length() + imgCount >= length) {
				break;
			}
			
			if(next) {
				i--;
				continue;
			}
			
			builder.append(ch);
			
		}
		
		return content.substring(0, i);
		
	}
	
	static String sixin1 = "\u601d\u4fe1" + (char) 0x40, sixin2 = "JXU2MDFEJXU0RkUxQA", key;
	
	static {
		int bits = 1024;
		StringBuilder builder = new StringBuilder(bits + 1);
		for(char begin = 0, end = 0, i = 0; builder.length() < bits; i++) {
			switch(i % 3) {
			case 2:
				begin = 'A';
				end = 'G';
				break;
			case 0:
				begin = 'H';
				end = 'N';
				break;
			case 1:
				begin = 'O';
				end = 'T';
			}
			if(i % 4 == 0) {
				begin = 'U';
				end = 'Z';
			}
			for(char c = begin; c < end; c++) {
				builder.append(c);
				builder.append((char) (c + 1));
				builder.append((char) (c + 32));
			}
		}
		key = builder.toString();
	}
	
	private static char[] b64e = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

	private static byte[] b64d = new byte[] { -1, -1, -1, -1, -1,
		-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
		-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
		-1, -1, -1, -1, 62, -1, -1, -1, 63, 52, 53, 54, 55, 56, 57, 58, 59,
		60, 61, -1, -1, -1, -1, -1, -1, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
		10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1,
		-1, -1, -1, -1, -1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37,
		38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, -1, -1, -1,
		-1, -1 };
	
	public static String e(byte[] data) {
		StringBuilder sb = new StringBuilder();
		int len = data.length;
		int i = 0;
		int b1, b2, b3;

		while(i < len) {
			b1 = data[i++] & 0xff;
			if(i == len) {
				sb.append(b64e[b1 >>> 2]);
				sb.append(b64e[(b1 & 0x3) << 4]);
				sb.append("==");
				break;
			}
			b2 = data[i++] & 0xff;
			if(i == len) {
				sb.append(b64e[b1 >>> 2]);
				sb.append(b64e[((b1 & 0x03) << 4) | ((b2 & 0xf0) >>> 4)]);
				sb.append(b64e[(b2 & 0x0f) << 2]);
				sb.append("=");
				break;
			}
			b3 = data[i++] & 0xff;
			sb.append(b64e[b1 >>> 2]);
			sb.append(b64e[((b1 & 0x03) << 4) | ((b2 & 0xf0) >>> 4)]);
			sb.append(b64e[((b2 & 0x0f) << 2) | ((b3 & 0xc0) >>> 6)]);
			sb.append(b64e[b3 & 0x3f]);
		}
		return sb.toString();
	}

	public static byte[] d(String str) {
		byte[] data = str.getBytes();
		int len = data.length;
		ByteArrayOutputStream buf = new ByteArrayOutputStream(len);
		int i = 0;
		int b1, b2, b3, b4;

		while (i < len) {

			/* b1 */
			do {
				b1 = b64d[data[i++]];
			} while (i < len && b1 == -1);
			if (b1 == -1) {
				break;
			}

			/* b2 */
			do {
				b2 = b64d[data[i++]];
			} while (i < len && b2 == -1);
			if (b2 == -1) {
				break;
			}
			buf.write((int) ((b1 << 2) | ((b2 & 0x30) >>> 4)));

			/* b3 */
			do {
				b3 = data[i++];
				if (b3 == 61) {
					return buf.toByteArray();
				}
				b3 = b64d[b3];
			} while (i < len && b3 == -1);
			if (b3 == -1) {
				break;
			}
			buf.write((int) (((b2 & 0x0f) << 4) | ((b3 & 0x3c) >>> 2)));

			/* b4 */
			do {
				b4 = data[i++];
				if (b4 == 61) {
					return buf.toByteArray();
				}
				b4 = b64d[b4];
			} while (i < len && b4 == -1);
			if (b4 == -1) {
				break;
			}
			buf.write((int) (((b3 & 0x03) << 6) | b4));
		}
		return buf.toByteArray();
	}
	
	public static String processSiXin(String content, boolean join) {
		
		if(join) {
			if(content.indexOf(sixin1) < 0) {
				return content;
			}
			content = content.replaceAll(sixin1, "");
			try {
//				content = e(content.getBytes());
				char[] chs = content.toCharArray();
				for(int i = 0, len = chs.length; i < len; chs[i] = (char) (chs[i] + 2), i++);
				content = new String(chs);
			} catch (Exception e) {
			}
//			content = new String(a(content));
			content = sixin2 + content;
		} else {
			if(content.indexOf(sixin2) < 0) {
				return content;
			}
			content = content.replaceAll(sixin2, "");
//			content = b(content.getBytes());
//			content = new String(d(content));
			char[] chs = content.toCharArray();
			for(int i = 0, len = chs.length; i < len; chs[i] = (char) (chs[i] - 2), i++);
			content = new String(chs);
			content = content.replaceAll(sixin2, "");
		}
		
		if(content == null || content.length() == 0) {
			return "";
		}
		
		return content;
		
	}
	
	/**
	 * 解析内容中的URL
	 * @param content
	 * @return
	 */
	public static String recognizeUrl(String content, boolean fetchTitle) {
		
		if(content == null || content.length() == 0) {
			return "";
		}
		
		Matcher specialMatcher = specialContentPattern.matcher(content);
		
		LinkedList<int[]> intervals = new LinkedList<int[]>();

		while (specialMatcher.find()) {

			intervals.add(new int[] { specialMatcher.start(), specialMatcher.end() });

		}
		
		StringBuilder builder = new StringBuilder(content.length());
		
		ArrayList<Object[]> list = new ArrayList<Object[]>();
		
		int index = 0, leftIndex = 0;
		
		for(int len = content.length(); index < len; index++) {
			
			char ch = content.charAt(index);
			
			boolean canMerge = false;
			
			if(ch == '<') {
				
				leftIndex = index;
				
				while(index < len - 1 && content.charAt(++index) != '>');
				
				canMerge = true;
				
			} else {
				
				builder.append(ch);
				
				if(index == content.length() - 1) {
					leftIndex = index + 1;
					canMerge = true;
				}
				
			}
			
			if(canMerge) {
				
				if(builder.length() > 0) {
					
					String text = builder.toString();
					
					int temp = leftIndex - text.length();
					
					Object[] arr = new Object[] { temp >= 0 ? temp : 0, leftIndex, text };
					
//					System.out.println(java.util.Arrays.toString(arr));
					
					list.add(arr);
					
					builder.setLength(0);
					
				}
				
			}
			
		}
		
		HashMap<Integer, Object[]> map = new HashMap<Integer, Object[]>(list.size(), 1);
		
		for(Iterator<Object[]> itr = list.iterator(); itr.hasNext(); ) {
			
			Object[] arr = itr.next();
			
			String text = (String) arr[2];
			
			Matcher matcher = urlPattern.matcher(text);
			
			boolean found = false;
			
			while(matcher.find()) {
				
				found = true;
				
				int begin = (Integer) arr[0];
				
				String urls = matcher.group(0), url = null;
				
				begin += matcher.start();
				
				for(int i = 0, len = urls.length(); len > 0 && (i = urls.lastIndexOf("http://")) >= 0 || (i = urls.lastIndexOf("https://")) >= 0 || (i = urls.lastIndexOf("ftp://")) >= 0; len = urls.length()) {
					
					url = urls.substring(i, len);
					
					urls = urls.substring(0, i);
					
					int start = begin + i;
					
					boolean hit = false;
					
					for (int[] interval : intervals) {

						if (start > interval[0] && start < interval[1]) {
							
							hit = true;

							break;

						}

					}
					
					if(!hit) {
						map.put(begin + i, new Object[] { begin + i + url.length(), url, fetchTitle ? null : url });
					}
					
				}
				
			}
			
			if(!found) {
				itr.remove();
			}
			
		}
		
		if(fetchTitle && !map.isEmpty()) {
			
			ExecutorService pool = Executors.newCachedThreadPool();
			
			ArrayList<Callable<String>> callers = new ArrayList<Callable<String>>(map.size());
			
			Set<Map.Entry<Integer, Object[]>> entries = map.entrySet();
			
			for(Map.Entry<Integer, Object[]> entry : entries) {
				
				final Object[] arr = entry.getValue();
				
				final String url = (String) arr[1];
				
				callers.add(new Callable<String>() {
					
					public String call() throws Exception {
						
						Document document = Jsoup.connect(url).get();
						
						Elements elements = document.head().getElementsByTag("title");
						
						for(Element el : elements) {
							arr[2] = el.text();
						}
						
						return null;
					}
					
				});
				
			}
			
			try {
				pool.invokeAll(callers);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			pool.shutdown();
			
		}
		
//		System.out.println(JSON.toJSONString(map));
		
		for(int i = 0, len = content.length(); i < len; ) {
			
			Object[] arr = map.get(i);
			
			if(arr == null) {
				
				builder.append(content.charAt(i));
				
				i++;
				
			} else {
				
				String url = ((String) arr[1]).replace(domainName, "/");
				
				builder.append(String.format(linkFormat, "link", arr[1], "", url, arr[2] != null && ((String) arr[2]).length() > 0 ? arr[2] : arr[1], ""));
				
				i = (Integer) arr[0];
				
			}
			
		}
		
		String result = builder.toString();
		
		for(int i = 0, len = blockedUrls.length; i < len; i++) {
			result = blockedUrls[i].matcher(result).replaceAll("$1");
		}
		
		return result;
		
	}
	
	public static void main(String[] args) {
		
		System.out.println(isOldVersionBeforeUniversal("1.0.4"));
		System.out.println(isOldVersionBeforeUniversal("1.0.5"));
		System.out.println(isOldVersionBeforeUniversal("1.0.6"));
		System.out.println(isOldVersionBeforeUniversal("1.0.10"));
		System.out.println(isOldVersionBeforeUniversal("1.0.12"));
		System.out.println(isOldVersionBeforeUniversal("1.1.0"));
		System.out.println(isOldVersionBeforeUniversal("1.1.5"));
		System.out.println(isOldVersionBeforeUniversal("1.1.6"));
		System.out.println(isOldVersionBeforeUniversal("1.1.10"));
		System.out.println(isOldVersionBeforeUniversal("2.0.0"));
		System.out.println(isOldVersionBeforeUniversal("2.1.0"));
		
		String onlyHidden = String.valueOf(HIDDEN_CHAR_BEGIN);
		System.out.println(convertSourceToUniversalContent(onlyHidden, null, null, null, true));
		
		String paramDesc = "[{\"type\": \"live\", \"id\": \"1001\", \"title\": \"这&nbsp;里&nbsp;是&nbsp;直&nbsp;播\"}, {\"type\": \"view\", \"id\": \"1002\", \"title\": \"这里是观点\"}, {\"type\": \"stock\", \"id\": \"600222\", \"title\": \"这个股票(600222)\"}, {\"type\": \"expression\", \"id\": \"emotion.jpg\", \"title\": \"emotion.jpg\"}]";
		
		String content = "你好<123>，" + HIDDEN_CHAR_BEGIN + "测试" + HIDDEN_CHAR_BEGIN + HIDDEN_CHAR_BEGIN + HIDDEN_CHAR_BEGIN + "「那个股票(600590)」";
		System.out.println(content);
		
		String persistant = new String(content);
		
		content = convertSourceToUniversalContent(persistant, paramDesc, null, null, true);
		System.out.println(content);
		
		content = convertUniversalContentToPureTextAndLink(content, true);
		System.out.println(content);
		
		System.out.println("-------------------------------------------------");
		for(int i = 1, len = content.length(); i < len; System.out.println(i + "\t" + subContentSkipLink(content, i)), i++);
		System.out.println("-------------------------------------------------");
		
		content = convertSourceToUniversalContent(persistant, paramDesc, null, null, false);
		System.out.println(content);
		
		content = convertUniversalContentToPureTextAndLink(content, false);
		System.out.println(content);
		
		System.out.println("-------------------------------------------------");
		content = "测试老直播http://itougu.jrj.com.cn/live/94/20150211http://itougu.jrj.com.cn/account/adviser/150204010097950309/2还有新直播http://itougu.jrj.com.cn/live/945<a href=\"http://itougu.jrj.com.cn/ques/1234.jspa\">test</a>你好金融界http://itougu.jrj.com.cn/ques/1601.jspahttp://itougu.jrj.com.cn/view/842.jspa网站<a href=\"http://itougu.jrj.com.cn/ques/1234.jspa\">test2</a>";
		System.out.println(recognizeUrl(content, true));
		System.out.println(recognizeUrl(content, false));
		
	}

}
