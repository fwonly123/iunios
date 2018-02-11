/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.app;

import android.annotation.TargetApi;
import android.app.ActionBar.OnMenuVisibilityListener;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateBeamUrisCallback;
import android.nfc.NfcEvent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.android.camera.CameraActivity;
import com.android.camera.ProxyLauncher;
import com.android.gallery3d.R;
import com.android.gallery3d.app.PhotoDataAdapter;
import com.android.gallery3d.app.AlbumPage.state;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.data.ComboAlbum;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.FilterDeleteSet;
import com.android.gallery3d.data.FilterSource;
import com.android.gallery3d.data.LocalImage;
import com.android.gallery3d.data.LocalVideo;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaObject.PanoramaSupportCallback;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.MtpSource;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.data.SecureAlbum;
import com.android.gallery3d.data.SecureSource;
import com.android.gallery3d.data.SnailAlbum;
import com.android.gallery3d.data.SnailItem;
import com.android.gallery3d.data.SnailSource;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.fragmentutil.MySelfBuildConfig;
import com.android.gallery3d.picasasource.PicasaSource;
import com.android.gallery3d.ui.DetailsHelper;
import com.android.gallery3d.ui.DetailsHelper.CloseListener;
import com.android.gallery3d.ui.DetailsHelper.DetailsSource;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.ImportCompleteListener;
import com.android.gallery3d.ui.MenuExecutor;
import com.android.gallery3d.ui.PhotoView;
import com.android.gallery3d.ui.SelectionManager;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.MediaSetUtils;
import com.android.gallery3d.util.MyLog;
import com.android.gallery3d.util.StatisticsUtils;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
//import aurora.widget.AuroraActionBar;
import aurora.app.AuroraActivity;
import android.view.View;


//Aurora <paul> <2014-02-27> for NEW_UI begin
import com.android.gallery3d.ui.GLCanvas;
import com.android.gallery3d.ui.PhotoFallbackEffect;
import com.android.gallery3d.ui.AuroraGifTexture;
import com.android.gallery3d.xcloudalbum.uploaddownload.UploadDownloadListActivity;


//Aurora <paul> <2014-02-27> for NEW_UI end
import java.util.ArrayList;// Aurora <paul> <2014-03-29> 

import android.content.IntentFilter;

import com.android.gallery3d.xcloudalbum.tools.BaiduAlbumUtils;
import com.aurora.utils.SystemUtils;

import android.content.BroadcastReceiver;
import android.database.ContentObserver;

