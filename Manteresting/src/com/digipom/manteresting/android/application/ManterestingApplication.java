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
package com.digipom.manteresting.android.application;

import android.app.ActivityManager;
import android.app.Application;
import android.os.Debug;
import android.util.Log;

import com.digipom.manteresting.android.config.LoggerConfig;
import com.digipom.manteresting.android.util.StringUtils;

public class ManterestingApplication extends Application {
	private static final String TAG = "ManterestingApplication";

	@Override
	public void onCreate() {
		super.onCreate();

		if (LoggerConfig.canLog(Log.INFO)) {
			Log.i(TAG, "Maximum VM heap size:" + StringUtils.getReadableSize(Runtime.getRuntime().maxMemory()));
			Log.i(TAG,
					"Recommended memory usage:"
							+ StringUtils.getReadableSize(((ActivityManager) getSystemService(ACTIVITY_SERVICE))
									.getMemoryClass() * 1024 * 1024));
			Log.i(TAG, "Native heap size: " + StringUtils.getReadableSize(Debug.getNativeHeapSize()));			
		}
	}
}
