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
package com.digipom.manteresting.android.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.digipom.manteresting.android.R;

/**
 * Adapted from
 * http://stackoverflow.com/questions/9540189/imageview-doesnt-scale
 * -on-large-screen-devices
 */
public class AspectRatioImageView extends ImageView {
	private enum FixedDimension {
		HORIZONTAL, VERTICAL
	}

	private final FixedDimension fixedDimension;

	public AspectRatioImageView(Context context) {
		this(context, null);
	}

	public AspectRatioImageView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public AspectRatioImageView(Context context, AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);
		final TypedArray array = context.obtainStyledAttributes(attrs,
				R.styleable.AspectRatioImageView, defStyle, 0);
		final int fixedDimension = array.getInt(
				R.styleable.AspectRatioImageView_fixedDimension, 0);

		if (fixedDimension == 0) {
			this.fixedDimension = FixedDimension.HORIZONTAL;
		} else {
			this.fixedDimension = FixedDimension.VERTICAL;
		}

		array.recycle();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		boolean widthExactly = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY;
		boolean heightExactly = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY;

		float ratio;
		
		try {
			ratio = ((float) ((BitmapDrawable) getDrawable())
					.getIntrinsicWidth())
					/ ((float) ((BitmapDrawable) getDrawable())
							.getIntrinsicHeight());
		} catch (ClassCastException e) {
			ratio = MeasureSpec.getSize(widthMeasureSpec)
					/ MeasureSpec.getSize(heightMeasureSpec);
		}

		int heightRatioSpec = MeasureSpec.makeMeasureSpec(
				(int) (MeasureSpec.getSize(widthMeasureSpec) / ratio),
				MeasureSpec.EXACTLY);

		int widthRatioSpec = MeasureSpec.makeMeasureSpec(
				(int) (MeasureSpec.getSize(heightMeasureSpec) * ratio),
				MeasureSpec.EXACTLY);

		if (widthExactly) {
			if (heightExactly) {
				super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			} else {
				super.onMeasure(widthMeasureSpec, heightRatioSpec);
			}
		} else {
			if (heightExactly) {
				super.onMeasure(widthRatioSpec, heightMeasureSpec);
			} else {
				if (fixedDimension == FixedDimension.HORIZONTAL) {
					super.onMeasure(widthMeasureSpec, heightRatioSpec);
				} else {
					super.onMeasure(widthRatioSpec, heightMeasureSpec);
				}
			}
		}
	}
}
