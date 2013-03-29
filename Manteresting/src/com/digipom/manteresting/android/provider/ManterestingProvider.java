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

import static com.digipom.manteresting.android.provider.ManterestingContract.CONTENT_AUTHORITY;
import static com.digipom.manteresting.android.provider.ManterestingContract.PATH_NAILS;

import java.io.FileNotFoundException;
import java.util.Arrays;

import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQuery;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.digipom.manteresting.android.config.LoggerConfig;
import com.digipom.manteresting.android.provider.DatabaseHelper.Tables;
import com.digipom.manteresting.android.provider.ManterestingContract.Nails;
import com.digipom.manteresting.android.util.SelectionBuilder;

public class ManterestingProvider extends SQLiteContentProvider {
	private static final String TAG = "ManterestingProvider";
	private static final UriMatcher URI_MATCHER = buildUriMatcher();

	private static final int CODE_NAILS = 100;

	private DatabaseHelper databaseHelper;

	private static UriMatcher buildUriMatcher() {
		final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);

		matcher.addURI(CONTENT_AUTHORITY, PATH_NAILS, CODE_NAILS);

		return matcher;
	}

	@Override
	public boolean onCreate() {
		super.onCreate();

		return true;
	}

	@Override
	public SQLiteOpenHelper getDatabaseHelper(Context context) {
		if (databaseHelper == null) {
			if (LoggerConfig.canLog(Log.DEBUG)) {
				class LoggingCursorFactory implements CursorFactory {

					class LoggedCursor extends SQLiteCursor {
						@SuppressWarnings("deprecation")
						public LoggedCursor(SQLiteDatabase db, SQLiteCursorDriver driver, String editTable,
								SQLiteQuery query) {
							super(db, driver, editTable, query);
						}

						public LoggedCursor(SQLiteCursorDriver driver, String editTable, SQLiteQuery query) {
							super(driver, editTable, query);
						}

						@Override
						public void close() {
							Log.d(TAG, "Cursor closed trace:");

							for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
								Log.d(TAG, stackTraceElement.toString());
							}

							super.close();
						}
					}

					@Override
					public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery, String editTable,
							SQLiteQuery query) {
						return new LoggedCursor(db, masterQuery, editTable, query);
					}
				}

				databaseHelper = new DatabaseHelper(getContext(), new LoggingCursorFactory());
			} else {
				databaseHelper = new DatabaseHelper(getContext());
			}
		}

		return databaseHelper;
	}

	@Override
	public String getType(Uri uri) {
		final int match = URI_MATCHER.match(uri);
		switch (match) {
			case CODE_NAILS:
				return Nails.CONTENT_TYPE_DIR;
			default:
				throw new IllegalArgumentException("Unknown uri: " + uri);
		}
	}

	@Override
	public Uri insertInTransaction(Uri uri, ContentValues values, boolean callerIsSyncAdapter) {
		if (LoggerConfig.canLog(Log.VERBOSE)) {
			Log.v(TAG, "insertInTransaction(" + uri + ", " + values + ", " + callerIsSyncAdapter + ")");
		}

		final int match = URI_MATCHER.match(uri);
		switch (match) {
			case CODE_NAILS: {
				mDb.insertOrThrow(Tables.NAILS, null, values);
				postNotifyUri(uri);
				return Nails.buildNailUri(values.getAsInteger(Nails.NAIL_ID));
			}
			default: {
				throw new IllegalArgumentException("Unknown uri: " + uri);
			}
		}
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		if (LoggerConfig.canLog(Log.VERBOSE)) {
			Log.v(TAG, "query(" + uri + ", " + Arrays.toString(projection) + ", " + Arrays.toString(selectionArgs)
					+ ", " + sortOrder + ")");
		}

		final SQLiteDatabase db = databaseHelper.getReadableDatabase();
		final SelectionBuilder builder = buildExpandedSelection(uri);

		final Cursor cursor = builder.where(selection, selectionArgs).query(db, projection, sortOrder);
		cursor.setNotificationUri(getContext().getContentResolver(), uri);
		return cursor;
	}

	@Override
	public int updateInTransaction(Uri uri, ContentValues values, String selection, String[] selectionArgs,
			boolean callerIsSyncAdapter) {
		if (LoggerConfig.canLog(Log.VERBOSE)) {
			Log.v(TAG,
					"updateInTransaction(" + uri + ", " + values + ", " + selection + ", "
							+ Arrays.toString(selectionArgs) + ", " + callerIsSyncAdapter + ")");
		}

		final SelectionBuilder builder = buildSimpleSelection(uri);
		final int count = builder.where(selection, selectionArgs).update(mDb, values);

		postNotifyUri(uri);
		return count;
	}

	@Override
	public int deleteInTransaction(Uri uri, String selection, String[] selectionArgs, boolean callerIsSyncAdapter) {
		if (LoggerConfig.canLog(Log.VERBOSE)) {
			Log.v(TAG, "deleteInTransaction(" + uri + ", " + selection + ", " + Arrays.toString(selectionArgs) + ", "
					+ callerIsSyncAdapter + ")");
		}

		final SelectionBuilder builder = buildSimpleSelection(uri);
		final int count = builder.where(selection, selectionArgs).delete(mDb);
		postNotifyUri(uri);
		return count;
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
		throw new UnsupportedOperationException("not supported");
	}

	private SelectionBuilder buildSimpleSelection(Uri uri) {
		final SelectionBuilder builder = new SelectionBuilder();
		final int match = URI_MATCHER.match(uri);
		switch (match) {
			case CODE_NAILS: {
				return builder.table(Tables.NAILS);
			}
			default: {
				throw new IllegalArgumentException("Unknown uri: " + uri);
			}
		}
	}

	private SelectionBuilder buildExpandedSelection(Uri uri) {
		final SelectionBuilder builder = new SelectionBuilder();
		final int match = URI_MATCHER.match(uri);
		switch (match) {
			case CODE_NAILS: {
				return builder.table(Tables.NAILS);
			}
			default: {
				throw new IllegalArgumentException("Unknown uri: " + uri);
			}
		}
	}

}
