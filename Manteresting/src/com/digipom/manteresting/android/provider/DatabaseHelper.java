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

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import com.digipom.manteresting.android.config.LoggerConfig;
import com.digipom.manteresting.android.provider.ManterestingContract.NailsColumns;

public class DatabaseHelper extends SQLiteOpenHelper {
	private static final String TAG = "DatabaseHelper";
	private static final int DB_VERSION_ON_LAUNCH = 1;
	public static final String DB_NAME = "manteresting.db";
	public static final int DB_VERSION = DB_VERSION_ON_LAUNCH;

	interface Tables {
		String NAILS = "nails";
	}	

	public DatabaseHelper(final Context context) {
		super(context, DB_NAME, null, DB_VERSION);
	}
	
	public DatabaseHelper(final Context context, final CursorFactory cursorFactory) {
		super(context, DB_NAME, cursorFactory, DB_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		if (LoggerConfig.canLog(Log.DEBUG)) {
			Log.d(TAG, "onCreate");
		}

		db.execSQL("CREATE TABLE " + Tables.NAILS + " (" + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
				+ NailsColumns.NAIL_ID + " INTEGER NOT NULL," + NailsColumns.NAIL_JSON + " TEXT," + "UNIQUE ("
				+ NailsColumns.NAIL_ID + ") ON CONFLICT REPLACE)");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (LoggerConfig.canLog(Log.INFO)) {
			Log.i(TAG, "onCreate");
		}

		int currentVersion = oldVersion;

		try {
			switch (currentVersion) {
			// No-op
			}

			if (LoggerConfig.canLog(Log.DEBUG)) {
				Log.d(TAG, "Upgraded database to version " + currentVersion);
			}

			if (currentVersion != DB_VERSION) {
				// If the upgrade switch didn't finish, drop everything and
				// recreate.
				dropTablesAndRecreate(db);
			}
		} catch (SQLException e) {
			// Something went horribly wrong, try to flush the db and recreate
			// the tables.
			dropTablesAndRecreate(db);
		}
	}

	private void dropTablesAndRecreate(SQLiteDatabase db) {
		db.execSQL("DROP TABLE IF EXISTS " + Tables.NAILS);

		onCreate(db);
	}
}
