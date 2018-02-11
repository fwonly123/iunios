
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

package com.android.systemui.statusbar;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.statusbar.StatusBarIcon;
import com.android.internal.statusbar.StatusBarIconList;
//import com.android.internal.statusbar.StatusBarNotification;
import android.service.notification.StatusBarNotification;
import com.android.internal.widget.SizeAdaptiveLayout;
import com.android.systemui.R;
import com.android.systemui.SearchPanelView;
import com.android.systemui.SystemUI;
import com.android.systemui.recent.HandlerBar;
import com.android.systemui.recent.RecentTasksLoader;
import com.android.systemui.recent.RecentsActivity;
import com.android.systemui.recent.TaskDescription;
import com.android.systemui.recent.utils.Utils;
import com.android.systemui.statusbar.NotificationData;
import com.android.systemui.statusbar.NotificationData.Entry;
import com.android.systemui.statusbar.policy.NotificationRowLayout;
import com.android.systemui.statusbar.tablet.StatusBarPanel;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.ActivityOptions;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Slog;
import android.view.Display;
import android.view.IWindowManager;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RemoteViews;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;

import com.android.systemui.Xlog;

// Aurora <zhanggp> <2013-10-08> added for systemui begin
import android.app.Notification;
import android.widget.Button;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import aurora.app.AuroraAlertDialog;
import android.content.DialogInterface;
import android.view.View.OnClickListener;
import android.app.INotificationManager;
import com.android.systemui.recent.RecentsPanelView;
// Aurora <zhanggp> <2013-10-08> added for systemui end
import android.widget.ImageView.ScaleType;
import com.aurora.utils.Utils2Icon;
import android.graphics.BitmapFactory;
import android.os.SystemProperties;
// Aurora <tongyh> <2014-10-28> hooking resources begin
import android.graphics.drawable.Drawable;
import android.widget.ProgressBar;
import android.graphics.Typeface;
import android.view.ViewStub;
// Aurora <tongyh> <2014-10-28> hooking resources end

// Aurora <Felix.Duan> <2015-2-6> <BEGIN> StatusBar color invert
import android.graphics.ColorFilter;
// Aurora <Felix.Duan> <2015-2-6> <END> StatusBar color invert
//update to 5.0 begin
import android.view.KeyEvent;
import android.service.notification.NotificationListenerService.RankingMap;
import android.service.notification.NotificationListenerService;
import android.content.ComponentName;
import android.util.SparseArray;
import android.content.pm.UserInfo;
import android.os.UserManager;
//update to 5.0 end
import com.android.systemui.totalCount.CountUtil;

public abstract class BaseStatusBar extends SystemUI implements
        CommandQueue.Callbacks, NotificationData.Environment {
    public static final String TAG = "StatusBar";
    public static final boolean DEBUG = false;
    public static final boolean MULTIUSER_DEBUG = false;
    public static final boolean FELIXDBG = (SystemProperties.getInt("ro.debuggable", 0) == 1);
    protected static final int MSG_TOGGLE_RECENTS_PANEL = 1020;
    protected static final int MSG_CLOSE_RECENTS_PANEL = 1021;
    protected static final int MSG_PRELOAD_RECENT_APPS = 1022;
    protected static final int MSG_CANCEL_PRELOAD_RECENT_APPS = 1023;
    protected static final int MSG_OPEN_SEARCH_PANEL = 1024;
    protected static final int MSG_CLOSE_SEARCH_PANEL = 1025;
    protected static final int MSG_SHOW_INTRUDER = 1026;
    protected static final int MSG_HIDE_INTRUDER = 1027;
	// Aurora <zhanggp> <2013-10-21> modified for systemui begin
	protected static final int MSG_REMOVE_NOTIFICATION = 1028;
	// Aurora <zhanggp> <2013-10-21> modified for systemui end
    protected static final boolean ENABLE_INTRUDERS = false;

    // Should match the value in PhoneWindowManager
    public static final String SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps";

    public static final int EXPANDED_LEAVE_ALONE = -10000;
    public static final int EXPANDED_FULL_OPEN = -10001;

    protected CommandQueue mCommandQueue;
    protected IStatusBarService mBarService;
    protected H mHandler = createHandler();

    // all notifications
    //update to 5.0 begin
//    protected NotificationData mNotificationData = new NotificationData();
    protected NotificationData mNotificationData;
    final protected SparseArray<UserInfo> mCurrentProfiles = new SparseArray<UserInfo>();
    private UserManager mUserManager;
    //update to 5.0 end
    protected NotificationRowLayout mPile;

    protected StatusBarNotification mCurrentlyIntrudingNotification;

    // used to notify status bar for suppressing notification LED
    protected boolean mPanelSlightlyVisible;

    // Search panel
    protected SearchPanelView mSearchPanelView;

    protected PopupMenu mNotificationBlamePopup;

    protected int mCurrentUserId = 0;

	// Aurora <zhanggp> <2013-10-21> modified for systemui begin
	protected ArrayList<RemoveData> mToRemoveList = new ArrayList<RemoveData>();

	protected static final String AURORA_TOSET = "auroraSBT8345";
	protected static final String AURORA_NOT_TOSET = "auroraSBNT653";

    // Aurora <Felix.Duan> <2015-2-6> <BEGIN> StatusBar color invert
    // Add by xiao.yong
    protected static final String AURORA_BLACK_IMMERSE = "aurorablackBG8345";
	protected static final String AURORA_WHITE_IMMERSE = "aurorawhiteBG653";
    // Aurora <Felix.Duan> <2015-2-6> <END> StatusBar color invert
	
	private AuroraAlertDialog mAlertDialog = null;

	private Utils2Icon mUtils2Icon;

    // Aurora <Felix.Duan> <2015-2-6> <BEGIN> StatusBar color invert
    public ColorFilter mColorFilter;
    // Aurora <Felix.Duan> <2015-2-6> <END> StatusBar color invert

		
	// Aurora <zhanggp> <2013-10-21> modified for systemui end

    // UI-specific methods

    /**
     * Create all windows necessary for the status bar (including navigation, overlay panels, etc)
     * and add them to the window manager.
     */
    protected abstract void createAndAddWindows();

    protected WindowManager mWindowManager;
    protected IWindowManager mWindowManagerService;
    protected Display mDisplay;

    private boolean mDeviceProvisioned = false;
    
    protected HandlerBar mHandlerBar;
 // Aurora <tongyh> <2013-12-13> set PhoneStatusBarView background transparent begin
    public static boolean isCanSetStatusBarViewBg = false;
 // Aurora <tongyh> <2013-12-13> set PhoneStatusBarView background transparent end
 // Aurora <tongyh> <2013-12-28> judgment of non-template begin
    private int mTemplatesIds[];
 // Aurora <tongyh> <2013-12-28> judgment of non-template end
    public static boolean isPhoneRinging = false;
    public boolean isNotificationClick = false;
    
    private int notification_wifi_id =  0;
    private int notification_adb_active_id = 0;
    private int notification_zuo_wei_anzhuangchengxu_id = 0;
    private int notification_usb_mtp_id = 0;
    private int notification_usb_ptp_id = 0;
    private int notification_stat_sys_data_usb_id = 0;
    private int notification_stat_sys_warning_id = 0;
    private int notification_stat_tether_general_id = 0;
    private int notification_stat_tether_usb_id = 0;
    private int notification_stat_tether_wifi_id = 0;
    private int notification_stat_notify_disk_full_id = 0;
    private int notification_stat_notify_sdcard_usb_id =  0;
    private int notification_stat_notify_sdcard_prepare_id = 0;
    private int notification_stat_notify_rssi_in_range = 0;
    private int notification_stat_notify_stat_sys_download_id = 0;
    private int notification_stat_notify_stat_sys_upload_id = 0;
    private int notification_stat_notify_stat_sys_download_done_id = 0;
    private int notification_stat_notify_stat_sys_download_done_static_id = 0;
    private int notification_stat_notify_sdcard_stat_notify_sdcard_usb =  0;
    private int notification_stat_notify_sdcard_stat_notify_sdcard =  0;
    private int internalTextId = 0;
    private int oppoInternalTextId = 0;
    private int internalIconId = 0;
    private int oppointernalIconId = 0;
// Aurora <tongyh> <2014-10-28> hooking resources begin
    private int internalActionDividerId = 0;
    private int internalOppActionDividerId = 0;
    private int internalOverflowDividerId = 0;
    private int internalOppOverflowDividerId = 0;
//    private final String AURORA_DEFAULT_FONT_PATH = "system/fonts/DroidSansFallback.ttf";
//    private Typeface auroraDefaultTf;
    private int internalTitleId = 0;
    private int internalOppTitleId = 0;
    private int internalTimeId = 0;
    private int internalOppTimeId = 0;
    private int internalChronometerId = 0;
    private int internalOppChronometerId = 0;
    private int internalNotificationSimIndicatorTextId = 0;
    private int internalOppNotificationSimIndicatorTextId = 0;
    private int internalText2Id = 0;
    private int internalOppText2Id = 0;
    private int internalInfoId = 0;
    private int internalOppInfoId = 0;
    private int internalBigTextId = 0;
    private int internalOppBigTextId = 0;
    
    private int internalInboxText0Id = 0;
    private int internalOppInboxText0Id = 0;
    
    private int internalInboxText1Id = 0;
    private int internalOppInboxText1Id = 0;
    
    private int internalInboxText2Id = 0;
    private int internalOppInboxText2Id = 0;
    
    private int internalInboxText3Id = 0;
    private int internalOppInboxText3Id = 0;
    
    private int internalInboxText4Id = 0;
    private int internalOppInboxText4Id = 0;
    
    private int internalInboxText5Id = 0;
    private int internalOppInboxText5Id = 0;
    
    private int internalInboxText6Id = 0;
    private int internalOppInboxText6Id = 0;
    
    private int internalInboxMoreId = 0;
    private int internalOppInboxMoreId = 0;
    private int internalProgressId = 0;
    private int oppointernalProgressId = 0;
//    private Drawable notificationTemplateIconBackgroundDrawable = null;
//    private Drawable notificationAuroraStatSeekbarBg = null;
//    private Drawable notificationAuroraImaginaryLineBg = null;
// Aurora <tongyh> <2014-10-28> hooking resources end
    private LruMemoryCache iconLruMemoryCache = null;
    // Aurora <tongyh> <2014-10-10> notification width adaptation without the virtual key begin
    private boolean isShowNavigationBar = false;
    // Aurora <tongyh> <2014-10-10> notification width adaptation without the virtual key end

    public IStatusBarService getStatusBarService() {
        return mBarService;
    }

    public boolean isDeviceProvisioned() {
        return mDeviceProvisioned;
    }

    private ContentObserver mProvisioningObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            final boolean provisioned = 0 != Settings.Global.getInt(
                    mContext.getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 0);
            if (provisioned != mDeviceProvisioned) {
                mDeviceProvisioned = provisioned;
                updateNotificationIcons();
            }
        }
    };

    private RemoteViews.OnClickHandler mOnClickHandler = new RemoteViews.OnClickHandler() {
        @Override
        public boolean onClickHandler(View view, PendingIntent pendingIntent, Intent fillInIntent) {
            if (DEBUG) {
                Slog.v(TAG, "Notification click handler invoked for intent: " + pendingIntent);
            }
            final boolean isActivity = pendingIntent.isActivity();
            if (isActivity) {
                try {
                    // The intent we are sending is for the application, which
                    // won't have permission to immediately start an activity after
                    // the user switches to home.  We know it is safe to do at this
                    // point, so make sure new activity switches are now allowed.
                    ActivityManagerNative.getDefault().resumeAppSwitches();
                    // Also, notifications can be launched from the lock screen,
                    // so dismiss the lock screen when the activity starts.
                  //update to 5.0 begin
//                    ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
                    ActivityManagerNative.getDefault().keyguardWaitingForActivityDrawn ();
                  //update to 5.0 end
                } catch (RemoteException e) {
                }
            }

            boolean handled = super.onClickHandler(view, pendingIntent, fillInIntent);

            if (isActivity && handled) {
                // close the shade if it was open
                animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
                visibilityChanged(false);
            }
            return handled;
        }
    };
    
    //update to 5.0 begin
    private void updateCurrentProfilesCache() {
        synchronized (mCurrentProfiles) {
            mCurrentProfiles.clear();
            if (mUserManager != null) {
                for (UserInfo user : mUserManager.getProfiles(mCurrentUserId)) {
                    mCurrentProfiles.put(user.id, user);
                }
            }
        }
    }
    //update to 5.0 end
    
    public void start() {
		// Aurora <zhanggp> <2013-10-08> added for systemui begin
		mPm = mContext.getPackageManager();
		// Aurora <zhanggp> <2013-10-08> added for systemui end
        mWindowManager = (WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
        mWindowManagerService = WindowManagerGlobal.getWindowManagerService();
        mDisplay = mWindowManager.getDefaultDisplay();
        mProvisioningObserver.onChange(false); // set up
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.DEVICE_PROVISIONED), true,
                mProvisioningObserver);
        //update to 5.0 begin
        mNotificationData = new NotificationData(this);
        mUserManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        //update to 5.0 end
        mBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));

        // Connect in to the status bar manager service
        StatusBarIconList iconList = new StatusBarIconList();
        ArrayList<IBinder> notificationKeys = new ArrayList<IBinder>();
        ArrayList<StatusBarNotification> notifications = new ArrayList<StatusBarNotification>();
		// Aurora <zhanggp> <2013-10-18> modified for systemui begin
		StatusBarIconList exIconList = new StatusBarIconList();
		String slots[] = {
						"headset"
					};
		mExIcons.defineSlots(slots);
		exIconList.copyFrom(mExIcons);
		
		mUtils2Icon = Utils2Icon.getInstance(mContext);

		mCommandQueue = new CommandQueue(this, iconList ,exIconList);
		// mCommandQueue = new CommandQueue(this, iconList);
		// Aurora <zhanggp> <2013-10-18> modified for systemui END
        //update to 5.0 begin
//        int[] switches = new int[7];
        int[] switches = new int[8];
        //update to 5.0 end
        ArrayList<IBinder> binders = new ArrayList<IBinder>();
        try {
            //update to 5.0 begin
//            mBarService.registerStatusBar(mCommandQueue, iconList, notificationKeys, notifications,
//                    switches, binders);
        	mBarService.registerStatusBar(mCommandQueue, iconList, switches, binders);
            //update to 5.0 end
        } catch (RemoteException ex) {
            // If the system process isn't there we're doomed anyway.
        }

        createAndAddWindows();

        disable(switches[0]);
        setSystemUiVisibility(switches[1], 0xffffffff);
        topAppWindowChanged(switches[2] != 0);
        // StatusBarManagerService has a back up of IME token and it's restored here.
        //update to 5.0 begin
//        setImeWindowStatus(binders.get(0), switches[3], switches[4]);
        setImeWindowStatus(binders.get(0), switches[3], switches[4], switches[5] != 0);
