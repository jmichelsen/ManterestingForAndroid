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
package com.digipom.manteresting.android.activity;

import java.io.File;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

import com.digipom.manteresting.android.R;
import com.digipom.manteresting.android.service.cache.CacheService;
import com.digipom.manteresting.android.util.FileUtils;

public class SettingsActivity extends PreferenceActivity {
	private static final int CLEAR_CACHE_DIALOG = 1;

	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.settings);

		long cacheSizes = 0;
		final File cacheDir = getCacheDir();

		if (cacheDir != null) {
			cacheSizes += FileUtils.getSizeOfDirectory(cacheDir);
		}

		final File externalCacheDir = getExternalCacheDir();

		if (externalCacheDir != null) {
			cacheSizes += FileUtils.getSizeOfDirectory(externalCacheDir);
		}

		findPreference(getText(R.string.clearCachePreferenceKey)).setSummary(FileUtils.getReadableFileSize(cacheSizes));
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
		if (preference != null && preference.getKey().equals(getText(R.string.clearCachePreferenceKey))) {
			showDialog(CLEAR_CACHE_DIALOG);
			return true;
		} else {
			return super.onPreferenceTreeClick(preferenceScreen, preference);
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	protected Dialog onCreateDialog(int id) {
		if (id == CLEAR_CACHE_DIALOG) {
			return new AlertDialog.Builder(this).setMessage(R.string.clearCacheDialogMessage).setCancelable(true)
					.setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.OK, new OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							final Intent intent = new Intent(SettingsActivity.this, CacheService.class);
							intent.setAction(CacheService.CLEAR_CACHE_ACTION);
							startService(intent);
							findPreference(getText(R.string.clearCachePreferenceKey)).setSummary(FileUtils.getReadableFileSize(0));
						}
					}).create();
		} else {
			return super.onCreateDialog(id);
		}
	}
}
