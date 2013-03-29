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

enum Category {
	NORMAL(600), SMALL(250), THUMB(60);

	private final int width;

	private Category(int width) {
		this.width = width;
	}

	int getWidth() {
		return width;
	}

	boolean isCategoryAdequateForWidth(int requestedWidth) {
		// Allow some scaling up, but to a certain extent. The image is
		// adequate so long as it doesn't need to be scaled up to double
		// size or more.
		if (requestedWidth < width * 2) {
			return true;
		} else {
			return false;
		}
	}

	static Category getAdequateCategoryForWidth(int requestedWidth) {
		if (THUMB.isCategoryAdequateForWidth(requestedWidth)) {
			return THUMB;
		} else if (SMALL.isCategoryAdequateForWidth(requestedWidth)) {
			return SMALL;
		} else {
			return NORMAL;
		}
	}
}