//        setHardKeyboardStatus(switches[5] != 0, switches[6] != 0); //annotate
        //update to 5.0 end
        // Aurora <tongyh> <2013-12-28> judgment of non-template begin
        getSystemTemplatesId();
        // Aurora <tongyh> <2013-12-28> judgment of non-template end
        // Set up the initial icon state
        int N = iconList.size();
        int viewIndex = 0;
        for (int i=0; i<N; i++) {
            StatusBarIcon icon = iconList.getIcon(i);
            if (icon != null) {
                addIcon(iconList.getSlot(i), i, viewIndex, icon);
                viewIndex++;
            }
        }

        // Set up the initial notification state
        N = notificationKeys.size();
        if (N == notifications.size()) {
            for (int i=0; i<N; i++) {
//                addNotification(notificationKeys.get(i), notifications.get(i));
            }
        } else {
            Log.wtf(TAG, "Notification list length mismatch: keys=" + N
                    + " notifications=" + notifications.size());
        }
        //update to 5.0 begin
     // Set up the initial notification state.
        try {
            mNotificationListener.registerAsSystemService(mContext,
                    new ComponentName(mContext.getPackageName(), getClass().getCanonicalName()),
                    UserHandle.USER_ALL);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to register notification listener", e);
        }
        //update to 5.0 end
        if (DEBUG) {
            Slog.d(TAG, String.format(
                    "init: icons=%d disabled=0x%08x lights=0x%08x menu=0x%08x imeButton=0x%08x",
                   iconList.size(),
                   switches[0],
                   switches[1],
                   switches[2],
                   switches[3]
                   ));
        }

        mCurrentUserId = ActivityManager.getCurrentUser();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        filter.addAction(Intent.ACTION_USER_ADDED);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                    mCurrentUserId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
                    //update to 5.0 begin
                    updateCurrentProfilesCache();
                    //update to 5.0 end
                    if (true) Slog.v(TAG, "userId " + mCurrentUserId + " is in the house");
                    userSwitched(mCurrentUserId);
                } //update to 5.0 begin
                else if (Intent.ACTION_USER_ADDED.equals(action)) {
                    updateCurrentProfilesCache();
                } 
              //update to 5.0 end
            }}, filter);
        mHandlerBar =  new HandlerBar(mContext);
        mHandlerBar.fun();
        initSystemResourceId();
        // Aurora <Felix.Duan> <2014-12-5> <BEGIN> Add Huawei Honor 6 to navigation bar device list
		// Aurora <tongyh> <2014-10-10> notification width adaptation without the virtual key begin
        //isShowNavigationBar = mContext.getResources().getBoolean(com.android.internal.R.bool.config_showNavigationBar);
        isShowNavigationBar = Utils.hasNavBar();
		// Aurora <tongyh> <2014-10-10> notification width adaptation without the virtual key end
        // Aurora <Felix.Duan> <2014-12-5> <END> Add Huawei Honor 6 to navigation bar device list

        // Aurora <tongyh> <2014-10-28> hooking resources begin
        /*notificationTemplateIconBackgroundDrawable = mContext.getResources().getDrawable(R.drawable.notification_template_icon_background);
        notificationAuroraStatSeekbarBg = mContext.getResources().getDrawable(R.drawable.aurora_stat_seekbar_bg);
        notificationAuroraImaginaryLineBg = mContext.getResources().getDrawable(R.drawable.aurora_imaginary_line);*/
        // Aurora <tongyh> <2014-10-28> hooking resources end
        //update to 5.0 begin
        updateCurrentProfilesCache();
        //update to 5.0 end
    }

    public void userSwitched(int newUserId) {
        // should be overridden
    }

    public boolean notificationIsForCurrentUser(StatusBarNotification n) {
        final int thisUserId = mCurrentUserId;
        final int notificationUserId = n.getUserId();
        if (DEBUG && MULTIUSER_DEBUG) {
            Slog.v(TAG, String.format("%s: current userid: %d, notification userid: %d",
                    n, thisUserId, notificationUserId));
        }
        return notificationUserId == UserHandle.USER_ALL
                || thisUserId == notificationUserId;
    }

    protected View updateNotificationVetoButton(View row, StatusBarNotification n) {
        View vetoButton = row.findViewById(R.id.veto);
        if (n.isClearable()) {
            final String _pkg = n.getPackageName();
            final String _tag = n.getTag();
            final int _id = n.getId();
            //update to 5.0 begin
            final int _userId = n.getUserId();
            //update to 5.0 end
            vetoButton.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        // Accessibility feedback
                        v.announceForAccessibility(
                                mContext.getString(R.string.accessibility_notification_dismissed));
                        try {
                            //update to 5.0 begin
//                            mBarService.onNotificationClear(_pkg, _tag, _id);
                        	mBarService.onNotificationClear(_pkg, _tag, _id, _userId);
                            //update to 5.0 end
                        } catch (RemoteException ex) {
                            // system process is dead if we're here.
                        }
                    }
                });
            vetoButton.setVisibility(View.VISIBLE);
        } else {
			// Aurora <zhanggp> <2013-11-01> modified for systemui begin
            vetoButton.setVisibility(View.INVISIBLE);
			//vetoButton.setVisibility(View.GONE);
			// Aurora <zhanggp> <2013-11-01> modified for systemui end
        }
        vetoButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return vetoButton;
    }
