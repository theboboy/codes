package com.jrj.tougu.common.util;

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

/**
 * 
 * @author bo.sun
 * @since 2015-01-15
 * @version 1.0.0
 *
 */
public final class RecognizeURL {

	//                                               url                                  title
	private static String linkFormat = "<a href=\"%s\" class=\"link\" target=\"_blank\">%s</a>";

	private static Pattern urlPattern = Pattern.compile("(http|https|ftp):\\/\\/[\\w\\-_]+(\\.[\\w\\-_]+)+([\\w\\-\\.,@?^=%&:/~\\+#]*[\\w\\-\\@?^=%&/~\\+#])?", Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);
	
	//                                                                   1                                                      2         3
	private static Pattern specialContentPattern = Pattern.compile("<(a href=\"[^\"^>]+\" class=\"link\" target=\"_blank\")>([^<^>]+)<(/a)>");
	
	private RecognizeURL() {
		super();
	}
	
	/**
	 * 解析内容中的URL
	 * @param content
	 * @return
	 */
	public static String recognize(String content, boolean fetchTitle) {
		if(content == null || content.length() == 0) {
			return "";
		}
		Matcher specialMatcher = specialContentPattern.matcher(content);
		LinkedList<int[]> intervals = new LinkedList<>();
		while (specialMatcher.find()) {
			intervals.add(new int[] { specialMatcher.start(), specialMatcher.end() });
		}
		StringBuilder builder = new StringBuilder(content.length());
		ArrayList<Object[]> list = new ArrayList<>();
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
		
		HashMap<Integer, Object[]> map = new HashMap<>(list.size(), 1);
		
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
				String url = (String) arr[1];
				builder.append(String.format(linkFormat, url, arr[2] != null && ((String) arr[2]).length() > 0 ? arr[2] : arr[1]));
				i = (Integer) arr[0];
			}
		}
		
		return builder.toString();
	}
	
	public static void main(String[] args) {
		System.out.println("-------------------------------------------------");
		String content = "测试老直播http://itougu.jrj.com.cn/live/94/20150211http://itougu.jrj.com.cn/account/adviser/150204010097950309/2还有新直播http://itougu.jrj.com.cn/live/945<a href=\"http://itougu.jrj.com.cn/ques/1234.jspa\">test</a>你好金融界http://itougu.jrj.com.cn/ques/1601.jspahttp://itougu.jrj.com.cn/view/842.jspa网站<a href=\"http://itougu.jrj.com.cn/ques/1234.jspa\">test2</a>";
		System.out.println(recognize(content, true));
		System.out.println(recognize(content, false));
		
	}

}
