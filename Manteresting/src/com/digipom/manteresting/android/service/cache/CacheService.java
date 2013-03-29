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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.support.v4.util.LruCache;
import android.util.Log;

import com.digipom.manteresting.android.config.LoggerConfig;
import com.digipom.manteresting.android.service.ILocalBinder;
import com.digipom.manteresting.android.util.NailUtils;

public class CacheService extends Service {
	private static final String TAG = "CacheService";

	private static final int EXECUTOR_CORE_POOL_SIZE = 1;
	private static final int EXECUTOR_MAXIMUM_POOL_SIZE = 5;
	private static final int EXECUTOR_KEEP_ALIVE = 30;

	public static final String CLEAR_CACHE_ACTION = "CLEAR_CACHE_ACTION";

	private final ThreadPoolExecutor loadExecutor = new ThreadPoolExecutor(EXECUTOR_CORE_POOL_SIZE,
			EXECUTOR_MAXIMUM_POOL_SIZE, EXECUTOR_KEEP_ALIVE, TimeUnit.SECONDS, new PriorityBlockingQueue<Runnable>(),
			new ThreadFactory() {
				private final AtomicInteger count = new AtomicInteger(1);

				public Thread newThread(Runnable r) {
					return new Thread(r, TAG + " Task #" + count.getAndIncrement());
				}
			});

	private class LocalBinder extends Binder implements ILocalBinder<CacheService> {
		public CacheService getService() {
			return CacheService.this;
		}
	};

	private final IBinder localBinder = new LocalBinder();
	private final Map<String, BitmapLoadLIFORunnable> pendingRunnables = new HashMap<String, BitmapLoadLIFORunnable>();
	private final AtomicLong fifoTimestamp = new AtomicLong();

	private ImageDownloader imageDownloader;

	// The bitmap cache stores actual decoded bitmaps in memory.
	private BitmapMemoryCache bitmapMemoryCache;

	// The image caches store original image data in a series of caches. The
	// memory cache is the smallest, but the quickest. There's then a cache in
	// the device internal storage, and one on the secondary storage.
	private LruCache<String, ImageWithCategory> imageMemoryCache;
	private ImageFileCache primaryFileCache;
	private ImageFileCache secondaryFileCache;

	@Override
	public void onCreate() {
		if (LoggerConfig.canLog(Log.VERBOSE)) {
			Log.v(TAG, "onCreate()");
		}

		final int memoryClass = tryGetLargeMemoryClass(((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE)));

		imageDownloader = new ImageDownloader();

		// 25% of memory allocated to the bitmap memory cache
		bitmapMemoryCache = new BitmapMemoryCache((memoryClass * 1024 * 1024) / 4);

		// 12% of memory allocated to the image memory cache
		imageMemoryCache = new LruCache<String, ImageWithCategory>((memoryClass * 1024 * 1024) / 8) {
			@Override
			protected int sizeOf(String key, ImageWithCategory value) {
				return value != null && value.image != null ? value.image.length : 0;
			}
		};

		initializePrimaryFileCache();
		initializeSecondaryFileCache();
	}

	private void initializeSecondaryFileCache() {
		// Limit external disk cache to 512 MB of available space.
		try {
			secondaryFileCache = new ImageFileCache(getExternalCacheDir(), 512 * 1024 * 1024);
		} catch (Throwable t) {
			if (LoggerConfig.canLog(Log.WARN)) {
				Log.w(TAG, "Could not open secondary image cache.", t);
			}
		}
	}

	private void initializePrimaryFileCache() {
		// Limit internal disk cache to 32 MB of available space.
		try {
			primaryFileCache = new ImageFileCache(getCacheDir(), 32 * 1024 * 1024);
		} catch (Throwable t) {
			if (LoggerConfig.canLog(Log.WARN)) {
				Log.w(TAG, "Could not open primary image cache.", t);
			}
		}
	}