/*

    protected void applyLegacyRowBackground(StatusBarNotification sbn, View content) {
        if (sbn.notification.contentView.getLayoutId() !=
                com.aurora.R.layout.notification_template_base) {
            int version = 0;
            try {
                ApplicationInfo info = mContext.getPackageManager().getApplicationInfo(sbn.pkg, 0);
                version = info.targetSdkVersion;
            } catch (NameNotFoundException ex) {
                Slog.e(TAG, "Failed looking up ApplicationInfo for " + sbn.pkg, ex);
            }
            if (version > 0 && version < Build.VERSION_CODES.GINGERBREAD) {
                content.setBackgroundResource(R.drawable.notification_row_legacy_bg);
            } else {
                content.setBackgroundResource(com.aurora.R.drawable.notification_bg);
            }
        }
    }
*/
    private void startApplicationDetailsActivity(String packageName) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null));
        intent.setComponent(intent.resolveActivity(mContext.getPackageManager()));
        TaskStackBuilder.create(mContext).addNextIntentWithParentStack(intent).startActivities(
                null, UserHandle.CURRENT);
    }

    protected View.OnLongClickListener getNotificationLongClicker() {
        return new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                final String packageNameF = (String) v.getTag();
                if (packageNameF == null) return false;
                if (v.getWindowToken() == null) return false;
                mNotificationBlamePopup = new PopupMenu(mContext, v);
                mNotificationBlamePopup.getMenuInflater().inflate(
                        R.menu.notification_popup_menu,
                        mNotificationBlamePopup.getMenu());
                mNotificationBlamePopup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        if (item.getItemId() == R.id.notification_inspect_item) {
                            startApplicationDetailsActivity(packageNameF);
                            animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
                        } else {
                            return false;
                        }
                        return true;
                    }
                });
                mNotificationBlamePopup.show();

                return true;
            }
        };
    }

    public void dismissPopups() {
        if (mNotificationBlamePopup != null) {
            mNotificationBlamePopup.dismiss();
            mNotificationBlamePopup = null;
        }
    }

    public void dismissIntruder() {
        // pass
    }

    @Override
    public void toggleRecentApps() {
        Log.d("felix","StatusBar.DEBUG toggleRecentApps()");
	// Aurora <zhanggp> <2013-10-08> modified for systemui begin
//    	if(isNotificationExpanded() || isKeyguard()){
//			cancelPreloadRecentApps();
//    	}else{
//	        int msg = MSG_TOGGLE_RECENTS_PANEL;
//	        mHandler.removeMessages(msg);
//	        mHandler.sendEmptyMessage(msg);
//    	}
		/*
	        int msg = MSG_TOGGLE_RECENTS_PANEL;
	        mHandler.removeMessages(msg);
	        mHandler.sendEmptyMessage(msg);
		*/
	// Aurora <zhanggp> <2013-10-08> modified for systemui end
    	
    // Aurora <tongyh> <2013-12-04> modified for systemui begin
//    	Intent intent = new Intent(RecentsPanelView.OPEN_RECENTSPAELVIEW);
//        mContext.sendBroadcast(intent);
    	//Aurora <tongyh> <2013-12-05> long press home key to open recentspanel view begin
    	
    	Object sbservice = mContext.getSystemService("statusbar");//tymy.tao_20150505_bug13154_begin
        try {
            Class<?> statusBarManager = Class.forName("android.app.StatusBarManager");
            Method collapse;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                collapse = statusBarManager.getMethod("collapsePanels");
            } else {
                collapse = statusBarManager.getMethod("collapse");
            }
            collapse.invoke(sbservice);
        } catch (Exception e) {
            e.printStackTrace();
        }//tymy.tao_20150505_bug13154_end
    	
    	if(!mHandlerBar.isRecentsPanelViewShow()){
    		// Aurora <tongyh> <2013-12-06> set the statusbar background to black begin
    		// Aurora <tongyh> <2013-12-13> set PhoneStatusBarView background transparent begin
//    		setStatusbarBgFlag(1);
    		// Aurora <tongyh> <2013-12-13> set PhoneStatusBarView background transparent end
    		// Aurora <tongyh> <2013-12-06> set the statusbar background to black end
    		int orientation = mContext.getResources().getConfiguration().orientation;
            Log.d("felix","StatusBar.DEBUG toggleRecentApps() orientation = " + orientation
                + " isPhoneRinging = " + isPhoneRinging);
			// Aurora <Steve.Tang> make the recent panel support Orientation Land. start
            //if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            	if(!isPhoneRinging){
            		BaseStatusBar.isCanSetStatusBarViewBg = true;
            		mHandlerBar.showRecentsPanelViewForHomeKey();
            		isPhoneRinging = false;
            	}
            	
            //}
			// Aurora <Steve.Tang> make the recent panel support Orientation Land. end
    	}else{//tymy.tao_20150505_bug13105
    		mHandlerBar.mRecentsPanelView.removeRecentsPanelView();    		
    	}
    	//Aurora <tongyh> <2013-12-05> long press home key to open recentspanel view end
    // Aurora <tongyh> <2013-12-04> modified for systemui end
    }

    @Override
    public void preloadRecentApps() {
//        int msg = MSG_PRELOAD_RECENT_APPS;
//        mHandler.removeMessages(msg);
//        mHandler.sendEmptyMessage(msg);
    }

    @Override
    public void cancelPreloadRecentApps() {
//        int msg = MSG_CANCEL_PRELOAD_RECENT_APPS;
//        mHandler.removeMessages(msg);
//        mHandler.sendEmptyMessage(msg);
    }

    @Override
    public void showSearchPanel() {
        int msg = MSG_OPEN_SEARCH_PANEL;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    @Override
    public void hideSearchPanel() {
        int msg = MSG_CLOSE_SEARCH_PANEL;
        mHandler.removeMessages(msg);
        mHandler.sendEmptyMessage(msg);
    }

    protected abstract WindowManager.LayoutParams getRecentsLayoutParams(
            LayoutParams layoutParams);

    protected abstract WindowManager.LayoutParams getSearchLayoutParams(
            LayoutParams layoutParams);


    protected void updateSearchPanel() {
    	//Aurora <tongyh> <2013-12-05> delete the searchPanelView  for htc begin
        // Search Panel
//        boolean visible = false;
//        if (mSearchPanelView != null) {
//            visible = mSearchPanelView.isShowing();
//            mWindowManager.removeView(mSearchPanelView);
//        }
//
//        // Provide SearchPanel with a temporary parent to allow layout params to work.
//        LinearLayout tmpRoot = new LinearLayout(mContext);
//        mSearchPanelView = (SearchPanelView) LayoutInflater.from(mContext).inflate(
//                 R.layout.status_bar_search_panel, tmpRoot, false);
//        mSearchPanelView.setOnTouchListener(
//                 new TouchOutsideListener(MSG_CLOSE_SEARCH_PANEL, mSearchPanelView));
//        mSearchPanelView.setVisibility(View.GONE);
//
//        WindowManager.LayoutParams lp = getSearchLayoutParams(mSearchPanelView.getLayoutParams());
//
//        mWindowManager.addView(mSearchPanelView, lp);
//        mSearchPanelView.setBar(this);
//        if (visible) {
//            mSearchPanelView.show(true, false);
//        }
    	//Aurora <tongyh> <2013-12-05> delete the searchPanelView  for htc end
    }

    protected H createHandler() {
         return new H();
    }

    static void sendCloseSystemWindows(Context context, String reason) {
        if (ActivityManagerNative.isSystemReady()) {
            try {
                ActivityManagerNative.getDefault().closeSystemDialogs(reason);
            } catch (RemoteException e) {
            }
        }
    }

    protected abstract View getStatusBarView();

    protected void toggleRecentsActivity() {
        try {
			
			if(RecentTasksLoader.getInstance(mContext).isRecentActivityShowing()){
				return;
			}
			
            RecentTasksLoader.getInstance(mContext).getFirstTask();

            Intent intent = new Intent(RecentsActivity.TOGGLE_RECENTS_INTENT);
            intent.setClassName("com.android.systemui",
                    "com.android.systemui.recent.RecentsActivity");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
//Aurora <tongyh> <2013-11-05> RecentsActivity  enter and exit animation begin
// Aurora <zhanggp> <2013-10-17> modified for systemui begin
//            ActivityOptions opts = ActivityOptions.makeCustomAnimation(mContext,
//                    R.anim.aurora_recents_launch_from_launcher_enter,
//                    R.anim.aurora_recents_launch_from_launcher_exit);
			//intent.putExtra(RecentsActivity.WAITING_FOR_WINDOW_ANIMATION_PARAM, true);
//            mContext.startActivityAsUser(intent, opts.toBundle(), new UserHandle(
//                    UserHandle.USER_CURRENT));
            mContext.startActivityAsUser(intent, new UserHandle(
                    UserHandle.USER_CURRENT));
//Aurora <tongyh> <2013-11-05> RecentsActivity  enter and exit animation end
/*
           if (firstTask == null) {
                if (RecentsActivity.forceOpaqueBackground(mContext)) {
                    ActivityOptions opts = ActivityOptions.makeCustomAnimation(mContext,
                            R.anim.recents_launch_from_launcher_enter,
                            R.anim.recents_launch_from_launcher_exit);
                    mContext.startActivityAsUser(intent, opts.toBundle(), new UserHandle(
                            UserHandle.USER_CURRENT));
                } else {
                    // The correct window animation will be applied via the activity's style
                    mContext.startActivityAsUser(intent, new UserHandle(
                            UserHandle.USER_CURRENT));
                }

            } else {
                Bitmap first = firstTask.getThumbnail();
                final Resources res = mContext.getResources();

                float thumbWidth = res
                        .getDimensionPixelSize(R.dimen.status_bar_recents_thumbnail_width);
                float thumbHeight = res
                        .getDimensionPixelSize(R.dimen.status_bar_recents_thumbnail_height);
                if (first == null) {
                    throw new RuntimeException("Recents thumbnail is null");
                }
                if (first.getWidth() != thumbWidth || first.getHeight() != thumbHeight) {
                    first = Bitmap.createScaledBitmap(first, (int) thumbWidth, (int) thumbHeight,
                            true);
                    if (first == null) {
                        throw new RuntimeException("Recents thumbnail is null");
                    }
                }


                DisplayMetrics dm = new DisplayMetrics();
                mDisplay.getMetrics(dm);
                // calculate it here, but consider moving it elsewhere
                // first, determine which orientation you're in.
                // todo: move the system_bar layouts to sw600dp ?
                final Configuration config = res.getConfiguration();
                int x, y;

                if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    float appLabelLeftMargin = res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_app_label_left_margin);
                    float appLabelWidth = res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_app_label_width);
                    float thumbLeftMargin = res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_thumbnail_left_margin);
                    float thumbBgPadding = res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_thumbnail_bg_padding);

                    float width = appLabelLeftMargin +
                            +appLabelWidth
                            + thumbLeftMargin
                            + thumbWidth
                            + 2 * thumbBgPadding;

                    x = (int) ((dm.widthPixels - width) / 2f + appLabelLeftMargin + appLabelWidth
                            + thumbBgPadding + thumbLeftMargin);
                    y = (int) (dm.heightPixels
                            - res.getDimensionPixelSize(R.dimen.status_bar_recents_thumbnail_height) - thumbBgPadding);
                } else { // if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    float thumbTopMargin = res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_thumbnail_top_margin);
                    float thumbBgPadding = res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_thumbnail_bg_padding);
                    float textPadding = res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_text_description_padding);
                    float labelTextSize = res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_app_label_text_size);
                    Paint p = new Paint();
                    p.setTextSize(labelTextSize);
                    float labelTextHeight = p.getFontMetricsInt().bottom
                            - p.getFontMetricsInt().top;
                    float descriptionTextSize = res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_app_description_text_size);
                    p.setTextSize(descriptionTextSize);
                    float descriptionTextHeight = p.getFontMetricsInt().bottom
                            - p.getFontMetricsInt().top;

                    float statusBarHeight = res
                            .getDimensionPixelSize(com.aurora.R.dimen.status_bar_height);
                    float recentsItemTopPadding = statusBarHeight;

                    float height = thumbTopMargin
                            + thumbHeight
                            + 2 * thumbBgPadding + textPadding + labelTextHeight
                            + recentsItemTopPadding + textPadding + descriptionTextHeight;
                    float recentsItemRightPadding = res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_item_padding);
                    float recentsScrollViewRightPadding = res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_right_glow_margin);
                    x = (int) (dm.widthPixels - res
                            .getDimensionPixelSize(R.dimen.status_bar_recents_thumbnail_width)
                            - thumbBgPadding - recentsItemRightPadding - recentsScrollViewRightPadding);
                    y = (int) ((dm.heightPixels - statusBarHeight - height) / 2f + thumbTopMargin
                            + recentsItemTopPadding + thumbBgPadding + statusBarHeight);
                }

                ActivityOptions opts = ActivityOptions.makeThumbnailScaleDownAnimation(
                        getStatusBarView(),
                        first, x, y,
                        new ActivityOptions.OnAnimationStartedListener() {
                            public void onAnimationStarted() {
                                Intent intent = new Intent(RecentsActivity.WINDOW_ANIMATION_START_INTENT);
                                intent.setPackage("com.android.systemui");
                                mContext.sendBroadcastAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
                            }
                        });
                intent.putExtra(RecentsActivity.WAITING_FOR_WINDOW_ANIMATION_PARAM, true);
                mContext.startActivityAsUser(intent, opts.toBundle(), new UserHandle(
                        UserHandle.USER_CURRENT));
            }
*/
// Aurora <zhanggp> <2013-10-17> modified for systemui end
    
            return;
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Failed to launch RecentAppsIntent", e);
        }
    }

    protected View.OnTouchListener mRecentsPreloadOnTouchListener = new View.OnTouchListener() {
        // additional optimization when we have software system buttons - start loading the recent
        // tasks on touch down
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getAction() & MotionEvent.ACTION_MASK;
            if (action == MotionEvent.ACTION_DOWN) {
                preloadRecentTasksList();
            } else if (action == MotionEvent.ACTION_CANCEL) {
                cancelPreloadingRecentTasksList();
            } else if (action == MotionEvent.ACTION_UP) {
                if (!v.isPressed()) {
                    cancelPreloadingRecentTasksList();
                }

            }
            return false;
        }
    };

    protected void preloadRecentTasksList() {
        if (DEBUG) Slog.d(TAG, "preloading recents");
        Intent intent = new Intent(RecentsActivity.PRELOAD_INTENT);
        intent.setClassName("com.android.systemui",
                "com.android.systemui.recent.RecentsPreloadReceiver");
        mContext.sendBroadcastAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));

        RecentTasksLoader.getInstance(mContext).preloadFirstTask();
    }

    protected void cancelPreloadingRecentTasksList() {
        if (DEBUG) Slog.d(TAG, "cancel preloading recents");
        Intent intent = new Intent(RecentsActivity.CANCEL_PRELOAD_INTENT);
        intent.setClassName("com.android.systemui",
                "com.android.systemui.recent.RecentsPreloadReceiver");
        mContext.sendBroadcastAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));

        RecentTasksLoader.getInstance(mContext).cancelPreloadingFirstTask();
    }

    protected class H extends Handler {
        public void handleMessage(Message m) {
            Intent intent;
            switch (m.what) {
             case MSG_TOGGLE_RECENTS_PANEL:
                 if (DEBUG) Slog.d(TAG, "toggle recents panel");
                 toggleRecentsActivity();
                 break;
             case MSG_CLOSE_RECENTS_PANEL:
                 if (DEBUG) Slog.d(TAG, "closing recents panel");
                 intent = new Intent(RecentsActivity.CLOSE_RECENTS_INTENT);
                 intent.setPackage("com.android.systemui");
                 mContext.sendBroadcastAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
                 break;
             case MSG_PRELOAD_RECENT_APPS:
                  preloadRecentTasksList();
                  break;
             case MSG_CANCEL_PRELOAD_RECENT_APPS:
                  cancelPreloadingRecentTasksList();
                  break;
             case MSG_OPEN_SEARCH_PANEL:
                 if (DEBUG) Slog.d(TAG, "opening search panel");
                 if (mSearchPanelView != null && mSearchPanelView.isAssistantAvailable()) {
                     mSearchPanelView.show(true, true);
                 }
                 break;
             case MSG_CLOSE_SEARCH_PANEL:
                 if (DEBUG) Slog.d(TAG, "closing search panel");
                 if (mSearchPanelView != null && mSearchPanelView.isShowing()) {
                     mSearchPanelView.show(false, true);
                 }
                 break;

			// Aurora <zhanggp> <2013-10-21> modified for systemui begin
			/*case MSG_REMOVE_NOTIFICATION:
				final INotificationManager nm = INotificationManager.Stub.asInterface(
				ServiceManager.getService(Context.NOTIFICATION_SERVICE));
				for(RemoveData data:mToRemoveList){
					 try {
						 nm.cancelNotificationWithTag(
								data.notification.getPackageName(), data.notification.getTag(),
								data.notification.getId(), UserHandle.USER_ALL);
					 } catch (android.os.RemoteException ex) {
					
					 }
				}
				mToRemoveList.clear();
				break;*/
			// Aurora <zhanggp> <2013-10-21> modified for systemui end	
            }
        }
    }

    public class TouchOutsideListener implements View.OnTouchListener {
        private int mMsg;
        private StatusBarPanel mPanel;

        public TouchOutsideListener(int msg, StatusBarPanel panel) {
            mMsg = msg;
            mPanel = panel;
        }

        public boolean onTouch(View v, MotionEvent ev) {
            final int action = ev.getAction();
            if (action == MotionEvent.ACTION_OUTSIDE
                || (action == MotionEvent.ACTION_DOWN
                    && !mPanel.isInContentArea((int)ev.getX(), (int)ev.getY()))) {
                mHandler.removeMessages(mMsg);
                mHandler.sendEmptyMessage(mMsg);
                return true;
            }
            return false;
        }
    }

    protected void workAroundBadLayerDrawableOpacity(View v) {
    }


    protected  boolean inflateViews(NotificationData.Entry entry, ViewGroup parent) {
        int minHeight =
                mContext.getResources().getDimensionPixelSize(R.dimen.notification_min_height);
        int maxHeight =
                mContext.getResources().getDimensionPixelSize(R.dimen.notification_max_height);
        // Aurora <tongyh> <2014-01-02> Modify the height of the Samsung notice begin
        int sansungHeight=
                mContext.getResources().getDimensionPixelSize(R.dimen.sansung_notification_row_min_height);
        // Aurora <tongyh> <2014-01-02> Modify the height of the Samsung notice end
        StatusBarNotification sbn = entry.notification;
        RemoteViews oneU = sbn.getNotification().contentView;
        RemoteViews large = sbn.getNotification().bigContentView;
        if (oneU == null) {
            return false;
        }

        // create the row view
        LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
		// Aurora <zhanggp> <2013-10-08> modified for systemui begin
        View row = null;
        
        //aurora_status_bar_notification_row_navibar
        //aurora_status_bar_notification_row_un_navibar
        
        final Configuration config = mContext.getResources().getConfiguration();

        if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
        	row = inflater.inflate(R.layout.aurora_status_bar_notification_row, parent, false);
        }else if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        	// Aurora <tongyh> <2014-10-10> notification width adaptation without the virtual key begin
        	if(isShowNavigationBar){
        		if(Settings.System.getInt(mContext.getContentResolver(),
            			"navigation_key_hide", 0) == 1){
            		row = inflater.inflate(R.layout.aurora_status_bar_notification_row_un_navibar, parent, false);
            			}
            	else{
            		row = inflater.inflate(R.layout.aurora_status_bar_notification_row_navibar, parent, false);
            	}
        	}else{
        		row = inflater.inflate(R.layout.aurora_status_bar_notification_row_un_navibar, parent, false);
        	}
        	// Aurora <tongyh> <2014-10-10> notification width adaptation without the virtual key begin
        }
		//View row = inflater.inflate(R.layout.status_bar_notification_row, parent, false);
		// Aurora <zhanggp> <2013-10-08> modified for systemui end
        // for blaming (see SwipeHelper.setLongPressListener)
        row.setTag(sbn.getPackageName());
        workAroundBadLayerDrawableOpacity(row);
        View vetoButton = updateNotificationVetoButton(row, sbn);
        vetoButton.setContentDescription(mContext.getString(
                R.string.accessibility_remove_notification));

        // NB: the large icon is now handled entirely by the template

        // bind the click event to the content area
        ViewGroup content = (ViewGroup)row.findViewById(R.id.content);
        ViewGroup adaptive = (ViewGroup)row.findViewById(R.id.adaptive);

        content.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        PendingIntent contentIntent = sbn.getNotification().contentIntent;
        if (contentIntent != null) {
            final View.OnClickListener listener = new NotificationClicker(contentIntent,
                    sbn.getPackageName(), sbn.getTag(), sbn.getId());
            content.setOnClickListener(listener);
        } else {
            content.setOnClickListener(null);
        }

        // TODO(cwren) normalize variable names with those in updateNotification
        View expandedOneU = null;
        View expandedLarge = null;
        try {
            expandedOneU = oneU.apply(mContext, adaptive, mOnClickHandler);
            if (large != null) {
                expandedLarge = large.apply(mContext, adaptive, mOnClickHandler);
            }
        }
        catch (RuntimeException e) {
            final String ident = sbn.getPackageName() + "/0x" + Integer.toHexString(sbn.getId());
            Slog.e(TAG, "couldn't inflate view for notification " + ident, e);
            return false;
        }

        if (expandedOneU != null) {
            SizeAdaptiveLayout.LayoutParams params =
                    new SizeAdaptiveLayout.LayoutParams(expandedOneU.getLayoutParams());
            // Aurora <tongyh> <2014-01-02> Modify the height of the Samsung notice begin 
            if("com.sec.android.pagebuddynotisvc".equals(sbn.getPackageName()) || "com.sec.android.app.music".equals(sbn.getPackageName())){
            	row.setTag(R.id.sansung_special_tag, Boolean.valueOf(true));
            	params.minHeight = sansungHeight;
            	params.maxHeight = sansungHeight;
            }else{
            	row.setTag(R.id.sansung_special_tag, Boolean.valueOf(false));
            // Aurora <tongyh> <2014-01-02> Modify the height of the Samsung notice end 
            	params.minHeight = minHeight;
            	params.maxHeight = minHeight;
            }
            // Aurora <tongyh> <2014-06-10> the iuni music does not support the darg notice begin 
            if("com.android.music".equals(sbn.getPackageName())){
            	row.setTag(R.id.sansung_special_tag, Boolean.valueOf(true));
            }
            // Aurora <tongyh> <2014-06-10> the iuni music does not support the darg notice end
            if("com.sina.weibo".equals(sbn.getPackageName())||"com.letv.android.client".equals(sbn.getPackageName())){//tymy_20150512_bug12265
            	row.setTag(R.id.sansung_special_tag, Boolean.valueOf(true));
            }
            adaptive.addView(expandedOneU, params);
        }
        if (expandedLarge != null) {
            SizeAdaptiveLayout.LayoutParams params =
                    new SizeAdaptiveLayout.LayoutParams(expandedLarge.getLayoutParams());
            params.minHeight = minHeight+1;
            params.maxHeight = maxHeight;
            adaptive.addView(expandedLarge, params);
        }
        row.setDrawingCacheEnabled(true);
		// Aurora <zhanggp> <2013-10-08> modified for systemui begin
        // Aurora <tongyh> <2013-12-28> judgment of non-template begin
//		AuroraApplyLegacyRowBackground(expandedOneU,expandedLarge,content);
        // Aurora <tongyh> <2014-01-22> set nitification's icon background begin
        /*ImageView iv = (ImageView)content.findViewById(getSystemTemplatesIconId());
        Bitmap icon = Utils.getSystemIconBitmapByPackage(mContext, sbn.getPackageName());
		if( iv != null && icon != null){
			iv.setImageBitmap(icon);
			iv.setBackgroundColor(android.R.color.transparent);
		}*/