public class PhotoPage extends ActivityState implements PhotoView.Listener,
		AppBridge.Server, PhotoPageBottomControls.Delegate,
		// Aurora <zhanggp> <2013-12-06> added for gallery begin
		AuroraPhotoPageTopControls.Delegate,
		// Aurora <zhanggp> <2013-12-06> added for gallery end
		GalleryActionBar.OnAlbumModeSelectedListener, SensorEventListener  {
	private static final String TAG = "PhotoPage";

	private static final int MSG_HIDE_BARS = 1;
	private static final int MSG_ON_FULL_SCREEN_CHANGED = 4;
	private static final int MSG_UPDATE_ACTION_BAR = 5;
	private static final int MSG_UNFREEZE_GLROOT = 6;
	private static final int MSG_WANT_BARS = 7;
	private static final int MSG_REFRESH_BOTTOM_CONTROLS = 8;
	private static final int MSG_ON_CAMERA_CENTER = 9;
	private static final int MSG_ON_PICTURE_CENTER = 10;
	private static final int MSG_REFRESH_IMAGE = 11;
	private static final int MSG_UPDATE_PHOTO_UI = 12;
	private static final int MSG_UPDATE_PROGRESS = 13;
	private static final int MSG_UPDATE_DEFERRED = 14;
	private static final int MSG_UPDATE_SHARE_URI = 15;
	private static final int MSG_UPDATE_PANORAMA_UI = 16;

	private static final int HIDE_BARS_TIMEOUT = 3500;
	private static final int UNFREEZE_GLROOT_TIMEOUT = 250;

	private static final int REQUEST_SLIDESHOW = 1;
	private static final int REQUEST_CROP = 2;
	private static final int REQUEST_CROP_PICASA = 3;
	public static final int REQUEST_EDIT = 4;
	private static final int REQUEST_PLAY_VIDEO = 5;
	private static final int REQUEST_TRIM = 6;

	public static final String KEY_MEDIA_SET_PATH = "media-set-path";
	public static final String KEY_MEDIA_ITEM_PATH = "media-item-path";
	public static final String KEY_INDEX_HINT = "index-hint";
	public static final String KEY_OPEN_ANIMATION_RECT = "open-animation-rect";
	public static final String KEY_APP_BRIDGE = "app-bridge";
	public static final String KEY_TREAT_BACK_AS_UP = "treat-back-as-up";
	public static final String KEY_START_IN_FILMSTRIP = "start-in-filmstrip";
	public static final String KEY_RETURN_INDEX_HINT = "return-index-hint";
	public static final String KEY_SHOW_WHEN_LOCKED = "show_when_locked";
	public static final String KEY_IN_CAMERA_ROLL = "in_camera_roll";

	public static final String KEY_ALBUMPAGE_TRANSITION = "albumpage-transition";
	public static final int MSG_ALBUMPAGE_NONE = 0;
	public static final int MSG_ALBUMPAGE_STARTED = 1;
	public static final int MSG_ALBUMPAGE_RESUMED = 2;
	public static final int MSG_ALBUMPAGE_PICKED = 4;

	public static final String ACTION_NEXTGEN_EDIT = "action_nextgen_edit";

	private GalleryApp mApplication;
	private SelectionManager mSelectionManager;

	private PhotoView mPhotoView;
	private PhotoPage.Model mModel;
	private DetailsHelper mDetailsHelper;
	private boolean mShowDetails;

	// mMediaSet could be null if there is no KEY_MEDIA_SET_PATH supplied.
	// E.g., viewing a photo in gmail attachment
	private FilterDeleteSet mMediaSet;

	// The mediaset used by camera launched from secure lock screen.
	private SecureAlbum mSecureAlbum;

	private int mCurrentIndex = 0;
	private Handler mHandler;
	// Aurora <zhanggp> <2013-12-23> modified for gallery begin
	private boolean mShowBars = false;
	// Aurora <zhanggp> <2013-12-23> modified for gallery end
	private volatile boolean mActionBarAllowed = true;
	private GalleryActionBar mActionBar;
	private boolean mIsMenuVisible;
	private boolean mHaveImageEditor;
	private PhotoPageBottomControls mBottomControls;
	// Aurora <zhanggp> <2013-12-06> added for gallery begin
	private AuroraPhotoPageTopControls mTopControls;
	private boolean mFromSet;
	// Aurora <zhanggp> <2013-12-06> added for gallery end

	private PhotoPageProgressBar mProgressBar;
	private MediaItem mCurrentPhoto = null;
	private MenuExecutor mMenuExecutor;
	private boolean mIsActive;
	private boolean mShowSpinner;
	private String mSetPathString;
	// This is the original mSetPathString before adding the camera preview
	// item.
	private String mOriginalSetPathString;
	private AppBridge mAppBridge;
	private SnailItem mScreenNailItem;
	private SnailAlbum mScreenNailSet;
	private OrientationManager mOrientationManager;
	// private boolean mTreatBackAsUp; paul del
	private boolean mStartInFilmstrip;
	private boolean mHasCameraScreennailOrPlaceholder = false;
	private boolean mRecenterCameraOnResume = true;

	// These are only valid after the panorama callback
	private boolean mIsPanorama;
	private boolean mIsPanorama360;

	private long mCameraSwitchCutoff = 0;
	private boolean mSkipUpdateCurrentPhoto = false;
	private static final long CAMERA_SWITCH_CUTOFF_THRESHOLD_MS = 300;

	private static final long DEFERRED_UPDATE_MS = 250;
	private boolean mDeferredUpdateWaiting = false;
	private long mDeferUpdateUntil = Long.MAX_VALUE;

	// The item that is deleted (but it can still be undeleted before commiting)
	private Path mDeletePath;
	private boolean mDeleteIsFocus; // whether the deleted item was in focus
	private Uri[] mNfcPushUris = new Uri[1];

	private final MyMenuVisibilityListener mMenuVisibilityListener = new MyMenuVisibilityListener();
	private UpdateProgressListener mProgressListener;

	private final PanoramaSupportCallback mUpdatePanoramaMenuItemsCallback = new PanoramaSupportCallback() {
		@Override
		public void panoramaInfoAvailable(MediaObject mediaObject,
				boolean isPanorama, boolean isPanorama360) {
			if (mediaObject == mCurrentPhoto) {
				mHandler.obtainMessage(MSG_UPDATE_PANORAMA_UI,
						isPanorama360 ? 1 : 0, 0, mediaObject).sendToTarget();
			}
		}
	};

	private final PanoramaSupportCallback mRefreshBottomControlsCallback = new PanoramaSupportCallback() {
		@Override
		public void panoramaInfoAvailable(MediaObject mediaObject,
				boolean isPanorama, boolean isPanorama360) {
			if (mediaObject == mCurrentPhoto) {
				mHandler.obtainMessage(MSG_REFRESH_BOTTOM_CONTROLS,
						isPanorama ? 1 : 0, isPanorama360 ? 1 : 0, mediaObject)
						.sendToTarget();
			}
		}
	};

	private final PanoramaSupportCallback mUpdateShareURICallback = new PanoramaSupportCallback() {
		@Override
		public void panoramaInfoAvailable(MediaObject mediaObject,
				boolean isPanorama, boolean isPanorama360) {
			if (mediaObject == mCurrentPhoto) {
				mHandler.obtainMessage(MSG_UPDATE_SHARE_URI,
						isPanorama360 ? 1 : 0, 0, mediaObject).sendToTarget();
			}
		}
	};

	public static interface Model extends PhotoView.Model {
		public void resume();

		public void pause();

		public boolean isEmpty();

		public void setCurrentPhoto(Path path, int indexHint);

		public void deletingItem();// Aurora <paul> <2014-04-24>
	}

	private class MyMenuVisibilityListener implements OnMenuVisibilityListener {
		@Override
		public void onMenuVisibilityChanged(boolean isVisible) {
			mIsMenuVisible = isVisible;
			refreshHidingMessage();
		}
	}

	private class UpdateProgressListener implements StitchingChangeListener {

		@Override
		public void onStitchingResult(Uri uri) {
			sendUpdate(uri, MSG_REFRESH_IMAGE);
		}

		@Override
		public void onStitchingQueued(Uri uri) {
			sendUpdate(uri, MSG_UPDATE_PROGRESS);
		}

		@Override
		public void onStitchingProgress(Uri uri, final int progress) {
			sendUpdate(uri, MSG_UPDATE_PROGRESS);
		}

		private void sendUpdate(Uri uri, int message) {
			MediaObject currentPhoto = mCurrentPhoto;
			boolean isCurrentPhoto = currentPhoto instanceof LocalImage
					&& currentPhoto.getContentUri().equals(uri);
			if (isCurrentPhoto) {
				mHandler.sendEmptyMessage(message);
			}
		}
	};

	@Override
	protected int getBackgroundColorId() {
		return R.color.photo_background;
	}

	private GLView mRootPane = new GLView() { // Aurora <paul> <2014-02-27> for
												// NEW_UI
		@Override
		protected void onLayout(boolean changed, int left, int top, int right,
				int bottom) {
			mPhotoView.layout(0, 0, right - left, bottom - top);
			if (mShowDetails) {
				// Aurora <zhanggp> <2013-12-06> modified for gallery begin
				// mDetailsHelper.layout(left, mActionBar.getHeight(), right,
				// bottom);
				mDetailsHelper.layout(left, mTopControls.getHeight(), right,
						bottom);
				// Aurora <zhanggp> <2013-12-06> modified for gallery end
			}
		}
	};

	// Aurora <paul> <2014-02-27> for NEW_UI begin
	private boolean mFromAlbumPage = false;
	private boolean mPhotoPageFinished = false;

	public int onBackToAlbum() {
		if (mPhotoView.CanBackToAlbum()) {
			hideBars();
			return mCurrentIndex;
		}
		return -1;
	}

	// Aurora <SQF> <2014-6-18> for NEW_UI begin
	public void setToFinishPhotoPage(boolean toFinish) {
		mPhotoPageFinished = toFinish;
	}

	// Aurora <SQF> <2014-6-18> for NEW_UI end

	public boolean toFinishPhotoPage() {
		return mPhotoPageFinished;
	}

	public void finishState() {
		mPhotoPageFinished = true;
		mActivity.getStateManager().getTopState().onBackPressed();
		mPhotoPageFinished = false;
	}

	public void setPhotoIndex(int index) {
		mCurrentIndex = index;
		mPhotoView.setFilmModeEx(false);
		mModel.skipTo(mCurrentIndex);
	}

	public PhotoView getPhotoView() {
		return mPhotoView;
	}

	public PhotoFallbackEffect getFallbackEffect(GLCanvas canvas) {
		return mPhotoView.buildFallbackEffectCenter(mRootPane, canvas);
	}

	public void toLayout(boolean changed, int left, int top, int right,
			int bottom) {

		mPhotoView.layout(0, 0, right - left, bottom - top);
		if (mShowDetails) {
			mDetailsHelper.layout(left, mActionBar.getHeight(), right, bottom);
		}

	}

	public void createDirect(Bundle data, Bundle restoreState, GLView root) {
		// Log.i("SQF_LOG", "PhotoPage::createDirect");
		mRootPane = root;
		mFromAlbumPage = true;
		onCreate(data, restoreState);

	}

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver(){
        
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BaiduAlbumUtils.ACTION_IMAGE_DOWNLOADED)) {
				if(null != mModel && mActivity.inCloudView()){
					mModel.reloadCurrentIndex();
				}
            }
        }
    };
	

	// Aurora <SQF> <2014-04-04> for NEW_UI begin
	public void setEnteringBitmap(int index, Bitmap bmp, int rotation) {
		if (null != mModel && (mModel instanceof PhotoDataAdapter)) {
			PhotoDataAdapter pda = (PhotoDataAdapter) mModel;
			pda.setEnteringBitmap(index, bmp, rotation);
		}
	}

	// Aurora <SQF> <2014-04-04> for NEW_UI end

	// Aurora <paul> <2014-02-27> for NEW_UI end
	@Override
	public void onCreate(Bundle data, Bundle restoreState) {
		super.onCreate(data, restoreState);
		//MyLog.i2("SQF_LOG", "PhotoPage::onCreate----> ");
		if (MySelfBuildConfig.USEGALLERY3D_FLAG) {
			setContentPaneBackGroud(false);// Iuni <lory><2014-02-28> add begin
		}
		//paul add start
		if(mActivity.inCloudView()){
			IntentFilter filter = new IntentFilter(BaiduAlbumUtils.ACTION_IMAGE_DOWNLOADED);
			mActivity.registerReceiver(mIntentReceiver, filter); 
		}
		//paul add end
		mActionBar = mActivity.getGalleryActionBar();
		mSelectionManager = new SelectionManager(mActivity, false);
		mMenuExecutor = new MenuExecutor(mActivity, mSelectionManager);

		mPhotoView = new PhotoView(mActivity);
		mPhotoView.setListener(this);
		mRootPane.addComponent(mPhotoView);
		// Aurora <SQF> <2014-6-20> for NEW_UI begin
		mMenuExecutor
				.setAlertDialogShowStatusListener(new MenuExecutor.AlertDialogShowStatusListener() {

					@Override
					public void notifyAlertDialogStatus(boolean isShowing) {
						// TODO Auto-generated method stub
						mPhotoView.setIsShowingAlertDialog(isShowing);
					}
				});
		// Aurora <SQF> <2014-6-20> for NEW_UI end

		// Aurora <paul> <2014-02-27> for NEW_UI begin
		if (mFromAlbumPage) {
			mPhotoView.setVisibility(GLView.INVISIBLE);
		}
		// Aurora <paul> <2014-02-27> for NEW_UI end
		mApplication = (GalleryApp) ((Activity) mActivity).getApplication();
		mOrientationManager = mActivity.getOrientationManager();
		mActivity.getGLRoot().setOrientationSource(mOrientationManager);
		// Aurora <paul> <2013-12-26> added for gallery begin
		mFromSet = true;
		// Aurora <paul> <2013-12-26> added for gallery end
		mHandler = new SynchronizedHandler(mActivity.getGLRoot()) {
			@Override
			public void handleMessage(Message message) {
				switch (message.what) {
				case MSG_HIDE_BARS: {
					hideBars();
					break;
				}
				case MSG_REFRESH_BOTTOM_CONTROLS: {
					if (mCurrentPhoto == message.obj && mBottomControls != null) {
						mIsPanorama = message.arg1 == 1;
						mIsPanorama360 = message.arg2 == 1;
						mBottomControls.refresh();
						// Aurora <zhanggp> <2013-12-06> added for gallery begin
						if (mTopControls != null) {
							mTopControls.refresh();
						}
						// Aurora <zhanggp> <2013-12-06> added for gallery end
					}
					break;
				}
				case MSG_ON_FULL_SCREEN_CHANGED: {
					if (mAppBridge != null) {
						mAppBridge.onFullScreenChanged(message.arg1 == 1);
					}
					break;
				}
				case MSG_UPDATE_ACTION_BAR: {
					updateBars();
					break;
				}
				case MSG_WANT_BARS: {
					wantBars();
					break;
				}
				case MSG_UNFREEZE_GLROOT: {
					mActivity.getGLRoot().unfreeze();
					break;
				}
				case MSG_UPDATE_DEFERRED: {
					long nextUpdate = mDeferUpdateUntil
							- SystemClock.uptimeMillis();
					if (nextUpdate <= 0) {
						mDeferredUpdateWaiting = false;
						updateUIForCurrentPhoto();
					} else {
						mHandler.sendEmptyMessageDelayed(MSG_UPDATE_DEFERRED,
								nextUpdate);
					}
					break;
				}
				case MSG_ON_CAMERA_CENTER: {
					mSkipUpdateCurrentPhoto = false;
					boolean stayedOnCamera = false;
					if (!mPhotoView.getFilmMode()) {
						stayedOnCamera = true;
					} else if (SystemClock.uptimeMillis() < mCameraSwitchCutoff
							&& mMediaSet.getMediaItemCount() > 1) {
						mPhotoView.switchToImage(1);
					} else {
						if (mAppBridge != null)
							mPhotoView.setFilmMode(false);
						stayedOnCamera = true;
					}

					if (stayedOnCamera) {
						if (mAppBridge == null) {
							launchCamera();
							/*
							 * We got here by swiping from photo 1 to the
							 * placeholder, so make it be the thing that is in
							 * focus when the user presses back from the camera
							 * app
							 */
							mPhotoView.switchToImage(1);
						} else {
							updateBars();
							updateCurrentPhoto(mModel.getMediaItem(0));
						}
					}
					break;
				}
				case MSG_ON_PICTURE_CENTER: {
					if (!mPhotoView.getFilmMode()
							&& mCurrentPhoto != null
							&& (mCurrentPhoto.getSupportedOperations() & MediaObject.SUPPORT_ACTION) != 0) {
						mPhotoView.setFilmMode(true);
					}
					break;
				}
				case MSG_REFRESH_IMAGE: {
					final MediaItem photo = mCurrentPhoto;
					mCurrentPhoto = null;
					updateCurrentPhoto(photo);
					break;
				}
				case MSG_UPDATE_PHOTO_UI: {
					updateUIForCurrentPhoto();
					break;
				}
				case MSG_UPDATE_PROGRESS: {
					updateProgressBar();
					break;
				}
				case MSG_UPDATE_SHARE_URI: {
					if (mCurrentPhoto == message.obj) {
						boolean isPanorama360 = message.arg1 != 0;
						Uri contentUri = mCurrentPhoto.getContentUri();
						Intent panoramaIntent = null;
						if (isPanorama360) {
							panoramaIntent = createSharePanoramaIntent(contentUri);
						}
						Intent shareIntent = createShareIntent(mCurrentPhoto);

						mActionBar.setShareIntents(panoramaIntent, shareIntent);
						setNfcBeamPushUri(contentUri);
					}
					break;
				}
				case MSG_UPDATE_PANORAMA_UI: {
					if (mCurrentPhoto == message.obj) {
						boolean isPanorama360 = message.arg1 != 0;
						updatePanoramaUI(isPanorama360);
					}
					break;
				}
				default:
					throw new AssertionError(message.what);
				}
			}
		};

		mSetPathString = data.getString(KEY_MEDIA_SET_PATH);
		mOriginalSetPathString = mSetPathString;
		setupNfcBeamPush();
		String itemPathString = data.getString(KEY_MEDIA_ITEM_PATH);
		Path itemPath = itemPathString != null ? Path.fromString(data
				.getString(KEY_MEDIA_ITEM_PATH)) : null;
		// mTreatBackAsUp = data.getBoolean(KEY_TREAT_BACK_AS_UP, false); paul
		// del
		mStartInFilmstrip = data.getBoolean(KEY_START_IN_FILMSTRIP, false);
		// Aurora <zhanggp> <2013-12-09> modified for gallery begin
		boolean inCameraRoll = false;
		// boolean inCameraRoll = data.getBoolean(KEY_IN_CAMERA_ROLL, false);
		// Aurora <zhanggp> <2013-12-09> modified for gallery end
		if (mFromAlbumPage) {// Aurora <paul> <2014-03-03> for NEW_UI
			mCurrentIndex = 0;
		} else {
			mCurrentIndex = data.getInt(KEY_INDEX_HINT, 0);
		}
		if (mSetPathString != null) {
			mShowSpinner = true;
			mAppBridge = (AppBridge) data.getParcelable(KEY_APP_BRIDGE);
			if (mAppBridge != null) {
				mShowBars = false;
				mHasCameraScreennailOrPlaceholder = true;
				mAppBridge.setServer(this);

				// Get the ScreenNail from AppBridge and register it.
				int id = SnailSource.newId();
				Path screenNailSetPath = SnailSource.getSetPath(id);
				Path screenNailItemPath = SnailSource.getItemPath(id);
				mScreenNailSet = (SnailAlbum) mActivity.getDataManager()
						.getMediaObject(screenNailSetPath);
				mScreenNailItem = (SnailItem) mActivity.getDataManager()
						.getMediaObject(screenNailItemPath);
				mScreenNailItem.setScreenNail(mAppBridge.attachScreenNail());

				if (data.getBoolean(KEY_SHOW_WHEN_LOCKED, false)) {
					// Set the flag to be on top of the lock screen.
					mFlags |= FLAG_SHOW_WHEN_LOCKED;
				}

				// Don't display "empty album" action item for capture intents.
				if (!mSetPathString.equals("/local/all/0")) {
					// Check if the path is a secure album.
					if (SecureSource.isSecurePath(mSetPathString)) {
						mSecureAlbum = (SecureAlbum) mActivity.getDataManager()
								.getMediaSet(mSetPathString);
						mShowSpinner = false;
					}
					mSetPathString = "/filter/empty/{" + mSetPathString + "}";
				}

				// Combine the original MediaSet with the one for ScreenNail
				// from AppBridge.
				mSetPathString = "/combo/item/{" + screenNailSetPath + ","
						+ mSetPathString + "}";

				// Start from the screen nail.
				itemPath = screenNailItemPath;
			} else if (inCameraRoll
					&& GalleryUtils.isCameraAvailable(mActivity)) {
				mSetPathString = "/combo/item/{"
						+ FilterSource.FILTER_CAMERA_SHORTCUT + ","
						+ mSetPathString + "}";
				mCurrentIndex++;
				mHasCameraScreennailOrPlaceholder = true;
			}
			MediaSet originalSet = mActivity.getDataManager().getMediaSet(
					mSetPathString);
			if (mHasCameraScreennailOrPlaceholder
					&& originalSet instanceof ComboAlbum) {
				// Use the name of the camera album rather than the default
				// ComboAlbum behavior
				((ComboAlbum) originalSet).useNameOfChild(1);
			}
			mSelectionManager.setSourceMediaSet(originalSet);
			mSetPathString = "/filter/delete/{" + mSetPathString + "}";
			mMediaSet = (FilterDeleteSet) mActivity.getDataManager()
					.getMediaSet(mSetPathString);
			if (mMediaSet == null) {
				Log.w(TAG, "failed to restore " + mSetPathString);
			}
			if (itemPath == null) {
				int mediaItemCount = mMediaSet.getMediaItemCount();
				if (mediaItemCount > 0) {
					if (mCurrentIndex >= mediaItemCount)
						mCurrentIndex = 0;
					// Aurora <paul> <2014-03-29> start
					ArrayList<MediaItem> list = mMediaSet.getMediaItem(
							mCurrentIndex, 1);

					if (null == list || list.size() <= 0) {
						return;
					}

					itemPath = list.get(0).getPath();
					/*
					 * itemPath = mMediaSet.getMediaItem(mCurrentIndex, 1)
					 * .get(0).getPath();
					 */
					// Aurora <paul> <2014-03-29> end
				} else {
					// Bail out, PhotoPage can't load on an empty album
					Log.e(TAG, "PhotoPage can't load on an empty album");
					if (!mFromAlbumPage) {// Aurora <paul> <2014-03-06> for
											// NEW_UI
						return;
					}
				}
			}
			PhotoDataAdapter pda = new PhotoDataAdapter(mActivity, mPhotoView,
					mMediaSet, itemPath, mCurrentIndex, mAppBridge == null ? -1
							: 0, mAppBridge == null ? false
							: mAppBridge.isPanorama(),
					mAppBridge == null ? false : mAppBridge.isStaticCamera(),
					mFromAlbumPage);// Aurora <paul> <2014-02-27> for NEW_UI
			mModel = pda;
			mPhotoView.setModel(mModel);

			pda.setDataListener(new PhotoDataAdapter.DataListener() {
				// Aurora <paul> <2014-05-12> start
				@Override
				public void onFakeChanged(int index, Path item, int count) {
					int oldIndex = mCurrentIndex;
					mCurrentIndex = index;

					if (null != mMediaSet) {
						if (count > 0 && count <= mCurrentIndex) {
							mCurrentIndex = mMediaSet.getMediaItemCount() - 1;
							mModel.moveTo(mCurrentIndex);
						}
					}
					if (null != mTopControls) {
						mTopControls
								.setTitle(mMediaSet != null ? (mCurrentIndex + 1)
										+ "/" + count
										: "");
					}

					if (item != null) {
						MediaItem photo = mModel.getMediaItem(0);
						if (photo != null)
							updateCurrentPhoto(photo);
					}
					updateBars();

					// Reset the timeout for the bars after a swipe
					refreshHidingMessage();
				}

				// Aurora <paul> <2014-05-12> end
				@Override
				public void onPhotoChanged(int index, Path item) {
					int oldIndex = mCurrentIndex;
					mCurrentIndex = index;

					// Aurora <zhanggp> <2013-12-06> added for gallery begin
					
					if (null != mMediaSet) {
						int count = mMediaSet.getMediaItemCount();
						if (count > 0 && count <= mCurrentIndex) {
							mCurrentIndex = mMediaSet.getMediaItemCount() - 1;
							mModel.moveTo(mCurrentIndex);
						}
					}

					if (null != mTopControls) {
						mTopControls
								.setTitle(mMediaSet != null ? (mCurrentIndex + 1)
										+ "/" + mMediaSet.getMediaItemCount()
										: "");
					}
					// Aurora <zhanggp> <2013-12-06> added for gallery end
					if (mHasCameraScreennailOrPlaceholder) {
						if (mCurrentIndex > 0) {
							mSkipUpdateCurrentPhoto = false;
						}

						if (oldIndex == 0 && mCurrentIndex > 0
								&& !mPhotoView.getFilmMode()) {
							mPhotoView.setFilmMode(true);
						} else if (oldIndex == 2 && mCurrentIndex == 1) {
							mCameraSwitchCutoff = SystemClock.uptimeMillis()
									+ CAMERA_SWITCH_CUTOFF_THRESHOLD_MS;
							mPhotoView.stopScrolling();
						} else if (oldIndex >= 1 && mCurrentIndex == 0) {
							mPhotoView.setWantPictureCenterCallbacks(true);
							mSkipUpdateCurrentPhoto = true;
						}
					}
					if (!mSkipUpdateCurrentPhoto) {
						if (item != null) {
							MediaItem photo = mModel.getMediaItem(0);
							if (photo != null)
								updateCurrentPhoto(photo);
						}
						updateBars();
					}
					// Reset the timeout for the bars after a swipe
					refreshHidingMessage();
				}

				@Override
				public void onLoadingFinished(boolean loadingFailed) {
					if (!mModel.isEmpty()) {
						MediaItem photo = mModel.getMediaItem(0);
						if (photo != null)
							updateCurrentPhoto(photo);
					} else if (mIsActive) {
						// We only want to finish the PhotoPage if there is no
						// deletion that the user can undo.
						if (mMediaSet.getNumberOfDeletions() == 0) {
							if (mFromAlbumPage) {// Aurora <paul> <2014-03-03>
													// for NEW_UI
								finishState();
							} else {
								mActivity.getStateManager().finishState(
										PhotoPage.this);
							}
						}
					}

					// onHideFragment();
				}

				@Override
				public void onLoadingStarted() {
				}
			});
		} else {
			// Get default media set by the URI
			// Log.i("SQF_LOG",
			// "PhotoPage::onCreate --> new SinglePhotoDataAdapter itemPath:" +
			// itemPath.toString());
			MediaItem mediaItem = (MediaItem) mActivity.getDataManager()
					.getMediaObject(itemPath);
			mModel = new SinglePhotoDataAdapter(mActivity, mPhotoView,
					mediaItem);
			mPhotoView.setModel(mModel);
			updateCurrentPhoto(mediaItem);
			mShowSpinner = false;
			// Aurora <paul> <2013-12-26> added for gallery begin
			mFromSet = false;
			// Aurora <paul> <2013-12-26> added for gallery end
		}

		mPhotoView.setFilmMode(mStartInFilmstrip
				&& mMediaSet.getMediaItemCount() > 1);
		RelativeLayout galleryRoot = (RelativeLayout) ((Activity) mActivity)
				.findViewById(mAppBridge != null ? R.id.content
						: R.id.gallery_root);
		if (galleryRoot != null) {
			if (mSecureAlbum == null) {
				// Aurora <zhanggp> <2013-12-06> added for gallery begin
				mTopControls = new AuroraPhotoPageTopControls(this, mActivity,
						galleryRoot);
				// Aurora <zhanggp> <2013-12-06> added for gallery end
				mBottomControls = new PhotoPageBottomControls(this, mActivity,
						galleryRoot);
			}
			StitchingProgressManager progressManager = mApplication
					.getStitchingProgressManager();
			if (progressManager != null) {
				mProgressBar = new PhotoPageProgressBar(mActivity, galleryRoot);
				mProgressListener = new UpdateProgressListener();
				progressManager.addChangeListener(mProgressListener);
				if (mSecureAlbum != null) {
					progressManager.addChangeListener(mSecureAlbum);
				}
			}
		}

	}

	@Override
	public void onPictureCenter(boolean isCamera) {
		isCamera = isCamera
				|| (mHasCameraScreennailOrPlaceholder && mAppBridge == null);
		mPhotoView.setWantPictureCenterCallbacks(false);
		mHandler.removeMessages(MSG_ON_CAMERA_CENTER);
		mHandler.removeMessages(MSG_ON_PICTURE_CENTER);
		mHandler.sendEmptyMessage(isCamera ? MSG_ON_CAMERA_CENTER
				: MSG_ON_PICTURE_CENTER);
	}

	// Aurora <zhanggp> <2013-12-06> added for gallery begin
	private boolean Aurora_toShowEditIcon() {
		return mShowBars && !mPhotoView.getFilmMode();
	}

	@Override
	public boolean canDisplayTopControl(int control) {
		if (mCurrentPhoto == null) {
			return false;
		}
		if(mActivity.inCloudView()) return false;
		boolean showEdit = Aurora_toShowEditIcon();
		switch (control) {
		// case R.id.leftButton:
		case R.id.leftTextView:
		case R.id.photopage_top_control_bgView:
			return showEdit;
		case R.id.rightButton:
			// Aurora <SQF> <2014-6-18> for NEW_UI begin
			// ORIGINALLY:
			 return showEdit && mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE;
			// MODIFIED TO:
			//return showEdit;
			// Aurora <SQF> <2014-6-18> for NEW_UI begin

		default:
			break;
		}
		return false;

	}

	@Override
	public boolean canDisplayTopControls() {
		return canDisplayBottomControls();
	}

	@Override
	public boolean showEdit() {
		return Aurora_toShowEditIcon();
	}

	// Aurora <SQF> <2015-03-30> for NEW_UI begin

