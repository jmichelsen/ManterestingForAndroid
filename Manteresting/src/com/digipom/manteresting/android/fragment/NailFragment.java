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
package com.digipom.manteresting.android.fragment;

import static com.digipom.manteresting.android.config.AppConfig.MANTERESTING_SERVER;
import static com.digipom.manteresting.android.service.rest.RestServiceHelper.EXTRA_OFFSET;
import static com.digipom.manteresting.android.service.rest.RestServiceHelper.EXTRA_REQUEST_ID;
import static com.digipom.manteresting.android.service.rest.RestServiceHelper.STATUS_ERROR;
import static com.digipom.manteresting.android.service.rest.RestServiceHelper.STATUS_RUNNING;
import static com.digipom.manteresting.android.service.rest.RestServiceHelper.STATUS_SUCCESS;

import org.json.JSONObject;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.digipom.manteresting.android.R;
import com.digipom.manteresting.android.adapter.NailCursorAdapter;
import com.digipom.manteresting.android.config.LoggerConfig;
import com.digipom.manteresting.android.connector.ServiceConnector;
import com.digipom.manteresting.android.provider.ManterestingContract;
import com.digipom.manteresting.android.provider.ManterestingContract.Nails;
import com.digipom.manteresting.android.service.cache.CacheService;
import com.digipom.manteresting.android.service.rest.RestServiceHelper;