//        if(!isThisASystemPackage(sbn.getPackageName()) && iv != null){
////        	iv.setScaleType(ScaleType.CENTER_CROP);
//        	iv.setBackgroundColor(android.R.color.transparent);
//        }
        // Aurora <tongyh> <2014-01-22> set nitification's icon background end
    	AuroraApplyLegacyRowBackground(expandedOneU,expandedLarge,content,isSystemNotification(sbn));
    	if(Build.MODEL.contains("H60-L01")){
    		setOtherTemplateLayoutTestColor(expandedOneU, expandedLarge, isSystemNotification(sbn));
    	}
		// Aurora <tongyh> <2013-12-28> judgment of non-template end
        //applyLegacyRowBackground(sbn, content);
		// Aurora <zhanggp> <2013-10-08> modified for systemui end
        row.setTag(R.id.expandable_tag, Boolean.valueOf(large != null));

        if (MULTIUSER_DEBUG) {
            TextView debug = (TextView) row.findViewById(R.id.debug_info);
            if (debug != null) {
                debug.setVisibility(View.VISIBLE);
                debug.setText("U " + entry.notification.getUserId());
            }
        }
        entry.row = row;
        entry.content = content;
        entry.expanded = expandedOneU;
        entry.setLargeView(expandedLarge);
        setSystemIconSynLauncher(entry,sbn);
        return true;
    }

    public NotificationClicker makeClicker(PendingIntent intent, String pkg, String tag, int id) {
        return new NotificationClicker(intent, pkg, tag, id);
    }

    private class NotificationClicker implements View.OnClickListener {
        private PendingIntent mIntent;
        private String mPkg;
        private String mTag;
        private int mId;
        //update to 5.0 begin
        private  String mNotificationKey = null;
        private boolean mIsHeadsUp;
        //update to 5.0 end
        NotificationClicker(PendingIntent intent, String pkg, String tag, int id) {
            mIntent = intent;
            mPkg = pkg;
            mTag = tag;
            mId = id;
        }
        //update to 5.0 begin
        public NotificationClicker(PendingIntent intent, String notificationKey, boolean forHun) {
            mIntent = intent;
            mNotificationKey = notificationKey;
            mIsHeadsUp = false;
        }
       //update to 5.0 end
        
        public void onClick(View v) {
		// Aurora <zhanggp> <2013-11-19> modified for systemui begin
        	Log.d("rock.tong", "NotificationClicker---intent = " + mIntent.toString());
			try {
                isNotificationClick = true;
                // Aurora <Felix.Duan> <2014-6-10> <BEGIN> Support full transparent status bar
                // Deprecated old style transparent code
                // setStatusbarBgFlag(0);
                // Aurora <Felix.Duan> <2014-6-10> <END> Support full transparent status bar
				// The intent we are sending is for the application, which
				// won't have permission to immediately start an activity after
				// the user switches to home.  We know it is safe to do at this
				// point, so make sure new activity switches are now allowed.
				ActivityManagerNative.getDefault().resumeAppSwitches();
				// Also, notifications can be launched from the lock screen,
				// so dismiss the lock screen when the activity starts.
	            //update to 5.0 begin
//				ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
				ActivityManagerNative.getDefault().keyguardWaitingForActivityDrawn ();
	            //update to 5.0 end
                isNotificationClick = false;
			} catch (RemoteException e) {
                isNotificationClick = false;
			}

            if (mIntent != null) {
		/*
            if (mIntent != null) {
                final boolean isActivity = mIntent.isActivity();
                if (isActivity) {
                    try {
                        // The intent we are sending is for the application, which
                        // won't have permission to immediately start an activity after
                        // the user switches to home.  We know it is safe to do at this
                        // point, so make sure new activity switches are now allowed.
                        ActivityManagerNative.getDefault().resumeAppSwitches();
                        // Also, notifications can be launched from the lock screen,
                        // so dismiss the lock screen when the activity starts.
                        ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
                    } catch (RemoteException e) {
                    }
                }
		*/
		// Aurora <zhanggp> <2013-11-19> modified for systemui begin

                int[] pos = new int[2];
                v.getLocationOnScreen(pos);
                Intent overlay = new Intent();
                overlay.setSourceBounds(
                        new Rect(pos[0], pos[1], pos[0]+v.getWidth(), pos[1]+v.getHeight()));
                try {
                    mIntent.send(mContext, 0, overlay);
                } catch (PendingIntent.CanceledException e) {
                    // the stack trace isn't very helpful here.  Just log the exception message.
                    Slog.w(TAG, "Sending contentIntent failed: " + e);
                }

                KeyguardManager kgm =
                    (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
                if (kgm != null) kgm.exitKeyguardSecurely(null);
            }

            try {
                //update to 5.0 begin
//                mBarService.onNotificationClick(mPkg, mTag, mId);
            	mBarService.onNotificationClick(mNotificationKey);
                //update to 5.0 end
            } catch (RemoteException ex) {
                // system process is dead if we're here.
            }

            // close the shade if it was open
            animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE);
            visibilityChanged(false);

            // If this click was on the intruder alert, hide that instead
//            mHandler.sendEmptyMessage(MSG_HIDE_INTRUDER);
        }
    }
    /**
     * The LEDs are turned o)ff when the notification panel is shown, even just a little bit.
     * This was added last-minute and is inconsistent with the way the rest of the notifications
     * are handled, because the notification isn't really cancelled.  The lights are just
     * turned off.  If any other notifications happen, the lights will turn back on.  Steve says
     * this is what he wants. (see bug 1131461)
     */
    protected void visibilityChanged(boolean visible) {
        if (mPanelSlightlyVisible != visible) {
            mPanelSlightlyVisible = visible;
            try {
                mBarService.onPanelRevealed();
            } catch (RemoteException ex) {
                // Won't fail unless the world has ended.
            }
        }
    }

    /**
     * Cancel this notification and tell the StatusBarManagerService / NotificationManagerService
     * about the failure.
     *
     * WARNING: this will call back into us.  Don't hold any locks.
     */
    //update to 5.0 begin
//    void handleNotificationError(IBinder key, StatusBarNotification n, String message) {
//        removeNotification(key);
//        try {
//            mBarService.onNotificationError(n.getPackageName(), n.getTag(), n.getId(), n.getUid(), n.getInitialPid(), message);
//        } catch (RemoteException ex) {
//            // The end is nigh.
//        }catch (Exception ex){
//        	
//        }
//    }
    void handleNotificationError(StatusBarNotification n, String message) {
        removeNotification(n.getKey(), null);
        try {
            mBarService.onNotificationError(n.getPackageName(), n.getTag(), n.getId(), n.getUid(),
                    n.getInitialPid(), message, n.getUserId());
        } catch (RemoteException ex) {
            // The end is nigh.
        }
    }
    //update to 5.0 end
    //update to 5.0 begin
//    protected StatusBarNotification removeNotificationViews(IBinder key) {
    protected StatusBarNotification removeNotificationViews(String key, RankingMap ranking) {
//        NotificationData.Entry entry = mNotificationData.remove(key);
    	NotificationData.Entry entry = mNotificationData.remove(key, ranking);
        //update to 5.0 end
        if (entry == null) {
            Slog.w(TAG, "removeNotification for unknown key: " + key);
            Log.d("PhoneStatusBar", "removeNotification for unknown key: " + key);
            return null;
        }
        // Remove the expanded view.
        ViewGroup rowParent = (ViewGroup)entry.row.getParent();
        if (rowParent != null) rowParent.removeView(entry.row);
// Aurora <tongyh> <2014-05-06>  Notification does not automatically expand begin
//        updateExpansionStates();
// Aurora <tongyh> <2014-11-17>  BUG # 9600 takedown notice, expand the notification will fold begin
//        updateExpansionStatesNoExpand();
// Aurora <tongyh> <2014-11-17>  BUG # 9600 takedown notice, expand the notification will fold end
// Aurora <tongyh> <2014-05-06>  Notification does not automatically expand end
        updateNotificationIcons();
        Log.d("PhoneStatusBar", "removeNotificationViews---updateNotificationIcons();");

        return entry.notification;
    }
	//update to 5.0 begin
/*    protected StatusBarIconView addNotificationViews(IBinder key,
            StatusBarNotification notification) {*/
    protected NotificationData.Entry createNotificationViews(StatusBarNotification notification) {
    	//update to 5.0 end
        final String key = notification.getKey();
        if (DEBUG) {
            Slog.d(TAG, "addNotificationViews(key=" + key + ", notification=" + notification);
        }
        // Construct the icon.
        final StatusBarIconView iconView = new StatusBarIconView(mContext,
                notification.getPackageName() + "/0x" + Integer.toHexString(notification.getId()),
                notification.getNotification(), this);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        final StatusBarIcon ic = new StatusBarIcon(notification.getPackageName(),
                    notification.getUser(),
                    notification.getNotification().icon,
                    notification.getNotification().iconLevel,
                    notification.getNotification().number,
                    notification.getNotification().tickerText);
        if (!iconView.set(ic)) {
            //update to 5.0 begin
//            handleNotificationError(key, notification, "Couldn't create icon: " + ic);
            handleNotificationError(notification, "Couldn't create icon: " + ic);
            //update to 5.0 end
            return null;
        }
        // Construct the expanded view.
    	//update to 5.0 begin
//        NotificationData.Entry entry = new NotificationData.Entry(key, notification, iconView);
        NotificationData.Entry entry = new NotificationData.Entry(notification, iconView);
    	//update to 5.0 end
        if (!inflateViews(entry, mPile)) {
        	//update to 5.0 begin
//            handleNotificationError(key, notification, "Couldn't expand RemoteViews for: "
//                    + notification);
        	handleNotificationError(notification, "Couldn't expand RemoteViews for: "
                    + notification);
          //update to 5.0 end
            return null;
        }
    	//update to 5.0 begin
        return entry;
    	//update to 5.0 end
    }
    
    
    protected void addNotificationViews(Entry entry, RankingMap ranking) {
    	Log.d("0401", "addNotificationViews(Entry entry, RankingMap ranking)");
        if (entry == null) {
        	Log.d("0401", "addNotificationViews(shadeEntry, ranking);------entry == null----return");
            return;
        }
     // Aurora <zhanggp> <2013-10-08> added for systemui begin
     		updateViewStatus(entry);
     		// Aurora <zhanggp> <2013-10-08> added for systemui end
             // Add the expanded view and icon.
//             int pos = mNotificationData.add(entry);
     		 mNotificationData.add(entry, ranking);
 /*            if (DEBUG) {
                 Slog.d(TAG, "addNotificationViews: added at " + pos);
             }*/
          // Aurora <tongyh> <2014-05-06>  Notification does not automatically expand begin
//             updateExpansionStates();
             updateExpansionStatesNoExpand();
          // Aurora <tongyh> <2014-05-06>  Notification does not automatically expand end
             updateNotificationIcons();
    }
    

    protected boolean expandView(NotificationData.Entry entry, boolean expand) {
        int rowHeight =
                mContext.getResources().getDimensionPixelSize(R.dimen.notification_row_min_height);
     // Aurora <tongyh> <2014-01-02> Modify the height of the Samsung notice begin
        int sansungHeight=
                mContext.getResources().getDimensionPixelSize(R.dimen.sansung_notification_row_min_height);
     // Aurora <tongyh> <2014-01-02> Modify the height of the Samsung notice end
        ViewGroup.LayoutParams lp = entry.row.getLayoutParams();
        if (entry.expandable() && expand) {
            if (DEBUG) Slog.d(TAG, "setting expanded row height to WRAP_CONTENT");
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        } else {
            if (DEBUG) Slog.d(TAG, "setting collapsed row height to " + rowHeight);
            // Aurora <tongyh> <2014-01-02> Modify the height of the Samsung notice begin
            if("com.sec.android.pagebuddynotisvc".equals(entry.notification.getPackageName()) || "com.sec.android.app.music".equals(entry.notification.getPackageName())){
            	lp.height = sansungHeight;
            }else{
            // Aurora <tongyh> <2014-01-02> Modify the height of the Samsung notice end
            	lp.height = rowHeight;
            }
            
        }
        entry.row.setLayoutParams(lp);
        return expand;
    }

    protected void updateExpansionStates() {
    	//update to 5.0 begin
//        int N = mNotificationData.size();
    	ArrayList<Entry> activeNotifications = mNotificationData.getActiveNotifications();
        int N = activeNotifications.size();
    	//update to 5.0 end
        for (int i = 0; i < N; i++) {
        	//update to 5.0 begin
//            NotificationData.Entry entry = mNotificationData.get(i);
        	NotificationData.Entry entry = activeNotifications.get(i);
        	//update to 5.0 end
         // Aurora <tongyh> <2014-06-10> the iuni music does not support the darg notice begin 
            if("com.android.music".equals(entry.notification.getPackageName())){
            	expandView(entry, true);
            	continue;
            }
         // Aurora <tongyh> <2014-06-10> the iuni music does not support the darg notice end
            if (!entry.userLocked()) {
                if (i == (N-1)) {
                    if (DEBUG) Slog.d(TAG, "expanding top notification at " + i);
                    expandView(entry, true);
                } else {
                    if (!entry.userExpanded()) {
                        if (DEBUG) Slog.d(TAG, "collapsing notification at " + i);
                        expandView(entry, false);
                    } else {
                        if (DEBUG) Slog.d(TAG, "ignoring user-modified notification at " + i);
                    }
                }
            } else {
                if (DEBUG) Slog.d(TAG, "ignoring notification being held by user at " + i);
            }
        }
    }
    
 // Aurora <tongyh> <2014-05-06>  Notification does not automatically expand begin
    protected void updateExpansionStatesNoExpand() {
    	//update to 5.0 begin
//        int N = mNotificationData.size();
    	ArrayList<Entry> activeNotifications = mNotificationData.getActiveNotifications();
        int N = activeNotifications.size();
      //update to 5.0 end
        for (int i = 0; i < N; i++) {
        	//update to 5.0 begin
//            NotificationData.Entry entry = mNotificationData.get(i);
        	NotificationData.Entry entry = activeNotifications.get(i);
        	//update to 5.0 end
         // Aurora <tongyh> <2014-06-10> the iuni music does not support the darg notice begin 
            if("com.android.music".equals(entry.notification.getPackageName())){
            	expandView(entry, true);
            	continue;
            }
         // Aurora <tongyh> <2014-06-10> the iuni music does not support the darg notice end 
            if (!entry.userLocked()) {
                /*if (i == (N-1)) {
                    if (DEBUG) Slog.d(TAG, "expanding top notification at " + i);
//                    expandView(entry, false);
                } else {*/
                    if (!entry.userExpanded()) {
                        if (DEBUG) Slog.d(TAG, "collapsing notification at " + i);
                        expandView(entry, false);
                    } else {
                        if (DEBUG) Slog.d(TAG, "ignoring user-modified notification at " + i);
                    }
//                }
            } else {
                if (DEBUG) Slog.d(TAG, "ignoring notification being held by user at " + i);
            }
        }
    }
 // Aurora <tongyh> <2014-05-06>  Notification does not automatically expand begin
    
    protected abstract void haltTicker();
    protected abstract void setAreThereNotifications();
    protected abstract void updateNotificationIcons();
    //update to 5.0 begin
//    protected abstract void tick(IBinder key, StatusBarNotification n, boolean firstTime);
    protected abstract void tick(StatusBarNotification n, boolean firstTime);
    //update to 5.0 end
    protected abstract void updateExpandedViewPos(int expandedPosition);
    protected abstract int getExpandedViewMaxHeight();
    protected abstract boolean shouldDisableNavbarGestures();
    /// M: Support "Dual SIM".
    public abstract boolean isExpanded();

    protected boolean isTopNotification(ViewGroup parent, NotificationData.Entry entry) {
        return parent != null && parent.indexOfChild(entry.row) == 0;
    }
    //update to 5.0 begin
    public abstract void addNotification(StatusBarNotification notification,
            RankingMap ranking);
    protected abstract void updateNotificationRanking(RankingMap ranking);
    public abstract void removeNotification(String key, RankingMap ranking);

