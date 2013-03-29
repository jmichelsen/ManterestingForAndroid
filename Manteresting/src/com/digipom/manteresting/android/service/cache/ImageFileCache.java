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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.util.Log;

import com.digipom.manteresting.android.config.LoggerConfig;
import com.digipom.manteresting.android.service.cache.DiskLruCache.Editor;
import com.digipom.manteresting.android.service.cache.DiskLruCache.Snapshot;

/**
 * A wrapper class for a file-based image cache. The methods of the class are
 * synchronized, so that only one thread can interact with the file system at a
 * given time.
 */
class ImageFileCache {
	private static final int CACHE_VERSION = 1;
	private static final int VALUE_COUNT = 1;
	
	private final String tag;
	private final DiskLruCache diskLruCache;

	private MessageDigest md5MessageDigest;

	ImageFileCache(File directory, long maxSize) throws IOException {
		diskLruCache = DiskLruCache.open(directory, CACHE_VERSION, VALUE_COUNT, maxSize);
		tag = "ImageFileCache:" + directory;			
	}

	synchronized void store(String uri, final ImageWithCategory imageWithCategory) {
		try {
			final Editor editor = diskLruCache.edit(hashUriForStorage(uri));			

			if (editor == null) {
				if (LoggerConfig.canLog(Log.WARN)) {
					Log.w(tag, "Could not get editor to store image data " + imageWithCategory + " for uri " + uri);
				}
			} else {
				DataOutputStream daos = null;

				try {
					daos = new DataOutputStream(editor.newOutputStream(0));
					daos.writeInt(imageWithCategory.category.ordinal());
					daos.write(imageWithCategory.image);
					daos.flush();
					daos.close();									
					daos = null;
					editor.commit();
				} catch (Throwable t) {
					editor.abort();
					throw t;
				} finally {
					if (daos != null) {
						daos.close();
					}
				}
			}
		} catch (Throwable t) {
			if (LoggerConfig.canLog(Log.WARN)) {
				Log.w(tag, "Could not store image data " + imageWithCategory + " for uri " + uri, t);
			}
		}
	}

	/**
	 * Create a hashed filename for the cache storage. This is needed since
	 * otherwise it will use the path characters and everything to try and cache
	 * the filename. An alternative could be also to encode the uri.
	 * 
	 * @return
	 */
	private String hashUriForStorage(String uri) {
		try {
			if (md5MessageDigest == null) {
				md5MessageDigest = MessageDigest.getInstance("MD5"); 
			}	
			
			md5MessageDigest.update(uri.getBytes("UTF-8"));
			return new BigInteger(1, md5MessageDigest.digest()).toString(16);
		} catch (NoSuchAlgorithmException e) {
			return String.valueOf(uri.hashCode());
		} catch (UnsupportedEncodingException e) {
			return String.valueOf(uri.hashCode());
		}		
	}

	synchronized ImageWithCategory get(final String uri) {
		ImageWithCategory imageWithCategory = null;
		Snapshot snapshot = null;

		try {
			snapshot = diskLruCache.get(hashUriForStorage(uri));

			if (snapshot == null) {
				if (LoggerConfig.canLog(Log.VERBOSE)) {
					Log.v(tag, "Key " + hashUriForStorage(uri) + " for uri " + uri + " not found in cache.");
				}
			} else {
				DataInputStream dais = null;

				try {															
					dais = new DataInputStream(snapshot.getInputStream(0));
					final int ordinal = dais.readInt();									
					final ByteArrayOutputStream baos = new ByteArrayOutputStream();
					final byte[] buffer = new byte[4096];

					int read;
					while ((read = dais.read(buffer)) > 0) {
						baos.write(buffer, 0, read);
					}					
					
					imageWithCategory = new ImageWithCategory(Category.values()[ordinal], baos.toByteArray());
				} finally {
					if (dais != null) {
						dais.close();
						dais = null;
					}
				}
			}
		} catch (Throwable t) {
			if (LoggerConfig.canLog(Log.WARN)) {
				Log.w(tag, "Could not get image data for uri " + uri, t);
			}
		} finally {
			if (snapshot != null) {
				snapshot.close();
				snapshot = null;
			}
		}

		return imageWithCategory;
	}

	synchronized void remove(final String uri) {
		try {
			diskLruCache.remove(hashUriForStorage(uri));
		} catch (Throwable t) {
			if (LoggerConfig.canLog(Log.WARN)) {
				Log.w(tag, "Could not remove uri " + uri, t);
			}
		}
	}

	synchronized void flush() {
		try {
			diskLruCache.flush();
		} catch (Throwable t) {
			if (LoggerConfig.canLog(Log.ERROR)) {
				Log.e(tag, "Could not flush", t);
			}
		}

	}

	synchronized void close() {
		try {
			diskLruCache.close();
		} catch (Throwable t) {
			if (LoggerConfig.canLog(Log.ERROR)) {
				Log.e(tag, "Could not close", t);
			}
		}
	}
	
	synchronized void delete() {
		try {
			diskLruCache.delete();
		} catch (Throwable t) {
			if (LoggerConfig.canLog(Log.ERROR)) {
				Log.e(tag, "Could not delete", t);
			}
		}
	}
}
