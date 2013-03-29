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
package com.digipom.manteresting.android.adapter;

import java.util.HashSet;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Handler;
import android.support.v4.util.LruCache;
import android.support.v4.widget.CursorAdapter;
import android.text.Html;
import android.text.SpannedString;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.digipom.manteresting.android.R;
import com.digipom.manteresting.android.config.LoggerConfig;
import com.digipom.manteresting.android.connector.ServiceConnector;
import com.digipom.manteresting.android.provider.ManterestingContract.Nails;
import com.digipom.manteresting.android.service.cache.BitmapLoadCallback;
import com.digipom.manteresting.android.service.cache.CacheService;

public class NailCursorAdapter extends CursorAdapter {
	private static final String TAG = "NailCursorAdapter";
	private final LayoutInflater layoutInflater;
	private final Handler handler = new Handler();
	private final Set<String> failedDownloads = new HashSet<String>();
	private final ServiceConnector<CacheService> serviceConnector;

	static class CachedData {
		String originalImageUriString;
		String nailDescription;
		CharSequence styledUserAndCategory;
	}

	// Cache info associated with a nail item ID. Assume that nails are
	// immutable, at least while this in-memory cache is in effect. Cache size
	// is 500 items.
	private final LruCache<String, CachedData> cachedData = new LruCache<String, CachedData>(500);

	private int nailIdColumnIndex;
	private int nailObjectColumnIndex;

	static class ViewHolder {
		View imageLoadingIndicator;
		View couldNotLoadImageIndicator;
		ImageView loadedImage;
		Button retryConnect;
		TextView nailDescription;
		TextView nailUserAndCategory;
	}

	private final BitmapLoadCallback bitmapCompleteReceiver = new BitmapLoadCallback(handler) {
		@Override
		protected void onBitmapLoadComplete(String uri, boolean successful) {
			if (!successful) {
				failedDownloads.add(uri);
			}

			if (mCursor == null || mCursor.isClosed()) {
				if (LoggerConfig.canLog(Log.DEBUG)) {
					Log.d(TAG, "Dropping bitmap callback since cursor is invalid.");
				}
			} else {
				notifyDataSetChanged();
			}
		}
	};

