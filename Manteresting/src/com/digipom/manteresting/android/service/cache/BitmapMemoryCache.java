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

import java.lang.reflect.Method;

import android.graphics.Bitmap;
import android.os.Build;
import android.support.v4.util.LruCache;
import android.util.Log;

import com.digipom.manteresting.android.config.LoggerConfig;

class BitmapMemoryCache extends LruCache<String, BitmapWithCategory> {
	private static final String TAG = "BitmapMemoryCache";
	
	private static final Method getByteCount;
	
	static {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
			Method tryGetByteCount = null;
			
			try {
				tryGetByteCount = Bitmap.class.getMethod("getByteCount", (Class[]) null);
			} catch (Exception e) {
				if (LoggerConfig.canLog(Log.ERROR)) {
					Log.e(TAG, "Could not get reflected method for getByteCount", e);
				}
			}
			
			getByteCount = tryGetByteCount;
		} else {
			getByteCount = null;
		}
	}

	BitmapMemoryCache(int maxSizeInBytes) {
		super(maxSizeInBytes);
	}

	@Override
	protected void entryRemoved(boolean evicted, String key, BitmapWithCategory oldValue, BitmapWithCategory newValue) {
		if (LoggerConfig.canLog(Log.VERBOSE)) {
			Log.v(TAG, "Removing entry: " + key);
		}

		super.entryRemoved(evicted, key, oldValue, newValue);
		oldValue.bitmap.recycle();
	}

	@Override
	protected int sizeOf(String key, BitmapWithCategory value) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
			return getByteCountHoneyCombMr1(value.bitmap);
		} else {
			return value.bitmap.getRowBytes() * value.bitmap.getHeight();
		}
	}

	private static final int getByteCountHoneyCombMr1(Bitmap bitmap) {
		try {			
			final Integer byteCount = (Integer) getByteCount.invoke(bitmap, (Object[]) null);
			return byteCount;
		} catch (Exception e) {
			if (LoggerConfig.canLog(Log.ERROR)) {
				Log.e(TAG, "Reflection call failed.", e);
			}
			
			return -1;
		}
	}
}