	private int tryGetLargeMemoryClass(ActivityManager am) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			try {
				final Method getLargeMemoryClass = ActivityManager.class.getMethod("getLargeMemoryClass",
						(Class[]) null);
				final Integer largeMemoryClass = (Integer) getLargeMemoryClass.invoke(am, (Object[]) null);
				return largeMemoryClass;
			} catch (Exception e) {
				if (LoggerConfig.canLog(Log.ERROR)) {
					Log.e(TAG, "Reflection call failed.", e);
				}

				return am.getMemoryClass();
			}
		} else {
			return am.getMemoryClass();
		}
	}

	@Override
	public void onLowMemory() {
		if (LoggerConfig.canLog(Log.VERBOSE)) {
			Log.v(TAG, "onLowMemory()");
		}

		if (bitmapMemoryCache != null) {
			bitmapMemoryCache.evictAll();
		}

		if (imageMemoryCache != null) {
			imageMemoryCache.evictAll();
		}
	}

	@Override
	public void onDestroy() {
		if (LoggerConfig.canLog(Log.VERBOSE)) {
			Log.v(TAG, "onDestroy()");
		}

		synchronized (pendingRunnables) {
			for (Runnable runnable : pendingRunnables.values()) {
				loadExecutor.remove(runnable);
			}

			pendingRunnables.clear();
			loadExecutor.shutdown();
		}

		if (bitmapMemoryCache != null) {
			bitmapMemoryCache.evictAll();
			bitmapMemoryCache = null;
		}

		if (imageMemoryCache != null) {
			imageMemoryCache.evictAll();
			imageMemoryCache = null;
		}

		if (primaryFileCache != null) {
			primaryFileCache.flush();
			primaryFileCache.close();
			primaryFileCache = null;
		}

		if (secondaryFileCache != null) {
			secondaryFileCache.flush();
			secondaryFileCache.close();
			secondaryFileCache = null;
		}

		imageDownloader = null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (LoggerConfig.canLog(Log.VERBOSE)) {
			Log.v(TAG, "onStartCommand(" + intent + ", " + flags + ", " + startId + ")");
		}
		
		if (intent != null && intent.getAction() != null && intent.getAction().equals(CLEAR_CACHE_ACTION)) {
			if (primaryFileCache != null) {
				synchronized(primaryFileCache) {
					primaryFileCache.delete();
					primaryFileCache = null;
					initializePrimaryFileCache();
				}
			}
			
			if (secondaryFileCache != null) {
				synchronized (secondaryFileCache) {
					secondaryFileCache.delete();
					secondaryFileCache = null;
					initializeSecondaryFileCache();
				}				
			}
		}

		return START_NOT_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		if (LoggerConfig.canLog(Log.VERBOSE)) {
			Log.v(TAG, "onBind(" + intent + ")");
		}

		return localBinder;
	}

	/**
	 * Returns the decoded bitmap, if available, or null if not.
	 */
	public Bitmap getOnly(String originalUriFromJson) {
		if (LoggerConfig.canLog(Log.VERBOSE)) {
			Log.v(TAG, "getOnly(" + originalUriFromJson + ")");
		}

		final BitmapWithCategory bitmapWithCategory = bitmapMemoryCache.get(originalUriFromJson);

		if (bitmapWithCategory != null) {
			return bitmapWithCategory.bitmap;
		} else {
			return null;
		}
	}

	/**
	 * Loads the bitmap only, to ensure it's cached for when we request it.
	 * These requests will be done at a lower priority, and in FIFO order. The
	 * width is used to determine the minimum quality required.
	 */
	public void loadOnlyLowerPriorityFifoAsync(String originalUriFromJson, int requestedWidth) {
		if (LoggerConfig.canLog(Log.VERBOSE)) {
			Log.v(TAG, "loadOnlyLowerPriorityFifoAsync(" + originalUriFromJson + ", " + requestedWidth + ")");
		}

		// Since the runnable itself is based on a LIFO queue, then putting a
		// decreasing timestamp for subsequent requests turns it into a FIFO
		// queue, and since the timestamp is so small, all of these requests
		// have a lower priority than the LIFO request.
		if (bitmapMemoryCache != null && !isCachedBitmapAdequateForWidth(originalUriFromJson, requestedWidth)) {
			queueOrUpdateRunnable(originalUriFromJson, requestedWidth, null, fifoTimestamp.getAndDecrement());
		}
	}

	private boolean isCachedBitmapAdequateForWidth(String originalUriFromJson, int requestedWidth) {
		if (bitmapMemoryCache != null) {
			final BitmapWithCategory bitmapWithCategory = bitmapMemoryCache.get(originalUriFromJson);

			if (bitmapWithCategory == null) {
				return false;
			} else {
				return bitmapWithCategory.category.isCategoryAdequateForWidth(requestedWidth);
			}
		} else {
			return false;
		}
	}

	/**
	 * Loads the bitmap only, to ensure it's cached for when we request it.
	 * These requests will be done at normal priority, in LIFO order. The width
	 * is used to determine the minimum quality required.
	 */
	public void loadOnlyLifoAsync(String originalUriFromJson, int requestedWidth) {
		if (LoggerConfig.canLog(Log.VERBOSE)) {
			Log.v(TAG, "loadOnlyLifoAsync(" + originalUriFromJson + ", " + requestedWidth + ")");
		}

		if (bitmapMemoryCache != null && !isCachedBitmapAdequateForWidth(originalUriFromJson, requestedWidth)) {
			queueOrUpdateRunnable(originalUriFromJson, requestedWidth, null, System.nanoTime());
		}
	}

	/**
	 * Returns the decoded bitmap, if available. If not, returns null and queues
	 * a load request. The bitmap will be decoded from one of the image caches
	 * or downloaded, if necessary. Once complete, the callback will be called
	 * with the status of the load. The width is used to determine the minimum
	 * quality required. This method could return a bitmap of the best quality
	 * found, and still call the callback if a higher quality bitmap is needed.
	 * 
	 */
	public Bitmap getOrLoadLifoAsync(String originalUriFromJson, int requestedWidth,
			BitmapLoadCallback bitmapLoadCallback) {
		if (LoggerConfig.canLog(Log.VERBOSE)) {
			Log.v(TAG, "getOrLoadLifoAsync(" + originalUriFromJson + ", " + requestedWidth + ", " + bitmapLoadCallback
					+ ")");
		}

		Bitmap cachedBitmap = null;

		if (bitmapMemoryCache != null) {
			final BitmapWithCategory bitmapWithCategory = bitmapMemoryCache.get(originalUriFromJson);
			cachedBitmap = bitmapWithCategory != null ? bitmapWithCategory.bitmap : null;

			if (!isCachedBitmapAdequateForWidth(originalUriFromJson, requestedWidth)) {
				// We'll need to load the bitmap asynchronously.
				queueOrUpdateRunnable(originalUriFromJson, requestedWidth, bitmapLoadCallback, System.nanoTime());
			} else {
				if (LoggerConfig.canLog(Log.VERBOSE)) {
					Log.v(TAG, "URI " + originalUriFromJson + " already cached.");
				}
			}
		}

		return cachedBitmap;
	}

	private void queueOrUpdateRunnable(String originalUriFromJson, int requestedWidth,
			BitmapLoadCallback bitmapLoadCallback, long requestedTimeStamp) {
		synchronized (pendingRunnables) {
			// If there's already a load in progress, piggyback onto
			// that load. Otherwise, create a new load request.
			BitmapLoadLIFORunnable runnableForUri = pendingRunnables.get(originalUriFromJson);

			if (runnableForUri == null) {
				if (LoggerConfig.canLog(Log.VERBOSE)) {
					Log.v(TAG, "Queuing load: " + originalUriFromJson);
				}

				runnableForUri = new BitmapLoadLIFORunnable(originalUriFromJson);
				runnableForUri.requestedWidth = requestedWidth;
				runnableForUri.timeStamp = requestedTimeStamp;

				if (bitmapLoadCallback != null) {
					runnableForUri.appendCallback(bitmapLoadCallback);
				}

				pendingRunnables.put(originalUriFromJson, runnableForUri);
				loadExecutor.execute(runnableForUri);
			} else {
				if (LoggerConfig.canLog(Log.VERBOSE)) {
					Log.v(TAG, "Moving to front of queue:" + originalUriFromJson);
				}

				runnableForUri.requestedWidth = requestedWidth;
				runnableForUri.timeStamp = requestedTimeStamp;

				if (bitmapLoadCallback != null) {
					runnableForUri.appendCallback(bitmapLoadCallback);
				}
			}
		}
	}

	final AtomicInteger runnableCount = new AtomicInteger(1);

	class BitmapLoadLIFORunnable implements Runnable, Comparable<BitmapLoadLIFORunnable> {
		private final String tag = "BitmapLoadLIFORunnable #" + runnableCount.getAndIncrement();
		private final String originalUriFromJson;

		// Since this is a weak hash map, entries will be automatically removed
		// from the map upon garbage collection. They shouldn't even show up in
		// the map as a null reference.
		private final Set<BitmapLoadCallback> callbacks = Collections
				.newSetFromMap(new WeakHashMap<BitmapLoadCallback, Boolean>());

		volatile int requestedWidth;
		volatile long timeStamp;

		BitmapLoadLIFORunnable(String originalUriFromJson) {
			this.originalUriFromJson = originalUriFromJson;
		}

		void appendCallback(BitmapLoadCallback bitmapLoadCallback) {
			synchronized (callbacks) {
				callbacks.add(bitmapLoadCallback);
			}
		}

		/** LIFO sort ordering. */
		@Override
		public int compareTo(BitmapLoadLIFORunnable another) {
			if (timeStamp > another.timeStamp) {
				return -1;
			} else if (timeStamp == another.timeStamp) {
				return 0;
			} else {
				return 1;
			}
		}

		@Override
		public void run() {
			Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

			if (LoggerConfig.canLog(Log.VERBOSE)) {
				Log.v(tag, "Beginning load: " + originalUriFromJson);
			}

			boolean status = false;

			try {
				if (bitmapMemoryCache == null) {
					if (LoggerConfig.canLog(Log.DEBUG)) {
						Log.d(tag, "Bitmap memory cache is null. Aborting bitmap load...");
					}
				} else {
					BitmapWithCategory existingBitmapWithCategory = bitmapMemoryCache.get(originalUriFromJson);

					if (existingBitmapWithCategory != null
							&& existingBitmapWithCategory.category.isCategoryAdequateForWidth(requestedWidth)) {
						if (LoggerConfig.canLog(Log.VERBOSE)) {
							Log.v(tag, "URI " + originalUriFromJson + " already loaded");
						}

						status = true; // No need to load twice.
					} else {
						// Check if it's in the image memory cache or any of the
						// file caches. If not, we'll need to download it.
						ImageWithCategory imageWithCategory = null;
						boolean syncDownloadToImageCaches = false;

						if (imageWithCategory == null && imageMemoryCache != null) {
							if (LoggerConfig.canLog(Log.VERBOSE)) {
								Log.v(tag, "Attempting to fetch URI " + originalUriFromJson
										+ " from image memory cache...");
							}

							imageWithCategory = imageMemoryCache.get(originalUriFromJson);
						}

						if (imageWithCategory == null && primaryFileCache != null) {
							if (LoggerConfig.canLog(Log.VERBOSE)) {
								Log.v(tag, "Attempting to fetch URI " + originalUriFromJson
										+ " from primary file cache...");
							}

							imageWithCategory = primaryFileCache.get(originalUriFromJson);
						}

						if (imageWithCategory == null && secondaryFileCache != null) {
							if (LoggerConfig.canLog(Log.VERBOSE)) {
								Log.v(tag, "Attempting to fetch URI " + originalUriFromJson
										+ " from secondary file cache...");
							}

							imageWithCategory = secondaryFileCache.get(originalUriFromJson);
						}

						// Even if the image was cached, we also have to check
						// if it's adequate for the requested width.
						if ((imageWithCategory == null || !imageWithCategory.category
								.isCategoryAdequateForWidth(requestedWidth)) && imageDownloader != null) {
							final Category appropriateCategory = Category.getAdequateCategoryForWidth(requestedWidth);

							final String uriToDownload;

							switch (appropriateCategory) {
								case THUMB:
									uriToDownload = NailUtils.getThumbImageUri(originalUriFromJson);
									break;
								case SMALL:
									uriToDownload = NailUtils.getSmallImageUri(originalUriFromJson);
									break;
								case NORMAL:
								default:
									uriToDownload = NailUtils.getNormalImageUri(originalUriFromJson);
									break;
							}

							if (LoggerConfig.canLog(Log.VERBOSE)) {
								Log.v(tag, "Attempting to download URI " + uriToDownload + " for original URI "
										+ originalUriFromJson + " ...");
							}

							final byte[] imageData = imageDownloader.download(uriToDownload);

							if (imageData != null) {
								imageWithCategory = new ImageWithCategory(appropriateCategory, imageData);
								syncDownloadToImageCaches = true;
							}
						}

						if (imageWithCategory == null) {
							if (LoggerConfig.canLog(Log.DEBUG)) {
								Log.d(tag, "Could not load image data for URI " + originalUriFromJson);
							}
						} else {
							final BitmapFactory.Options options = new BitmapFactory.Options();

							// Don't let any dimension be greater than 2048
							// pixels;
							options.inJustDecodeBounds = true;
							BitmapFactory.decodeByteArray(imageWithCategory.image, 0, imageWithCategory.image.length,
									options);
							final int largestDimension = Math.max(options.outWidth, options.outHeight);
							final int sampleSize = (largestDimension / 2048) + 1;
							options.inJustDecodeBounds = false;
							options.inSampleSize = sampleSize;
							Bitmap bitmap = BitmapFactory.decodeByteArray(imageWithCategory.image, 0,
									imageWithCategory.image.length, options);

							if (bitmap == null) {
								if (LoggerConfig.canLog(Log.WARN)) {
									Log.w(tag, "URI " + originalUriFromJson + " could not be decoded.");
								}
							} else {
								bitmapMemoryCache.put(originalUriFromJson, new BitmapWithCategory(
										imageWithCategory.category, bitmap));

								// Always post to the memory cache, even if this
								// wasn't a new download. That way, the next
								// time we don't have to hit the file system.
								if (imageMemoryCache != null) {
									imageMemoryCache.put(originalUriFromJson, imageWithCategory);
								}

								if (syncDownloadToImageCaches) {
									if (LoggerConfig.canLog(Log.VERBOSE)) {
										Log.v(tag, "URI " + originalUriFromJson
												+ " syncing downloaded file to file caches.");
									}

									if (primaryFileCache != null) {
										primaryFileCache.store(originalUriFromJson, imageWithCategory);
									}

									if (secondaryFileCache != null) {
										secondaryFileCache.store(originalUriFromJson, imageWithCategory);
									}
								}

								status = true;

								if (LoggerConfig.canLog(Log.VERBOSE)) {
									Log.v(tag, "URI " + originalUriFromJson + " decoded and cached in memory.");
								}
							}
						}
					}
				}
			} catch (Throwable t) {
				if (LoggerConfig.canLog(Log.WARN)) {
					Log.w(tag, "Error loading image URI " + originalUriFromJson, t);
				}
			}

			// If the load failed, ensure we're not keeping bad data in the
			// caches.
			if (status == false) {
				flushCachesForUri();
			}

			doCallbacks(originalUriFromJson, status);
		}

		private void flushCachesForUri() {
			if (LoggerConfig.canLog(Log.VERBOSE)) {
				Log.v(tag, "Flushing cache for uri URI " + originalUriFromJson);
			}

			if (bitmapMemoryCache != null) {
				bitmapMemoryCache.remove(originalUriFromJson);
			}

			if (imageMemoryCache != null) {
				imageMemoryCache.remove(originalUriFromJson);
			}

			if (primaryFileCache != null) {
				primaryFileCache.remove(originalUriFromJson);
			}

			if (secondaryFileCache != null) {
				secondaryFileCache.remove(originalUriFromJson);
			}
		}

		private void doCallbacks(final String uri, final boolean status) {
			synchronized (pendingRunnables) {
				pendingRunnables.remove(uri);
			}

			// Service should not add any more callbacks at this point, since
			// we've been removed from the set of pending runnables.
			synchronized (callbacks) {
				for (final BitmapLoadCallback bitmapLoadCallback : callbacks) {
					if (bitmapLoadCallback != null) {
						bitmapLoadCallback.send(uri, status);
					} else {
						if (LoggerConfig.canLog(Log.VERBOSE)) {
							Log.v(tag, "Download callback garbage collected.");
						}
					}
				}
			}
		}
	}
}
