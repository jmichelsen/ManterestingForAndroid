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
package com.digipom.manteresting.android.util;

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.content.Context;
import android.os.Debug;
import android.util.Log;

import com.digipom.manteresting.android.config.LoggerConfig;

public abstract class LoggerUtils {

	public static void logReadableMemoryStats(String tag, Context context) {
		if (LoggerConfig.canLog(Log.VERBOSE)) {
			final StringBuilder builder = new StringBuilder();
			final Runtime rt = Runtime.getRuntime();

			builder.append("VM Memory (free/total/max): ").append(StringUtils.getReadableSize(rt.freeMemory()))
					.append('/').append(StringUtils.getReadableSize(rt.totalMemory())).append('/')
					.append(StringUtils.getReadableSize(rt.maxMemory()));

			Log.v(tag, builder.toString());
			builder.setLength(0);

			builder.append("Native memory (free/total/max): ")
					.append(StringUtils.getReadableSize(Debug.getNativeHeapFreeSize())).append('/')
					.append(StringUtils.getReadableSize(Debug.getNativeHeapAllocatedSize())).append('/')
					.append(StringUtils.getReadableSize(Debug.getNativeHeapSize()));

			Log.v(tag, builder.toString());
			builder.setLength(0);

			final MemoryInfo memoryInfo = new MemoryInfo();
			((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memoryInfo);

			builder.append("System memory (available/low memory threshold)")
					.append(StringUtils.getReadableSize(memoryInfo.availMem)).append('/')
					.append(StringUtils.getReadableSize(memoryInfo.threshold));

			Log.v(tag, builder.toString());
		}
	}
}
