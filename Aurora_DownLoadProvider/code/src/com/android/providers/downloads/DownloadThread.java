/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.providers.downloads;

import static android.provider.Downloads.Impl.STATUS_BAD_REQUEST;
import static android.provider.Downloads.Impl.STATUS_CANNOT_RESUME;
import static android.provider.Downloads.Impl.STATUS_FILE_ERROR;
import static android.provider.Downloads.Impl.STATUS_HTTP_DATA_ERROR;
import static android.provider.Downloads.Impl.STATUS_TOO_MANY_REDIRECTS;
import static android.provider.Downloads.Impl.STATUS_WAITING_FOR_NETWORK;
import static android.provider.Downloads.Impl.STATUS_WAITING_TO_RETRY;
import static android.text.format.DateUtils.SECOND_IN_MILLIS;
import static com.android.providers.downloads.Constants.TAG;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_PARTIAL;
import static java.net.HttpURLConnection.HTTP_SEE_OTHER;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.drm.DrmManagerClient;
import android.net.ConnectivityManager;
import android.net.INetworkPolicyListener;
import android.net.NetworkInfo;
import android.net.NetworkPolicyManager;
import android.net.TrafficStats;
import android.os.FileUtils;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Downloads;
import android.text.TextUtils;
import android.util.Pair;

import com.android.providers.downloads.DownloadInfo.NetworkState;
import com.android.providers.downloads.util.GnStorage;
import com.android.providers.downloads.util.IOUtils;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import org.apache.http.Header;
import org.apache.http.HttpResponse;

import libcore.io.IoUtils;

import com.android.providers.downloads.util.Log;

/**
 * Task which executes a given {@link DownloadInfo}: making network requests,
 * persisting data to disk, and updating {@link DownloadProvider}.
 */
public class DownloadThread implements Runnable {

	// TODO: bind each download to a specific network interface to avoid state
	// checking races once we have ConnectivityManager API

	private static final int HTTP_REQUESTED_RANGE_NOT_SATISFIABLE = 416;
	private static final int HTTP_TEMP_REDIRECT = 307;

	private static final int DEFAULT_TIMEOUT = (int) (20 * SECOND_IN_MILLIS);

	private final Context mContext;
	private final DownloadInfo mInfo;
	private final SystemFacade mSystemFacade;
	private final StorageManager mStorageManager;
	private final DownloadNotifier mNotifier;

	private DrmConvertSession mDrmConvertSession;

	private volatile boolean mPolicyDirty;

	// Add for Carrier Feature - Download Breakpoint continuing.
	// Support continuing download after the download is broken
	// although HTTP Server doesn't contain etag in its response.
	private static final String QRD_ETAG = "qrd_magic_etag";

	public DownloadThread(Context context, SystemFacade systemFacade,
			DownloadInfo info, StorageManager storageManager,
			DownloadNotifier notifier) {
		mContext = context;
		mSystemFacade = systemFacade;
		mInfo = info;
		mStorageManager = storageManager;
		mNotifier = notifier;
	}

	/**
	 * Returns the user agent provided by the initiating app, or use the default
	 * one
	 */
	private String userAgent() {
		String userAgent = mInfo.mUserAgent;
		if (userAgent == null) {
			userAgent = Constants.DEFAULT_USER_AGENT;
		}
		return userAgent;
	}

	/**
	 * State for the entire run() method.
	 */
	static class State {
		public String mFilename;
		public String mMimeType;
		public int mRetryAfter = 0;
		public boolean mGotData = false;
		public String mRequestUri;
		public long mTotalBytes = -1;
		public long mCurrentBytes = 0;
		public String mHeaderETag;
		public boolean mContinuingDownload = false;
		public long mBytesNotified = 0;
		public long mTimeLastNotification = 0;
		public int mNetworkType = ConnectivityManager.TYPE_NONE;

		/** Historical bytes/second speed of this download. */
		public long mSpeed;
		/** Time when current sample started. */
		public long mSpeedSampleStart;
		/** Bytes transferred since current sample started. */
		public long mSpeedSampleBytes;

		public long mContentLength = -1;
		public String mContentDisposition;
		public String mContentLocation;

		public int mRedirectionCount;
		public URL mUrl;

		public State(DownloadInfo info) {
			mMimeType = Intent.normalizeMimeType(info.mMimeType);
			mRequestUri = info.mUri;
			mTotalBytes = info.mTotalBytes;
			mCurrentBytes = info.mCurrentBytes;
			// modify by jxh 2014-2-23 begin
			mFilename = info.mFileName;// GnStorage.getNowFileName(info.mFileName,
										// info.mStorage);
			//modify by jxh 2014-2-23 end
		}