//    public void updateNotification(IBinder key, StatusBarNotification notification) {
    public void updateNotification(StatusBarNotification notification, RankingMap ranking) {
        final String key = notification.getKey();
        //update to 5.0 end
    	Log.d("PhoneStatusBar", "updateNotification------notification.PackageName = " + notification.getPackageName()+ " ; notification.getId() = " + notification.getId());
        if (DEBUG) Slog.d(TAG, "updateNotification(" + key + " -> " + notification + ")");
        //update to 5.0 begin
//        final NotificationData.Entry oldEntry = mNotificationData.findByKey(key);
        final NotificationData.Entry oldEntry = mNotificationData.get(key);
        //update to 5.0 end
        if (oldEntry == null) {
            Slog.w(TAG, "updateNotification for unknown key: " + key);
			// Aurora <zhanggp> <2013-10-30> added for systemui begin	

			if(null != findData(key)){
				if(AURORA_TOSET.equals(notification.getTag())){
					setStatusbarBgFlag(1);
				}
				else if(AURORA_NOT_TOSET.equals(notification.getTag())){
					setStatusbarBgFlag(0);
				}

                // Aurora <Felix.Duan> <2015-2-6> <BEGIN> StatusBar color invert
                // add by xiao.yong
				if(AURORA_BLACK_IMMERSE.equals(notification.getTag())){
					Log.v("xiaoyong", "updateNotification AURORA_BLACK_IMMERSE");
                    invertColorStatusBar(true);
				}
				else if(AURORA_WHITE_IMMERSE.equals(notification.getTag())){
					Log.v("xiaoyong", "updateNotification AURORA_WHITE_IMMERSE");
                    invertColorStatusBar(false);
				}
                // Aurora <Felix.Duan> <2015-2-6> <END> StatusBar color invert
			}

			
			// Aurora <zhanggp> <2013-10-30> added for systemui end
            return;
        }

        final StatusBarNotification oldNotification = oldEntry.notification;

        // XXX: modify when we do something more intelligent with the two content views
        final RemoteViews oldContentView = oldNotification.getNotification().contentView;
        final RemoteViews contentView = notification.getNotification().contentView;
        final RemoteViews oldBigContentView = oldNotification.getNotification().bigContentView;
        final RemoteViews bigContentView = notification.getNotification().bigContentView;

        if (DEBUG) {
            Slog.d(TAG, "old notification: when=" + oldNotification.getNotification().when
                    + " ongoing=" + oldNotification.isOngoing()
                    + " expanded=" + oldEntry.expanded
                    + " contentView=" + oldContentView
                    + " bigContentView=" + oldBigContentView
                    + " rowParent=" + oldEntry.row.getParent());
            Slog.d(TAG, "new notification: when=" + notification.getNotification().when
                    + " ongoing=" + oldNotification.isOngoing()
                    + " contentView=" + contentView
                    + " bigContentView=" + bigContentView);
        }

        // Can we just reapply the RemoteViews in place?  If when didn't change, the order
        // didn't change.

        // 1U is never null
        boolean contentsUnchanged = oldEntry.expanded != null
                && contentView.getPackage() != null
                && oldContentView.getPackage() != null
                && oldContentView.getPackage().equals(contentView.getPackage())
                && oldContentView.getLayoutId() == contentView.getLayoutId();
        // large view may be null
        boolean bigContentsUnchanged =
                (oldEntry.getLargeView() == null && bigContentView == null)
                || ((oldEntry.getLargeView() != null && bigContentView != null)
                    && bigContentView.getPackage() != null
                    && oldBigContentView.getPackage() != null
                    && oldBigContentView.getPackage().equals(bigContentView.getPackage())
                    && oldBigContentView.getLayoutId() == bigContentView.getLayoutId());
        ViewGroup rowParent = (ViewGroup) oldEntry.row.getParent();
        boolean orderUnchanged = notification.getNotification().when==oldNotification.getNotification().when
                || notification.getScore() == oldNotification.getScore();
                // score now encompasses/supersedes isOngoing()

        boolean updateTicker = notification.getNotification().tickerText != null
                && !TextUtils.equals(notification.getNotification().tickerText,
                        oldEntry.notification.getNotification().tickerText);
        boolean isTopAnyway = isTopNotification(rowParent, oldEntry);
        if (contentsUnchanged && bigContentsUnchanged && (orderUnchanged || isTopAnyway)) {
            if (DEBUG) Slog.d(TAG, "reusing notification for key: " + key);
            oldEntry.notification = notification;
            try {
                // Reapply the RemoteViews
                contentView.reapply(mContext, oldEntry.expanded, mOnClickHandler);
                if (bigContentView != null && oldEntry.getLargeView() != null) {
                    bigContentView.reapply(mContext, oldEntry.getLargeView(), mOnClickHandler);
                }
                // update the contentIntent
                final PendingIntent contentIntent = notification.getNotification().contentIntent;
                if (contentIntent != null) {
                    final View.OnClickListener listener = makeClicker(contentIntent,
                            notification.getPackageName(), notification.getTag(), notification.getId());
                    oldEntry.content.setOnClickListener(listener);
                } else {
                    oldEntry.content.setOnClickListener(null);
                }
                // Update the icon.
                final StatusBarIcon ic = new StatusBarIcon(notification.getPackageName(),
                        notification.getUser(),
                        (notification.getNotification().icon == notification_stat_notify_stat_sys_download_id) ? com.aurora.R.drawable.stat_sys_download : notification.getNotification().icon, notification.getNotification().iconLevel,
                        notification.getNotification().number,
                        notification.getNotification().tickerText);
                if (!oldEntry.icon.set(ic)) {
                	//update to 5.0 begin
//                    handleNotificationError(key, notification, "Couldn't update icon: " + ic);
                	handleNotificationError(notification, "Couldn't update icon: " + ic);
                  //update to 5.0 end
                    return;
                }
				// Aurora <zhanggp> <2013-10-18> added for systemui begin				
				refreshInCallState(oldEntry.notification);
				// Aurora <zhanggp> <2013-10-18> added for systemui end
				// Aurora <tongyh> <2014-04-11> Add alarm retreated to the background of green tips begin
				refreshAlarmClockState(oldEntry.notification);
				// Aurora <tongyh> <2014-04-11> Add alarm retreated to the background of green tips end
				// Aurora <tongyh> <2014-07-20>  Notification does not automatically expand begin
//                updateExpansionStates();
//                updateExpansionStatesNoExpand();
              // Aurora <tongyh> <2014-07-20>  Notification does not automatically expand end
            }
            catch (RuntimeException e) {
                // It failed to add cleanly.  Log, and remove the view from the panel.
                Slog.w(TAG, "Couldn't reapply views for package " + contentView.getPackage(), e);
             	//update to 5.0 begin
//                removeNotificationViews(key);
                removeNotificationViews(key, ranking);
//                addNotificationViews(key, notification);
                addNotification(notification, ranking);  //this will pop the headsup
             	//update to 5.0 end
                
            }
        } else {
            if (DEBUG) Slog.d(TAG, "not reusing notification for key: " + key);
            if (DEBUG) Slog.d(TAG, "contents was " + (contentsUnchanged ? "unchanged" : "changed"));
            if (DEBUG) Slog.d(TAG, "order was " + (orderUnchanged ? "unchanged" : "changed"));
            if (DEBUG) Slog.d(TAG, "notification is " + (isTopAnyway ? "top" : "not top"));
            final boolean wasExpanded = oldEntry.userExpanded();
         	//update to 5.0 begin
//            removeNotificationViews(key);
            removeNotificationViews(key, ranking);
//            addNotificationViews(key, notification);
            addNotification(notification, ranking);  //this will pop the headsup
         	//update to 5.0 end
            if (wasExpanded) {
            	//update to 5.0 begin
//                final NotificationData.Entry newEntry = mNotificationData.findByKey(key);
                final NotificationData.Entry newEntry = mNotificationData.get(key);
            	//update to 5.0 begin
             // Aurora <tongyh> <2014-05-06>  Notification does not automatically expand begin
//                expandView(newEntry, true);
                expandView(newEntry, false);
             // Aurora <tongyh> <2014-05-06>  Notification does not automatically expand end
                newEntry.setUserExpanded(true);
            }
        }

        // Update the veto button accordingly (and as a result, whether this row is
        // swipe-dismissable)
        updateNotificationVetoButton(oldEntry.row, notification);
        setSystemIconSynLauncher(oldEntry, notification);
        // Is this for you?
        boolean isForCurrentUser = notificationIsForCurrentUser(notification);
        if (DEBUG) Slog.d(TAG, "notification is " + (isForCurrentUser ? "" : "not ") + "for you");

        // Restart the ticker if it's still running
        if (updateTicker && isForCurrentUser) {
            haltTicker();
        	//update to 5.0 begin
//            tick(key, notification, false);
            tick(notification, false);
        	//update to 5.0 end
        }

        // Recalculate the position of the sliding windows and the titles.
        setAreThereNotifications();
        updateExpandedViewPos(EXPANDED_LEAVE_ALONE);

        // See if we need to update the intruder.
        if (ENABLE_INTRUDERS && oldNotification == mCurrentlyIntrudingNotification) {
            if (DEBUG) Slog.d(TAG, "updating the current intruder:" + notification);
            // XXX: this is a hack for Alarms. The real implementation will need to *update*
            // the intruder.
            if (notification.getNotification().fullScreenIntent == null) { // TODO(dsandler): consistent logic with add()
                if (DEBUG) Slog.d(TAG, "no longer intrudes!");
                mHandler.sendEmptyMessage(MSG_HIDE_INTRUDER);
            }
        }
    }

    // Q: What kinds of notifications should show during setup?
    // A: Almost none! Only things coming from the system (package is "android") that also
    // have special "kind" tags marking them as relevant for setup (see below).
    //update to 5.0 begin
    /*protected boolean showNotificationEvenIfUnprovisioned(StatusBarNotification sbn) {
        if ("android".equals(sbn.getPackageName())) {
            if (sbn.getNotification().kind != null) {
                for (String aKind : sbn.getNotification().kind) {
                    // IME switcher, created by InputMethodManagerService
                    if ("android.system.imeswitcher".equals(aKind)) return true;
                    // OTA availability & errors, created by SystemUpdateService
                    if ("android.system.update".equals(aKind)) return true;
                }
            }
        }
        return false;
    }*/
    
    protected boolean showNotificationEvenIfUnprovisioned(StatusBarNotification sbn) {
        return "android".equals(sbn.getPackageName())
                && sbn.getNotification().extras.getBoolean(Notification.EXTRA_ALLOW_DURING_SETUP);
    }
   //update to 5.0 end

    public boolean inKeyguardRestrictedInputMode() {
        KeyguardManager km = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        return km.inKeyguardRestrictedInputMode();
    }
    
    // Aurora <tongyh> <2014-6-6> Support kitkat hiden bar begin
    public void setInteracting(int barWindow, boolean interacting) {
        // hook for subclasses
    }
    // Aurora <tongyh> <2014-6-6> Support kitkat hiden bar end
	

// Aurora <zhanggp> <2013-10-08> added for systemui begin
	protected StatusBarIconList mExIcons = new StatusBarIconList();
	public abstract boolean isNotificationExpanded();
	public abstract void refreshInCallState(StatusBarNotification notification);
	public abstract void setStatusbarBgFlag(int flag);
    // Aurora <Felix.Duan> <2015-2-6> <BEGIN> StatusBar color invert
	public abstract void invertColorStatusBar(boolean invert);
    // Aurora <Felix.Duan> <2015-2-6> <END> StatusBar color invert
	// Aurora <tongyh> <2014-04-11> Add alarm retreated to the background of green tips begin
	public abstract void refreshAlarmClockState(StatusBarNotification notification);
	// Aurora <tongyh> <2014-04-11> Add alarm retreated to the background of green tips end
	public View.OnClickListener getNotificationClickListener(StatusBarNotification notification){
			View.OnClickListener l = null;
			PendingIntent contentIntent = notification.getNotification().contentIntent;
			if(null != contentIntent){
				l = new NotificationClicker(contentIntent,
                    notification.getPackageName(), notification.getTag(), notification.getId());
			}
			return l;
	}

	private PackageManager mPm;
	private boolean isThisASystemPackage(String packageName) {
        try {
			PackageInfo packageInfo = mPm.getPackageInfo(packageName,
					PackageManager.GET_DISABLED_COMPONENTS |
					PackageManager.GET_UNINSTALLED_PACKAGES |
					PackageManager.GET_SIGNATURES);	
			/*
            PackageInfo sys = mPm.getPackageInfo("android", PackageManager.GET_SIGNATURES);
            return (packageInfo != null && packageInfo.signatures != null &&
                    sys.signatures[0].equals(packageInfo.signatures[0]));*/
			//Aurora <tongyh> <2013-12-10> update conditional system applications begin
			ApplicationInfo appInfo = packageInfo.applicationInfo;
			if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) > 0){
				return true;
			}else{
				return false;
			}
			//Aurora <tongyh> <2013-12-10> update conditional system applications end
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
	}

	private void setNotificationsEnabledForPackage(String packageName, int uid, boolean enabled){
		
        final INotificationManager nm = INotificationManager.Stub.asInterface(
        ServiceManager.getService(Context.NOTIFICATION_SERVICE));
		 try {
			 nm.setNotificationsEnabledForPackage(packageName, uid, enabled);
		 } catch (android.os.RemoteException ex) {
			
		 }

	}

	public void updateViewStatus(NotificationData.Entry entry){
		StatusBarNotification sbn = entry.notification;

		boolean isSystem = isThisASystemPackage(sbn.getPackageName());
		entry.setCanShowButton(!isSystem);
		if(!isSystem){			
			View stopBuuton = entry.row.findViewById(com.android.systemui.R.id.statusbar_notification_row_stop);
			if(null != stopBuuton){
				stopBuuton.setVisibility(View.VISIBLE);
				final String disablePkg = sbn.getPackageName();
				final int uid = sbn.getUid();
				stopBuuton.setOnClickListener(new OnClickListener(){			
					@Override			
					public void onClick(View arg0) {
						// Aurora <Steve.Tang> 2015-03-03 count disable notification counts. start
						CountUtil.getInstance(mContext).update(CountUtil.COUNT_ITEM_002, 1);
						// Aurora <Steve.Tang> 2015-03-03 count disable notification counts. end
						cancelSetNotificationsEnabled();
						AuroraOpenAskDialog(disablePkg, uid);
					}					
				});
			}
		}
	
		
		if("android".equals(sbn.getPackageName())){
			return;//System Notification 
		}

		if(!sbn.isClearable()){
			return;
		}
		/*
		if(0 != (sbn.notification.flags & Notification.FLAG_NO_CLEAR)){
			return;//fix notification
		}
		*/
		if(("com.android.phone".equals(sbn.getPackageName()) && sbn.getId() < 17000) || 
			"com.tencent.mobileqq".equals(sbn.getPackageName()) || 
			"com.tencent.mm".equals(sbn.getPackageName()) || 
			"com.android.mms".equals(sbn.getPackageName()) || 
			"com.android.music".equals(sbn.getPackageName()) || 
			//Aurora <tongyh> <2015-03-10> add aurora account animation icon begin
			("com.aurora.account".equals(sbn.getPackageName()) && (sbn.getId() == 1000))){
			//Aurora <tongyh> <2015-03-10> add aurora account animation icon end
			return;//except pkg
		}
		entry.ToMerge = true;

	}

	
	protected void AuroraApplyLegacyRowBackground(View expandedOneU,View expandedLarge,View content) {
		/*
		int color = mContext.getResources().getColor(R.color.translate_bg);
		View fatherView = content.findViewById(com.aurora.R.id.status_bar_latest_event_content);
		if(null != fatherView){
			fatherView.setBackgroundColor(color);
		}
		
		View iconView = content.findViewById(com.aurora.R.id.icon);
		if(null != iconView){
			iconView.setBackgroundColor(color);
		}
		
		View titleView = content.findViewById(com.aurora.R.id.title);
		if(null != titleView && titleView instanceof TextView){
			int fontColor = mContext.getResources().getColor(R.color.font_color);
			((TextView)titleView).setTextColor(fontColor);
		}
		*/
		int color = mContext.getResources().getColor(R.color.translate_bg);
		if(expandedOneU != null){
			expandedOneU.setBackgroundColor(color);
		}
		if(expandedLarge != null){
			expandedLarge.setBackgroundColor(color);
		}
	}
	
	protected void AuroraApplyLegacyRowBackground(View expandedOneU,View expandedLarge,View content,boolean isTranslate) {
		int color = mContext.getResources().getColor(R.color.translate_bg);
		if(expandedOneU != null && isTranslate){
			expandedOneU.setBackgroundColor(color);
		}
		if(expandedLarge != null && isTranslate){
			expandedLarge.setBackgroundColor(color);
		}
	}
 	private void AuroraOpenAskDialog(final String pkg, final int uid){
		mAlertDialog = new AuroraAlertDialog.Builder(mContext,AuroraAlertDialog.THEME_AMIGO_FULLSCREEN)
							.setMessage(R.string.notify_warning)
							.setTitle(R.string.notify_warning_title)
							.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int which) {
									cancelSetNotificationsEnabled();
								}})
							.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int which) {
									setNotificationsEnabledForPackage(pkg, uid, false);
								}})
							.create();
		mAlertDialog.setCancelable(true);
		mAlertDialog.setCanceledOnTouchOutside(true);
		mAlertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL);
		mAlertDialog.show();
	}

	
	protected void AuroraCloseAskDialog(){
		if(null != mAlertDialog){
			if(mAlertDialog.isShowing()){
				mAlertDialog.dismiss();
			}
			mAlertDialog = null;
		}
	}
	protected void cancelSetNotificationsEnabled(){
		mPile.releaseClearAllItem();
	}

	protected boolean isKeyguard(){
		KeyguardManager keyguardManager = (KeyguardManager)mContext.getSystemService(Context.KEYGUARD_SERVICE);   
		boolean enable = keyguardManager.isKeyguardLocked();
		return enable;
	}

	class RemoveData{
		//update to 5.0 begin
		public String key = null; 
		//update to 5.0 end
		public StatusBarNotification notification = null;
	}
	//update to 5.0 begin
