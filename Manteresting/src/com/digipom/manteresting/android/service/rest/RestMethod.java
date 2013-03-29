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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;

public class RestMethod {
	protected final HttpClient httpClient;
	protected final String url;	

	public RestMethod(HttpClient httpClient, String url) {
		this.httpClient = httpClient;
		this.url = url;
	}
	
	public byte[] executeGet() throws ClientProtocolException, IOException {
		final HttpUriRequest request = new HttpGet(url);
		return execute(request);
	}

	protected byte[] execute(HttpUriRequest request) throws ClientProtocolException, IOException {
		byte[] receivedContent;
		
		final HttpResponse response = httpClient.execute(request);
		final int status = response.getStatusLine().getStatusCode();

		if (status != HttpStatus.SC_OK) {
			throw new RestException("Unexpected server response " + response.getStatusLine() + " for "
					+ request.getRequestLine());
		}

		final HttpEntity responseEntity = response.getEntity();
		final InputStream content = responseEntity.getContent();

		try {
			final ByteArrayOutputStream baos = new ByteArrayOutputStream();
			final byte[] buffer = new byte[4096];
			final int bufferLength = buffer.length;
			int read;

			while ((read = content.read(buffer, 0, bufferLength)) != -1) {
				baos.write(buffer, 0, read);
			}

			baos.flush();

			responseEntity.consumeContent();

			receivedContent = baos.toByteArray();
		} finally {
			if (content != null) {
				content.close();
			}
		}
		
		return receivedContent;
	}
}