		@Override
		public String toString() {
			StringBuffer sb = new StringBuffer();
			sb.append(" mFilename:" + mFilename);
			sb.append(" mMimeType:" + mMimeType);
			sb.append(" mRetryAfter:" + mRetryAfter);
			sb.append(" mGotData:" + mGotData);
			sb.append(" mRequestUri:" + mRequestUri);
			sb.append(" mTotalBytes:" + mTotalBytes);
			sb.append(" mCurrentBytes:" + mCurrentBytes);
			sb.append(" mHeaderETag:" + mHeaderETag);
			sb.append(" mContinuingDownload:" + mContinuingDownload);
			sb.append(" mBytesNotified:" + mBytesNotified);
			sb.append(" mTimeLastNotification:" + mTimeLastNotification);
			return sb.toString();
		}

		public void resetBeforeExecute() {
			// Reset any state from previous execution
			mContentLength = -1;
			mContentDisposition = null;
			mContentLocation = null;
			mRedirectionCount = 0;
		}
	}

	@Override
	public void run() {
		Log.d(TAG, Log.getThreadName() + " start run download thread");
		Log.i(TAG, "start run download thread");
		Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
		try {
			runInternal();
		} finally {
			mNotifier.notifyDownloadSpeed(mInfo.mId, 0);
		}
		Log.i(TAG, "Download Thread Stop: id = " + mInfo.mId + ", status = "
				+ mInfo.mStatus + ", control = " + mInfo.mControl
				+ ", total = " + mInfo.mTotalBytes + ", current = "
				+ mInfo.mCurrentBytes);
	}

