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
package com.digipom.manteresting.android.service.rest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.util.Log;
import android.util.SparseArray;

import com.digipom.manteresting.android.config.LoggerConfig;

public class RestServiceHelper {
	private static final String TAG = "RestServiceHelper";

	public static final String EXTRA_REQUEST_ID = "EXTRA_REQUEST_ID";

	public static final String EXTRA_COUNT = "EXTRA_COUNT";
	public static final String EXTRA_SMALLEST_ID = "EXTRA_SMALLEST_ID";
	public static final String EXTRA_GREATEST_ID = "EXTRA_GREATEST_ID";
	public static final String EXTRA_OFFSET = "EXTRA_OFFSET";
	public static final String EXTRA_LIMIT = "EXTRA_LIMIT";
	public static final String EXTRA_RESULT_RECEIVER = "EXTRA_RESULT_RECEIVER";

	public static final int STATUS_RUNNING = 0x1;
	public static final int STATUS_ERROR = 0x2;
	public static final int STATUS_SUCCESS = 0x3;

	private static final Handler sHandler = new Handler();
	private static final AtomicInteger sRequestId = new AtomicInteger();
	private static final SparseArray<Set<ResultReceiver>> sRequestCallbacks = new SparseArray<Set<ResultReceiver>>();

	static enum RequestType {
		GET_NAILS
	}

	private static final Map<RequestType, Integer> sOngoingRequests = new HashMap<RestServiceHelper.RequestType, Integer>();

	private static final Object sMapLock = new Object();

	private static final ResultReceiver sRestResultReceiver = new ResultReceiver(sHandler) {
		@Override
		protected void onReceiveResult(int resultCode, Bundle resultData) {
			if (resultData != null) {
				final int requestId = resultData.getInt(EXTRA_REQUEST_ID, -1);

				if (requestId != -1) {
					final Set<ResultReceiver> requestCallbacks;

					synchronized (sMapLock) {
						requestCallbacks = sRequestCallbacks.get(requestId);

						if (resultCode == STATUS_ERROR || resultCode == STATUS_SUCCESS) {
							sRequestCallbacks.remove(requestId);

							Iterator<Entry<RequestType, Integer>> it = sOngoingRequests.entrySet().iterator();

							while (it.hasNext()) {
								final Entry<RequestType, Integer> entry = it.next();

								if (requestId == entry.getValue()) {
									it.remove();
								}
							}
						}
					}

					if (requestCallbacks != null) {
						for (final ResultReceiver requestCallback : requestCallbacks) {
							if (requestCallback != null) {
								if (LoggerConfig.canLog(Log.VERBOSE)) {
									Log.v(TAG, "onReceiveResult(): Calling callback " + requestCallback
											+ " with resultCode=" + resultCode + ", resultData=" + resultData);
								}

								requestCallback.send(resultCode, resultData);
							} else {
								if (LoggerConfig.canLog(Log.VERBOSE)) {
									Log.v(TAG, "onReceiveResult(): Callback has been garbage-collected.");
								}
							}
						}
					} else {
						if (LoggerConfig.canLog(Log.ERROR)) {
							Log.e(TAG, "onReceiveResult(): Null set of listeners.");
						}
					}
				} else {
					if (LoggerConfig.canLog(Log.ERROR)) {
						Log.e(TAG, "onReceiveResult(): No request ID returned.");
					}
				}
			} else {
				if (LoggerConfig.canLog(Log.ERROR)) {
					Log.e(TAG, "onReceiveResult(): No bundle returned.");
				}
			}
		}
	};

	private RestServiceHelper() {
		// Private constructor
	}

	public static final int syncNails(Context context, ResultReceiver resultReceiver) {
		return syncNails(context, resultReceiver, -1, -1);		
	}

	public static final int syncNails(Context context, ResultReceiver resultReceiver, int offset, int limit) {
		final Bundle requestParams = new Bundle();

		if (offset >= 0 && limit > 0) {
			requestParams.putInt(EXTRA_OFFSET, offset);
			requestParams.putInt(EXTRA_LIMIT, limit);
		}

		return fireRequestIfNotAlreadyOngoing(context, resultReceiver, RequestType.GET_NAILS, requestParams);
	}

	private static final int fireRequestIfNotAlreadyOngoing(Context context, ResultReceiver resultReceiver,
			RequestType requestType, final Bundle extras) {
		final int requestId;
		final boolean shouldFireIntent;

		synchronized (sMapLock) {
			if (sOngoingRequests.containsKey(requestType)) {
				requestId = sOngoingRequests.get(requestType);
				shouldFireIntent = false;
			} else {
				requestId = sRequestId.getAndIncrement();
				sOngoingRequests.put(requestType, requestId);
				shouldFireIntent = true;
			}

			Set<ResultReceiver> listeners = sRequestCallbacks.get(requestId);

			if (listeners == null) {
				// Since this is a weak hash map, entries will be automatically
				// removed from the map upon garbage collection. They shouldn't
				// even show up in the map as a null reference.
				listeners = Collections.newSetFromMap(new WeakHashMap<ResultReceiver, Boolean>());
				sRequestCallbacks.put(requestId, listeners);
			}

			listeners.add(resultReceiver);
		}

		if (shouldFireIntent) {
			if (LoggerConfig.canLog(Log.VERBOSE)) {
				Log.v(TAG, "fireRequestIfNotAlreadyOngoing(): Firing new request " + requestId + " for type "
						+ requestType);
			}

			final Intent requestIntent = new Intent(context, RestService.class);
			requestIntent.putExtra(EXTRA_REQUEST_ID, requestId);
			requestIntent.putExtra(EXTRA_RESULT_RECEIVER, sRestResultReceiver);
			requestIntent.putExtras(extras);
			context.startService(requestIntent);
		}

		return requestId;
	}
}