public class NailFragment extends SherlockListFragment implements LoaderManager.LoaderCallbacks<Cursor>,
		ServiceConnection {

	private static final int MAX_COUNT = 500;
	private static final int DEFAULT_LIMIT = 50;
	private static final String TAG = "NailFragment";
	private static final int LOADER_ID = 1;

	private final ResultReceiver resultReceiver = new ResultReceiver(new Handler()) {
		@Override
		protected void onReceiveResult(int resultCode, Bundle resultData) {
			if (resultCode == STATUS_SUCCESS) {
				if (resultCode == STATUS_SUCCESS && resultData.getInt(EXTRA_REQUEST_ID) == offsetRequestId) {
					offsetForNextPage = resultData.getInt(EXTRA_OFFSET);
				}
			}

			updateUiState(resultCode);
		}
	};

	private ServiceConnector<CacheService> serviceConnector;

	private NailCursorAdapter nailAdapter;

	private ListView listView;
	private View connectingEmpty;
	private View couldNotConnectEmpty;

	private View headerMoreItemsLoadingIndicator;
	private View headerLoadMoreItemsIndicator;
	private View headerCouldNotLoadMoreItemsIndicator;

	private View footerView;
	private View footerMoreItemsLoadingIndicator;
	private View footerCouldNotLoadMoreItemsIndicator;

	private int offsetForNextPage = -1;
	private int offsetRequestId = -1;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

		final View layout = inflater.inflate(R.layout.nail_fragment, container, false);

		connectingEmpty = layout.findViewById(R.id.connectingEmpty);
		couldNotConnectEmpty = layout.findViewById(R.id.couldNotConnectEmpty);

		listView = (ListView) layout.findViewById(android.R.id.list);

		final View headerView = inflater.inflate(R.layout.nail_header_placeholder, null);
		headerMoreItemsLoadingIndicator = headerView.findViewById(R.id.moreItemsLoadingIndicator);
		headerLoadMoreItemsIndicator = headerView.findViewById(R.id.loadMoreItemsIndicator);
		headerCouldNotLoadMoreItemsIndicator = headerView.findViewById(R.id.couldNotLoadMoreItemsIndicator);

		final OnClickListener fetchNewNailsOnClickListener = new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (getActivity() != null) {
					doSync();
				}
			}
		};

		((Button) headerView.findViewById(R.id.fetchNewNails)).setOnClickListener(fetchNewNailsOnClickListener);
		((Button) headerView.findViewById(R.id.retryConnect)).setOnClickListener(fetchNewNailsOnClickListener);
		listView.addHeaderView(headerView);

		footerView = inflater.inflate(R.layout.nail_paging_placeholder, null);
		footerMoreItemsLoadingIndicator = footerView.findViewById(R.id.moreItemsLoadingIndicator);
		footerCouldNotLoadMoreItemsIndicator = footerView.findViewById(R.id.couldNotLoadMoreItemsIndicator);

		((Button) footerView.findViewById(R.id.retryConnect)).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (getActivity() != null) {
					if (offsetForNextPage == -1) {
						offsetForNextPage = calculateInitialOffsetForNextFetch();
					}

					if (offsetForNextPage != -1) {
						doSyncForPageOffset(offsetForNextPage);
					}
				}
			}
		});

		listView.addFooterView(footerView);

		listView.setOnScrollListener(new AbsListView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {
				// Ignore
			}

			@Override
			public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
				if (totalItemCount > 2 && getActivity() != null && footerView != null
						&& footerMoreItemsLoadingIndicator != null && footerView.getVisibility() == View.VISIBLE
						&& footerMoreItemsLoadingIndicator.getVisibility() == View.VISIBLE
						&& firstVisibleItem + visibleItemCount >= totalItemCount) {
					if (offsetForNextPage == -1) {
						offsetForNextPage = calculateInitialOffsetForNextFetch();
					}

					if (offsetForNextPage != -1) {
						doSyncForPageOffset(offsetForNextPage);
					}
				}
			}
		});

		((Button) layout.findViewById(R.id.retryConnect)).setOnClickListener(fetchNewNailsOnClickListener);

		return layout;
	}

	private int calculateInitialOffsetForNextFetch() {
		int offsetToReturn = -1;

		if (listView != null) {
			try {
				final int listViewCount = listView.getCount();

				if (listViewCount > 2) {
					// Get the first and last nail IDs, and subtract the 2
					// to get the initial offset.
					final Cursor firstCursor = (Cursor) listView.getItemAtPosition(1);

					if (firstCursor != null && !firstCursor.isClosed()) {
						final int columnIndex = firstCursor.getColumnIndex(Nails.NAIL_ID);
						final int firstNailId = Integer.parseInt(firstCursor.getString(columnIndex));

						final Cursor lastCursor = (Cursor) listView.getItemAtPosition(listViewCount - 2);
						if (lastCursor != null && !lastCursor.isClosed()) {
							final int lastNailId = Integer.parseInt(firstCursor.getString(columnIndex));

							offsetForNextPage = firstNailId - lastNailId;
							return firstNailId - lastNailId;
						}
					}
				}
			} catch (Exception e) {
				if (LoggerConfig.canLog(Log.ERROR)) {
					Log.e(TAG, "getOffsetForNextFetch()", e);
				}
			}
		}

		return offsetToReturn;
	}

	private void doSync() {
		RestServiceHelper.syncNails(getActivity(), resultReceiver, 0, DEFAULT_LIMIT);
		updateUiState(STATUS_RUNNING);
	}

	private void doSyncForPageOffset(int offset) {
		offsetRequestId = RestServiceHelper.syncNails(getActivity(), resultReceiver, offset, DEFAULT_LIMIT);
		updateUiState(STATUS_RUNNING);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		serviceConnector = new ServiceConnector<CacheService>(CacheService.class, getActivity(), this);
		serviceConnector.startAndBindService();

		nailAdapter = new NailCursorAdapter(this.getActivity().getApplicationContext(), serviceConnector);

		setListAdapter(nailAdapter);

		registerForContextMenu(listView);

		if (LoggerConfig.canLog(Log.VERBOSE)) {
			LoaderManager.enableDebugLogging(true);
		}

		getLoaderManager().initLoader(LOADER_ID, null, this);

		// This is a little bit heavy, but means we'll be doing a sync each
		// time we initialize the fragment.
		doSync();
	}

	private void updateUiState(int nailFetchState) {
		if (connectingEmpty != null && couldNotConnectEmpty != null) {
			if (nailFetchState == STATUS_RUNNING || nailFetchState == STATUS_SUCCESS) {
				connectingEmpty.setVisibility(View.VISIBLE);
				couldNotConnectEmpty.setVisibility(View.GONE);
			} else {
				connectingEmpty.setVisibility(View.GONE);
				couldNotConnectEmpty.setVisibility(View.VISIBLE);
			}
		}

		if (headerMoreItemsLoadingIndicator != null && headerLoadMoreItemsIndicator != null
				&& headerCouldNotLoadMoreItemsIndicator != null) {
			if (nailFetchState == STATUS_RUNNING) {
				headerMoreItemsLoadingIndicator.setVisibility(View.VISIBLE);
				headerLoadMoreItemsIndicator.setVisibility(View.INVISIBLE);
				headerCouldNotLoadMoreItemsIndicator.setVisibility(View.INVISIBLE);
			} else if (nailFetchState == STATUS_ERROR) {
				headerMoreItemsLoadingIndicator.setVisibility(View.INVISIBLE);
				headerLoadMoreItemsIndicator.setVisibility(View.INVISIBLE);
				headerCouldNotLoadMoreItemsIndicator.setVisibility(View.VISIBLE);
			} else if (nailFetchState == STATUS_SUCCESS) {
				headerMoreItemsLoadingIndicator.setVisibility(View.INVISIBLE);
				headerLoadMoreItemsIndicator.setVisibility(View.VISIBLE);
				headerCouldNotLoadMoreItemsIndicator.setVisibility(View.INVISIBLE);
			}
		}

		if (listView != null && footerView != null && footerMoreItemsLoadingIndicator != null
				&& footerCouldNotLoadMoreItemsIndicator != null) {

			// Maybe should be 502 instead, to account for the header/footer.
			if (listView.getCount() >= MAX_COUNT) {
				footerView.setVisibility(View.GONE);
			} else {
				footerView.setVisibility(View.VISIBLE);
			}

			if (nailFetchState == STATUS_RUNNING || nailFetchState == STATUS_SUCCESS) {
				footerMoreItemsLoadingIndicator.setVisibility(View.VISIBLE);
				footerCouldNotLoadMoreItemsIndicator.setVisibility(View.INVISIBLE);
			} else if (nailFetchState == STATUS_ERROR) {
				footerMoreItemsLoadingIndicator.setVisibility(View.INVISIBLE);
				footerCouldNotLoadMoreItemsIndicator.setVisibility(View.VISIBLE);
			}
		}
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		// If we didn't start downloading/loading cached bitmaps because the
		// service was not yet loaded, then notify the adapter that now we can
		// do so.
		nailAdapter.notifyDataSetChanged();
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		// No-op
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		// This is called when a new Loader needs to be created. This
		// sample only has one Loader, so we don't care about the ID.
		if (LoggerConfig.canLog(Log.VERBOSE)) {
			Log.v(TAG, "onCreateLoader(" + id + ", " + args + "): Creating a new cursor loader...");
		}

		return new CursorLoader(getActivity(), ManterestingContract.Nails.CONTENT_URI, null, null, null,
				ManterestingContract.Nails.NAIL_ID + " DESC");
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		// Swap the new cursor in. (The framework will take care of closing the
		// old cursor once we return.)
		nailAdapter.swapCursor(data);

		if (LoggerConfig.canLog(Log.VERBOSE)) {
			Log.v(TAG, "onLoadFinished(" + loader + ", " + data + "): Swapped nailAdapter cursor.");
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		// This is called when the last Cursor provided to onLoadFinished()
		// above is about to be closed. We need to make sure we are no
		// longer using it.
		nailAdapter.swapCursor(null);

		if (LoggerConfig.canLog(Log.VERBOSE)) {
			Log.v(TAG, "onLoaderReset(" + loader + "): Swapped nailAdapter with a null cursor.");
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.manteresting_actionbar_context_menu, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.up:
				// Illusion of quickly scrolling to the top. Regular scrolling
				// doesn't seem to work; perhaps because of the
				// notifyDataSetChanged() from the adapter calls.
				listView.smoothScrollBy(-5000, 500);
				listView.postDelayed(new Runnable() {
					public void run() {
						listView.smoothScrollBy(0, 0);
						listView.setSelection(0);
					}
				}, 250);
				return true;
			default:
				return false;
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		getSherlockActivity().getMenuInflater().inflate(R.menu.nail_context_menu, menu);
	}

	@Override
	public boolean onContextItemSelected(android.view.MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

		if (handleMenuItemSelected(info.position, item.getItemId())) {
			return true;
		} else {
			return super.onContextItemSelected(item);
		}
	}

	private boolean handleMenuItemSelected(int listItemPosition, int itemId) {
		listItemPosition -= listView.getHeaderViewsCount();

		if (listItemPosition >= 0 && listItemPosition < nailAdapter.getCount()) {
			switch (itemId) {
				case R.id.share:
					try {
						final Cursor cursor = (Cursor) nailAdapter.getItem(listItemPosition);

						if (cursor != null && !cursor.isClosed()) {
							final int nailId = cursor.getInt(cursor.getColumnIndex(Nails.NAIL_ID));
							final JSONObject nailJson = new JSONObject(cursor.getString(cursor
									.getColumnIndex(Nails.NAIL_JSON)));

							final Uri uri = MANTERESTING_SERVER.buildUpon().appendPath("nail")
									.appendPath(String.valueOf(nailId)).build();
							String description = nailJson.getString("description");

							if (description.length() > 100) {
								description = description.substring(0, 97) + 'É';
							}

							final String user = nailJson.getJSONObject("user").getString("username");
							final String category = nailJson.getJSONObject("workbench").getJSONObject("category")
									.getString("title");

							final Intent shareIntent = new Intent(Intent.ACTION_SEND);
							shareIntent.setType("text/plain");
							shareIntent.putExtra(Intent.EXTRA_TEXT, description + ' ' + uri.toString());
							shareIntent.putExtra(Intent.EXTRA_SUBJECT,
									String.format(getResources().getString(R.string.shareSubject), user, category));
							try {
								startActivity(Intent.createChooser(shareIntent, getText(R.string.share)));
							} catch (ActivityNotFoundException e) {
								new AlertDialog.Builder(getActivity()).setMessage(R.string.noShareApp).show();
							}
						}
					} catch (Exception e) {
						if (LoggerConfig.canLog(Log.WARN)) {
							Log.w(TAG, "Could not share nail at position " + listItemPosition + " with id " + itemId);
						}
					}

					return true;
				default:
					return false;
			}
		} else {
			return false;
		}
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();

		if (nailAdapter != null) {
			nailAdapter.clearCache();
		}
	}

	@Override
	public void onDestroyView() {
		if (LoggerConfig.canLog(Log.VERBOSE)) {
			Log.d(TAG, "onDestroyView()");
		}

		super.onDestroyView();

		// Make sure adapter's cursor has been released, otherwise we can get
		// exceptions as the old adapter still receives events.
		nailAdapter.swapCursor(null);

		serviceConnector.unbindService();
	}
}
