/*
 * Copyright (C) 2013 Digipom Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Copright notice for original code on which this is based:
 *
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.digipom.manteresting.android.service.rest;

import static com.digipom.manteresting.android.config.AppConfig.MANTERESTING_SERVER;
import static com.digipom.manteresting.android.service.rest.RestServiceHelper.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.HttpClient;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HttpContext;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.text.format.DateUtils;
import android.util.Log;

import com.digipom.manteresting.android.config.LoggerConfig;
import com.digipom.manteresting.android.processor.Meta;
import com.digipom.manteresting.android.processor.json.NailsJsonProcessor;

public class RestService extends IntentService {
	private static final String TAG = "RestService";
	private static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";
	private static final String ENCODING_GZIP = "gzip";

	private static final int SECOND_IN_MILLIS = (int) DateUtils.SECOND_IN_MILLIS;

	private static final Uri API = MANTERESTING_SERVER.buildUpon().appendPath("api").appendPath("v1").build();
	private static final Uri NAIL_URI = buildUriWithAppendedPath("nail");

	private static final boolean USE_JSON = true;

	public RestService() {
		super(TAG);
	}

	private static final Uri buildUriWithAppendedPath(String apiPath) {
		final Uri.Builder builder = API.buildUpon().appendPath(apiPath);

		if (USE_JSON) {
			builder.appendQueryParameter("format", "json");
		}

		return builder.build();
	}

	@Override
	public void onCreate() {
		super.onCreate();

		if (LoggerConfig.canLog(Log.VERBOSE)) {
			Log.v(TAG, "onCreate()");
		}
	}

	/**
	 * This method runs in a worker thread, as per {@link IntentService}.
	 */
	@Override
	protected void onHandleIntent(Intent intent) {
		if (LoggerConfig.canLog(Log.VERBOSE)) {
			Log.v(TAG, "onHandleIntent(" + intent + ")");
		}

		final int requestId = intent.getIntExtra(EXTRA_REQUEST_ID, -1);
		final ResultReceiver receiver = intent.getParcelableExtra(EXTRA_RESULT_RECEIVER);
		final Bundle responseBundle = new Bundle();
		responseBundle.putInt(EXTRA_REQUEST_ID, requestId);

		if (receiver != null) {
			receiver.send(STATUS_RUNNING, responseBundle);
		}

		try {
			final int requestedOffset = intent.getIntExtra(EXTRA_OFFSET, -1);
			final int requestedLimit = intent.getIntExtra(EXTRA_LIMIT, -1);

			final Uri nailRequestUri;

			if (requestedOffset != -1) {
				nailRequestUri = NAIL_URI.buildUpon().appendQueryParameter("offset", String.valueOf(requestedOffset))
						.appendQueryParameter("limit", String.valueOf(requestedLimit)).build();
			} else {
				nailRequestUri = NAIL_URI;
			}

			if (LoggerConfig.canLog(Log.VERBOSE)) {
				Log.v(TAG, "calling URI " + nailRequestUri);
			}

			Meta meta = new NailsJsonProcessor(getContentResolver(), new RestMethod(getHttpClient(this), nailRequestUri.toString()))
					.executeMethodAndApply();
			
			responseBundle.putInt(EXTRA_COUNT, meta.count);
			responseBundle.putInt(EXTRA_SMALLEST_ID, meta.smallestId);
			responseBundle.putInt(EXTRA_GREATEST_ID, meta.greatestId);			
			responseBundle.putInt(EXTRA_OFFSET, meta.nextOffset);
			responseBundle.putInt(EXTRA_LIMIT, meta.nextLimit);

			if (LoggerConfig.canLog(Log.VERBOSE)) {
				Log.v(TAG, "onHandleIntent(): sync successful.");
			}

			if (receiver != null) {
				receiver.send(STATUS_SUCCESS, responseBundle);
			}
		} catch (Exception e) {
			if (LoggerConfig.canLog(Log.ERROR)) {
				Log.e(TAG, "Problem while syncing", e);
			}

			responseBundle.putString(Intent.EXTRA_TEXT, e.toString());

			if (receiver != null) {
				receiver.send(STATUS_ERROR, responseBundle);
			}
		}
	}

	private static HttpClient getHttpClient(Context context) {
		final HttpParams params = new BasicHttpParams();

		// Use generous timeouts for slow mobile networks
		HttpConnectionParams.setConnectionTimeout(params, 20 * SECOND_IN_MILLIS);
		HttpConnectionParams.setSoTimeout(params, 20 * SECOND_IN_MILLIS);
		HttpConnectionParams.setSocketBufferSize(params, 8192);
		HttpProtocolParams.setUserAgent(params, buildUserAgent(context));

		final DefaultHttpClient client = new DefaultHttpClient(params);

		client.addRequestInterceptor(new HttpRequestInterceptor() {
			public void process(HttpRequest request, HttpContext context) {
				if (!request.containsHeader(HEADER_ACCEPT_ENCODING)) {
					request.addHeader(HEADER_ACCEPT_ENCODING, ENCODING_GZIP);
				}
			}
		});

		client.addResponseInterceptor(new HttpResponseInterceptor() {
			public void process(HttpResponse response, HttpContext context) {
				final HttpEntity entity = response.getEntity();
				final Header encoding = entity.getContentEncoding();

				if (encoding != null) {
					for (HeaderElement element : encoding.getElements()) {
						if (element.getName().equalsIgnoreCase(ENCODING_GZIP)) {
							response.setEntity(new InflatingEntity(response.getEntity()));
							break;
						}
					}
				}
			}
		});

		return client;
	}

	private static String buildUserAgent(Context context) {
		try {
			final PackageManager manager = context.getPackageManager();
			final PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);

			// Some APIs require "(gzip)" in the user-agent string.
			return info.packageName + "/" + info.versionName + " (" + info.versionCode + ") (gzip)";
		} catch (NameNotFoundException e) {
			return null;
		}
	}

	private static class InflatingEntity extends HttpEntityWrapper {
		public InflatingEntity(HttpEntity wrapped) {
			super(wrapped);
		}

		@Override
		public InputStream getContent() throws IOException {
			return new GZIPInputStream(wrappedEntity.getContent());
		}

		@Override
		public long getContentLength() {
			return -1;
		}
	}
}
