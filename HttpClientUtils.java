package com.yoho.erp.sync.inventory.util;

import com.alibaba.fastjson.JSON;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author bo.sun
 */
public final class HttpClientUtils {

	/**
	 * http连接管理
	 */
	private static PoolingHttpClientConnectionManager httpClientConnectionManager;

	/**
	 * http请求设置
	 */
	private static RequestConfig requestConfig;

	static {

		httpClientConnectionManager = new PoolingHttpClientConnectionManager();
		httpClientConnectionManager.setMaxTotal(200);
		httpClientConnectionManager.setDefaultMaxPerRoute(20);
		httpClientConnectionManager.setValidateAfterInactivity(0);

		requestConfig = RequestConfig.custom()
									 .setConnectTimeout(10_000)
									 .setSocketTimeout(10_000)
									 .build();


	}

	private static CloseableHttpClient getHttpClient() {
		return HttpClientBuilder.create()
								.setConnectionManager(httpClientConnectionManager)
								.setDefaultRequestConfig(requestConfig)
								.setConnectionManagerShared(true)
								.build();
	}

	public static String postFormFields(String url, Map<String, Object> param) {
		CloseableHttpClient httpClient = getHttpClient();
		HttpPost request = new HttpPost(url);
		List<NameValuePair> nameValuePairs = new ArrayList<>();
		for (Map.Entry<String, Object> entry : param.entrySet()) {
			nameValuePairs.add(new BasicNameValuePair(entry.getKey(), String.valueOf(entry.getValue())));
		}
		request.addHeader("Content-type", "application/x-www-form-urlencoded");
		CloseableHttpResponse response = null;
		try {
			request.setEntity(new UrlEncodedFormEntity(nameValuePairs, "UTF-8"));
			response = httpClient.execute(request);
			if (response.getStatusLine().getStatusCode() == 200) {
				return EntityUtils.toString(response.getEntity());
			}
			throw new RuntimeException("call " + url + " return http code is " + response.getStatusLine().getStatusCode());
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			if (response != null) {
				if (response.getEntity() != null) {
					try {
						EntityUtils.consume(response.getEntity());
					} catch (IOException ignored) {
					}
				}
				try {
					response.close();
				} catch (Exception ignored) {
				}
			}
			if (httpClient != null) {
				try {
					httpClient.close();
				} catch (IOException ignored) {
				}
			}
		}
	}

	public static String postRawBody(String url, Object body) {
		CloseableHttpClient httpClient = getHttpClient();
		HttpPost request = new HttpPost(url);
		String json = JSON.toJSONString(body);
		CloseableHttpResponse response = null;
		try {
			request.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
			response = httpClient.execute(request);
			if (response.getStatusLine().getStatusCode() == 200) {
				return EntityUtils.toString(response.getEntity());
			}
			throw new RuntimeException("call " + url + " return http code is " + response.getStatusLine().getStatusCode());
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			if (response != null) {
				if (response.getEntity() != null) {
					try {
						EntityUtils.consume(response.getEntity());
					} catch (IOException ignored) {
					}
				}
				try {
					response.close();
				} catch (Exception ignored) {
				}
			}
			if (httpClient != null) {
				try {
					httpClient.close();
				} catch (IOException ignored) {
				}
			}
		}
	}

}