//	protected RemoveData findData(IBinder key){
		protected RemoveData findData(String key){
		//update to 5.0 begin
		for(RemoveData tmp : mToRemoveList){
			if(tmp.key == key){
				return tmp;
			}
		}
		return null;
	}

	protected void addData(String key,StatusBarNotification notification){
		RemoveData data = new RemoveData();
		data.key = key;
		data.notification = notification;
		mToRemoveList.add(data);
	}
// Aurora <zhanggp> <2013-10-08> added for systemui end
// Aurora <tongyh> <2013-12-28> judgment of non-template begin
	private void getSystemTemplatesId(){
		if(mTemplatesIds == null){
			mTemplatesIds = new int[5];
		}
		mTemplatesIds[0] = mContext.getResources().getSystem().getIdentifier("notification_template_base", "layout", "com.aurora");
		mTemplatesIds[1] = mContext.getResources().getSystem().getIdentifier("notification_template_big_base", "layout", "com.aurora");
		mTemplatesIds[2] = mContext.getResources().getSystem().getIdentifier("notification_template_big_picture", "layout", "com.aurora");
		mTemplatesIds[3] = mContext.getResources().getSystem().getIdentifier("notification_template_big_text", "layout", "com.aurora");
		mTemplatesIds[4] = mContext.getResources().getSystem().getIdentifier("notification_template_inbox", "layout", "com.aurora");
	}
	
    private boolean isSystemTemplate(int id){
    	for(int i = 0; i < mTemplatesIds.length; i++){
    		if(mTemplatesIds[i] == id){
    			if(mTemplatesIds[0] == id){
    				Log.d("PhoneStatusBar", "notification template is notification_template_base");
    			}else if(mTemplatesIds[1] == id){
    				Log.d("PhoneStatusBar", "notification template is notification_template_big_base");
    			}else if(mTemplatesIds[2] == id){
    				Log.d("PhoneStatusBar", "notification template is notification_template_big_picture");
    			}else if(mTemplatesIds[3] == id){
    				Log.d("PhoneStatusBar", "notification template is notification_template_big_text");
    			}else if(mTemplatesIds[4] == id){
    				Log.d("PhoneStatusBar", "notification template is notification_template_inbox");
    			}
    			return true;
    		}
    	}
    	return false;
    }
    private boolean isBaseSystemNotification(StatusBarNotification sbn){
    	return isSystemTemplate(sbn.getNotification().contentView.getLayoutId());
    }
    public boolean isSystemNotification(StatusBarNotification sbn){
    	if(sbn.getNotification().bigContentView != null){
    		return isSystemTemplate(sbn.getNotification().bigContentView.getLayoutId());
    	}
    	else{
    		return isBaseSystemNotification(sbn);
    	}
    	
    }
    // Aurora <tongyh> <2013-12-28> judgment of non-template end
    // Aurora <tongyh> <2014-01-22> set nitification's icon background begin
    private int getSystemTemplatesIconId(){
    	return internalIconId;
    }
    
    private int getOppoSystemTemplatesIconId(){
    	return oppointernalIconId;
    }
    
    // Aurora <tongyh> <2014-01-22> set nitification's icon background begin
    private void setSystemIconSynLauncher(NotificationData.Entry entry, StatusBarNotification sbn){
    	// Aurora <tongyh> <2014-10-28> hooking resources begin
//        setNotificationTextTypeface(entry, sbn);
        // Aurora <tongyh> <2014-10-28> hooking resources end
    	if(!(sbn.getPackageName().equals("android") || sbn.getPackageName().equals("com.android.systemui"))){
        if (iconLruMemoryCache == null) {
			iconLruMemoryCache = LruMemoryCache.getInstance();
			if (iconLruMemoryCache == null) {
				return;
			}
        }
    	if(entry.expandedLarge != null){
    		ImageView expandIv = (ImageView)entry.expandedLarge.findViewById(getSystemTemplatesIconId());
    		if(expandIv == null){
    			expandIv = (ImageView)entry.expandedLarge.findViewById(getOppoSystemTemplatesIconId());
    		}
// Aurora <tongyh> <2014-10-28> hooking resources begin
    		//ProgressBar
//        	ProgressBar expandPb = (ProgressBar)entry.expandedLarge.findViewById(internalProgressId);
//    		if(expandPb == null){
//    			expandPb = (ProgressBar)entry.expandedLarge.findViewById(oppointernalProgressId);
//    		}
//    		if(expandPb != null){
//    			if(View.VISIBLE == expandPb.getVisibility()){
//    				expandPb.setBackground(notificationAuroraStatSeekbarBg);
//    			}
//    		}
// Aurora <tongyh> <2014-10-28> hooking resources end
    		if(sbn.getPackageName().equals("com.android.phone") && sbn.getId() >= 17000){
    			if( expandIv != null){
    				expandIv.setBackgroundColor(android.R.color.transparent);
        	    }
        		return;
        	}
    		Bitmap expandIcon = null;
    		int id = R.drawable.aurora_contacts_icon;
    		if(sbn.getPackageName().equals("com.android.contacts")){
    			expandIcon = iconLruMemoryCache.getBitmapFromMemCache(String.valueOf(id));
    			if(expandIcon == null){
    				expandIcon = BitmapFactory.decodeResource(mContext.getResources(), id);
    				iconLruMemoryCache.addBitmapToMemoryCache(String.valueOf(id), expandIcon);
    			}	
    		}else{
    			
    			expandIcon = iconLruMemoryCache.getBitmapFromMemCache(sbn.getPackageName());
    			if(expandIcon == null){
    			    expandIcon = mUtils2Icon.getIcon(sbn.getPackageName(), Utils2Icon.INTER_SHADOW);
    				iconLruMemoryCache.addBitmapToMemoryCache(sbn.getPackageName(), expandIcon);
    			}	
    		}
    		if( expandIv != null && expandIcon != null){
    			expandIv.setImageBitmap(expandIcon);
    			expandIv.setBackgroundColor(android.R.color.transparent);
// Aurora <tongyh> <2014-10-28> hooking resources begin
    		}
//    		else if(expandIv != null && expandIcon == null){
//    			expandIv.setBackground(notificationTemplateIconBackgroundDrawable);
//// Aurora <tongyh> <2014-10-28> hooking resources end
//    		}
		}
    	if(entry.row != null){
    		ImageView baseIv = (ImageView)entry.row.findViewById(getSystemTemplatesIconId());
    		if(baseIv == null){
    			baseIv = (ImageView)entry.row.findViewById(getOppoSystemTemplatesIconId());
    		}
// Aurora <tongyh> <2014-10-28> hooking resources begin
    		/*//ProgressBar
        	ProgressBar basePb = (ProgressBar)entry.row.findViewById(internalProgressId);
    		if(basePb == null){
    			basePb = (ProgressBar)entry.row.findViewById(oppointernalProgressId);
    		}
    		if(basePb != null){
    			if(View.VISIBLE == basePb.getVisibility()){
    				basePb.setBackground(notificationAuroraStatSeekbarBg);
    			}
    		}*/
// Aurora <tongyh> <2014-10-28> hooking resources end
    		if(sbn.getPackageName().equals("com.android.phone") && sbn.getId() >= 17000){
    			if( baseIv != null){
        	    	baseIv.setBackgroundColor(android.R.color.transparent);
        	    }
        		return;
        	}
        	Bitmap baseIcon = null;
        	if(sbn.getPackageName().equals("com.android.contacts")){
        		int id = R.drawable.aurora_contacts_icon;
        		baseIcon = iconLruMemoryCache.getBitmapFromMemCache(String.valueOf(id));
    			if(baseIcon == null){
    				baseIcon = BitmapFactory.decodeResource(mContext.getResources(), id);
    				iconLruMemoryCache.addBitmapToMemoryCache(String.valueOf(id), baseIcon);
    			}
    		}else{
    			baseIcon = iconLruMemoryCache.getBitmapFromMemCache(sbn.getPackageName());
    			if(baseIcon == null){
    			    baseIcon = mUtils2Icon.getIcon(sbn.getPackageName(),Utils2Icon.INTER_SHADOW);
    				iconLruMemoryCache.addBitmapToMemoryCache(sbn.getPackageName(), baseIcon);
    			}
    		}
    	    if( baseIv != null && baseIcon != null){
    	    	baseIv.setImageBitmap(baseIcon);
    	    	if(sbn.getPackageName() != null && !"com.android.providers.downloads".equals(sbn.getPackageName())){
    	    		baseIv.setBackgroundColor(android.R.color.transparent);
    	    	}
// Aurora <tongyh> <2014-10-28> hooking resources begin
    	    }
//    	    else if(baseIv != null && baseIcon == null){
//    	    	baseIv.setBackground(notificationTemplateIconBackgroundDrawable);
//    		}
// Aurora <tongyh> <2014-10-28> hooking resources end
    	}
    	}
    	setSystemNotificationIcon(entry, sbn);
    }
    
    private void setAuroraNotificationIconBitmap(ImageView baseIv, ImageView expendIv, int id){
        if (iconLruMemoryCache == null) {
			iconLruMemoryCache = LruMemoryCache.getInstance();
			if (iconLruMemoryCache == null) {
				return;
			}
        }	
    	Bitmap notificationIcon = iconLruMemoryCache.getBitmapFromMemCache(String.valueOf(id));
    	if(notificationIcon == null){
    		notificationIcon = BitmapFactory.decodeResource(mContext.getResources(), id);
        	iconLruMemoryCache.addBitmapToMemoryCache(String.valueOf(id), notificationIcon);
    	}
    	if(baseIv != null){
    		baseIv.setImageBitmap(notificationIcon);
    	}
    	if(expendIv != null){
    		expendIv.setImageBitmap(notificationIcon);
    	}
    	
    }
    
    private void setAuroraNotificationIconBitmap(ImageView baseIv, ImageView expendIv, int id, boolean isAnimation){
    	if(baseIv != null){
    		baseIv.setImageResource(id);
    	}
    	if(expendIv != null){
    		expendIv.setImageResource(id);
    	}
    	
    }
    
    
    private void setSystemNotificationIcon(NotificationData.Entry entry, StatusBarNotification sbn){
    	ImageView baseIv = null;
    	ImageView expendIv = null;
// Aurora <tongyh> <2014-10-28> hooking resources begin
    	ProgressBar basePb = null;
    	ProgressBar expendPb = null;
// Aurora <tongyh> <2014-10-28> hooking resources end
    	if(entry.expandedLarge != null){
    		expendIv = (ImageView)entry.expandedLarge.findViewById(getSystemTemplatesIconId());
    		if(expendIv == null){
    			expendIv = (ImageView)entry.expandedLarge.findViewById(getOppoSystemTemplatesIconId());
    		}
// Aurora <tongyh> <2014-10-28> hooking resources begin
//    		if(expendIv != null){
//    			expendIv.setBackground(notificationTemplateIconBackgroundDrawable);
//    		}
    		/*expendPb = (ProgressBar)entry.expandedLarge.findViewById(internalProgressId);
    		if(expendPb == null){
    			expendPb = (ProgressBar)entry.expandedLarge.findViewById(oppointernalProgressId);
    		}
    		if(expendPb != null){
    			if(View.VISIBLE == expendPb.getVisibility()){
    				expendPb.setBackground(notificationAuroraStatSeekbarBg);
    			}
    		}*/
// Aurora <tongyh> <2014-10-28> hooking resources end
		}
    	if(entry.row != null){
    		baseIv = (ImageView)entry.row.findViewById(getSystemTemplatesIconId());
    		if(baseIv == null){
    			baseIv = (ImageView)entry.row.findViewById(getOppoSystemTemplatesIconId());
    		}
// Aurora <tongyh> <2014-10-28> hooking resources begin
//            if(baseIv != null){
//            	baseIv.setBackground(notificationTemplateIconBackgroundDrawable);
//    		}
            
            //ProgressBar
            /*basePb = (ProgressBar)entry.row.findViewById(internalProgressId);
    		if(basePb == null){
    			basePb = (ProgressBar)entry.row.findViewById(oppointernalProgressId);
    		}
    		if(basePb != null){
    			if(View.VISIBLE == basePb.getVisibility()){
    			    basePb.setBackground(notificationAuroraStatSeekbarBg);
    			}
    		}*/
// Aurora <tongyh> <2014-10-28> hooking resources end
    	}
    		if(sbn.getId() == notification_wifi_id || sbn.getId() == -2000010){
    			setAuroraNotificationIconBitmap(baseIv,expendIv,R.drawable.aurora_systemui_stat_notify_wifi_in_range);
        	}else if(sbn.getNotification().icon == notification_wifi_id){
        		setAuroraNotificationIconBitmap(baseIv,expendIv,R.drawable.aurora_systemui_stat_notify_wifi_in_range);
        	}else if(sbn.getId() == notification_adb_active_id){
        		setAuroraNotificationIconBitmap(baseIv,expendIv,R.drawable.aurora_systemui_stat_sys_adb);
        	}else if(sbn.getId() == notification_stat_sys_data_usb_id || sbn.getNotification().icon == notification_stat_sys_data_usb_id){//
        		setAuroraNotificationIconBitmap(baseIv,expendIv,R.drawable.aurora_systemui_stat_sys_data_usb);
        	}else if(sbn.getId() == notification_stat_sys_warning_id){
        		setAuroraNotificationIconBitmap(baseIv,expendIv,R.drawable.aurora_systemui_stat_notify_error);
        	}else if(sbn.getId() == notification_zuo_wei_anzhuangchengxu_id || sbn.getId() == notification_usb_mtp_id || sbn.getId() == notification_usb_ptp_id){
        		setAuroraNotificationIconBitmap(baseIv,expendIv,R.drawable.aurora_systemui_stat_sys_data_usb);
        	}else if(sbn.getNotification().icon == notification_stat_notify_stat_sys_download_id || sbn.getNotification().icon == notification_stat_notify_stat_sys_download_done_id){
        		setAuroraNotificationIconBitmap(baseIv,expendIv,R.drawable.aurora_systemui_stat_sys_download);
        	}else if(sbn.getNotification().icon == notification_stat_notify_stat_sys_upload_id){
        		setAuroraNotificationIconBitmap(baseIv,expendIv,R.drawable.aurora_systemui_stat_sys_upload);
        	}else if(sbn.getId() == notification_stat_tether_general_id){
        		setAuroraNotificationIconBitmap(baseIv,expendIv,R.drawable.aurora_systemui_stat_sys_tether_general);
        	}else if(sbn.getId() == notification_stat_tether_usb_id){
        		setAuroraNotificationIconBitmap(baseIv,expendIv,R.drawable.aurora_systemui_stat_sys_tether_usb);
        	}else if(sbn.getId() == notification_stat_tether_wifi_id){
        		setAuroraNotificationIconBitmap(baseIv,expendIv,R.drawable.aurora_systemui_stat_sys_tether_wifi);
        	}else if(sbn.getNotification().icon == notification_stat_notify_disk_full_id){
        		setAuroraNotificationIconBitmap(baseIv,expendIv,R.drawable.aurora_systemui_stat_notify_disk_full);
        	}else if(sbn.getNotification().icon == notification_stat_notify_sdcard_usb_id){
        		setAuroraNotificationIconBitmap(baseIv,expendIv,R.drawable.aurora_systemui_stat_notify_sdcard_usb);
        	}else if(sbn.getNotification().icon == notification_stat_notify_sdcard_prepare_id){
        		setAuroraNotificationIconBitmap(baseIv,expendIv,R.drawable.aurora_systemui_stat_notify_sdcard_prepare);
        	}else if(sbn.getNotification().icon == notification_stat_notify_rssi_in_range){
        		setAuroraNotificationIconBitmap(baseIv,expendIv,R.drawable.aurora_systemui_stat_notify_rssi_in_range);
        	}else if(sbn.getNotification().icon == 0x01080089){
    			setAuroraNotificationIconBitmap(baseIv,expendIv,R.drawable.aurora_systemui_stat_sys_upload);
        	}else if(sbn.getNotification().icon == 0x01080082){
        		setAuroraNotificationIconBitmap(baseIv,expendIv,R.drawable.aurora_systemui_stat_sys_download);
        	}else if(sbn.getId() == (374203-122084)){
        		setAuroraNotificationIconBitmap(baseIv,expendIv,R.drawable.aurora_systemui_stat_sys_gps_acquiring_anim,true);
        	}else if(sbn.getNotification().icon == notification_stat_notify_sdcard_stat_notify_sdcard_usb){
        		setAuroraNotificationIconBitmap(baseIv,expendIv,R.drawable.aurora_systemui_stat_notify_sdcard_usb);
        	}else if(sbn.getNotification().icon == notification_stat_notify_sdcard_stat_notify_sdcard){
        		setAuroraNotificationIconBitmap(baseIv,expendIv,R.drawable.aurora_systemui_stat_notify_sdcard_usb);
        	}
    }
    public int getSystemTemplatesLineThreeId(){
    	return internalTextId;
    }
    public int getOppoSystemTemplatesLineThreeId(){
    	return oppoInternalTextId;
    }
    public void initSystemResourceId(){
    	//drawable id
    	notification_wifi_id =  mContext.getResources().getSystem().getIdentifier("stat_notify_wifi_in_range", "drawable", "android");
		notification_adb_active_id =  mContext.getResources().getSystem().getIdentifier("adb_active_notification_title", "string", "com.aurora");
		notification_zuo_wei_anzhuangchengxu_id = mContext.getResources().getSystem().getIdentifier("usb_cd_installer_notification_title", "string", "com.aurora");
		notification_usb_mtp_id = mContext.getResources().getSystem().getIdentifier("usb_mtp_notification_title", "string", "com.aurora");
		notification_usb_ptp_id = mContext.getResources().getSystem().getIdentifier("usb_ptp_notification_title", "string", "com.aurora");
		notification_stat_sys_data_usb_id =  mContext.getResources().getSystem().getIdentifier("stat_sys_data_usb", "drawable", "android");
		notification_stat_sys_warning_id =  mContext.getResources().getSystem().getIdentifier("stat_sys_warning", "drawable", "android");
		notification_stat_tether_general_id =  mContext.getResources().getSystem().getIdentifier("stat_sys_tether_general", "drawable", "android");
		notification_stat_tether_usb_id =  mContext.getResources().getSystem().getIdentifier("stat_sys_tether_usb", "drawable", "android");
		notification_stat_tether_wifi_id =  mContext.getResources().getSystem().getIdentifier("stat_sys_tether_wifi", "drawable", "android");
		notification_stat_notify_disk_full_id = mContext.getResources().getSystem().getIdentifier("stat_notify_disk_full", "drawable", "android");
		notification_stat_notify_sdcard_usb_id =  mContext.getResources().getSystem().getIdentifier("stat_notify_sdcard_usb", "drawable", "android");
		notification_stat_notify_sdcard_prepare_id =  mContext.getResources().getSystem().getIdentifier("stat_notify_sdcard_prepare", "drawable", "android");
		notification_stat_notify_rssi_in_range = mContext.getResources().getSystem().getIdentifier("stat_notify_rssi_in_range", "drawable", "android");
		notification_stat_notify_stat_sys_download_id = mContext.getResources().getSystem().getIdentifier("stat_sys_download", "drawable", "android");
		notification_stat_notify_stat_sys_upload_id = mContext.getResources().getSystem().getIdentifier("stat_sys_upload", "drawable", "android");
		notification_stat_notify_stat_sys_download_done_id = mContext.getResources().getSystem().getIdentifier("stat_sys_download_anim0", "drawable", "android");
		notification_stat_notify_stat_sys_download_done_static_id = mContext.getResources().getSystem().getIdentifier("stat_sys_download_done_static", "drawable", "android");
		notification_stat_notify_sdcard_stat_notify_sdcard_usb =  mContext.getResources().getSystem().getIdentifier("stat_notify_sdcard_usb", "drawable", "android");
		notification_stat_notify_sdcard_stat_notify_sdcard =  mContext.getResources().getSystem().getIdentifier("stat_notify_sdcard", "drawable", "android");
		
		//get notification icon id
		internalIconId = mContext.getResources().getSystem().getIdentifier("icon", "id", "com.aurora");
		oppointernalIconId = mContext.getResources().getSystem().getIdentifier("icon", "id", "oppo");
		internalTextId = mContext.getResources().getSystem().getIdentifier("text", "id", "com.aurora");
		oppoInternalTextId = mContext.getResources().getSystem().getIdentifier("text", "id", "oppo");
		
		//
		iconLruMemoryCache = LruMemoryCache.getInstance();
// Aurora <tongyh> <2014-10-28> hooking resources begin
		//ProgressBar
//	    internalProgressId = mContext.getResources().getSystem().getIdentifier("progress", "id", "com.aurora");
//	    oppointernalProgressId = mContext.getResources().getSystem().getIdentifier("progress", "id", "oppo");
//		try{
//			auroraDefaultTf = Typeface.createFromFile(AURORA_DEFAULT_FONT_PATH);
//		}catch(Exception e){
//			e.printStackTrace();
//			auroraDefaultTf = null;
//		}
		//title
	    internalTitleId = mContext.getResources().getSystem().getIdentifier("title", "id", "com.aurora");
	    internalOppTitleId = mContext.getResources().getSystem().getIdentifier("title", "id", "oppo");
	    //time
	    internalTimeId = mContext.getResources().getSystem().getIdentifier("time", "id", "com.aurora");
	    internalOppTimeId = mContext.getResources().getSystem().getIdentifier("time", "id", "oppo");
	    //chronometer
	    internalChronometerId = mContext.getResources().getSystem().getIdentifier("chronometer", "id", "com.aurora");
	    internalOppChronometerId = mContext.getResources().getSystem().getIdentifier("chronometer", "id", "oppo");
	    //notification_sim_indicator_text
	    internalNotificationSimIndicatorTextId = mContext.getResources().getSystem().getIdentifier("notification_sim_indicator_text", "id", "com.aurora");
	    internalOppNotificationSimIndicatorTextId = mContext.getResources().getSystem().getIdentifier("notification_sim_indicator_text", "id", "oppo");
	    //text2
	    internalText2Id = mContext.getResources().getSystem().getIdentifier("text2", "id", "com.aurora");
	    internalOppText2Id = mContext.getResources().getSystem().getIdentifier("text2", "id", "oppo");
	    //info
	    internalInfoId = mContext.getResources().getSystem().getIdentifier("info", "id", "com.aurora");
	    internalOppInfoId = mContext.getResources().getSystem().getIdentifier("info", "id", "oppo");
	    //big_text
	    internalBigTextId = mContext.getResources().getSystem().getIdentifier("big_text", "id", "com.aurora");
	    internalOppBigTextId = mContext.getResources().getSystem().getIdentifier("big_text", "id", "oppo");
	    //inbox_text0
	    internalInboxText0Id = mContext.getResources().getSystem().getIdentifier("inbox_text0", "id", "com.aurora");
	    internalOppInboxText0Id = mContext.getResources().getSystem().getIdentifier("inbox_text0", "id", "oppo");
	    //inbox_text1
	    internalInboxText1Id = mContext.getResources().getSystem().getIdentifier("inbox_text1", "id", "com.aurora");
	    internalOppInboxText1Id = mContext.getResources().getSystem().getIdentifier("inbox_text1", "id", "oppo");
	    //inbox_text2
	    internalInboxText2Id = mContext.getResources().getSystem().getIdentifier("inbox_text2", "id", "com.aurora");
	    internalOppInboxText2Id = mContext.getResources().getSystem().getIdentifier("inbox_text2", "id", "oppo");
	    //inbox_text3
	    internalInboxText3Id = mContext.getResources().getSystem().getIdentifier("inbox_text3", "id", "com.aurora");
	    internalOppInboxText3Id = mContext.getResources().getSystem().getIdentifier("inbox_text3", "id", "oppo");
	    //inbox_text4
	    internalInboxText4Id = mContext.getResources().getSystem().getIdentifier("inbox_text4", "id", "com.aurora");
	    internalOppInboxText4Id = mContext.getResources().getSystem().getIdentifier("inbox_text4", "id", "oppo");
	    //inbox_text5
	    internalInboxText5Id = mContext.getResources().getSystem().getIdentifier("inbox_text5", "id", "com.aurora");
	    internalOppInboxText5Id = mContext.getResources().getSystem().getIdentifier("inbox_text5", "id", "oppo");
	    //inbox_text6
	    internalInboxText6Id = mContext.getResources().getSystem().getIdentifier("inbox_text6", "id", "com.aurora");
	    internalOppInboxText6Id = mContext.getResources().getSystem().getIdentifier("inbox_text6", "id", "oppo");
	    //inbox_more
	    internalInboxMoreId = mContext.getResources().getSystem().getIdentifier("inbox_more", "id", "com.aurora");
	    internalOppInboxMoreId = mContext.getResources().getSystem().getIdentifier("inbox_more", "id", "oppo");
//	    //action_divider
//	    internalActionDividerId = mContext.getResources().getSystem().getIdentifier("action_divider", "id", "com.aurora");
//	    internalOppActionDividerId = mContext.getResources().getSystem().getIdentifier("action_divider", "id", "oppo");
//	    //overflow_divider
//	    internalOverflowDividerId = mContext.getResources().getSystem().getIdentifier("overflow_divider", "id", "com.aurora");
//	    internalOppOverflowDividerId = mContext.getResources().getSystem().getIdentifier("overflow_divider", "id", "oppo");
    }
    
    /*private void setNotificationTextTypeface(NotificationData.Entry entry, StatusBarNotification sbn){
    	//title
    	TextView expendTitleTv = null;
    	TextView baseTitleTv = null;
    	if(entry.expandedLarge != null){
    		expendTitleTv = (TextView)entry.expandedLarge.findViewById(internalTitleId);
    		if(expendTitleTv == null){
    			expendTitleTv = (TextView)entry.expandedLarge.findViewById(internalOppTitleId);
    		}
    		setNotificationTextTypeface(expendTitleTv);
		}
    	
    	if(entry.row != null){
    		baseTitleTv = (TextView)entry.row.findViewById(internalTitleId);
    		if(baseTitleTv == null){
    			baseTitleTv = (TextView)entry.row.findViewById(internalOppTitleId);
    		}
    		setNotificationTextTypeface(baseTitleTv);
    	}
    	
    	//NotificationSimIndicator
    	TextView expendNotificationSimIndicatorTv = null;
    	TextView baseNotificationSimIndicatorTv = null;
    	if(entry.expandedLarge != null){
    		expendNotificationSimIndicatorTv = (TextView)entry.expandedLarge.findViewById(internalNotificationSimIndicatorTextId);
    		if(expendNotificationSimIndicatorTv == null){
    			expendNotificationSimIndicatorTv = (TextView)entry.expandedLarge.findViewById(internalOppNotificationSimIndicatorTextId);
    		}
    		setNotificationTextTypeface(expendNotificationSimIndicatorTv);
		}
    	
    	if(entry.row != null){
    		baseNotificationSimIndicatorTv = (TextView)entry.row.findViewById(internalNotificationSimIndicatorTextId);
    		if(baseNotificationSimIndicatorTv == null){
    			baseNotificationSimIndicatorTv = (TextView)entry.row.findViewById(internalOppNotificationSimIndicatorTextId);
    		}
    		setNotificationTextTypeface(baseNotificationSimIndicatorTv);
    	}
    	
    	//Text2
    	TextView expendText2Tv = null;
    	TextView baseText2Tv = null;
    	if(entry.expandedLarge != null){
    		expendText2Tv = (TextView)entry.expandedLarge.findViewById(internalText2Id);
    		if(expendText2Tv == null){
    			expendText2Tv = (TextView)entry.expandedLarge.findViewById(internalOppText2Id);
    		}
    		setNotificationTextTypeface(expendText2Tv);
		}
    	
    	if(entry.row != null){
    		baseText2Tv = (TextView)entry.row.findViewById(internalText2Id);
    		if(baseText2Tv == null){
    			baseText2Tv = (TextView)entry.row.findViewById(internalOppText2Id);
    		}
    		setNotificationTextTypeface(baseText2Tv);
    	}
    	
    	//text
    	TextView expendTextTv = null;
    	TextView baseTextTv = null;
    	if(entry.expandedLarge != null){
    		expendTextTv = (TextView)entry.expandedLarge.findViewById(internalTextId);
    		if(expendTextTv == null){
    			expendTextTv = (TextView)entry.expandedLarge.findViewById(oppoInternalTextId);
    		}
    		if(expendTextTv != null){
    			expendTextTv.setTextSize(px2dip(mContext.getResources().getDimensionPixelSize(R.dimen.aurora_notification_text_size)));
    		}
    		setNotificationTextTypeface(expendTextTv);
		}
    	
    	if(entry.row != null){
    		baseTextTv = (TextView)entry.row.findViewById(internalTextId);
    		if(baseTextTv == null){
    			baseTextTv = (TextView)entry.row.findViewById(oppoInternalTextId);
    		}
    		if(baseTextTv != null){
    			baseTextTv.setTextSize(px2dip(mContext.getResources().getDimensionPixelSize(R.dimen.aurora_notification_text_size)));
    		}
    		setNotificationTextTypeface(baseTextTv);
    	}
    	
    	//Info
    	TextView expendInfoTv = null;
    	TextView baseInfoTv = null;
    	if(entry.expandedLarge != null){
    		expendInfoTv = (TextView)entry.expandedLarge.findViewById(internalInfoId);
    		if(expendInfoTv == null){
    			expendInfoTv = (TextView)entry.expandedLarge.findViewById(internalOppInfoId);
    		}
    		setNotificationTextTypeface(expendInfoTv);
		}
    	
    	if(entry.row != null){
    		baseInfoTv = (TextView)entry.row.findViewById(internalInfoId);
    		if(baseInfoTv == null){
    			baseInfoTv = (TextView)entry.row.findViewById(internalOppInfoId);
    		}
    		setNotificationTextTypeface(baseInfoTv);
    	}
    	
    	//big_text
    	TextView expendBigTextTv = null;
    	TextView baseBigTextTv = null;
    	if(entry.expandedLarge != null){
    		expendBigTextTv = (TextView)entry.expandedLarge.findViewById(internalBigTextId);
    		if(expendBigTextTv == null){
    			expendBigTextTv = (TextView)entry.expandedLarge.findViewById(internalOppBigTextId);
    		}
    		setNotificationTextTypeface(expendBigTextTv);
		}
    	
    	if(entry.row != null){
    		baseBigTextTv = (TextView)entry.row.findViewById(internalBigTextId);
    		if(baseBigTextTv == null){
    			baseBigTextTv = (TextView)entry.row.findViewById(internalOppBigTextId);
    		}
    		setNotificationTextTypeface(baseBigTextTv);
    	}
    	//inbox_text0
    	TextView expendInboxText0Tv = null;
    	TextView baseInboxText0Tv = null;
    	if(entry.expandedLarge != null){
    		expendInboxText0Tv = (TextView)entry.expandedLarge.findViewById(internalInboxText0Id);
    		if(expendInboxText0Tv == null){
    			expendInboxText0Tv = (TextView)entry.expandedLarge.findViewById(internalOppInboxText0Id);
    		}
    		setNotificationTextTypeface(expendInboxText0Tv);
		}
    	
    	if(entry.row != null){
    		baseInboxText0Tv = (TextView)entry.row.findViewById(internalInboxText0Id);
    		if(baseInboxText0Tv == null){
    			baseInboxText0Tv = (TextView)entry.row.findViewById(internalOppInboxText0Id);
    		}
    		setNotificationTextTypeface(baseInboxText0Tv);
    	}
	    //inbox_text1
    	TextView expendInboxText1Tv = null;
    	TextView baseInboxText1Tv = null;
	    if(entry.expandedLarge != null){
	    	expendInboxText1Tv = (TextView)entry.expandedLarge.findViewById(internalInboxText1Id);
    		if(expendInboxText1Tv == null){
    			expendInboxText1Tv = (TextView)entry.expandedLarge.findViewById(internalOppInboxText1Id);
    		}
    		setNotificationTextTypeface(expendInboxText1Tv);
		}
    	
    	if(entry.row != null){
    		baseInboxText1Tv = (TextView)entry.row.findViewById(internalInboxText1Id);
    		if(baseInboxText1Tv == null){
    			baseInboxText1Tv = (TextView)entry.row.findViewById(internalOppInboxText1Id);
    		}
    		setNotificationTextTypeface(baseInboxText1Tv);
    	}
	    //inbox_text2
	    TextView expendInboxText2Tv = null;
    	TextView baseInboxText2Tv = null;
	    if(entry.expandedLarge != null){
	    	expendInboxText2Tv = (TextView)entry.expandedLarge.findViewById(internalInboxText2Id);
    		if(expendInboxText2Tv == null){
    			expendInboxText2Tv = (TextView)entry.expandedLarge.findViewById(internalOppInboxText2Id);
    		}
    		setNotificationTextTypeface(expendInboxText2Tv);
		}
    	
    	if(entry.row != null){
    		baseInboxText2Tv = (TextView)entry.row.findViewById(internalInboxText2Id);
    		if(baseInboxText2Tv == null){
    			baseInboxText2Tv = (TextView)entry.row.findViewById(internalOppInboxText2Id);
    		}
    		setNotificationTextTypeface(baseInboxText2Tv);
    	}
	    //inbox_text3
	    TextView expendInboxText3Tv = null;
    	TextView baseInboxText3Tv = null;
	    if(entry.expandedLarge != null){
	    	expendInboxText3Tv = (TextView)entry.expandedLarge.findViewById(internalInboxText3Id);
    		if(expendInboxText3Tv == null){
    			expendInboxText3Tv = (TextView)entry.expandedLarge.findViewById(internalOppInboxText3Id);
    		}
    		setNotificationTextTypeface(expendInboxText3Tv);
		}
    	
    	if(entry.row != null){
    		baseInboxText3Tv = (TextView)entry.row.findViewById(internalInboxText3Id);
    		if(baseInboxText3Tv == null){
    			baseInboxText3Tv = (TextView)entry.row.findViewById(internalOppInboxText3Id);
    		}
    		setNotificationTextTypeface(baseInboxText3Tv);
    	}
	    //inbox_text4
	    TextView expendInboxText4Tv = null;
    	TextView baseInboxText4Tv = null;
	    if(entry.expandedLarge != null){
	    	expendInboxText4Tv = (TextView)entry.expandedLarge.findViewById(internalInboxText4Id);
    		if(expendInboxText4Tv == null){
    			expendInboxText4Tv = (TextView)entry.expandedLarge.findViewById(internalOppInboxText4Id);
    		}
    		setNotificationTextTypeface(expendInboxText4Tv);
		}
    	
    	if(entry.row != null){
    		baseInboxText4Tv = (TextView)entry.row.findViewById(internalInboxText4Id);
    		if(baseInboxText4Tv == null){
    			baseInboxText4Tv = (TextView)entry.row.findViewById(internalOppInboxText4Id);
    		}
    		setNotificationTextTypeface(baseInboxText4Tv);
    	}
	    //inbox_text5
	    TextView expendInboxText5Tv = null;
    	TextView baseInboxText5Tv = null;
	    if(entry.expandedLarge != null){
	    	expendInboxText5Tv = (TextView)entry.expandedLarge.findViewById(internalInboxText5Id);
    		if(expendInboxText5Tv == null){
    			expendInboxText5Tv = (TextView)entry.expandedLarge.findViewById(internalOppInboxText5Id);
    		}
    		setNotificationTextTypeface(expendInboxText5Tv);
		}
    	
    	if(entry.row != null){
    		baseInboxText5Tv = (TextView)entry.row.findViewById(internalInboxText5Id);
    		if(baseInboxText5Tv == null){
    			baseInboxText5Tv = (TextView)entry.row.findViewById(internalOppInboxText5Id);
    		}
    		setNotificationTextTypeface(baseInboxText5Tv);
    	}
	    //inbox_text6
	    TextView expendInboxText6Tv = null;
    	TextView baseInboxText6Tv = null;
	    if(entry.expandedLarge != null){
	    	expendInboxText6Tv = (TextView)entry.expandedLarge.findViewById(internalInboxText6Id);
    		if(expendInboxText6Tv == null){
    			expendInboxText6Tv = (TextView)entry.expandedLarge.findViewById(internalOppInboxText6Id);
    		}
    		setNotificationTextTypeface(expendInboxText6Tv);
		}
    	
    	if(entry.row != null){
    		baseInboxText6Tv = (TextView)entry.row.findViewById(internalInboxText6Id);
    		if(baseInboxText6Tv == null){
    			baseInboxText6Tv = (TextView)entry.row.findViewById(internalOppInboxText6Id);
    		}
    		setNotificationTextTypeface(baseInboxText6Tv);
    	}
	    //inbox_more
	    TextView expendInboxMoreTv = null;
    	TextView baseInboxMoreTv = null;
	    if(entry.expandedLarge != null){
	    	expendInboxMoreTv = (TextView)entry.expandedLarge.findViewById(internalInboxMoreId);
    		if(expendInboxMoreTv == null){
    			expendInboxMoreTv = (TextView)entry.expandedLarge.findViewById(internalOppInboxMoreId);
    		}
    		setNotificationTextTypeface(expendInboxMoreTv);
		}
    	
    	if(entry.row != null){
    		baseInboxMoreTv = (TextView)entry.row.findViewById(internalInboxMoreId);
    		if(baseInboxMoreTv == null){
    			baseInboxMoreTv = (TextView)entry.row.findViewById(internalOppInboxMoreId);
    		}
    		setNotificationTextTypeface(baseInboxMoreTv);
    	}*/
    	/*//action_divider
        ImageView expendActionDividerTv = null;
        ImageView baseActionDividerTv = null;
	    if(entry.expandedLarge != null){
	    	expendActionDividerTv = (ImageView)entry.expandedLarge.findViewById(internalActionDividerId);
    		if(expendActionDividerTv == null){
    			expendActionDividerTv = (ImageView)entry.expandedLarge.findViewById(internalOppActionDividerId);
    		}
    		if(expendActionDividerTv != null){
    			if(View.VISIBLE == expendActionDividerTv.getVisibility()){
    				expendActionDividerTv.setBackground(notificationAuroraImaginaryLineBg);
    			}
    		}
		}
    	
    	if(entry.row != null){
    		baseActionDividerTv = (ImageView)entry.row.findViewById(internalActionDividerId);
    		if(baseActionDividerTv == null){
    			baseActionDividerTv = (ImageView)entry.row.findViewById(internalOppActionDividerId);
    		}
    		if(baseActionDividerTv != null){
    			if(View.VISIBLE == baseActionDividerTv.getVisibility()){
    				baseActionDividerTv.setBackground(notificationAuroraImaginaryLineBg);
    			}
    		}
    	}
    	
    	//overflow_divider
    	ImageView expendOverflowDividerTv = null;
        ImageView baseOppOverflowDividerTv = null;
        if(entry.expandedLarge != null){
        	expendOverflowDividerTv = (ImageView)entry.expandedLarge.findViewById(internalOverflowDividerId);
    		if(expendOverflowDividerTv == null){
    			expendOverflowDividerTv = (ImageView)entry.expandedLarge.findViewById(internalOppOverflowDividerId);
    		}
    		if(expendOverflowDividerTv != null){
    			if(View.VISIBLE == expendOverflowDividerTv.getVisibility()){
    				expendOverflowDividerTv.setBackground(notificationAuroraImaginaryLineBg);
    			}
    		}
		}
    	
    	if(entry.row != null){
    		baseOppOverflowDividerTv = (ImageView)entry.row.findViewById(internalOverflowDividerId);
    		if(baseOppOverflowDividerTv == null){
    			baseOppOverflowDividerTv = (ImageView)entry.row.findViewById(internalOppOverflowDividerId);
    		}
    		if(baseOppOverflowDividerTv != null){
    			if(View.VISIBLE == baseOppOverflowDividerTv.getVisibility()){
    				baseOppOverflowDividerTv.setBackground(notificationAuroraImaginaryLineBg);
    			}
    		}
    		
    	}*/
        
    	
