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
package com.digipom.manteresting.android.processor.json;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.http.client.ClientProtocolException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;

import com.digipom.manteresting.android.processor.Meta;
import com.digipom.manteresting.android.processor.ResponseProcessor;
import com.digipom.manteresting.android.provider.ManterestingContract;
import com.digipom.manteresting.android.service.rest.RestException;
import com.digipom.manteresting.android.service.rest.RestMethod;

public abstract class JsonProcessor implements ResponseProcessor {
	protected final ContentResolver resolver;
	private final RestMethod method;

	public JsonProcessor(ContentResolver resolver, RestMethod method) {
		this.resolver = resolver;
		this.method = method;
	}

	public final Meta executeMethodAndApply() throws RestException {
		try {
			final Meta metaToReturn = new Meta();
			final byte[] response = method.executeGet();
			final JSONObject object = new JSONObject(new String(response));
			final JSONObject meta = object.getJSONObject("meta");
			final String next = meta.getString("next");
			final JSONArray objects = object.getJSONArray("objects");
			final int objectsLength = objects.length();
			
			if (next != null) {
				try {
					final Uri nextUri = Uri.parse(next);

					final int nextOffset = Integer.parseInt(nextUri.getQueryParameter("offset"));
					final int nextLimit = Integer.parseInt(nextUri.getQueryParameter("limit"));	
					
					metaToReturn.count = objectsLength;
					metaToReturn.nextOffset = nextOffset;
					metaToReturn.nextLimit = nextLimit;
				} catch (NumberFormatException e) {
					throw new RestException("Problem parsing meta 'next' URL");
				}
			}
			
			int greatestId = Integer.MIN_VALUE;
			int smallestId = Integer.MAX_VALUE;
			
			try {
				for (int i = 0; i < objectsLength; i++) {
					final int currentId = Integer.parseInt(objects.getJSONObject(i).getString("id"));
					
					if (greatestId < currentId) greatestId = currentId;
					if (smallestId > currentId) smallestId = currentId;
				}
			} catch (NumberFormatException e) {
				throw new RestException("Problem parsing ids");
			}
			
			metaToReturn.greatestId = greatestId;
			metaToReturn.smallestId = smallestId;
			
			final ArrayList<ContentProviderOperation> batch = parse(object, metaToReturn);
			resolver.applyBatch(ManterestingContract.CONTENT_AUTHORITY, batch);					
			return metaToReturn;
		} catch (RemoteException e) {
			throw new RestException("Problem applying batch operation", e);
		} catch (OperationApplicationException e) {
			throw new RestException("Problem applying batch operation", e);
		} catch (JSONException e) {
			throw new RestException("Problem parsing JSON", e);
		} catch (ClientProtocolException e) {
			throw new RestException("Problem sending request", e);
		} catch (IOException e) {
			throw new RestException("Problem sending request", e);
		}
	}

	protected abstract ArrayList<ContentProviderOperation> parse(JSONObject response, Meta meta) throws JSONException;	
}
