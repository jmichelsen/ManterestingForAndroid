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

import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.util.Log;

import com.digipom.manteresting.android.config.LoggerConfig;

public abstract class BitmapLoadCallback extends ResultReceiver {
	private static final String TAG = "BitmapLoadCallback";
	private static final String EXTRA_URI = "EXTRA_URI";
	private static final String EXTRA_SUCCESSFUL = "EXTRA_SUCCESSFUL";

	public BitmapLoadCallback(Handler handler) {
		super(handler);
	}

	void send(String uri, boolean successful) {
		final Bundle resultData = new Bundle();
		resultData.putString(EXTRA_URI, uri);
		resultData.putBoolean(EXTRA_SUCCESSFUL, successful);

		super.send(0, resultData);
	}

	@Override
	protected final void onReceiveResult(int resultCode, Bundle resultData) {
		final String uri = resultData.getString(EXTRA_URI);
		final boolean successful = resultData.getBoolean(EXTRA_SUCCESSFUL);

		if (LoggerConfig.canLog(Log.VERBOSE)) {
			Log.v(TAG, "onReceiveResult(): Calling callback: " + uri);
		}

		onBitmapLoadComplete(uri, successful);
	}

	/**
	 * Callback when a bitmap load is complete. Will be called for each time
	 * that a bitmap was queued.
	 */
	protected abstract void onBitmapLoadComplete(String uri, boolean successful);
}
