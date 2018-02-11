/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.settings;

import aurora.app.AuroraActivity;
import android.app.backup.IBackupManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import aurora.widget.AuroraActionBar;

public class SetFullBackupPassword extends AuroraActivity {
    static final String TAG = "SetFullBackupPassword";

    IBackupManager mBackupManager;
    TextView mCurrentPw, mNewPw, mConfirmNewPw;
    Button mCancel, mSet;

    OnClickListener mButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v == mSet) {
                final String curPw = mCurrentPw.getText().toString();
                final String newPw = mNewPw.getText().toString();
                final String confirmPw = mConfirmNewPw.getText().toString();

     		// Gionee:wangyaohui 20120620 add for CR00625357 begin 
		if(("".equals(newPw)) && ("".equals(confirmPw))) {
			Toast.makeText(SetFullBackupPassword.this,
				SetFullBackupPassword.this.getString(R.string.password_null),
                            	Toast.LENGTH_LONG).show();
			return;
		}
     		// Gionee:wangyaohui 20120620 add for CR00625357 end 

                if (!newPw.equals(confirmPw)) {
                    // Mismatch between new pw and its confirmation re-entry
Log.i(TAG, "password mismatch");
                    Toast.makeText(SetFullBackupPassword.this,
                            R.string.local_backup_password_toast_confirmation_mismatch,
                            Toast.LENGTH_LONG).show();
                    return;
                }

                // TODO: should we distinguish cases of has/hasn't set a pw before?

                if (setBackupPassword(curPw, newPw)) {
                    // success
Log.i(TAG, "password set successfully");
                    Toast.makeText(SetFullBackupPassword.this,
                            R.string.local_backup_password_toast_success,
                            Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    // failure -- bad existing pw, usually
Log.i(TAG, "failure; password mismatch?");
                    Toast.makeText(SetFullBackupPassword.this,
                            R.string.local_backup_password_toast_validation_failure,
                            Toast.LENGTH_LONG).show();
                }
            } else if (v == mCancel) {
                finish();
            } else {
                Log.w(TAG, "Click on unknown view");
            }
        }
    };

    @Override
    public void onCreate(Bundle icicle) {
        // Gionee fangbin 20120619 added for CR00622030 start
        if (GnSettingsUtils.getThemeType(getApplicationContext()).equals(GnSettingsUtils.TYPE_LIGHT_THEME)){
            setTheme(R.style.GnSettingsLightTheme);
        } else {
            setTheme(R.style.GnSettingsDarkTheme);
        }
        // Gionee fangbin 20120619 added for CR00622030 end

        super.onCreate(icicle);
        //AURORA-START::add for Actionbar ::waynelin::2013-9-18
        setAuroraContentView(R.layout.set_backup_pw,AuroraActionBar.Type.Normal);
        //AURORA-END::add for Actionbar ::waynelin::2013-9-18
        //Gionee:zhang_xin 20121215 add for CR00746521 start
	//AURORA-START::delete temporarily for compile::waynelin::2013-9-14 
	/*
        getAuroraActionBar().setDisplayShowHomeEnabled(false);
	*/
	//AURORA-END::delete temporarily for compile::waynelin::2013-9-14
        getAuroraActionBar().setDisplayHomeAsUpEnabled(true);
        //Gionee:zhang_xin 20121215 add for CR00746521 end

        mBackupManager = IBackupManager.Stub.asInterface(ServiceManager.getService("backup"));

        //setContentView(R.layout.set_backup_pw);

        mCurrentPw = (TextView) findViewById(R.id.current_backup_pw);
        mNewPw = (TextView) findViewById(R.id.new_backup_pw);
        mConfirmNewPw = (TextView) findViewById(R.id.confirm_new_backup_pw);

        mCancel = (Button) findViewById(R.id.backup_pw_cancel_button);
        mSet = (Button) findViewById(R.id.backup_pw_set_button);

        mCancel.setOnClickListener(mButtonListener);
        mSet.setOnClickListener(mButtonListener);
    }

    private boolean setBackupPassword(String currentPw, String newPw) {
        try {
            return mBackupManager.setBackupPassword(currentPw, newPw);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to communicate with backup manager");
            return false;
        }
    }
    
    /*Gionee: huangsf 20121210 add for CR00741405 start*/
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
        case android.R.id.home:
            onBackPressed();
            break;
        }
        return super.onOptionsItemSelected(item);
    }
    /*Gionee: huangsf 20121210 add for CR00741405 end*/
}
