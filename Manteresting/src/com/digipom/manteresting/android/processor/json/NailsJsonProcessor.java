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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.database.Cursor;
import android.util.Log;

import com.digipom.manteresting.android.config.LoggerConfig;
import com.digipom.manteresting.android.processor.Meta;
import com.digipom.manteresting.android.provider.ManterestingContract;
import com.digipom.manteresting.android.provider.ManterestingContract.Nails;
import com.digipom.manteresting.android.service.rest.RestMethod;
import com.digipom.manteresting.android.util.Lists;
import com.digipom.manteresting.android.util.SelectionBuilder;

public class NailsJsonProcessor extends JsonProcessor {
	private static final int MAX_COUNT = 500;
	private static final String TAG = "NailsJsonProcessor";

	public NailsJsonProcessor(ContentResolver resolver, RestMethod method) {
		super(resolver, method);
	}

	@Override
	public ArrayList<ContentProviderOperation> parse(JSONObject response, Meta meta) throws JSONException {
		final ArrayList<ContentProviderOperation> batch = Lists.newArrayList();
		final TreeSet<Integer> nailIds = new TreeSet<Integer>();
		final Cursor nails = resolver.query(ManterestingContract.Nails.CONTENT_URI, new String[] { Nails.NAIL_ID },
				null, null, Nails.NAIL_ID + " DESC");
		int greatestOfExisting = Integer.MIN_VALUE;

		if (nails != null && !nails.isClosed()) {
			try {
				nails.moveToFirst();

				final int idColumn = nails.getColumnIndex(Nails.NAIL_ID);

				while (!nails.isAfterLast()) {
					final int nailId = nails.getInt(idColumn);
					nailIds.add(nailId);
					greatestOfExisting = nailId > greatestOfExisting ? nailId : greatestOfExisting;
					nails.moveToNext();
				}
			} finally {
				if (nails != null) {
					nails.close();
				}
			}
		}

		final JSONArray objects = response.getJSONArray("objects");
		int smallestOfNew = Integer.MAX_VALUE;

		for (int i = 0; i < objects.length(); i++) {
			final JSONObject nailObject = objects.getJSONObject(i);

			final boolean isPrivate = nailObject.getJSONObject("workbench").getBoolean("private");

			if (!isPrivate) {
				final ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(Nails.CONTENT_URI);
				final int nailId = nailObject.getInt("id");
				smallestOfNew = nailId < smallestOfNew ? nailId : smallestOfNew;

				builder.withValue(Nails.NAIL_ID, nailId);
				builder.withValue(Nails.NAIL_JSON, nailObject.toString());

				batch.add(builder.build());
				nailIds.add(nailId);
			}
		}

		// If more than LIMIT were fetched, and this was the initial fetch, then
		// we flush everything in the DB before adding the new nails (as
		// otherwise we would introduce a gap).
		if (meta.nextOffset == meta.nextLimit // For initial fetch
				&& smallestOfNew > greatestOfExisting) {
			if (LoggerConfig.canLog(Log.DEBUG)) {
				Log.d(TAG, "Flushing all existing nails on initial fetch, so as to avoid a gap.");
			}
			
			resolver.delete(Nails.CONTENT_URI, null, null);
		} else {
			// If more than 500 nails, find the 500th biggest and delete those
			// after it.
			if (nailIds.size() > MAX_COUNT) {
				Iterator<Integer> it = nailIds.descendingIterator();

				for (int i = 0; i < MAX_COUNT; i++) {
					it.next();
				}

				final Integer toDelete = it.next();

				if (LoggerConfig.canLog(Log.DEBUG)) {
					Log.d(TAG, "deleting from nails where NAIL_ID is less than or equal to " + toDelete);
				}

				SelectionBuilder selectionBuilder = new SelectionBuilder();
				selectionBuilder.where(ManterestingContract.Nails.NAIL_ID + " <= ?",
						new String[] { String.valueOf(toDelete) });
				resolver.delete(ManterestingContract.Nails.CONTENT_URI, selectionBuilder.getSelection(),
						selectionBuilder.getSelectionArgs());
			}
		}

		return batch;
	}
}