/*	@Override
	public boolean isShortVideo() {
		MediaItem current = mModel.getMediaItem(0);
		if (!(current instanceof LocalVideo))
			return false;
		LocalVideo video = (LocalVideo) current;
		if (video.durationInSec > 10)
			return false;
		return true;
	}*/

	/*
	public void showShareShortVideoPopupWindow() {
		//Log.i("SQF_LOG", "showShareShortVideoPopupWindow------------------------------");
		ShareShortVideoPopupWindowManager manager = new ShareShortVideoPopupWindowManager();
		MediaItem current = mModel.getMediaItem(0);
		manager.setShortVideoFilePath(current.getPlayUri());
		manager.initPopupWindow(mActivity);
		manager.show();
	}
	*/

	// Aurora <SQF> <2015-03-30> for NEW_UI end

	@Override
	public void onTopControlClicked(int control) {
		if (mModel == null)
			return;
		refreshHidingMessage();
		MediaItem current = mModel.getMediaItem(0);

		if (current == null) {
			// item is not ready, ignore
			return;
		}

		switch (control) {
		// case R.id.leftButton:
		case R.id.leftTextView:
			onBackPressed();
			return;
		case R.id.rightButton:
			// Aurora <SQF> <2015-03-30> for NEW_UI end
			// ORIGINALLY:
			AuroraSlideshowPage();
			// MODIFIED TO:
			
			/*
			Intent intent = new Intent(mActivity, UploadDownloadListActivity.class);
			mActivity.startActivity(intent);
			*/
			
			/*
			if (isShortVideo()) {
				showShareShortVideoPopupWindow();
			} else {
				AuroraSlideshowPage();
			}
			*/
			// Aurora <SQF> <2015-03-30> for NEW_UI end
			return;

		default:
			return;

		}
	}

	/*
	 * @Override public void refreshTopControlsWhenReady() {
	 * refreshBottomControlsWhenReady(); }
	 */

	private void AuroraSlideshowPage() {
		if (mModel == null)
			return;
		refreshHidingMessage();
		MediaItem current = mModel.getMediaItem(0);

		if (current == null) {
			// item is not ready, ignore
			return;
		}

		// Aurora <SQF> <2014-08-06> for NEW_UI begin
		mIsActive = false;
		mTopControls.refresh();
		mBottomControls.refresh();
		mShowBars = false;
		// Aurora <SQF> <2014-08-06> for NEW_UI end

		int currentIndex = mModel.getCurrentIndex();
		Path path = current.getPath();

		Bundle data = new Bundle();
		data.putString(SlideshowPage.KEY_SET_PATH, mMediaSet.getPath()
				.toString());
		data.putString(SlideshowPage.KEY_ITEM_PATH, path.toString());
		data.putInt(SlideshowPage.KEY_PHOTO_INDEX, currentIndex);
		data.putBoolean(SlideshowPage.KEY_REPEAT, true);
		mActivity.getStateManager().startStateForResult(SlideshowPage.class,
				REQUEST_SLIDESHOW, data);

	}

	// Aurora <zhanggp> <2013-12-06> added for gallery end

	@Override
	public boolean canDisplayBottomControls() {
		// Aurora <paul> <2013-12-26> modified for gallery begin
		return mFromSet && mIsActive && !mPhotoView.canUndo() /* && mShowBars */;// SQF
																				// ADD
																				// "&& mShowBars"
																				// ON
																				// 2014.7.25
		// Aurora <paul> <2013-12-26> modified for gallery end
	}

	// Aurora <SQF> <2014-07-23> for NEW_UI begin
	private boolean isToShowEditOnBottomControl(int control) {
		boolean canEdit = true;
		if (AuroraGifTexture.isGif(mCurrentPhoto.getFilePath())
				|| mCurrentPhoto.getMediaType() != MediaObject.MEDIA_TYPE_IMAGE) {
			canEdit = false;
		}
		return canEdit;
	}

	// Aurora <SQF> <2014-07-23> for NEW_UI end

	@Override
	public boolean canDisplayBottomControl(int control) {
		if (mCurrentPhoto == null) {
			return false;
		}

		// Aurora <zhanggp> <2013-12-06> modified for gallery begin
		if(mActivity.inCloudView()) return false;
		
		boolean showEdit = Aurora_toShowEditIcon();
		if (!showEdit)
			return false;
		switch (control) {
		case R.id.photopage_bottom_images:
		case R.id.photopage_bottom_control_share:
		case R.id.photopage_bottom_control_delete:
		case R.id.photopage_bottom_control_detail:
		case R.id.photopage_bottom_control_bgView:
			return true;
		case R.id.photopage_bottom_control_set:
			return mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE;
		case R.id.photopage_bottom_control_set_gray:
			return mCurrentPhoto.getMediaType() != MediaObject.MEDIA_TYPE_IMAGE;
			// Aurora <SQF> <2014-07-23> for NEW_UI begin
			// ORIGINALLY:
			/*
			 * case R.id.photopage_bottom_control_edit: case
			 * R.id.photopage_bottom_control_edit_gray: boolean canEdit = true;
			 * if(AuroraGifTexture.isGif(mCurrentPhoto.getFilePath()) ||
			 * !showEdit || mCurrentPhoto.getMediaType() !=
			 * MediaObject.MEDIA_TYPE_IMAGE){ canEdit = false; }
			 * if(R.id.photopage_bottom_control_edit == control) return canEdit;
			 * return !canEdit;
			 */
			// SQF MODIFIED TO:
		case R.id.photopage_bottom_control_edit:
			return isToShowEditOnBottomControl(control);
		case R.id.photopage_bottom_control_edit_gray:
			// return isToShowEditGrayOnBottomControl(control);
			return !isToShowEditOnBottomControl(control);
			// Aurora <SQF> <2014-07-23> for NEW_UI end

		default:
			break;
		}
		return false;
		/*
		 * switch(control) { case R.id.photopage_bottom_control_edit: return
		 * mHaveImageEditor && mShowBars && !mPhotoView.getFilmMode() &&
		 * (mCurrentPhoto.getSupportedOperations() & MediaItem.SUPPORT_EDIT) !=
		 * 0 && mCurrentPhoto.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE;
		 * case R.id.photopage_bottom_control_panorama: return mIsPanorama; case
		 * R.id.photopage_bottom_control_tiny_planet: return mHaveImageEditor &&
		 * mShowBars && mIsPanorama360 && !mPhotoView.getFilmMode(); default:
		 * return false; }
		 */
		// Aurora <zhanggp> <2013-12-06> modified for gallery end
	}

	@Override
	public void onBottomControlClicked(int control) {
		// Aurora <zhanggp> <2013-12-06> modified for gallery begin
		String confirmMsg;

		if (mModel == null)
			return;
		refreshHidingMessage();
		MediaItem current = mModel.getMediaItem(0);

		if (current == null) {
			// item is not ready, ignore
			return;
		}
		Path path = current.getPath();

		switch (control) {
		case R.id.photopage_bottom_control_share:
			if (mCurrentPhoto == null) {
				return;
			}
			StatisticsUtils.addPhotoPageShareStatistics(mActivity
					.getBaseContext());
			Intent intent = new Intent(Intent.ACTION_SEND);
			intent.setType(mCurrentPhoto.getMimeType());
			intent.putExtra(Intent.EXTRA_STREAM, mCurrentPhoto.getContentUri());
			GalleryUtils.shareContent(mActivity, intent);
			return;

		case R.id.photopage_bottom_control_detail:
			showDetails();
			return;

		case R.id.photopage_bottom_control_delete:
			if (mPhotoView.isShowingAlertDialog()) {
				return;
			}
			StatisticsUtils.addPhotoPageDeleteStatistics(mActivity
					.getBaseContext());
			confirmMsg = mActivity.getResources().getQuantityString(
					R.plurals.delete_selection, 1);
			mSelectionManager.deSelectAll();
			mSelectionManager.toggle(path);
			mMenuExecutor.onMenuClickedEx(R.id.action_delete, confirmMsg,
					mDeleteDialogListener);
			return;
		case R.id.photopage_bottom_control_set:
			StatisticsUtils.addPhotoPageSetAsStatistics(mActivity
					.getBaseContext());
			mSelectionManager.deSelectAll();
			mSelectionManager.toggle(path);
			mMenuExecutor.onMenuClickedEx(R.id.action_setas, null,
					mConfirmDialogListener);
			return;

		case R.id.photopage_bottom_control_edit:
			mSelectionManager.deSelectAll();
			mSelectionManager.toggle(path);
			mMenuExecutor.onMenuClickedEx(R.id.action_edit, null,
					mConfirmDialogListener);
			return;

		default:
			return;
		}
		/*
		 * switch(control) { case R.id.photopage_bottom_control_edit:
		 * launchPhotoEditor(); return; case
		 * R.id.photopage_bottom_control_panorama:
		 * mActivity.getPanoramaViewHelper()
		 * .showPanorama(mCurrentPhoto.getContentUri()); return; case
		 * R.id.photopage_bottom_control_tiny_planet: launchTinyPlanet();
		 * return; default: return; }
		 */
		// Aurora <zhanggp> <2013-12-06> modified for gallery end
	}

	@TargetApi(ApiHelper.VERSION_CODES.JELLY_BEAN)
	private void setupNfcBeamPush() {
		if (!ApiHelper.HAS_SET_BEAM_PUSH_URIS)
			return;

		NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mActivity);
		if (adapter != null) {
			adapter.setBeamPushUris(null, mActivity);
			adapter.setBeamPushUrisCallback(new CreateBeamUrisCallback() {
				@Override
				public Uri[] createBeamUris(NfcEvent event) {
					return mNfcPushUris;
				}
			}, mActivity);
		}
	}

	private void setNfcBeamPushUri(Uri uri) {
		mNfcPushUris[0] = uri;
	}

	private static Intent createShareIntent(MediaObject mediaObject) {
		int type = mediaObject.getMediaType();
		return new Intent(Intent.ACTION_SEND)
				.setType(MenuExecutor.getMimeType(type))
				.putExtra(Intent.EXTRA_STREAM, mediaObject.getContentUri())
				.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
	}

	private static Intent createSharePanoramaIntent(Uri contentUri) {
		return new Intent(Intent.ACTION_SEND)
				.setType(GalleryUtils.MIME_TYPE_PANORAMA360)
				.putExtra(Intent.EXTRA_STREAM, contentUri)
				.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
	}

	private void overrideTransitionToEditor() {
		((Activity) mActivity).overridePendingTransition(
				android.R.anim.slide_in_left, android.R.anim.fade_out);
	}

	private void launchTinyPlanet() {
		// Deep link into tiny planet
		MediaItem current = mModel.getMediaItem(0);
		Intent intent = new Intent(FilterShowActivity.TINY_PLANET_ACTION);
		intent.setClass(mActivity, FilterShowActivity.class);
		intent.setDataAndType(current.getContentUri(), current.getMimeType())
				.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		intent.putExtra(FilterShowActivity.LAUNCH_FULLSCREEN,
				mActivity.isFullscreen());
		mActivity.startActivityForResult(intent, REQUEST_EDIT);
		overrideTransitionToEditor();
	}

	private void launchCamera() {
		// Aurora <zhanggp> <2013-12-16> modified for gallery begin
		/*
		 * Intent intent = new Intent(mActivity, CameraActivity.class)
		 * .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); mRecenterCameraOnResume =
		 * false; mActivity.startActivity(intent);
		 */
		// Aurora <zhanggp> <2013-12-16> modified for gallery end
	}

	private void launchPhotoEditor() {
		MediaItem current = mModel.getMediaItem(0);
		if (current == null
				|| (current.getSupportedOperations() & MediaObject.SUPPORT_EDIT) == 0) {
			return;
		}

		Intent intent = new Intent(ACTION_NEXTGEN_EDIT);

		intent.setDataAndType(current.getContentUri(), current.getMimeType())
				.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		if (mActivity
				.getPackageManager()
				.queryIntentActivities(intent,
						PackageManager.MATCH_DEFAULT_ONLY).size() == 0) {
			intent.setAction(Intent.ACTION_EDIT);
		}
		intent.putExtra(FilterShowActivity.LAUNCH_FULLSCREEN,
				mActivity.isFullscreen());
		((Activity) mActivity).startActivityForResult(
				Intent.createChooser(intent, null), REQUEST_EDIT);
		overrideTransitionToEditor();
	}

	private void requestDeferredUpdate() {
		mDeferUpdateUntil = SystemClock.uptimeMillis() + DEFERRED_UPDATE_MS;
		if (!mDeferredUpdateWaiting) {
			mDeferredUpdateWaiting = true;
			mHandler.sendEmptyMessageDelayed(MSG_UPDATE_DEFERRED,
					DEFERRED_UPDATE_MS);
		}
	}

	private void updateUIForCurrentPhoto() {
		if (mCurrentPhoto == null)
			return;

		// If by swiping or deletion the user ends up on an action item
		// and zoomed in, zoom out so that the context of the action is
		// more clear
		if ((mCurrentPhoto.getSupportedOperations() & MediaObject.SUPPORT_ACTION) != 0
				&& !mPhotoView.getFilmMode()) {
			mPhotoView.setWantPictureCenterCallbacks(true);
		}
		// Aurora <paul> <2014-01-13> modified for gallery begin
		// updateMenuOperations();
		// Aurora <paul> <2014-01-13> modified for gallery end
		refreshBottomControlsWhenReady();
		if (mShowDetails) {
			mDetailsHelper.reloadDetails();
		}
		if ((mSecureAlbum == null)
				&& (mCurrentPhoto.getSupportedOperations() & MediaItem.SUPPORT_SHARE) != 0) {
			mCurrentPhoto.getPanoramaSupport(mUpdateShareURICallback);
		}
		updateProgressBar();
	}

	private void updateCurrentPhoto(MediaItem photo) {
		if (mCurrentPhoto == photo)
			return;
		mCurrentPhoto = photo;
		if (mPhotoView.getFilmMode()) {
			requestDeferredUpdate();
		} else {
			updateUIForCurrentPhoto();
		}
	}

	private void updateProgressBar() {
		if (mProgressBar != null) {
			mProgressBar.hideProgress();
			StitchingProgressManager progressManager = mApplication
					.getStitchingProgressManager();
			if (progressManager != null && mCurrentPhoto instanceof LocalImage) {
				Integer progress = progressManager.getProgress(mCurrentPhoto
						.getContentUri());
				if (progress != null) {
					mProgressBar.setProgress(progress);
				}
			}
		}
	}

	// Aurora <paul> <2014-01-13> modified for gallery begin
	/*
	 * private void updateMenuOperations() { Menu menu = mActionBar.getMenu();
	 * 
	 * // it could be null if onCreateActionBar has not been called yet if (menu
	 * == null) return;
	 * 
	 * MenuItem item = menu.findItem(R.id.action_slideshow); if (item != null) {
	 * item.setVisible((mSecureAlbum == null) && canDoSlideShow()); } if
	 * (mCurrentPhoto == null) return;
	 * 
	 * int supportedOperations = mCurrentPhoto.getSupportedOperations(); if
	 * (mSecureAlbum != null) { supportedOperations &=
	 * MediaObject.SUPPORT_DELETE; } else if (!mHaveImageEditor) {
	 * supportedOperations &= ~MediaObject.SUPPORT_EDIT; } // Aurora <zhanggp>
	 * <2013-12-06> modified for gallery begin
	 * MenuExecutor.updateMenuOperation(menu, 0);
	 * //MenuExecutor.updateMenuOperation(menu, supportedOperations); // Aurora
	 * <zhanggp> <2013-12-06> modified for gallery end
	 * mCurrentPhoto.getPanoramaSupport(mUpdatePanoramaMenuItemsCallback); }
	 */
	// Aurora <paul> <2014-01-13> modified for gallery end
	private boolean canDoSlideShow() {
		if (mMediaSet == null || mCurrentPhoto == null) {
			return false;
		}
		if (mCurrentPhoto.getMediaType() != MediaObject.MEDIA_TYPE_IMAGE) {
			return false;
		}
		if (MtpSource.isMtpPath(mOriginalSetPathString)) {
			return false;
		}
		return true;
	}

	// ////////////////////////////////////////////////////////////////////////
	// Action Bar show/hide management
	// ////////////////////////////////////////////////////////////////////////

	private void showBars() {
		// Aurora <SQF> <2014-08-08> for NEW_UI begin
		// ORIGINALLY:
		/*
		 * if (mShowBars) { return; }
		 */
		// SQF MODIFIED TO:
		if (mBottomControls.isShowing() && !mPhotoView.getFilmMode())
			return;
		if (!mBottomControls.isShowing() && mPhotoView.getFilmMode())
			return;
		// Aurora <SQF> <2014-08-08> for NEW_UI end

		// Aurora <SQF> <2014-07-31> for NEW_UI begin
		if (GalleryUtils.needNavigationBarControl()) {
			((Gallery) mActivity).setNavigationBarShowStatus(true);
		}
		// Aurora <SQF> <2014-07-31> for NEW_UI end
		// Log.i("SQF_LOG", "showBars -->  !mPhotoView.getFilmMode() --> " +
		// (!mPhotoView.getFilmMode()));
		// Aurora <SQF> <2014-08-08> for NEW_UI begin
		// ORIGINALLY:
		// mShowBars = true;
		// SQF MODIFIED TO:
		mShowBars = (!mPhotoView.getFilmMode()) ? true : false;
		// Aurora <SQF> <2014-08-08> for NEW_UI end

		mOrientationManager.unlockOrientation();
		// Aurora <zhanggp> <2013-12-06> modified for gallery begin
		// mActionBar.show();
		// Aurora <zhanggp> <2013-12-06> modified for gallery end

		// Aurora <SQF> <2014-07-28> for NEW_UI begin
		// ORIGINALLY:
		mActivity.getGLRoot().setLightsOutMode(false);
		// SQF MODIFIED TO:
		// mActivity.getGLRoot().setHideNavigationBarMode(true);
		// Aurora <SQF> <2014-07-28> for NEW_UI end

		refreshHidingMessage();
		refreshBottomControlsWhenReady();
	}

	public void hideBarsDirectly() {
		// if(mBottomControls.isShowing()) return;
		mShowBars = false;
		mTopControls.hideDirectly();
		mBottomControls.hideDirectly();
	}

	private void hideBars() {
		// Aurora <SQF> <2014-08-08> for NEW_UI begin
		// ORIGINALLY:
		// if (!mShowBars) return;
		// SQF MODIFIED TO:
		if (!mBottomControls.isShowing()) {
			return;
		}
		// Aurora <SQF> <2014-08-08> for NEW_UI end

		// Aurora <SQF> <2014-07-31> for NEW_UI begin
		if (GalleryUtils.needNavigationBarControl()) {
			((Gallery) mActivity).setNavigationBarShowStatus(false);
		}
		// Aurora <SQF> <2014-07-31> for NEW_UI end
		mShowBars = false;
		// Aurora <zhanggp> <2013-12-06> modified for gallery begin
		// mActionBar.hide();
		// Aurora <zhanggp> <2013-12-06> modified for gallery end
		// mActivity.getGLRoot().setLightsOutMode(true);//SQF ANNOTATED ON
		// 2014-07-16
		mHandler.removeMessages(MSG_HIDE_BARS);
		refreshBottomControlsWhenReady();
	}

	private void refreshHidingMessage() {
		mHandler.removeMessages(MSG_HIDE_BARS);
		if (!mIsMenuVisible && !mPhotoView.getFilmMode()) {
			mHandler.sendEmptyMessageDelayed(MSG_HIDE_BARS, HIDE_BARS_TIMEOUT);
		}
	}

	private boolean canShowBars() {
		// No bars if we are showing camera preview.
		if (mAppBridge != null && mCurrentIndex == 0
				&& !mPhotoView.getFilmMode())
			return false;

		// No bars if it's not allowed.
		if (!mActionBarAllowed)
			return false;

		return true;
	}

	private void wantBars() {
		if (canShowBars())
			showBars();
	}

	private void toggleBars() {
		// Aurora <SQF> <2014-08-08> for NEW_UI begin
		// ORIGINALLY:
		/*
		 * if (mShowBars) { hideBars(); } else { if (canShowBars()) showBars();
		 * }
		 */
		// SQF MODIFIED TO:

		if (mBottomControls.isShowing()) {
			hideBars();
		} else {
			if (canShowBars())
				showBars();
		}

		// Aurora <SQF> <2014-08-08> for NEW_UI end
	}

	private void updateBars() {
		if (!canShowBars()) {
			hideBars();
		}
	}

	// Aurora <SQF> <2014-6-25> for NEW_UI begin
	private void setCanShowBottomActionBarInAlbumPage(boolean canShow) {
		if (mActivity == null || mActivity.getStateManager() == null
				|| mActivity.getStateManager().getTopState() == null) {
			return;
		}
		if (mActivity.getStateManager().getTopState() instanceof AlbumPage) {
			AlbumPage albumPage = (AlbumPage) mActivity.getStateManager()
					.getTopState();
			albumPage.ShowBottomActionBar(!canShow);
		}
	}

	// Aurora <SQF> <2014-6-25> for NEW_UI end

	@Override
	protected void onBackPressed() {
		if (mShowDetails) {
			hideDetails();
		} else if (mAppBridge == null || !switchWithCaptureAnimation(-1)) {
			// Aurora <paul> <2014-02-28> for NEW_UI begin
			if (mFromAlbumPage) {
				// Log.i("SQF_LOG",
				// "PhotoPage::onBackPressed 1111 setCanShowBottomActionBarInAlbumPage(true)");
				mActivity.getStateManager().getTopState().onBackPressed();
				// Aurora <SQF> <2014-6-25> for NEW_UI begin
				// setCanShowBottomActionBarInAlbumPage(true);
				// Aurora <SQF> <2014-6-25> for NEW_UI end
				// Aurora <SQF> <2014-07-31> for NEW_UI begin
				mActivity.getGLRoot().setLightsOutMode(true);
				// Aurora <SQF> <2014-07-31> for NEW_UI end
				return;
			}
			// Aurora <paul> <2014-02-28> for NEW_UI end
			// We are leaving this page. Set the result now.
			setResult();
			if (mStartInFilmstrip && !mPhotoView.getFilmMode()) {
				mPhotoView.setFilmMode(true);
				/*
				 * } else if (mTreatBackAsUp) { paul del onUpPressed();
				 */
			} else {
				super.onBackPressed();

				// Log.i("SQF_LOG",
				// "PhotoPage::onBackPressed 2222 setCanShowBottomActionBarInAlbumPage(true)");

				// Aurora <SQF> <2014-6-25> for NEW_UI begin
				// setCanShowBottomActionBarInAlbumPage(true);
				// Aurora <SQF> <2014-6-25> for NEW_UI end
				// Aurora <SQF> <2014-07-31> for NEW_UI begin
				mActivity.getGLRoot().setLightsOutMode(true);
				// Aurora <SQF> <2014-07-31> for NEW_UI end
			}
		}
	}

	// paul del
	/*
	 * private void onUpPressed() {
	 * 
	 * 
	 * 
	 * if ((mStartInFilmstrip || mAppBridge != null) &&
	 * !mPhotoView.getFilmMode()) { mPhotoView.setFilmMode(true); return; }
	 * 
	 * if (mActivity.getStateManager().getStateCount() > 1) { setResult();
	 * super.onBackPressed(); return; }
	 * 
	 * if (mOriginalSetPathString == null) return;
	 * 
	 * if (mAppBridge == null) { // We're in view mode so set up the stacks on
	 * our own. Bundle data = new Bundle(getData());
	 * data.putString(AlbumPage.KEY_MEDIA_PATH, mOriginalSetPathString);
	 * data.putString(AlbumPage.KEY_PARENT_MEDIA_PATH,
	 * mActivity.getDataManager().getTopSetPath( DataManager.INCLUDE_ALL));
	 * mActivity.getStateManager().switchState(this, AlbumPage.class, data); }
	 * else { GalleryUtils.startGalleryActivity(mActivity); }
	 * 
	 * }
	 */

	private void setResult() {
		Intent result = null;
		result = new Intent();
		result.putExtra(KEY_RETURN_INDEX_HINT, mCurrentIndex);
		setStateResult(Activity.RESULT_OK, result);
	}

	// ////////////////////////////////////////////////////////////////////////
	// AppBridge.Server interface
	// ////////////////////////////////////////////////////////////////////////

	@Override
	public void setCameraRelativeFrame(Rect frame) {
		mPhotoView.setCameraRelativeFrame(frame);
	}

	@Override
	public boolean switchWithCaptureAnimation(int offset) {
		return mPhotoView.switchWithCaptureAnimation(offset);
	}

	@Override
	public void setSwipingEnabled(boolean enabled) {
		mPhotoView.setSwipingEnabled(enabled);
	}

	@Override
	public void notifyScreenNailChanged() {
		mScreenNailItem.setScreenNail(mAppBridge.attachScreenNail());
		mScreenNailSet.notifyChange();
	}

	@Override
	public void addSecureAlbumItem(boolean isVideo, int id) {
		mSecureAlbum.addMediaItem(isVideo, id);
	}

	@Override
	protected boolean onCreateActionBar(Menu menu) {
		// Aurora <zhanggp> <2013-12-06> modified for gallery begin
		// mActionBar.createActionBarMenu(R.menu.photo, menu);
		// mHaveImageEditor = GalleryUtils.isEditorAvailable(mActivity,
		// "image/*");
		// updateMenuOperations();
		// mActionBar.setTitle(mMediaSet != null ? mMediaSet.getName() : "");
		// Aurora <zhanggp> <2013-12-06> modified for gallery end
		return true;
	}

	private MenuExecutor.ProgressListener mConfirmDialogListener = new MenuExecutor.ProgressListener() {
		@Override
		public void onProgressUpdate(int index) {
			// Log.i("SQF_LOG", "MenuExecutor::onProgressUpdate index:" +
			// index);
		}

		@Override
		public void onProgressComplete(int result) {
		}

		@Override
		public void onConfirmDialogShown() {
			mHandler.removeMessages(MSG_HIDE_BARS);
		}

		@Override
		public void onConfirmDialogDismissed(boolean confirmed) {
			refreshHidingMessage();
		}

		@Override
		public void onProgressStart() {
		}
	};

	private MenuExecutor.ProgressListener mDeleteDialogListener = new MenuExecutor.ProgressListener() {
		@Override
		public void onProgressUpdate(int index) {
		}

		@Override
		public void onProgressComplete(int result) {

		}

		@Override
		public void onConfirmDialogShown() {
			mHandler.removeMessages(MSG_HIDE_BARS);
		}

		@Override
		public void onConfirmDialogDismissed(boolean confirmed) {
			refreshHidingMessage();
		}

		@Override
		public void onProgressStart() {
			mModel.deletingItem();// Aurora <paul> <2014-04-24>
		}
	};

	private void switchToGrid() {
		if (mActivity.getStateManager().hasStateClass(AlbumPage.class)) {
			// paul modify
			// onUpPressed();
			onBackPressed();
		} else {
			if (mOriginalSetPathString == null)
				return;
			if (mProgressBar != null) {
				updateCurrentPhoto(null);
				mProgressBar.hideProgress();
			}
			Bundle data = new Bundle(getData());
			data.putString(AlbumPage.KEY_MEDIA_PATH, mOriginalSetPathString);
			data.putString(AlbumPage.KEY_PARENT_MEDIA_PATH, mActivity
					.getDataManager().getTopSetPath(DataManager.INCLUDE_ALL));

			// We only show cluster menu in the first AlbumPage in stack
			// TODO: Enable this when running from the camera app
			boolean inAlbum = mActivity.getStateManager().hasStateClass(
					AlbumPage.class);
			data.putBoolean(AlbumPage.KEY_SHOW_CLUSTER_MENU, !inAlbum
					&& mAppBridge == null);

			data.putBoolean(PhotoPage.KEY_APP_BRIDGE, mAppBridge != null);

			// Account for live preview being first item
			mActivity.getTransitionStore().put(KEY_RETURN_INDEX_HINT,
					mAppBridge != null ? mCurrentIndex - 1 : mCurrentIndex);

			if (mHasCameraScreennailOrPlaceholder && mAppBridge != null) {
				mActivity.getStateManager().startState(AlbumPage.class, data);
			} else {
				mActivity.getStateManager().switchState(this, AlbumPage.class,
						data);
			}
		}
	}

	@Override
	protected boolean onItemSelected(MenuItem item) {
		if (mModel == null)
			return true;
		refreshHidingMessage();
		MediaItem current = mModel.getMediaItem(0);

		if (current == null) {
			// item is not ready, ignore
			return true;
		}

		int currentIndex = mModel.getCurrentIndex();
		Path path = current.getPath();

		DataManager manager = mActivity.getDataManager();
		int action = item.getItemId();
		String confirmMsg = null;
		switch (action) {
		case android.R.id.home: {
			// paul modify
			// onUpPressed();
			onBackPressed();
			return true;
		}
		case R.id.action_slideshow: {
			Bundle data = new Bundle();
			data.putString(SlideshowPage.KEY_SET_PATH, mMediaSet.getPath()
					.toString());
			data.putString(SlideshowPage.KEY_ITEM_PATH, path.toString());
			data.putInt(SlideshowPage.KEY_PHOTO_INDEX, currentIndex);
			data.putBoolean(SlideshowPage.KEY_REPEAT, true);
			mActivity.getStateManager().startStateForResult(
					SlideshowPage.class, REQUEST_SLIDESHOW, data);
			return true;
		}
		case R.id.action_crop: {
			/*
			 * Activity activity = mActivity; Intent intent = new
			 * Intent(FilterShowActivity.CROP_ACTION); intent.setClass(activity,
			 * FilterShowActivity.class);
			 * intent.setDataAndType(manager.getContentUri(path),
			 * current.getMimeType())
			 * .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			 * activity.startActivityForResult(intent,
			 * PicasaSource.isPicasaImage(current) ? REQUEST_CROP_PICASA :
			 * REQUEST_CROP);
			 */
			return true;
		}
		case R.id.action_trim: {
			Intent intent = new Intent(mActivity, TrimVideo.class);
			intent.setData(manager.getContentUri(path));
			// We need the file path to wrap this into a RandomAccessFile.
			intent.putExtra(KEY_MEDIA_ITEM_PATH, current.getFilePath());
			mActivity.startActivityForResult(intent, REQUEST_TRIM);
			return true;
		}
		case R.id.action_edit: {
			launchPhotoEditor();
			return true;
		}
		case R.id.action_details: {
			if (mShowDetails) {
				hideDetails();
			} else {
				showDetails();
			}
			return true;
		}
		case R.id.action_delete:
			confirmMsg = mActivity.getResources().getQuantityString(
					R.plurals.delete_selection, 1);
		case R.id.action_setas:
		case R.id.action_rotate_ccw:
		case R.id.action_rotate_cw:
		case R.id.action_show_on_map:
			mSelectionManager.deSelectAll();
			mSelectionManager.toggle(path);
			mMenuExecutor.onMenuClicked(item, confirmMsg,
					mConfirmDialogListener);
			return true;
		case R.id.action_import:
			mSelectionManager.deSelectAll();
			mSelectionManager.toggle(path);
			mMenuExecutor.onMenuClicked(item, confirmMsg,
					new ImportCompleteListener(mActivity));
			return true;
		default:
			return false;
		}
	}

	private void hideDetails() {
		mShowDetails = false;
		mDetailsHelper.hide();
	}

	private void showDetails() {
		mShowDetails = true;
		if (mDetailsHelper == null) {
			mDetailsHelper = new DetailsHelper(mActivity, mRootPane,
					new MyDetailsSource());
			mDetailsHelper.setCloseListener(new CloseListener() {
				@Override
				public void onClose() {
					hideDetails();
				}
			});
		}
		mDetailsHelper.show();
	}

	// //////////////////////////////////////////////////////////////////////////
	// Callbacks from PhotoView
	// //////////////////////////////////////////////////////////////////////////
	@Override
	public void onSingleTapUp(int x, int y) {
		if (mAppBridge != null) {
			if (mAppBridge.onSingleTapUp(x, y))
				return;
		}

		MediaItem item = mModel.getMediaItem(0);
		if (item == null || item == mScreenNailItem) {
			// item is not ready or it is camera preview, ignore
			return;
		}

		int supported = item.getSupportedOperations();
		boolean playVideo = ((supported & MediaItem.SUPPORT_PLAY) != 0);
		boolean unlock = ((supported & MediaItem.SUPPORT_UNLOCK) != 0);
		boolean goBack = ((supported & MediaItem.SUPPORT_BACK) != 0);
		boolean launchCamera = ((supported & MediaItem.SUPPORT_CAMERA_SHORTCUT) != 0);

		if (playVideo) {
			// determine if the point is at center (1/6) of the photo view.
			// (The position of the "play" icon is at center (1/6) of the photo)
			int w = mPhotoView.getWidth();
			int h = mPhotoView.getHeight();
			playVideo = (Math.abs(x - w / 2) * 12 <= w)
					&& (Math.abs(y - h / 2) * 12 <= h);
		}

		if (playVideo) {
			if (mSecureAlbum == null) {
				playVideo(mActivity, item.getPlayUri(), item.getName());
			} else {
				if (!mFromAlbumPage) {// Aurora <paul> <2014-03-03> for NEW_UI
					mActivity.getStateManager().finishState(this);
				}
			}
		} else if (goBack) {
			onBackPressed();
		} else if (unlock) {
			Intent intent = new Intent(mActivity, Gallery.class);
			intent.putExtra(Gallery.KEY_DISMISS_KEYGUARD, true);
			mActivity.startActivity(intent);
		} else if (launchCamera) {
			launchCamera();
		} else {
			toggleBars();
		}
	}

	@Override
	public void onActionBarAllowed(boolean allowed) {
		mActionBarAllowed = allowed;
		mHandler.sendEmptyMessage(MSG_UPDATE_ACTION_BAR);
	}

	@Override
	public void onActionBarWanted() {
		mHandler.sendEmptyMessage(MSG_WANT_BARS);
	}

	@Override
	public void onFullScreenChanged(boolean full) {
		Message m = mHandler.obtainMessage(MSG_ON_FULL_SCREEN_CHANGED, full ? 1
				: 0, 0);
		m.sendToTarget();
	}

	// How we do delete/undo:
	//
	// When the user choose to delete a media item, we just tell the
	// FilterDeleteSet to hide that item. If the user choose to undo it, we
	// again tell FilterDeleteSet not to hide it. If the user choose to commit
	// the deletion, we then actually delete the media item.
	@Override
	public void onDeleteImage(Path path, int offset) {
		onCommitDeleteImage(); // commit the previous deletion
		mDeletePath = path;
		mDeleteIsFocus = (offset == 0);
		mMediaSet.addDeletion(path, mCurrentIndex + offset);
	}

	@Override
	public void onUndoDeleteImage() {
		if (mDeletePath == null)
			return;
		// If the deletion was done on the focused item, we want the model to
		// focus on it when it is undeleted.
		if (mDeleteIsFocus)
			mModel.setFocusHintPath(mDeletePath);
		mMediaSet.removeDeletion(mDeletePath);
		mDeletePath = null;
	}

	@Override
	public void onCommitDeleteImage() {
		if (mDeletePath == null)
			return;
		mSelectionManager.deSelectAll();
		mSelectionManager.toggle(mDeletePath);
		mMenuExecutor.onMenuClicked(R.id.action_delete, null, true, false);
		mDeletePath = null;
	}

	public void playVideo(Activity activity, Uri uri, String title) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "video/*")
                    .putExtra(Intent.EXTRA_TITLE, title)
                    .putExtra(MovieActivity.KEY_TREAT_UP_AS_BACK, true);
            activity.startActivityForResult(intent, REQUEST_PLAY_VIDEO);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, activity.getString(R.string.video_err),
                    Toast.LENGTH_SHORT).show();
        }
    }

	// Aurora <SQF> <2014-6-18> for NEW_UI begin

	private boolean isFilePathInSearch(String filePath) {
		if (filePath != null) {
			String INTERNAL_SD_STORAGE = gionee.os.storage.GnStorageManager
					.getInstance(mActivity.getAndroidContext())
					.getInternalStoragePath();
			String EXTERNAL_SD_STORAGE = gionee.os.storage.GnStorageManager
					.getInstance(mActivity.getAndroidContext())
					.getExternalStoragePath();
			// Log.i("SQF_LOG", " internal:" + INTERNAL_SD_STORAGE +
			// " external:" + EXTERNAL_SD_STORAGE + " test null + sss -->" +
			// (null + "sss"));
			if (INTERNAL_SD_STORAGE != null) {
				if (filePath.contains(INTERNAL_SD_STORAGE
						+ MediaSetUtils.CAMERA_BUCKET_NAME)
						|| filePath.contains(INTERNAL_SD_STORAGE
								+ MediaSetUtils.SNAPSHOT_BUCKET_NAME)
						|| filePath.contains(INTERNAL_SD_STORAGE
								+ MediaSetUtils.MTXX_BUCKET_NAME)
						|| filePath.contains(INTERNAL_SD_STORAGE
								+ MediaSetUtils.WEIXIN_BUCKET_NAME)) {
					return true;
				}
			}
			if (EXTERNAL_SD_STORAGE != null) {
				if (filePath.contains(EXTERNAL_SD_STORAGE
						+ MediaSetUtils.CAMERA_BUCKET_NAME)
						|| filePath.contains(EXTERNAL_SD_STORAGE
								+ MediaSetUtils.SNAPSHOT_BUCKET_NAME)
						|| filePath.contains(EXTERNAL_SD_STORAGE
								+ MediaSetUtils.MTXX_BUCKET_NAME)
						|| filePath.contains(EXTERNAL_SD_STORAGE
								+ MediaSetUtils.WEIXIN_BUCKET_NAME)) {
					return true;
				}
			}
		}
		return false;
	}

	public void setCurrentPhotoByIntentEx(Intent intent) {
		// Log.i("SQF_LOG",
		// "AlbumPage::setCurrentPhotoByIntentEx -----------11111");
		if (intent == null)
			return;
		ActivityState topState = mActivity.getStateManager().getTopState();
		if (topState instanceof AlbumPage) {
			// Log.i("SQF_LOG",
			// "AlbumPage::setCurrentPhotoByIntentEx -------22222");
			AlbumPage albumPage = (AlbumPage) topState;
			setToFinishPhotoPage(true);
			onBackPressed();
			// Log.i("SQF_LOG",
			// "AlbumPage::setCurrentPhotoByIntentEx -----------33333");
			setToFinishPhotoPage(false);
			setCanShowBottomActionBarInAlbumPage(false);
		}

		GalleryApp application = (GalleryApp) (mActivity.getApplication());
		if (intent == null)
			return;
		Path path = application.getDataManager().findPathByUri(
				intent.getData(), intent.getType());
		if (path != null) {
			// Log.i("SQF_LOG",
			// "AlbumPage::setCurrentPhotoByIntentEx -------333333333");
			// Path albumPath =
			// application.getDataManager().getDefaultSetOf(path);
			// If the edited image is stored in a different album, we need
			// to start a new activity state to show the new image
			// Log.i("SQF_LOG", " Go on....222222222");
			Bundle data = new Bundle(getData());
			// data.putString(PhotoPage.KEY_MEDIA_SET_PATH,
			// albumPath.toString());
			// Aurora <SQF> <2014-6-19> for NEW_UI begin
			String pathString = null;
			MediaObject object = application.getDataManager().getMediaObject(
					path);
			boolean pathStringSet = false;
			if (object instanceof LocalImage) {
				LocalImage localImage = (LocalImage) object;
				// Log.i("SQF_LOG", "filePath:" + localImage.getFilePath() + " "
				// + " name:" + localImage.getName() + " path:" +
				// localImage.getPath());
				String filePath = localImage.getFilePath();
				if (isFilePathInSearch(filePath)) {
					pathString = "/local/allsets/"
							+ MediaSetUtils.CAMERA_BUCKET_ID;
					pathStringSet = true;
				}
			}
			if (!pathStringSet) {
				pathString = application.getDataManager().getDefaultSetOf(path)
						.toString();
			}
			// Log.i("SQF_LOG",
			// "AlbumPage::setCurrentPhotoByIntentEx pathString:" + pathString);
			data.putString(PhotoPage.KEY_MEDIA_SET_PATH, pathString.toString());
			// Aurora <SQF> <2014-6-19> for NEW_UI end
			data.putString(PhotoPage.KEY_MEDIA_ITEM_PATH, path.toString());
			if (topState instanceof PhotoPage) {
				// Log.i("SQF_LOG",
				// "AlbumPage::setCurrentPhotoByIntentEx -------switchState ------4444444");
				mActivity.getStateManager().switchState(topState,
						PhotoPage.class, data);
			} else {
				// Log.i("SQF_LOG",
				// "AlbumPage::setCurrentPhotoByIntentEx -------switchState ------55555555");
				mActivity.getStateManager().startState(PhotoPage.class, data);
			}
			// Aurora <SQF> <2014-07-31> for NEW_UI begin
			if (GalleryUtils.needNavigationBarControl()) {
				((Gallery) mActivity).setNavigationBarShowStatus(false);
			}
			// Aurora <SQF> <2014-07-31> for NEW_UI end
		}
	}

	// Aurora <SQF> <2014-6-18> for NEW_UI end

	private void setCurrentPhotoByIntent(Intent intent) {
		if (intent == null)
			return;
		Path path = mApplication.getDataManager().findPathByUri(
				intent.getData(), intent.getType());
		if (path != null) {
			Path albumPath = mApplication.getDataManager()
					.getDefaultSetOf(path);
			if (!albumPath.equalsIgnoreCase(mOriginalSetPathString)) {
				// If the edited image is stored in a different album, we need
				// to start a new activity state to show the new image
				Bundle data = new Bundle(getData());
				data.putString(KEY_MEDIA_SET_PATH, albumPath.toString());
				data.putString(PhotoPage.KEY_MEDIA_ITEM_PATH, path.toString());
				mActivity.getStateManager().startState(PhotoPage.class, data);
				return;
			}
			mModel.setCurrentPhoto(path, mCurrentIndex);
		}
	}

	@Override
	protected void onStateResult(int requestCode, int resultCode, Intent data) {
		// if(data != null) {
		// Log.i("SQF_LOG", "AlbumPage::onStateResult request:" + requestCode +
		// " result:" + resultCode + " data:" + data.getData());
		// } else {
		// Log.i("SQF_LOG", "AlbumPage::onStateResult request:" + requestCode +
		// " result:" + resultCode + " data == null");
		// }
		if (resultCode == Activity.RESULT_CANCELED) {
			// This is a reset, not a canceled
			return;
		}
		if (resultCode == ProxyLauncher.RESULT_USER_CANCELED) {
			// Unmap reset vs. canceled
			resultCode = Activity.RESULT_CANCELED;
		}
		mRecenterCameraOnResume = false;
		switch (requestCode) {
		case REQUEST_EDIT:
			// Aurora <SQF> <2014-6-19> for NEW_UI begin
			// ORIGINALLY:
			// setCurrentPhotoByIntent(data);
			// SQF MODIFIED TO:
			setCurrentPhotoByIntentEx(data);
			// Aurora <SQF> <2014-6-19> for NEW_UI end
			break;
		case REQUEST_CROP:
			if (resultCode == Activity.RESULT_OK) {
				setCurrentPhotoByIntent(data);
			}
			break;
		case REQUEST_CROP_PICASA: {
			if (resultCode == Activity.RESULT_OK) {
				Context context = mActivity.getAndroidContext();
				String message = context
						.getString(
								R.string.crop_saved,
								context.getString(R.string.folder_edited_online_photos));
				Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
			}
			break;
		}
		case REQUEST_SLIDESHOW: {
			if (data == null)
				break;
			String path = data.getStringExtra(SlideshowPage.KEY_ITEM_PATH);
			int index = data.getIntExtra(SlideshowPage.KEY_PHOTO_INDEX, 0);
			if (path != null) {
				mModel.setCurrentPhoto(Path.fromString(path), index);
			}
		}
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		mIsActive = false;
		mActivity.getGLRoot().unfreeze();
		mHandler.removeMessages(MSG_UNFREEZE_GLROOT);

		DetailsHelper.pause();
		// Hide the detail dialog on exit
		if (mShowDetails)
			hideDetails();
		if (mModel != null) {
			mModel.pause();
		}
		mPhotoView.pause();
		mHandler.removeMessages(MSG_HIDE_BARS);
		mHandler.removeMessages(MSG_REFRESH_BOTTOM_CONTROLS);
		// refreshBottomControlsWhenReady();//SQF ANNOTATED ON 2014.7.31
		// Aurora <zhanggp> <2013-12-06> modified for gallery begin
		// mActionBar.removeOnMenuVisibilityListener(mMenuVisibilityListener);
		// if (mShowSpinner) {
		// mActionBar.disableAlbumModeMenu(true);
		// }
		// Aurora <zhanggp> <2013-12-06> modified for gallery end
		onCommitDeleteImage();
		mMenuExecutor.pause();
		if (mMediaSet != null)
			mMediaSet.clearDeletion();
	}

	@Override
	public void onCurrentImageUpdated() {
		mActivity.getGLRoot().unfreeze();
	}

	@Override
	public void onFilmModeChanged(boolean enabled) {
		// Aurora <zhanggp> <2013-12-06> modified for gallery begin
		/*
		 * refreshBottomControlsWhenReady();
		 * 
		 * if (mShowSpinner) { if (enabled) { mActionBar.enableAlbumModeMenu(
		 * GalleryActionBar.ALBUM_FILMSTRIP_MODE_SELECTED, this); } else {
		 * mActionBar.disableAlbumModeMenu(true); } }
		 * 
		 * if (enabled) { mHandler.removeMessages(MSG_HIDE_BARS); } else {
		 * refreshHidingMessage(); }
		 */
		// Aurora <zhanggp> <2013-12-06> modified for gallery end

	}

	private void transitionFromAlbumPageIfNeeded() {
		TransitionStore transitions = mActivity.getTransitionStore();

		int albumPageTransition = transitions.get(KEY_ALBUMPAGE_TRANSITION,
				MSG_ALBUMPAGE_NONE);

		if (albumPageTransition == MSG_ALBUMPAGE_NONE && mAppBridge != null
				&& mRecenterCameraOnResume) {
			// Generally, resuming the PhotoPage when in Camera should
			// reset to the capture mode to allow quick photo taking
			mCurrentIndex = 0;
			mPhotoView.resetToFirstPicture();
		} else {
			int resumeIndex = transitions.get(KEY_INDEX_HINT, -1);
			if (resumeIndex >= 0) {
				if (mHasCameraScreennailOrPlaceholder) {
					// Account for preview/placeholder being the first item
					resumeIndex++;
				}
				if (resumeIndex < mMediaSet.getMediaItemCount()) {
					mCurrentIndex = resumeIndex;
					mModel.moveTo(mCurrentIndex);
				}
			}
		}

		if (albumPageTransition == MSG_ALBUMPAGE_RESUMED) {
			mPhotoView.setFilmMode(mStartInFilmstrip || mAppBridge != null);
		} else if (albumPageTransition == MSG_ALBUMPAGE_PICKED) {
			mPhotoView.setFilmMode(false);
		}
	}

	//wenyongzhe 2015.10.8 全屏切换
		private void fullScreen() {
		      WindowManager.LayoutParams lp =  mActivity.getWindow().getAttributes();
		      lp.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
		      mActivity.getWindow().setAttributes(lp);
		      SystemUtils.switchStatusBarColorMode(SystemUtils.STATUS_BAR_MODE_WHITE, mActivity);
		}
		
	@Override
	protected void onResume() {
		super.onResume();
		
		//wenyongzhe 2015.10.8 全屏切换
		if (!mFromAlbumPage) {
			fullScreen();
		}
		
		if (mModel == null) {
			if (!mFromAlbumPage) {// Aurora <paul> <2014-03-03> for NEW_UI
				mActivity.getStateManager().finishState(this);
			}
			return;
		}
		if (!mFromAlbumPage) {// Aurora <paul> <2014-02-27> for NEW_UI
			transitionFromAlbumPageIfNeeded();
			// mActivity.getGLRoot().freeze();
		}
		mActivity.getGLRoot().freeze();// 2014.08.27 SQF MOVE TO HERE to avoid
										// glittering
		mIsActive = true;
		setContentPane(mRootPane);

		mModel.resume();
		mPhotoView.resume();
		// Aurora <SQF> <2014-08-07> for NEW_UI begin
		mHandler.removeMessages(MSG_HIDE_BARS);
		mHandler.removeMessages(MSG_REFRESH_BOTTOM_CONTROLS);
		// Aurora <SQF> <2014-08-07> for NEW_UI end

		// Aurora <zhanggp> <2013-12-06> modified for gallery begin
		// mActionBar.setDisplayOptions(
		// ((mSecureAlbum == null) && (mSetPathString != null)), true);
		// mActionBar.addOnMenuVisibilityListener(mMenuVisibilityListener);

		// refreshBottomControlsWhenReady();//SQF ANNOATED ON 2014.08.07

		/*
		 * if (mShowSpinner && mPhotoView.getFilmMode()) {
		 * mActionBar.enableAlbumModeMenu(
		 * GalleryActionBar.ALBUM_FILMSTRIP_MODE_SELECTED, this); }
		 */

		// if (!mShowBars) {//SQF ANNOTATED ON 2014-08-04
		// mActionBar.hide();
		// mActivity.getGLRoot().setLightsOutMode(true);//SQF ANNOTATED ON
		// 2014-07-16
		// }//SQF ANNOTATED ON 2014-08-04
		// Aurora <zhanggp> <2013-12-06> modified for gallery end
		/*
		 * boolean haveImageEditor = GalleryUtils.isEditorAvailable(mActivity,
		 * "image/*"); if (haveImageEditor != mHaveImageEditor) {
		 * mHaveImageEditor = haveImageEditor; updateMenuOperations(); }
		 */
		mRecenterCameraOnResume = true;
		// if(!mFromAlbumPage){//2014.08.27, SQF ANNOTATED to avoid glittering
		mHandler.sendEmptyMessageDelayed(MSG_UNFREEZE_GLROOT,
				UNFREEZE_GLROOT_TIMEOUT);
		// }
	}

	@Override
	protected void onDestroy() {
		// Aurora <zhanggp> <2013-12-06> added for gallery begin
		if (null != mPhotoView) {
			mPhotoView.onDestroyCalled();
		}
		
		//paul add start
		if(mActivity.inCloudView()){
			mActivity.unregisterReceiver(mIntentReceiver); 
		}
		//paul add end

		// Aurora <zhanggp> <2013-12-06> added for gallery end
		if (mAppBridge != null) {
			mAppBridge.setServer(null);
			mScreenNailItem.setScreenNail(null);
			mAppBridge.detachScreenNail();
			mAppBridge = null;
			mScreenNailSet = null;
			mScreenNailItem = null;
		}
		mActivity.getGLRoot().setOrientationSource(null);
		if (mBottomControls != null)
			mBottomControls.cleanup();
		// Aurora <zhanggp> <2013-12-06> added for gallery begin
		if (mTopControls != null)
			mTopControls.cleanup();
		// Aurora <zhanggp> <2013-12-06> added for gallery end
		// Remove all pending messages.
		mHandler.removeCallbacksAndMessages(null);
		super.onDestroy();
	}

	private class MyDetailsSource implements DetailsSource {

		@Override
		public MediaDetails getDetails() {
			return mModel.getMediaItem(0).getDetails();
		}

		@Override
		public int size() {
			return mMediaSet != null ? mMediaSet.getMediaItemCount() : 1;
		}

		@Override
		public int setIndex() {
			return mModel.getCurrentIndex();
		}
	}

	@Override
	public void onAlbumModeSelected(int mode) {
		if (mode == GalleryActionBar.ALBUM_GRID_MODE_SELECTED) {
			switchToGrid();
		}
	}

	@Override
	public void refreshBottomControlsWhenReady() {
		if (mBottomControls == null) {
			return;
		}
		MediaObject currentPhoto = mCurrentPhoto;
		if (currentPhoto == null) {
			mHandler.obtainMessage(MSG_REFRESH_BOTTOM_CONTROLS, 0, 0,
					currentPhoto).sendToTarget();
		} else {
			currentPhoto.getPanoramaSupport(mRefreshBottomControlsCallback);
		}
	}

	private void updatePanoramaUI(boolean isPanorama360) {
		Menu menu = mActionBar.getMenu();

		// it could be null if onCreateActionBar has not been called yet
		if (menu == null) {
			return;
		}

		MenuExecutor.updateMenuForPanorama(menu, isPanorama360, isPanorama360);

		if (isPanorama360) {
			MenuItem item = menu.findItem(R.id.action_share);
			if (item != null) {
				item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
				item.setTitle(mActivity.getResources().getString(
						R.string.share_as_photo));
			}
		} else if ((mCurrentPhoto.getSupportedOperations() & MediaObject.SUPPORT_SHARE) != 0) {
			MenuItem item = menu.findItem(R.id.action_share);
			if (item != null) {
				item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
				item.setTitle(mActivity.getResources()
						.getString(R.string.share));
			}
		}
	}

	@Override
	public void onUndoBarVisibilityChanged(boolean visible) {
		refreshBottomControlsWhenReady();
	}

	// Aurora <SQF> <2014-07-30> for NEW_UI begin
	@Override
	protected void onConfigurationChanged(Configuration config) {
		// TODO Auto-generated method stub
		super.onConfigurationChanged(config);
		if (GalleryUtils.needNavigationBarControl()) {
			if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
				mBottomControls.adjustTopPosition(
						mActivity.getAndroidContext(), false);
			} else if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
				mBottomControls.adjustTopPosition(
						mActivity.getAndroidContext(), true);
			}
		}
	}
	// Aurora <SQF> <2014-07-30> for NEW_UI end

	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		// TODO Auto-generated method stub
		
	}
	
	private boolean isShowing() {
		return getPhotoView() != null && getPhotoView().getVisibility() == GLView.VISIBLE;
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub
		if (event.sensor == null) {
            return;
        }
		if( ! isShowing() ) return;
		if(event.sensor.getType() == GestureSensorUtils.SENSOR_TYPE_GESTURE) {
			int direction = (int)event.values[0];
			PhotoDataAdapter adapter = (PhotoDataAdapter)mModel;
			switch(direction) {
			case GestureSensorUtils.DIR_LEFT:
				//Log.i("SQF_LOG", "sensor: type:" + event.sensor.getType() + " LEFT");
				adapter.moveTo(mCurrentIndex + 1);
				break;
			case GestureSensorUtils.DIR_RIGHT:
				//Log.i("SQF_LOG", "sensor: type:" + event.sensor.getType() + " RIGHT");
				if(mCurrentIndex >= 1) {
					adapter.moveTo(mCurrentIndex - 1);
				}
				break;
			}
		}
	}

}