//    }
    
//    private void setNotificationTextTypeface(TextView mTextView){
//    	if(mTextView != null){
//    		if(View.VISIBLE == mTextView.getVisibility()){
//    			Log.d("1027", "mTextView.setTypeface(auroraDefaultTf);");
//    			mTextView.setTypeface(auroraDefaultTf);
//        	}
//    	}
//    }
    
//    private int px2dip(float pxValue) {
//    	final float scale = mContext.getResources().getDisplayMetrics().density;
//    	return (int)(pxValue / scale +0.5f);
//    }
    // Aurora <tongyh> <2014-12-28> hooking resources end
    
    //Aurora <tongyh> <2014-12-17> H60-L01 notification's font color bug end
    private void setOtherTemplateLayoutTestColor(View expandedOneU,View expandedLarge,boolean isSystemNotificationTemplate){
    	if(expandedOneU != null){
    		if(!isSystemNotificationTemplate){
    			setViewGroupTextColor(expandedOneU);
        	}
    	}
    	if(expandedLarge != null){
    		if(!isSystemNotificationTemplate){
    			setViewGroupTextColor(expandedLarge);
        	}
    	}
    }
    
    private static void setViewGroupTextColor(View v) {
		if (v instanceof ViewGroup) {
			int mCount = ((ViewGroup)v).getChildCount();
			for (int i = 0; i < mCount; i++) {
				View mChild = ((ViewGroup)v).getChildAt(i);
				setViewGroupTextColor(mChild);
			}
		} else {
			if (v instanceof TextView) {
			    ((TextView)v).setTextColor(0xffffffff);
			}
		}
	}
    //Aurora <tongyh> <2014-12-17> H60-L01 notification's font color bug end
    //update to 5.0 begin
    /** M: [SystemUI] Support Smartbook Feature. @{ */
    public void dispatchStatusBarKeyEvent(KeyEvent event) {}
    private final NotificationListenerService mNotificationListener =
            new NotificationListenerService() {
        @Override
        public void onListenerConnected() {
            if (DEBUG) Log.d(TAG, "onListenerConnected");
            Log.d("0401", "onListenerConnected");
            final StatusBarNotification[] notifications = getActiveNotifications();
            final RankingMap currentRanking = getCurrentRanking();
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (StatusBarNotification sbn : notifications) {
                        addNotification(sbn, currentRanking);
                    }
                }
            });
        }

        @Override
        public void onNotificationPosted(final StatusBarNotification sbn,
                final RankingMap rankingMap) {
            if (DEBUG) Log.d(TAG, "onNotificationPosted: " + sbn);
            Log.d("0401", "onNotificationPosted: " + sbn);
            Log.d("0401", "sbn.getKey() = " + sbn.getKey());
            Log.d("0401", "mNotificationData == null is-----" + (mNotificationData == null));
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Notification n = sbn.getNotification();
                  //update to 5.0 begin
/*                    boolean isUpdate = mNotificationData.get(sbn.getKey()) != null
                            || isHeadsUp(sbn.getKey());*/

                    boolean isUpdate = mNotificationData.get(sbn.getKey()) != null || 
                    		(mNotificationData.get(sbn.getKey())  != null && sbn.getKey() != null &&  (mNotificationData.get(sbn.getKey())).key.equals(sbn.getKey()));
//                            || isHeadsUp(sbn.getKey());
                	//update to 5.0 end
                    // Ignore children of notifications that have a summary, since we're not
                    // going to show them anyway. This is true also when the summary is canceled,
                    // because children are automatically canceled by NoMan in that case.
                    if (n.isGroupChild() &&
                            mNotificationData.isGroupWithSummary(sbn.getGroupKey())) {
                        if (DEBUG) {
                            Log.d(TAG, "Ignoring group child due to existing summary: " + sbn);
                        }
                        Log.d("0401","Ignoring group child due to existing summary: " + sbn);
                        // Remove existing notification to avoid stale data.
                        if (isUpdate) {
                            removeNotification(sbn.getKey(), rankingMap);
                        } else {
                            mNotificationData.updateRanking(rankingMap);
                        }
                        return;
                    }
                    if (isUpdate) {
                        Log.d("0401", "updateNotification(sbn, rankingMap);");
                        updateNotification(sbn, rankingMap);
                    } else {
                        Log.d("0401", "addNotification(sbn, rankingMap);");
                        addNotification(sbn, rankingMap);
                    }
                }
            });
        }

        @Override
        public void onNotificationRemoved(final StatusBarNotification sbn,
                final RankingMap rankingMap) {
            if (DEBUG) Log.d(TAG, "onNotificationRemoved: " + sbn);
            Log.d("0401", "onNotificationRemoved: " + sbn);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    removeNotification(sbn.getKey(), rankingMap);
                }
            });
        }

        @Override
        public void onNotificationRankingUpdate(final RankingMap rankingMap) {
            if (DEBUG) Log.d(TAG, "onRankingUpdate");
            Log.d("0401", "onRankingUpdate");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateNotificationRanking(rankingMap);
                }
            });
        }

    };
//    public boolean shouldHideSensitiveContents(int userid){return false;}
    @Override  // NotificationData.Environment
    public boolean isNotificationForCurrentProfiles(StatusBarNotification n) {
        final int thisUserId = mCurrentUserId;
        final int notificationUserId = n.getUserId();
        if (DEBUG && MULTIUSER_DEBUG) {
            Log.v(TAG, String.format("%s: current userid: %d, notification userid: %d",
                    n, thisUserId, notificationUserId));
        }
        synchronized (mCurrentProfiles) {
            return notificationUserId == UserHandle.USER_ALL
                    || mCurrentProfiles.get(notificationUserId) != null;
        }
    }
    @Override
    public String getCurrentMediaNotificationKey() {
        return null;
    }
    //update to 5.0 end
}
