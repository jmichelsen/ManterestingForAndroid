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
package com.digipom.manteresting.android.service.cache;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.util.Log;

import com.digipom.manteresting.android.config.LoggerConfig;
import com.digipom.manteresting.android.util.FileUtils;

class ImageDownloader {
	private static final String TAG = "ImageDownloader";

	private static final String[] otherExtensionsToTry = { "jpg", "jpe", "jpeg" };

	byte[] download(String uri) {
		final HttpClient httpClient = new DefaultHttpClient();

		HttpGet httpGet = new HttpGet(uri);
		byte[] data = null;

		try {
			HttpResponse response = httpClient.execute(httpGet);

			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
				for (String extension : otherExtensionsToTry) {
					final String rewrittenUri = FileUtils.getFileNameWithoutExtension(uri) + '.' + extension;

					if (LoggerConfig.canLog(Log.VERBOSE)) {
						Log.v(TAG, "Original URI " + uri + " not found. Trying with another extension: " + rewrittenUri);
					}

					// Consume the content so the connection can be re-used.
					response.getEntity().consumeContent();
					
					httpGet = new HttpGet(rewrittenUri);
					response = httpClient.execute(httpGet);

					if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
						break;
					}
				}
			}

			InputStream is = null;

			try {
				final HttpEntity responseEntity = response.getEntity();
				is = responseEntity.getContent();
				final ByteArrayOutputStream baos = new ByteArrayOutputStream();
				final byte[] buffer = new byte[4096];

				int read;
				while ((read = is.read(buffer)) > 0) {
					baos.write(buffer, 0, read);
				}

				data = baos.toByteArray();
			} finally {
				if (is != null) {
					is.close();
					is = null;
				}
			}
		} catch (Throwable t) {
			if (LoggerConfig.canLog(Log.DEBUG)) {
				Log.d(TAG, "Download failed: " + uri, t);
			}
		}

		return data;
	}
}
