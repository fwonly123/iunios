/*
**
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/
package com.android.packageinstaller;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import aurora.app.AuroraActivity;
import aurora.app.AuroraAlertDialog;
import aurora.widget.AuroraActionBar;

import java.util.List;

import com.android.packageinstaller.R;
//import com.android.packageinstaller.com;

/*
 * This activity presents UI to uninstall an application. Usually launched with intent
 * Intent.ACTION_UNINSTALL_PKG_COMMAND and attribute 
 * com.android.packageinstaller.PackageName set to the application package name
 */
public class UninstallerActivity extends AuroraActivity implements OnClickListener,
        DialogInterface.OnCancelListener {
    private static final String TAG = "UninstallerActivity";
    /// M: [ALPS00287901] [Rose][ICS][MT6577][Free Test][APPIOT]The "Open" will be displayed in gray after you install one apk twice.(5/5) @{
    private boolean localLOGV = true;
    /// @}
    PackageManager mPm;
    private ApplicationInfo mAppInfo;
    private Button mOk;
    private Button mCancel;

    // Dialog identifiers used in showDialog
    private static final int DLG_BASE = 0;
    private static final int DLG_APP_NOT_FOUND = DLG_BASE + 1;
    private static final int DLG_UNINSTALL_FAILED = DLG_BASE + 2;

    @Override
    public Dialog onCreateDialog(int id) {
        switch (id) {
        case DLG_APP_NOT_FOUND :
        	//huangbin hide 
            //return new AmigoAlertDialog.Builder(this,AmigoAlertDialog.THEME_AMIGO_FULLSCREEN)
        	//huangbin add
        	 return new AuroraAlertDialog.Builder(this)
                    .setTitle(R.string.app_not_found_dlg_title)
                    .setIcon(com.android.internal.R.drawable.ic_dialog_alert)
                    .setMessage(R.string.app_not_found_dlg_text)
                    .setNeutralButton(getString(R.string.dlg_ok), 
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    setResult(Activity.RESULT_FIRST_USER);
                                    finish();
                                }})
                    .create();
        case DLG_UNINSTALL_FAILED :
            // Guaranteed not to be null. will default to package name if not set by app
           CharSequence appTitle = mPm.getApplicationLabel(mAppInfo);
           String dlgText = getString(R.string.uninstall_failed_msg,
                    appTitle.toString());
            // Display uninstall failed dialog
           //huangbin hide
           // return new AmigoAlertDialog.Builder(this,AmigoAlertDialog.THEME_AMIGO_FULLSCREEN)
           //huangbin add
           return new AuroraAlertDialog.Builder(this)
                    .setTitle(R.string.uninstall_failed)
                    .setIcon(com.android.internal.R.drawable.ic_dialog_alert)
                    .setMessage(dlgText)
                    .setNeutralButton(getString(R.string.dlg_ok), 
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    setResult(Activity.RESULT_FIRST_USER);
                                    finish();
                                }})
                    .create();
        }
        return null;
    }

    private void startUninstallProgress() {
        Intent newIntent = new Intent(Intent.ACTION_VIEW);
        newIntent.putExtra(PackageUtil.INTENT_ATTR_APPLICATION_INFO, 
                                                  mAppInfo);
        if (getIntent().getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false)) {
            newIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            newIntent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        }
        newIntent.setClass(this, UninstallAppProgress.class);
        startActivity(newIntent);
        
        //如果不延时finish，就会出现闪屏
        new Handler().postDelayed(new Runnable() {			
			@Override
			public void run() {
				finish();			
			}
		}, 1000);
        
    }

    @Override
    public void onCreate(Bundle icicle) {
        //Gionee qiuxd 2013117 add for CR00765187 start
//        String theme = PackageUtil.getThemeType(getApplicationContext());
//        if (theme.equals(PackageUtil.TYPE_LIGHT_THEME)) {
//            setTheme(R.style.GN_Perm_Ctrl_Theme_light);
//        } else if(theme.equals(PackageUtil.TYPE_DARK_THEME)){
//            setTheme(R.style.GN_Perm_Ctrl_Theme_dark);
//        }
        //Gionee qiuxd 2013117 add for CR00765187 end
        super.onCreate(icicle);
        // Get intent information.
        // We expect an intent with URI of the form package://<packageName>#<className>
        // className is optional; if specified, it is the activity the user chose to uninstall
        final Intent intent = getIntent();
        Uri packageURI = intent.getData();
        String packageName = packageURI.getEncodedSchemeSpecificPart();
        if(packageName == null) {
            Log.e(TAG, "Invalid package name:" + packageName);
            showDialog(DLG_APP_NOT_FOUND);
            return;
        }

        mPm = getPackageManager();
        boolean errFlag = false;
        try {
            mAppInfo = mPm.getApplicationInfo(packageName, PackageManager.GET_UNINSTALLED_PACKAGES);
        } catch (NameNotFoundException e) {
            errFlag = true;
        }

        // The class name may have been specified (e.g. when deleting an app from all apps)
        String className = packageURI.getFragment();
        ActivityInfo activityInfo = null;
        if (className != null) {
            try {
                activityInfo = mPm.getActivityInfo(new ComponentName(packageName, className), 0);
            } catch (NameNotFoundException e) {
                errFlag = true;
            }
        }

        if(mAppInfo == null || errFlag) {
            Log.e(TAG, "Invalid packageName or componentName in " + packageURI.toString());
            showDialog(DLG_APP_NOT_FOUND);
        } else {
            boolean isUpdate = ((mAppInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0);

            setContentView(R.layout.uninstall_confirm);//huang hide
          //huang add begin
//        	setAuroraContentView(R.layout.uninstall_confirm,AuroraActionBar.Type.Normal);
//            getAuroraActionBar().setTitle(R.string.uninstall_application_title);
//            getAuroraActionBar().getHomeButton().setVisibility(View.GONE);
        	//huangbin add end

            TextView confirm = (TextView) findViewById(R.id.uninstall_confirm);
            if (isUpdate) {
                setTitle(R.string.uninstall_update_title);
                confirm.setText(R.string.uninstall_update_text);
            } else {
                setTitle(R.string.uninstall_application_title);
                confirm.setText(R.string.uninstall_application_text);
            }

            // If an activity was specified (e.g. when dragging from All Apps to trash can),
            // give a bit more info if the activity label isn't the same as the package label.
            if (activityInfo != null) {
                CharSequence activityLabel = activityInfo.loadLabel(mPm);
                if (!activityLabel.equals(mAppInfo.loadLabel(mPm))) {
                    TextView activityText = (TextView) findViewById(R.id.activity_text);
                    CharSequence text = getString(R.string.uninstall_activity_text, activityLabel);
                    activityText.setText(text);
//                    activityText.setVisibility(View.VISIBLE);
                }
            }

            View snippetView = findViewById(R.id.uninstall_activity_snippet);
            PackageUtil.initSnippetForInstalledApp(this, mAppInfo, snippetView);

            //initialize ui elements
            mOk = (Button)findViewById(R.id.ok_button);
            mCancel = (Button)findViewById(R.id.cancel_button);
            mOk.setOnClickListener(this);
            mCancel.setOnClickListener(this);
        }
        
        // Gionee <qiuxd> <2013-04-02> modify for CR00791381 start
        //getActionBar().setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_SHOW_HOME);
        // Gionee <qiuxd> <2013-04-02> modify for CR00791381 end
        //huangbin hide begin       
//      AmigoActionBar actionBar = getAmigoActionBar();
//      if(null != actionBar){
//          actionBar.setDisplayShowHomeEnabled(false);
//      }
//huangbin hide end
      
      //huangbin add beign
//      AuroraActionBar actionBar = getAuroraActionBar();
//      if(null != actionBar){
//      	actionBar.getHomeButton().setVisibility(View.GONE);
//      }
      //huangbin add end
    }
    
    public void onClick(View v) {
        if(v == mOk) {
            //initiate next screen
            startUninstallProgress();
        } else if(v == mCancel) {
            finish();
        }
    }

    public void onCancel(DialogInterface dialog) {
        finish();
    }
}