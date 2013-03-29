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
package com.digipom.manteresting.android.provider;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.BaseColumns;

public class ManterestingContract {
	public static final String CONTENT_AUTHORITY = "com.manteresting.provider";
	private static final Uri BASE_CONTENT_URI = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
			.authority(CONTENT_AUTHORITY).build();
	private static final String MIME_TYPE_PREFIX = "vnd." + CONTENT_AUTHORITY;

	static final String PATH_NAILS = "nails";

	interface NailsColumns {
		String NAIL_ID = "nail_id";
		String NAIL_JSON = "nail_json";
	}

	public static class Nails implements NailsColumns, BaseColumns {
		private static final String MIME_TYPE = MIME_TYPE_PREFIX + "." + PATH_NAILS;
		public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon().appendPath(PATH_NAILS).build();
		public static final String CONTENT_TYPE_DIR = ContentResolver.CURSOR_DIR_BASE_TYPE + '/' + MIME_TYPE;
		public static final String CONTENT_TYPE_ITEM = ContentResolver.CURSOR_ITEM_BASE_TYPE + '/' + MIME_TYPE;

		public static Uri buildNailUri(int nailId) {
			return CONTENT_URI.buildUpon().appendPath(String.valueOf(nailId)).build();
		}
	}

	private ManterestingContract() {
	}
}
