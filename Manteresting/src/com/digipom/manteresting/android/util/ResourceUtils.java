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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import android.content.Context;
import android.text.Html;

public abstract class ResourceUtils {
	public static CharSequence getTextFromResource(final Context context, final int resourceId) {
		try {
			final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(context.getResources()
					.openRawResource(resourceId)));
			final char[] charBuffer = new char[4096];
			final StringBuilder stringBuilder = new StringBuilder();
			int read;

			while ((read = bufferedReader.read(charBuffer)) > 0) {
				stringBuilder.append(charBuffer, 0, read);
			}

			bufferedReader.close();

			final CharSequence ret = Html.fromHtml(stringBuilder.toString().replace("\n", "<br />"));
			return ret;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