	private void runInternal() {
		// Skip when download already marked as finished; this download was
		// probably started again while racing with UpdateThread.
		if (DownloadInfo.queryDownloadStatus(mContext.getContentResolver(),
				mInfo.mId) == Downloads.Impl.STATUS_SUCCESS) {
			Log.d(TAG, "Download " + mInfo.mId + " already finished; skipping");
			return;
		}

		State state = new State(mInfo);
		if(Constants.LOGV){
			Log.i(TAG, "******************************************"
					+ state.mFilename);
		}
		PowerManager.WakeLock wakeLock = null;
		int finalStatus = Downloads.Impl.STATUS_UNKNOWN_ERROR;
		int numFailed = mInfo.mNumFailed;
		String errorMsg = null;

		final NetworkPolicyManager netPolicy = NetworkPolicyManager
				.from(mContext);
		final PowerManager pm = (PowerManager) mContext
				.getSystemService(Context.POWER_SERVICE);

		try {
			wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
					Constants.TAG);
			wakeLock.acquire();

			// while performing download, register for rules updates
			netPolicy.registerListener(mPolicyListener);

			Log.i(Constants.TAG, "Download " + mInfo.mId + " starting");

			// Remember which network this download started on; used to
			// determine if errors were due to network changes.
			final NetworkInfo info = mSystemFacade
					.getActiveNetworkInfo(mInfo.mUid);
			if (info != null) {
				state.mNetworkType = info.getType();
			}

			// Network traffic on this thread should be counted against the
			// requesting UID, and is tagged with well-known value.
			TrafficStats.setThreadStatsTag(TrafficStats.TAG_SYSTEM_DOWNLOAD);
			TrafficStats.setThreadStatsUid(mInfo.mUid);

			try {
				// TODO: migrate URL sanity checking into client side of API
				state.mUrl = new URL(state.mRequestUri);
			} catch (MalformedURLException e) {
				throw new StopRequestException(STATUS_BAD_REQUEST, e);
			}

			executeDownload(state);

			finalizeDestinationFile(state);
			finalStatus = Downloads.Impl.STATUS_SUCCESS;
		} catch (StopRequestException error) {
			// remove the cause before printing, in case it contains PII
			errorMsg = error.getMessage();
			String msg = "Aborting request for download " + mInfo.mId + ": "
					+ errorMsg;
			finalStatus = error.getFinalStatus();
			Log.w(Constants.TAG, msg+" finalStatus=="+finalStatus);

			// Nobody below our level should request retries, since we handle
			// failure counts at this level.
			if (finalStatus == STATUS_WAITING_TO_RETRY) {
				throw new IllegalStateException(
						"Execution should always throw final error codes");
			}

			// Some errors should be retryable, unless we fail too many times.
			if (isStatusRetryable(finalStatus)) {
				if (state.mGotData) {
					numFailed = 1;
				} else {
					numFailed += 1;
				}

				if (numFailed < Constants.MAX_RETRIES) {
					final NetworkInfo info = mSystemFacade
							.getActiveNetworkInfo(mInfo.mUid);
					if (info != null && info.getType() == state.mNetworkType
							&& info.isConnected()) {
						// Underlying network is still intact, use normal
						// backoff
						finalStatus = STATUS_WAITING_TO_RETRY;
					} else {
						// Network changed, retry on any next available
						finalStatus = STATUS_WAITING_FOR_NETWORK;
					}
				}
			}

			// fall through to finally block
		} catch (Throwable ex) {
			errorMsg = ex.getMessage();
			String msg = "Exception for id " + mInfo.mId + ": " + errorMsg;
			Log.w(Constants.TAG, msg);
			finalStatus = Downloads.Impl.STATUS_UNKNOWN_ERROR;
			// falls through to the code that reports an error
		} finally {
			TrafficStats.clearThreadStatsTag();
			TrafficStats.clearThreadStatsUid();

			cleanupDestination(state, finalStatus);
			notifyDownloadCompleted(state, finalStatus, errorMsg, numFailed);

			Log.i(Constants.TAG,
					"Download " + mInfo.mId + " finished with status "
							+ Downloads.Impl.statusToString(finalStatus));

			netPolicy.unregisterListener(mPolicyListener);

			if (wakeLock != null) {
				wakeLock.release();
				wakeLock = null;
			}
		}
		mStorageManager.incrementNumDownloadsSoFar();
	}

	/**
	 * Fully execute a single download request. Setup and send the request,
	 * handle the response, and transfer the data to the destination file.
	 */
	private void executeDownload(State state) throws StopRequestException {

		state.resetBeforeExecute();
		setupDestinationFile(state);

		// skip when already finished; remove after fixing race in 5217390
		if (state.mCurrentBytes == state.mTotalBytes) {
			Log.i(Constants.TAG, "Skipping initiating request for download "
					+ mInfo.mId + "; already completed");
			return;
		}

		while (state.mRedirectionCount++ < Constants.MAX_REDIRECTS) {//
			// Open connection and follow any redirects until we have a useful
			// response with body.
			HttpURLConnection conn = null;
			try {
				checkConnectivity();
				conn = (HttpURLConnection) state.mUrl.openConnection();
				conn.setInstanceFollowRedirects(false);
				conn.setConnectTimeout(DEFAULT_TIMEOUT);
				conn.setReadTimeout(DEFAULT_TIMEOUT);
				// conn.setDoOutput(true);
				addRequestHeaders(state, conn);
				final int responseCode = conn.getResponseCode();
				Log.d(TAG, "HTTP responseCode==" + responseCode);
				switch (responseCode) {
				case HTTP_OK:
					if (Constants.LOGVV) {
						Log.d(Constants.TAG, "HTTP_OK");
					}
					if (state.mContinuingDownload) {
						throw new StopRequestException(STATUS_CANNOT_RESUME,
								"Expected partial, but received OK");
					}
					processResponseHeaders(state, conn);
					transferData(state, conn);
					return;

				case HTTP_PARTIAL:
					if (Constants.LOGVV) {
						Log.d(Constants.TAG, "HTTP_PARTIAL==" + HTTP_PARTIAL);
					}
					if (!state.mContinuingDownload) {
						throw new StopRequestException(STATUS_CANNOT_RESUME,
								"Expected OK, but received partial");
					}
					transferData(state, conn);
					return;

				case HTTP_MOVED_PERM:
				case HTTP_MOVED_TEMP:
					if (isLoginWlan(state, conn, responseCode)) {
						synchronized (mInfo) {
							mInfo.mControl = Downloads.Impl.CONTROL_PAUSED;
							ContentValues values = new ContentValues();
							values.put(Downloads.Impl.COLUMN_CONTROL,
									mInfo.mControl);
							mContext.getContentResolver().update(
									mInfo.getAllDownloadsUri(), values, null,
									null);
							checkPausedOrCanceled(state);
						}
					}
				case HTTP_SEE_OTHER:
				case HTTP_TEMP_REDIRECT:
					final String location = conn.getHeaderField("Location");
					state.mUrl = new URL(state.mUrl, location);
					if (responseCode == HTTP_MOVED_PERM) {
						// Push updated URL back to database
						state.mRequestUri = state.mUrl.toString();
					}
					continue;

				case HTTP_REQUESTED_RANGE_NOT_SATISFIABLE:
					throw new StopRequestException(STATUS_CANNOT_RESUME,
							"Requested range not satisfiable");

				case HTTP_UNAVAILABLE:
					parseRetryAfterHeaders(state, conn);
					throw new StopRequestException(HTTP_UNAVAILABLE,
							conn.getResponseMessage());

				case HTTP_INTERNAL_ERROR:
					throw new StopRequestException(HTTP_INTERNAL_ERROR,
							conn.getResponseMessage());

				default:
					StopRequestException.throwUnhandledHttpError(responseCode,
							conn.getResponseMessage());
				}
			} catch (IOException e) {
				// Trouble with low-level sockets
				throw new StopRequestException(STATUS_HTTP_DATA_ERROR, e);

			} finally {
				if (conn != null) {
					long time = SystemClock.currentThreadTimeMillis();
					if(Constants.LOGV){
						Log.e(TAG, "conn.disconnect() before");
					}
					conn.disconnect();
					if(Constants.LOGV){
						long time2 = SystemClock.currentThreadTimeMillis();
						Log.e(TAG, "conn.disconnect() end time =="+(time2-time));
					}
				}
			}
		}

		throw new StopRequestException(STATUS_TOO_MANY_REDIRECTS,
				"Too many redirects");
	}

	// Gionee <wangpf> <2013-09-09> modify for CR00894142 begin
	private boolean isLoginWlan(State state, HttpURLConnection conn,
			int statusCode) {
		final String transferEncoding = conn
				.getHeaderField("Transfer-Encoding");
		long contentLength = -1;
		if (transferEncoding == null) {
			contentLength = getHeaderFieldLong(conn, "Content-Length", -1);
		}
		Log.d(TAG, "fileName = " + state.mFilename + "  contentLength = "
				+ contentLength + "  total = " + mInfo.mTotalBytes
				+ "   statusCode = " + statusCode);
		if (state.mFilename == null) {
			return false;
		}
		if (statusCode != HTTP_MOVED_TEMP) {
			return false;
		}
		if (contentLength <= 0) {
			return false;
		}
		if (contentLength == state.mTotalBytes) {
			return false;
		}
		return true;
	}

	// Gionee <wangpf> <2013-09-09> modify for CR00894142 end

	/**
	 * Transfer data from the given connection to the destination file.
	 */
	private void transferData(State state, HttpURLConnection conn)
			throws StopRequestException {
		DrmManagerClient drmClient = null;
		InputStream in = null;
		OutputStream out = null;
		FileDescriptor outFd = null;
		try {
			try {
				in = conn.getInputStream();
			} catch (IOException e) {
				throw new StopRequestException(STATUS_HTTP_DATA_ERROR, e);
			}

			try {
				out = new FileOutputStream(state.mFilename, true);
				outFd = ((FileOutputStream) out).getFD();
			} catch (FileNotFoundException e) {
				// TODO: handle exception
			} catch (IOException e) {
				// TODO: handle exception
			}

			// Start streaming data, periodically watch for pause/cancel
			// commands and checking disk space as needed.

			transferData(state, in, out);
			if (Constants.LOGVV) {
				Log.d(Constants.TAG, "return1");
			}

		} finally {
			if (drmClient != null) {
				drmClient.release();
			}
		
			long time = SystemClock.currentThreadTimeMillis();
			if (Constants.LOGVV) {
				Log.e(Constants.TAG, "closeQuietly before");
			}
			IoUtils.closeQuietly(in);
//			 IOUtils.closeQuietly(in);

			if (Constants.LOGVV) {
				long time2 = SystemClock.currentThreadTimeMillis();
				Log.e(Constants.TAG, "closeQuietly end time =="+(time2-time));
			}
			
			try {
				if (out != null) {
					out.flush();
				}
				if (outFd != null) {
					outFd.sync();
				}
			} catch (IOException e) {
			} finally {
				IoUtils.closeQuietly(out);
			}
			
		}
	}

	/**
	 * Check if current connectivity is valid for this request.
	 */
	private void checkConnectivity() throws StopRequestException {
		// checking connectivity will apply current policy
		mPolicyDirty = false;

		final NetworkState networkUsable = mInfo.checkCanUseNetwork();
		if (networkUsable != NetworkState.OK) {
			int status = Downloads.Impl.STATUS_WAITING_FOR_NETWORK;
			if (networkUsable == NetworkState.UNUSABLE_DUE_TO_SIZE) {
				status = Downloads.Impl.STATUS_QUEUED_FOR_WIFI;
				mInfo.notifyPauseDueToSize(true);
			} else if (networkUsable == NetworkState.RECOMMENDED_UNUSABLE_DUE_TO_SIZE) {
				status = Downloads.Impl.STATUS_QUEUED_FOR_WIFI;
				mInfo.notifyPauseDueToSize(false);
			}
			throw new StopRequestException(status, networkUsable.name());
		}
	}

	/**
	 * Transfer as much data as possible from the HTTP response to the
	 * destination file.
	 */
	private void transferData(State state, InputStream in, OutputStream out)
			throws StopRequestException {
		final byte[] data = new byte[Constants.BUFFER_SIZE];
		for (;;) {
			int bytesRead = readFromResponse(state, data, in);
			if (bytesRead == -1) { // success, end of stream already reached
				handleEndOfStream(state);
				return;
			}

			state.mGotData = true;
			writeDataToDestination(state, data, bytesRead, out);
			state.mCurrentBytes += bytesRead;
			reportProgress(state);

			if (Constants.LOGVV) {
				// Log.v(Constants.TAG, "downloaded " + state.mCurrentBytes
				// + " for " + mInfo.mUri);
			}

			checkPausedOrCanceled(state);
		}
	}

	/**
	 * Called after a successful completion to take any necessary action on the
	 * downloaded file.
	 */
	private void finalizeDestinationFile(State state) {
		if (state.mFilename != null) {
			// make sure the file is readable
			FileUtils.setPermissions(state.mFilename, 0644, -1, -1);
		}
	}

	/**
	 * Called just before the thread finishes, regardless of status, to take any
	 * necessary action on the downloaded file.
	 */
	private void cleanupDestination(State state, int finalStatus) {
		if (mDrmConvertSession != null) {
			mDrmConvertSession.close(state.mFilename);
		}

		if (state.mFilename != null
				&& Downloads.Impl.isStatusError(finalStatus)) {
			if (Constants.LOGVV) {
				Log.d(TAG, "cleanupDestination() deleting " + state.mFilename);
			}
			new File(state.mFilename).delete();
			state.mFilename = null;
		}
	}

	/**
	 * Check if the download has been paused or canceled, stopping the request
	 * appropriately if it has been.
	 */
	private void checkPausedOrCanceled(State state) throws StopRequestException {
		synchronized (mInfo) {
			if (mInfo.mControl == Downloads.Impl.CONTROL_PAUSED) {
				//android.util.Log.e(TAG, "download paused by owner paused");
				throw new StopRequestException(
						Downloads.Impl.STATUS_PAUSED_BY_APP,
						"download paused by owner");
			}
			if (mInfo.mStatus == Downloads.Impl.STATUS_CANCELED) {
				throw new StopRequestException(Downloads.Impl.STATUS_CANCELED,
						"download canceled");
			}
			// Gionee <duansw><2013-3-7> modify for CR00789329 begin
			if (mInfo.mStatus != Downloads.Impl.STATUS_RUNNING) {
				throw new StopRequestException(mInfo.mStatus,
						"download status is not running");
			}
			// Gionee <duansw><2013-3-7> modify for CR00789329 end
		}

		// if policy has been changed, trigger connectivity check
		if (mPolicyDirty) {
			checkConnectivity();
		}
	}

	/**
	 * Report download progress through the database if necessary.
	 */
	private void reportProgress(State state) {
		final long now = SystemClock.elapsedRealtime();

		final long sampleDelta = now - state.mSpeedSampleStart;
		if (sampleDelta > 500) {
			final long sampleSpeed = ((state.mCurrentBytes - state.mSpeedSampleBytes) * 1000)
					/ sampleDelta;

			if (state.mSpeed == 0) {
				state.mSpeed = sampleSpeed;
			} else {
				state.mSpeed = ((state.mSpeed * 3) + sampleSpeed) / 4;
			}

			// Only notify once we have a full sample window
			if (state.mSpeedSampleStart != 0) {
				mNotifier.notifyDownloadSpeed(mInfo.mId, state.mSpeed);
			}

			state.mSpeedSampleStart = now;
			state.mSpeedSampleBytes = state.mCurrentBytes;
		}

		if (state.mCurrentBytes - state.mBytesNotified > Constants.MIN_PROGRESS_STEP
				&& now - state.mTimeLastNotification > Constants.MIN_PROGRESS_TIME) {
			ContentValues values = new ContentValues();
			values.put(Downloads.Impl.COLUMN_CURRENT_BYTES, state.mCurrentBytes);
			mContext.getContentResolver().update(mInfo.getAllDownloadsUri(),
					values, null, null);
			state.mBytesNotified = state.mCurrentBytes;
			state.mTimeLastNotification = now;
		}
	}

	/**
	 * Write a data buffer to the destination file.
	 * 
	 * @param data
	 *            buffer containing the data to write
	 * @param bytesRead
	 *            how many bytes to write from the buffer
	 */
	private void writeDataToDestination(State state, byte[] data,
			int bytesRead, OutputStream out) throws StopRequestException {
		mStorageManager.verifySpaceBeforeWritingToFile(mInfo.mDestination,
				state.mFilename, bytesRead);

		boolean forceVerified = false;
		while (true) {
			try {
				File file = new File(state.mFilename);
				if (!file.exists()) {
					throw new StopRequestException(
							Downloads.Impl.STATUS_FILE_ERROR,
							"download file is not exists!!!");
				}
				if (!DownloadDrmHelper.isDrmConvertNeeded(mInfo.mMimeType)) {
					out.write(data, 0, bytesRead);
				} else {
					byte[] convertedData = mDrmConvertSession.convert(data,
							bytesRead);
					if (convertedData != null) {
						out.write(convertedData, 0, convertedData.length);
					} else {
						throw new StopRequestException(
								Downloads.Impl.STATUS_FILE_ERROR,
								"Error converting drm data.");
					}
				}
				return;
			} catch (IOException ex) {
				// TODO: better differentiate between DRM and disk failures
				if (!forceVerified) {
					// couldn't write to file. are we out of space? check.
					mStorageManager.verifySpace(mInfo.mDestination,
							state.mFilename, bytesRead);
					forceVerified = true;
				} else {
					throw new StopRequestException(
							Downloads.Impl.STATUS_FILE_ERROR,
							"Failed to write data: " + ex);
				}
			}
		}
	}

	/**
	 * Called when we've reached the end of the HTTP response stream, to update
	 * the database and check for consistency.
	 */
	private void handleEndOfStream(State state) throws StopRequestException {
		ContentValues values = new ContentValues();
		values.put(Downloads.Impl.COLUMN_CURRENT_BYTES, state.mCurrentBytes);
		if (state.mContentLength == -1) {
			values.put(Downloads.Impl.COLUMN_TOTAL_BYTES, state.mCurrentBytes);
		}
		mContext.getContentResolver().update(mInfo.getAllDownloadsUri(),
				values, null, null);

		final boolean lengthMismatched = (state.mContentLength != -1)
				&& (state.mCurrentBytes != state.mContentLength);
		if (lengthMismatched) {
			if (cannotResume(state)) {
				throw new StopRequestException(STATUS_CANNOT_RESUME,
						"mismatched content length; unable to resume");
			} else {
				throw new StopRequestException(STATUS_HTTP_DATA_ERROR,
						"closed socket before end of file");
			}
		}
	}

	private boolean cannotResume(State state) {
		return (state.mCurrentBytes > 0 && !mInfo.mNoIntegrity && state.mHeaderETag == null)
				|| DownloadDrmHelper.isDrmConvertNeeded(state.mMimeType);
	}

	/**
	 * Read some data from the HTTP response stream, handling I/O errors.
	 * 
	 * @param data
	 *            buffer to use to read data
	 * @param entityStream
	 *            stream for reading the HTTP response entity
	 * @return the number of bytes actually read or -1 if the end of the stream
	 *         has been reached
	 */
	private int readFromResponse(State state, byte[] data,
			InputStream entityStream) throws StopRequestException {
		try {
			return entityStream.read(data);
		} catch (IOException ex) {
			// TODO: handle stream errors the same as other retries
			if ("unexpected end of stream".equals(ex.getMessage())) {
				return -1;
			}

			ContentValues values = new ContentValues();
			values.put(Downloads.Impl.COLUMN_CURRENT_BYTES, state.mCurrentBytes);
			mContext.getContentResolver().update(mInfo.getAllDownloadsUri(),
					values, null, null);
			if (cannotResume(state)) {
				throw new StopRequestException(
						STATUS_CANNOT_RESUME,
						"Failed reading response: " + ex + "; unable to resume",
						ex);
			} else {
				throw new StopRequestException(STATUS_HTTP_DATA_ERROR,
						"Failed reading response: " + ex, ex);
			}
		}
	}

	/**
	 * Prepare target file based on given network response. Derives filename and
	 * target size as needed.
	 */
	private void processResponseHeaders(State state, HttpURLConnection conn)
			throws StopRequestException {
		// TODO: fallocate the entire file if header gave us specific length

		readResponseHeaders(state, conn);

		if (DownloadDrmHelper.isDrmConvertNeeded(state.mMimeType)) {
			mDrmConvertSession = DrmConvertSession.open(mContext,
					state.mMimeType);
			if (mDrmConvertSession == null) {
				throw new StopRequestException(
						Downloads.Impl.STATUS_NOT_ACCEPTABLE, "Mimetype "
								+ state.mMimeType + " can not be converted.");
			}
		}

		state.mFilename = Helpers.generateSaveFile(
				mContext,
				mInfo.mUri,
				mInfo.mHint,
				// Gione <wangpf> <2013-11-11> modify for CR00916671 begin
				// GnStorage.getNowFileName(mInfo.mHint, mInfo.mStorage),
				// Gione <wangpf> <2013-11-11> modify for CR00916671 end
				state.mContentDisposition, state.mContentLocation,
				state.mMimeType, mInfo.mDestination, state.mContentLength,
				mStorageManager);

		updateDatabaseFromHeaders(state);
		// check connectivity again now that we know the total size
		checkConnectivity();
	}

	/**
	 * Update necessary database fields based on values of HTTP response headers
	 * that have been read.
	 */
	private void updateDatabaseFromHeaders(State state) {
		ContentValues values = new ContentValues();
		values.put(Downloads.Impl._DATA, state.mFilename);
		if (state.mHeaderETag != null) {
			values.put(Constants.ETAG, state.mHeaderETag);
		}
		if (state.mMimeType != null) {
			values.put(Downloads.Impl.COLUMN_MIME_TYPE, state.mMimeType);
		}
		if (mInfo.mTotalBytes < 0) {
			mInfo.mTotalBytes = state.mTotalBytes;
		}
		values.put(Downloads.Impl.COLUMN_TOTAL_BYTES, state.mTotalBytes);
		Log.i(TAG, mInfo.mId + ": " + Log.getThreadName() + ", total bytes = "
				+ mInfo.mTotalBytes);
		mContext.getContentResolver().update(mInfo.getAllDownloadsUri(),
				values, null, null);
	}

	/**
	 * Read headers from the HTTP response and store them into local state.
	 */
	private void readResponseHeaders(State state, HttpURLConnection conn)
			throws StopRequestException {
		state.mContentDisposition = conn.getHeaderField("Content-Disposition");
		state.mContentLocation = conn.getHeaderField("Content-Location");

		if (state.mMimeType == null) {
			state.mMimeType = Intent.normalizeMimeType(conn.getContentType());
		}

		state.mHeaderETag = conn.getHeaderField("ETag");

		if (state.mHeaderETag == null) {
			state.mHeaderETag = QRD_ETAG;
		}

		final String transferEncoding = conn
				.getHeaderField("Transfer-Encoding");
		if (transferEncoding == null) {
			state.mContentLength = getHeaderFieldLong(conn, "Content-Length",
					-1);
		} else {
			Log.i(TAG,
					"Ignoring Content-Length since Transfer-Encoding is also defined");
			state.mContentLength = -1;
		}

		state.mTotalBytes = state.mContentLength;
		mInfo.mTotalBytes = state.mContentLength;

		final boolean noSizeInfo = state.mContentLength == -1
				&& (transferEncoding == null || !transferEncoding
						.equalsIgnoreCase("chunked"));
		if (!mInfo.mNoIntegrity && noSizeInfo) {
			throw new StopRequestException(STATUS_CANNOT_RESUME,
					"can't know size of download, giving up");
		}
	}

	private void parseRetryAfterHeaders(State state, HttpURLConnection conn) {
		state.mRetryAfter = conn.getHeaderFieldInt("Retry-After", -1);
		if (state.mRetryAfter < 0) {
			state.mRetryAfter = 0;
		} else {
			if (state.mRetryAfter < Constants.MIN_RETRY_AFTER) {
				state.mRetryAfter = Constants.MIN_RETRY_AFTER;
			} else if (state.mRetryAfter > Constants.MAX_RETRY_AFTER) {
				state.mRetryAfter = Constants.MAX_RETRY_AFTER;
			}
			state.mRetryAfter += Helpers.sRandom
					.nextInt(Constants.MIN_RETRY_AFTER + 1);
			state.mRetryAfter *= 1000;
		}
	}

	/**
	 * Prepare the destination file to receive data. If the file already exists,
	 * we'll set up appropriately for resumption.
	 */
	private void setupDestinationFile(State state) throws StopRequestException {
		if (!TextUtils.isEmpty(state.mFilename)) { // only true if we've already
													// run a thread for this
													// download
			if (Constants.LOGV) {
				Log.i(Constants.TAG, "have run thread before for id: "
						+ mInfo.mId + ", and state.mFilename: "
						+ state.mFilename);
			}
			if (!Helpers.isFilenameValid(state.mFilename,
					mStorageManager.getDownloadDataDirectory())) {
				// this should never happen
				throw new StopRequestException(
						Downloads.Impl.STATUS_FILE_ERROR,
						"found invalid internal destination filename");
			}
			// We're resuming a download that got interrupted
			File f = new File(state.mFilename);
			if (f.exists()) {
				if (Constants.LOGV) {
					Log.i(Constants.TAG, "resuming download for id: "
							+ mInfo.mId + ", and state.mFilename: "
							+ state.mFilename);
				}
				long fileLength = f.length();
				if (fileLength == 0) {
					// The download hadn't actually started, we can restart from
					// scratch
					if (Constants.LOGVV) {
						Log.d(TAG,
								"setupDestinationFile() found fileLength=0, deleting "
										+ state.mFilename);
					}
					f.delete();
					state.mFilename = null;
					if (Constants.LOGV) {
						Log.i(Constants.TAG, "resuming download for id: "
								+ mInfo.mId
								+ ", BUT starting from scratch again: ");
					}
				} else if (mInfo.mETag == null && !mInfo.mNoIntegrity) {
					// This should've been caught upon failure
					if (Constants.LOGVV) {
						Log.d(TAG,
								"setupDestinationFile() unable to resume download, deleting "
										+ state.mFilename);
					}
					f.delete();
					throw new StopRequestException(
							Downloads.Impl.STATUS_CANNOT_RESUME,
							"Trying to resume a download that can't be resumed");
				} else {
					// All right, we'll be able to resume this download
					if (Constants.LOGV) {
						Log.i(Constants.TAG, "resuming download for id: "
								+ mInfo.mId
								+ ", and starting with file of length: "
								+ fileLength);
					}
					state.mCurrentBytes = (int) fileLength;
					if (mInfo.mTotalBytes != -1) {
						state.mContentLength = mInfo.mTotalBytes;
					}
					state.mHeaderETag = mInfo.mETag;
					state.mContinuingDownload = true;
					if (Constants.LOGV) {
						Log.i(Constants.TAG, "resuming download for id: "
								+ mInfo.mId + ", state.mCurrentBytes: "
								+ state.mCurrentBytes
								+ ", and setting mContinuingDownload to true: ");
					}
				}
			} else {
				resetCurrentBytes(state);
				Log.e(TAG, "file = " + state.mFilename + "  is not exists!!!!");
			}
		} else {
			resetCurrentBytes(state);
			Log.e(TAG, "file = " + state.mFilename + "  name is Empty !!!!!");
		}
	}

	private void resetCurrentBytes(State state) {
		state.mCurrentBytes = 0;
		ContentValues values = new ContentValues();
		values.put(Downloads.Impl.COLUMN_CURRENT_BYTES, state.mCurrentBytes);
		mContext.getContentResolver().update(mInfo.getAllDownloadsUri(),
				values, null, null);
	}

	/**
	 * Add custom headers for this download to the HTTP request.
	 */
	private void addRequestHeaders(State state, HttpURLConnection conn) {
		for (Pair<String, String> header : mInfo.getHeaders()) {
			 Log.e(TAG, "header.first==" + header.first + " header.second=="
			 + header.second);
			conn.addRequestProperty(header.first, header.second);
		}

		// Only splice in user agent when not already defined
		if (conn.getRequestProperty("User-Agent") == null) {
			conn.addRequestProperty("User-Agent", userAgent());
		}

		// Defeat transparent gzip compression, since it doesn't allow us to
		// easily resume partial downloads.
		conn.setRequestProperty("Accept-Encoding", "identity");

		if (state.mContinuingDownload) {
			if (state.mHeaderETag != null) {
				if (!state.mHeaderETag.equals(QRD_ETAG)) {
					conn.addRequestProperty("If-Match", state.mHeaderETag);
				}
			}
			File f = new File(state.mFilename);
			state.mCurrentBytes = (int) f.length();
			ContentValues values = new ContentValues();
			values.put(Downloads.Impl.COLUMN_CURRENT_BYTES, state.mCurrentBytes);
			mContext.getContentResolver().update(mInfo.getAllDownloadsUri(),
					values, null, null);
			conn.addRequestProperty("Range", "bytes=" + state.mCurrentBytes
					+ "-");
		}
	}

	/**
	 * Stores information about the completed download, and notifies the
	 * initiating application.
	 */
	private void notifyDownloadCompleted(State state, int finalStatus,
			String errorMsg, int numFailed) {
		notifyThroughDatabase(state, finalStatus, errorMsg, numFailed);
		if (Downloads.Impl.isStatusCompleted(finalStatus)) {
			mInfo.sendIntentIfRequested();
		}
	}

	private void notifyThroughDatabase(State state, int finalStatus,
			String errorMsg, int numFailed) {
		ContentValues values = new ContentValues();
		values.put(Downloads.Impl.COLUMN_STATUS, finalStatus);
		values.put(Downloads.Impl._DATA, state.mFilename);
		values.put(Downloads.Impl.COLUMN_MIME_TYPE, state.mMimeType);
		values.put(Downloads.Impl.COLUMN_LAST_MODIFICATION,
				mSystemFacade.currentTimeMillis());
		values.put(Constants.FAILED_CONNECTIONS, numFailed);
		values.put(Constants.RETRY_AFTER_X_REDIRECT_COUNT, state.mRetryAfter);

		if (!TextUtils.equals(mInfo.mUri, state.mRequestUri)) {
			values.put(Downloads.Impl.COLUMN_URI, state.mRequestUri);
		}

		// save the error message. could be useful to developers.
		if (!TextUtils.isEmpty(errorMsg)) {
			values.put(Downloads.Impl.COLUMN_ERROR_MSG, errorMsg);
		}
		mContext.getContentResolver().update(mInfo.getAllDownloadsUri(),
				values, null, null);
	}

	private INetworkPolicyListener mPolicyListener = new INetworkPolicyListener.Stub() {
		@Override
		public void onUidRulesChanged(int uid, int uidRules) {
			// caller is NPMS, since we only register with them
			if (uid == mInfo.mUid) {
				mPolicyDirty = true;
			}
		}

		@Override
		public void onMeteredIfacesChanged(String[] meteredIfaces) {
			// caller is NPMS, since we only register with them
			mPolicyDirty = true;
		}

		@Override
		public void onRestrictBackgroundChanged(boolean restrictBackground) {
			// caller is NPMS, since we only register with them
			mPolicyDirty = true;
		}
	};

	public static long getHeaderFieldLong(URLConnection conn, String field,
			long defaultValue) {
		try {
			return Long.parseLong(conn.getHeaderField(field));
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	/**
	 * Return if given status is eligible to be treated as
	 * {@link android.provider.Downloads.Impl#STATUS_WAITING_TO_RETRY}.
	 */
	public static boolean isStatusRetryable(int status) {
		switch (status) {
		case STATUS_HTTP_DATA_ERROR:
		case HTTP_UNAVAILABLE:
		case HTTP_INTERNAL_ERROR:
			return true;
		default:
			return false;
		}
	}
}
