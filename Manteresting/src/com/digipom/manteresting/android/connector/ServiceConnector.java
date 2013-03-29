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
package com.digipom.manteresting.android.connector;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.digipom.manteresting.android.config.LoggerConfig;
import com.digipom.manteresting.android.service.ILocalBinder;

public class ServiceConnector<T extends Service> {
	private static final String TAG = "ServiceConnector";
	private T service;
	private final Context context;
	private final Class<T> tClass;
	private final ServiceConnection listener;

	private ServiceConnection connection = new ServiceConnection() {
		@SuppressWarnings("unchecked")
		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service. Because we have bound to a explicit
			// service that we know is running in our own process, we can
			// cast its IBinder to a concrete class and directly access it.
			if (LoggerConfig.canLog(Log.VERBOSE)) {
				Log.v(TAG, "onServiceConnected(" + className + ", " + service + ")");
			}

			ServiceConnector.this.service = ((ILocalBinder<T>) service).getService();

			if (listener != null) {
				listener.onServiceConnected(className, service);
			}
		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			// Because it is running in our same process, we should never
			// see this happen.
			if (LoggerConfig.canLog(Log.DEBUG)) {
				Log.d(TAG, "onServiceDisconnected(" + className + ")");
			}

			ServiceConnector.this.service = null;

			if (listener != null) {
				listener.onServiceDisconnected(className);
			}
		}
	};

	public ServiceConnector(final Class<T> tClass, final Context context) {
		this.tClass = tClass;
		this.context = context;
		this.listener = null;
	}

	public ServiceConnector(final Class<T> tClass, final Context context, final ServiceConnection listener) {
		this.tClass = tClass;
		this.context = context;
		this.listener = listener;
	}

	public void startAndBindService() {
		// Establish a connection with the service. We use an explicit
		// class name because we want a specific service implementation that
		// we know will be running in our own process (and thus won't be
		// supporting component replacement by other applications).
		if (LoggerConfig.canLog(Log.VERBOSE)) {
			Log.v(TAG, "startAndBindService()");
		}
		context.startService(new Intent(context, tClass));
		context.bindService(new Intent(context, tClass), connection, Context.BIND_AUTO_CREATE);
	}

	public void unbindService() {
		// Detach our existing connection.
		if (LoggerConfig.canLog(Log.VERBOSE)) {
			Log.v(TAG, "unbindService()");
		}
		context.unbindService(connection);
		service = null;
	}

	public T getService() {
		return service;
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();

		if (LoggerConfig.canLog(Log.WARN)) {
			if (service != null) {
				Log.w(TAG, toString() + " was not unbound from the service.");
			}
		}
	}

	@Override
	public String toString() {
		return "ServiceConnector [service=" + service + ", context=" + context + ", listener=" + listener
				+ ", connection=" + connection + "]";
	}
}
