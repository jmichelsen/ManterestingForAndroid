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

import java.io.File;
import java.text.DecimalFormat;

public abstract class FileUtils {
	public static final String getFileNameWithoutExtension(final File file) {
		return getFileNameWithoutExtension(file.getName());
	}

	public static final String getFileNameWithoutExtension(final String fileName) {
		final int lastIndexOf = fileName.lastIndexOf('.');

		if (lastIndexOf != -1) {
			return fileName.substring(0, lastIndexOf);
		} else {
			return fileName;
		}
	}

	public static final String getFileExtension(final File file) {
		return getFileExtension(file.getName());
	}

	public static final String getFileExtension(final String fileName) {
		final int lastIndexOf = fileName.lastIndexOf('.');

		if (lastIndexOf != -1) {
			return fileName.substring(lastIndexOf + 1);
		} else {
			return "";
		}
	}

	public static long getSizeOfDirectory(File dir) {
		long size = 0;
		final File[] fileList = dir.listFiles();

		for (int i = 0; i < fileList.length; i++) {
			if (fileList[i].isDirectory()) {
				size += getSizeOfDirectory(fileList[i]);
			} else {
				size += fileList[i].length();
			}
		}

		return size;
	}

	// From:
	// http://stackoverflow.com/questions/3263892/format-file-size-as-mb-gb-etc
	public static String getReadableFileSize(long size) { // Hello World By AT
		if (size <= 0) {
			return "0";
		}
		final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
		final int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
		return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
	}
}