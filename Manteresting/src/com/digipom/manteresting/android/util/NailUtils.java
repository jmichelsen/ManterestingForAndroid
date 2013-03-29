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

import static com.digipom.manteresting.android.config.AppConfig.MANTERESTING_SERVER;

import java.util.List;

import android.net.Uri;

/**
 * Given an original image URI, this class will transform that into various
 * cached short,
 */
public class NailUtils {	
	public static String getNormalImageUri(String originalPathFromJson) {
		return rewriteAndAddCachedPath(originalPathFromJson, "normal").toString();
	}

	public static String getSmallImageUri(String originalPathFromJson) {
		return rewriteAndAddCachedPath(originalPathFromJson, "small").toString();
	}

	public static String getThumbImageUri(String originalPathFromJson) {
		return rewriteAndAddCachedPath(originalPathFromJson, "thumb").toString();
	}

	private static final Uri rewriteAndAddCachedPath(String originalPathFromJson, String imageSpecifier) {
		final Uri originalImageUri = MANTERESTING_SERVER.buildUpon().encodedPath(originalPathFromJson).build();

		// Rewrite path to get the cached image
		final List<String> pathSegments = originalImageUri.getPathSegments();
		final int pathSegmentsLength = pathSegments.size();
		final Uri.Builder builder = originalImageUri.buildUpon();
		builder.path("");

		boolean appendCachePath = true;

		for (int i = 0; i < pathSegmentsLength; i++) {
			String currentSegment = pathSegments.get(i);

			if (i == pathSegmentsLength - 1) {
				final String rewrittenName = FileUtils.getFileNameWithoutExtension(currentSegment) + "_"
						+ imageSpecifier;
				final String rewrittenPathSegment = rewrittenName + '.' + FileUtils.getFileExtension(currentSegment);
				currentSegment = rewrittenPathSegment;
			}

			builder.appendEncodedPath(currentSegment);

			if (appendCachePath && currentSegment.equalsIgnoreCase("media")) {
				builder.appendPath("cache");
				appendCachePath = false;
			}

		}

		return builder.build();
	}
}