	public NailCursorAdapter(Context context, ServiceConnector<CacheService> serviceConnector) {
		super(context, null, 0);
		layoutInflater = LayoutInflater.from(context);
		this.serviceConnector = serviceConnector;
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		final View newView;

		newView = layoutInflater.inflate(R.layout.nail_item, null);

		final ViewHolder viewHolder = new ViewHolder();
		viewHolder.imageLoadingIndicator = newView.findViewById(R.id.imageLoadingIndicator);
		viewHolder.couldNotLoadImageIndicator = newView.findViewById(R.id.couldNotLoadImageIndicator);
		viewHolder.loadedImage = (ImageView) newView.findViewById(R.id.loadedImage);
		viewHolder.retryConnect = (Button) newView.findViewById(R.id.retryConnect);
		viewHolder.nailDescription = (TextView) newView.findViewById(R.id.nailDescription);
		viewHolder.nailUserAndCategory = (TextView) newView.findViewById(R.id.nailUserAndCategory);

		viewHolder.retryConnect.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// To refresh the bitmap, we just need to clear the uri
				// string from the set of failed downloads. A retry will
				// clear all failed downloads so the user doesn't have to 
				// retry one by one.
				if (LoggerConfig.canLog(Log.VERBOSE)) {
					Log.v(TAG, "retryConnect.onClick(): Clearing failed downloads.");
				}

				failedDownloads.clear();

				// Can't use the cursor passed in as a parameter as that
				// cursor can get swapped.
				if (mCursor != null && !mCursor.isClosed()) {
					notifyDataSetChanged();
				} else {
					if (LoggerConfig.canLog(Log.VERBOSE)) {
						Log.v(TAG, "retryConnect.onClick(): Did not call notifyDataSetChanged() as cursor is invalid.");
					}
				}
			}
		});

		newView.setTag(viewHolder);

		return newView;
	}

	/**
	 * BindView will also be called after newView. See {@link CursorAdapter}
	 */
	@Override
	public void bindView(View view, Context context, Cursor cursor) {
		try {
			// 84, or 0.825, comes from the margins/paddings around the
			// image. This will need to be changed if the margins/paddings are
			// changed or multi-column mode is used.
			final int approxImageWidthInPixels = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.825f);

			// Request a load of the bitmaps on either side, to avoid
			// problems when scrolling up and down.
			final int cursorPosition = cursor.getPosition();

			if (cursorPosition > 0) {
				cursor.moveToPrevious();
				if (cursor.getString(nailObjectColumnIndex) != null) {
					cacheDataAndGet(context, cursor, approxImageWidthInPixels);
				}
				cursor.moveToNext();
			}

			if (cursorPosition < cursor.getCount() - 1) {
				cursor.moveToNext();
				if (cursor.getString(nailObjectColumnIndex) != null) {
					cacheDataAndGet(context, cursor, approxImageWidthInPixels);
				}
				cursor.moveToPrevious();
			}

			final ViewHolder viewHolder = (ViewHolder) view.getTag();

			final CachedData cachedDataForThisNailItem = cacheDataAndGet(context, cursor, approxImageWidthInPixels);

			viewHolder.nailDescription.setText(cachedDataForThisNailItem.nailDescription);
			viewHolder.nailUserAndCategory.setText(cachedDataForThisNailItem.styledUserAndCategory);

			if (failedDownloads.contains(cachedDataForThisNailItem.originalImageUriString)) {
				// This image has failed. The user will have to select to
				// retry this image. INVISIBLE and not GONE so that the view
				// doesn't jump in sizes for couldNotLoadImageIndicator.
				viewHolder.imageLoadingIndicator.setVisibility(View.INVISIBLE);
				viewHolder.couldNotLoadImageIndicator.setVisibility(View.VISIBLE);
				viewHolder.loadedImage.setVisibility(View.GONE);
			} else {
				// Should default to WRAP_CONTENT
				viewHolder.loadedImage.getLayoutParams().height = LayoutParams.WRAP_CONTENT;

				Bitmap bitmap = null;

				if (serviceConnector.getService() != null) {
					bitmap = serviceConnector.getService().getOrLoadLifoAsync(
							cachedDataForThisNailItem.originalImageUriString, approxImageWidthInPixels,
							bitmapCompleteReceiver);
				}

				if (bitmap == null) {
					viewHolder.imageLoadingIndicator.setVisibility(View.VISIBLE);
					viewHolder.couldNotLoadImageIndicator.setVisibility(View.INVISIBLE);
					viewHolder.loadedImage.setVisibility(View.GONE);
				} else {
					viewHolder.imageLoadingIndicator.setVisibility(View.GONE);
					viewHolder.couldNotLoadImageIndicator.setVisibility(View.GONE);
					viewHolder.loadedImage.setVisibility(View.VISIBLE);

					viewHolder.loadedImage.setImageBitmap(bitmap);
				}
			}
		} catch (JSONException e) {
			if (LoggerConfig.canLog(Log.ERROR)) {
				Log.e(TAG, "Could not load JSON object from database.", e);
			}
		} catch (Exception e) {
			if (LoggerConfig.canLog(Log.ERROR)) {
				Log.e(TAG, "Error binding view.", e);
			}
		}
	}

	private CachedData cacheDataAndGet(Context context, Cursor cursor, int requestedWidth) throws JSONException {
		final String nailId = cursor.getString(nailIdColumnIndex);

		CachedData cachedDataForThisNailItem = cachedData.get(nailId);

		if (cachedDataForThisNailItem == null) {
			cachedDataForThisNailItem = new CachedData();

			final JSONObject nailObject = new JSONObject(cursor.getString(nailObjectColumnIndex));

			cachedDataForThisNailItem.originalImageUriString = nailObject.getString("original");
			cachedDataForThisNailItem.nailDescription = nailObject.getString("description");

			final String userName = nailObject.getJSONObject("user").getString("username");
			final String userNameFirstLetterCapitalised = userName.substring(0, 1).toUpperCase()
					+ userName.substring(1);
			final String workBench = nailObject.getJSONObject("workbench").getString("title");

			cachedDataForThisNailItem.styledUserAndCategory = Html.fromHtml(String.format(
					Html.toHtml(new SpannedString(context.getResources().getText(R.string.nailUserAndCategory))),
					userNameFirstLetterCapitalised, workBench));

			cachedData.put(nailId, cachedDataForThisNailItem);
		}

		// Request a bitmap load.
		if (!failedDownloads.contains(cachedDataForThisNailItem.originalImageUriString)
				&& serviceConnector.getService() != null) {
			serviceConnector.getService().loadOnlyLifoAsync(cachedDataForThisNailItem.originalImageUriString,
					requestedWidth);
		}

		return cachedDataForThisNailItem;
	}

	@Override
	public Cursor swapCursor(Cursor newCursor) {
		if (newCursor != null) {
			nailIdColumnIndex = newCursor.getColumnIndex(Nails.NAIL_ID);
			nailObjectColumnIndex = newCursor.getColumnIndex(Nails.NAIL_JSON);
		} else {
			nailIdColumnIndex = -1;
			nailObjectColumnIndex = -1;
			cachedData.evictAll();
		}

		return super.swapCursor(newCursor);
	}

	public void clearCache() {
		cachedData.evictAll();
	}
}
