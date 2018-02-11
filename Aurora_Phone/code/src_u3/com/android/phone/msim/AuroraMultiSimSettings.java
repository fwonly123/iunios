/* Copyright (c) 2011-13, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.android.phone;


import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Message;
import android.os.Handler;
import android.os.AsyncResult;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.MSimTelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.android.internal.telephony.MSimConstants;
import com.android.internal.telephony.Phone;
import com.codeaurora.telephony.msim.MSimPhoneFactory;
import com.codeaurora.telephony.msim.Subscription.SubscriptionStatus;
import com.codeaurora.telephony.msim.SubscriptionManager;

import java.lang.Object;
import aurora.preference.*;
import aurora.app.*;

public class AuroraMultiSimSettings extends AuroraPreferenceActivity implements DialogInterface.
        OnDismissListener, DialogInterface.OnClickListener, AuroraPreference.OnPreferenceChangeListener  {
    private static final String TAG = "AuroraMultiSimSettings";

    private static final String KEY_VOICE = "voice";
    private static final String KEY_DATA = "data";
    private static final String KEY_SMS = "sms";
    private static final String KEY_CONFIG_SUB = "config_sub";

    private static final String CONFIG_SUB = "CONFIG_SUB";
    private static final String TUNE_AWAY = "tune_away";
    private static final String PRIORITY_SUB = "priority_subscription";

    private static final int DIALOG_SET_DATA_SUBSCRIPTION_IN_PROGRESS = 100;

    static final int EVENT_SET_DATA_SUBSCRIPTION_DONE = 1;
    static final int EVENT_SUBSCRIPTION_ACTIVATED = 2;
    static final int EVENT_SUBSCRIPTION_DEACTIVATED = 3;
    static final int EVENT_SET_VOICE_SUBSCRIPTION = 4;
    static final int EVENT_SET_SMS_SUBSCRIPTION = 5;
    static final int EVENT_SET_TUNE_AWAY = 6;
    static final int EVENT_SET_TUNE_AWAY_DONE = 7;
    static final int EVENT_SET_PRIORITY_SUBSCRIPTION = 8;
    static final int EVENT_SET_PRIORITY_SUBSCRIPTION_DONE = 9;
    static final int EVENT_SET_VOICE_SUBSCRIPTION_DONE = 10;

    protected boolean mIsForeground = false;
    static final int SUBSCRIPTION_ID_INVALID = -1;
    static final int SUBSCRIPTION_DUAL_STANDBY = 2;

    private AuroraListPreference mVoice;
    private AuroraListPreference mData;
    private AuroraListPreference mSms;
    private AuroraPreferenceScreen mConfigSub;
    private int mNumPhones = MSimTelephonyManager.getDefault().getPhoneCount();
    private CharSequence[] entries; // Used for entries like Subscription1, Subscription2 ...
    private CharSequence[] entryValues; // Used for entryValues like 0, 1 ,2 ...
    private CharSequence[] summaries; // Used for Summaries like Aubscription1, Subscription2....
    private CharSequence[] entriesPrompt; // Used in case of prompt option is required.
    private CharSequence[] entryValuesPrompt; // Used in case of prompt option is required.
    private CharSequence[] summariesPrompt; // Used in case of prompt option is required.

    /* tune away initial/old state */
    private boolean mTuneAwayValue = false;
    /* Priority subscription initial/old state */
    private int mPrioritySubValue = 0;
    /* Default voice subscription initial/old state */
    private int mVoiceSub = 0;
    private Phone mPhone = null;

    private AuroraCheckBoxPreference mTuneAway;
    private AuroraListPreference mPrioritySub;

    SubscriptionManager subManager = SubscriptionManager.getInstance();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.aurora_multi_sim_settings);
        getAuroraActionBar().setTitle(R.string.callind_multi_sim_card);

        mVoice = (AuroraListPreference) findPreference(KEY_VOICE);
        mVoice.setOnPreferenceChangeListener(this);
        mData = (AuroraListPreference) findPreference(KEY_DATA);
        mData.setOnPreferenceChangeListener(this);
        mSms = (AuroraListPreference) findPreference(KEY_SMS);
        mSms.setOnPreferenceChangeListener(this);
        mConfigSub = (AuroraPreferenceScreen) findPreference(KEY_CONFIG_SUB);
        mConfigSub.getIntent().putExtra(CONFIG_SUB, true);
        mTuneAway = (AuroraCheckBoxPreference) findPreference(TUNE_AWAY);
        mTuneAway.setOnPreferenceChangeListener(this);
        mPrioritySub = (AuroraListPreference) findPreference(PRIORITY_SUB);
        mPrioritySub.setOnPreferenceChangeListener(this);
    	if(AuroraPhoneUtils.isSimulate()) {
            mPhone = MSimSimulatedPhoneFactory.getPhone(MSimConstants.SUB1);
    	} else {
            mPhone = MSimPhoneFactory.getPhone(MSimConstants.SUB1);
    	}

        for (int subId = 0; subId < SubscriptionManager.NUM_SUBSCRIPTIONS; subId++) {
            subManager.registerForSubscriptionActivated(subId,
                    mHandler, EVENT_SUBSCRIPTION_ACTIVATED, null);
            subManager.registerForSubscriptionDeactivated(subId,
                    mHandler, EVENT_SUBSCRIPTION_DEACTIVATED, null);
        }

        // Create and Intialize the strings required for MultiSIM
        // Dynamic creation of entries instead of using static array vlues.
        // entries are Subscription1, Subscription2, Subscription3 ....
        // EntryValues are 0, 1 ,2 ....
        // Summaries are Subscription1, Subscription2, Subscription3 ....
        entries = new CharSequence[mNumPhones];
        entryValues = new CharSequence[mNumPhones];
        summaries = new CharSequence[mNumPhones];
        entriesPrompt = new CharSequence[mNumPhones + 1];
        entryValuesPrompt = new CharSequence[mNumPhones + 1];
        summariesPrompt = new CharSequence[mNumPhones + 1];
        CharSequence[] subString = getResources().getTextArray(R.array.multi_sim_entries);
        int i = 0;
        for (i = 0; i < mNumPhones; i++) {
            entries[i] = subString[i];
            summaries[i] = subString[i];
            summariesPrompt[i] = subString[i];
            entriesPrompt[i] = subString[i];
            entryValues[i] = Integer.toString(i);
            entryValuesPrompt[i] = Integer.toString(i);
        }
        entryValuesPrompt[i] = Integer.toString(i);
        entriesPrompt[i] = getResources().getString(R.string.prompt);
        summariesPrompt[i] = getResources().getString(R.string.prompt_user);
        IntentFilter intentFilter =
                new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        registerReceiver(new AirplaneModeBroadcastReceiver(), intentFilter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isAirplaneModeOn()) {
            Log.d(TAG, "Airplane mode is ON, grayout the config subscription menu!!!");
            mConfigSub.setEnabled(false);
        } else {
            mConfigSub.setEnabled(true);
        }
        updateMultiSimEntriesForVoice();
        updateMultiSimEntriesForData();
        updateMultiSimEntriesForSms();
        mIsForeground = true;
        updateState();
        updateTuneAwayState();
        updatePrioritySubState();
    }

    /**
     ** Receiver for Airplane mode changed intent broadcasts.
     **/
    private class AirplaneModeBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                Log.d(TAG, "Airplane mode is: " + isAirplaneModeOn());
                if (isAirplaneModeOn()) {
                    Log.d(TAG, "Airplane mode is ON, grayout the config subscription menu!!!");
                    mConfigSub.setEnabled(false);
                } else {
                    mConfigSub.setEnabled(true);
                }
            }
        }
    }

    protected void updateMultiSimEntriesForData() {
        mData.setEntries(entries);
        mData.setEntryValues(entryValues);
    }

    protected void updateMultiSimEntriesForSms() {
        int count = subManager.getActiveSubscriptionsCount();
        if (count >= SUBSCRIPTION_DUAL_STANDBY) {
            mSms.setEntries(entriesPrompt);
            mSms.setEntryValues(entryValuesPrompt);
        } else  {
            mSms.setEntries(entries);
            mSms.setEntryValues(entryValues);
        }
    }

    protected void updateMultiSimEntriesForVoice() {
        int count = subManager.getActiveSubscriptionsCount();
        if (count >= SUBSCRIPTION_DUAL_STANDBY) {
            mVoice.setEntries(entriesPrompt);
            mVoice.setEntryValues(entryValuesPrompt);
        } else  {
            mVoice.setEntries(entries);
            mVoice.setEntryValues(entryValues);
        }
    }

    private void updateTuneAwayState() {
        boolean tuneAwayStatus = (Settings.Global.getInt(getContentResolver(),
                Settings.Global.TUNE_AWAY_STATUS,  0) == 1);
        int resId = tuneAwayStatus ? R.string.tune_away_enabled : R.string.tune_away_disabled;

        mTuneAway.setChecked(tuneAwayStatus);
        mTuneAway.setSummary(getResources().getString(resId));
    }

    private void updatePrioritySubState() {
        mPrioritySub.setEntries(entries);
        mPrioritySub.setEntryValues(entryValues);

        try {
            int priorityValue = Settings.Global.getInt(getContentResolver(),
                    Settings.Global.MULTI_SIM_PRIORITY_SUBSCRIPTION);
            mPrioritySub.setValue(Integer.toString(priorityValue));
            mPrioritySub.setSummary(summaries[priorityValue]);
            mPrioritySubValue = priorityValue;

        } catch (SettingNotFoundException snfe) {
            Log.e(TAG, "Settings Exception Reading Dual Sim Priority Subscription Values");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsForeground = false;
    }

    private boolean isAirplaneModeOn() {
        return Settings.Global.getInt(getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    private void updateState() {
        updateVoiceSummary();
        updateDataSummary();
        updateSmsSummary();
    }

    private void updateVoiceSummary() {

        int voiceSub = MSimPhoneFactory.getVoiceSubscription();
        boolean promptEnabled  = MSimPhoneFactory.isPromptEnabled();
        int count = subManager.getActiveSubscriptionsCount();

        Log.d(TAG, "updateVoiceSummary: voiceSub =  " + voiceSub
                + " promptEnabled = " + promptEnabled
                + " number of active SUBs = " + count);

        if (promptEnabled && count >= SUBSCRIPTION_DUAL_STANDBY) {

            Log.d(TAG, "prompt is enabled: setting value to : " + mNumPhones);
            mVoice.setValue(Integer.toString(mNumPhones));
            mVoice.setSummary(summariesPrompt[mNumPhones]);
        } else {
            String sub = Integer.toString(voiceSub);
            Log.d(TAG, "setting value to : " + sub);
            mVoice.setValue(sub);
            mVoice.setSummary(summaries[voiceSub]);
        }
    }

    private void updateDataSummary() {
        int dataSub = MSimPhoneFactory.getDataSubscription();

        Log.d(TAG, "updateDataSummary: Data Subscription : = " + dataSub);
        mData.setValue(Integer.toString(dataSub));
        mData.setSummary(summaries[dataSub]);
    }

    private void updateSmsSummary() {
        int smsSub = MSimPhoneFactory.getSMSSubscription();
        boolean promptEnabled  = MSimPhoneFactory.isSMSPromptEnabled();
        int count = subManager.getActiveSubscriptionsCount();

        Log.d(TAG, "updateSmsSummary: SmsSub =  " + smsSub
                + " promptEnabled = " + promptEnabled
                + " number of active SUBs = " + count);

        if (promptEnabled && count >= SUBSCRIPTION_DUAL_STANDBY) {
            Log.d(TAG, "prompt is enabled: setting value to : " + mNumPhones);
            mSms.setValue(Integer.toString(mNumPhones));
            mSms.setSummary(summariesPrompt[mNumPhones]);
        } else {
            String sub = Integer.toString(smsSub);
            Log.d(TAG, "setting value to : " + sub);
            mSms.setValue(sub);
            mSms.setSummary(summaries[smsSub]);
        }
    }

    public boolean onPreferenceChange(AuroraPreference preference, Object objValue) {
        final String key = preference.getKey();
        String status;
        Log.d(TAG, "onPreferenceChange:::: " );

        if (KEY_VOICE.equals(key)) {

            mVoiceSub = Integer.parseInt((String) objValue);
            if (mVoiceSub == mNumPhones) { //mNumPhones is the maximum index of the UI options.
                                         //This will be the Prompt option.
                MSimPhoneFactory.setPromptEnabled(true);
                mVoice.setSummary(summariesPrompt[mVoiceSub]);
                Log.d(TAG, "prompt is enabled " + mVoiceSub);
            } else if (subManager.getCurrentSubscription(mVoiceSub).subStatus
                    == SubscriptionStatus.SUB_ACTIVATED) {
                Log.d(TAG, "setVoiceSubscription " + mVoiceSub);
                MSimPhoneFactory.setPromptEnabled(false);
                mHandler.sendMessage(mHandler.obtainMessage(EVENT_SET_VOICE_SUBSCRIPTION,
                        mVoiceSub));
            } else {
                status = getResources().getString(R.string.set_voice_error);
                displayAlertDialog(status);
            }
        }

        if (KEY_DATA.equals(key)) {
            int dataSub = Integer.parseInt((String) objValue);
            Log.d(TAG, "setDataSubscription " + dataSub);
            if (mIsForeground) {
                showDialog(DIALOG_SET_DATA_SUBSCRIPTION_IN_PROGRESS);
            }
            SubscriptionManager mSubscriptionManager = SubscriptionManager.getInstance();
            Message setDdsMsg = Message.obtain(mHandler, EVENT_SET_DATA_SUBSCRIPTION_DONE, null);
            mSubscriptionManager.setDataSubscription(dataSub, setDdsMsg);
        }

        if (KEY_SMS.equals(key)) {
            int smsSub = Integer.parseInt((String) objValue);
            if (smsSub == mNumPhones) { //mNumPhones is the maximum index of the UI options.
                                         //This will be the Prompt option.
                MSimPhoneFactory.setSMSPromptEnabled(true);
                mSms.setSummary(summariesPrompt[smsSub]);
                Log.d(TAG, "prompt is enabled " + smsSub);
            } else if (subManager.getCurrentSubscription(smsSub).subStatus
                   == SubscriptionStatus.SUB_ACTIVATED) {
                Log.d(TAG, "setSMSSubscription " + smsSub);
                MSimPhoneFactory.setSMSPromptEnabled(false);
                MSimPhoneFactory.setSMSSubscription(smsSub);
                mSms.setSummary(summaries[smsSub]);
            } else {
                status = getResources().getString(R.string.set_sms_error);
                displayAlertDialog(status);
            }
            mHandler.sendMessage(mHandler.obtainMessage(EVENT_SET_SMS_SUBSCRIPTION));
        }

        if (TUNE_AWAY.equals(key)) {
            mHandler.sendMessage(mHandler.obtainMessage(EVENT_SET_TUNE_AWAY));
        }

        if (PRIORITY_SUB.equals(key)) {
            int prioritySubIndex = Integer.parseInt((String) objValue);
            if (subManager.getCurrentSubscription(prioritySubIndex).subStatus
                    == SubscriptionStatus.SUB_ACTIVATED) {
                mPrioritySubValue = prioritySubIndex;
                mHandler.sendMessage(mHandler.obtainMessage(EVENT_SET_PRIORITY_SUBSCRIPTION,
                        prioritySubIndex));
            } else {
                status = getResources().getString(R.string.set_priority_sub_error);
                displayAlertDialog(status);
            }
        }

        return true;
    }


    @Override
    protected Dialog onCreateDialog(int id) {
        if (id == DIALOG_SET_DATA_SUBSCRIPTION_IN_PROGRESS) {
            AuroraProgressDialog dialog = new AuroraProgressDialog(this);

            dialog.setMessage(getResources().getString(R.string.set_data_subscription_progress));
            dialog.setCancelable(false);
            dialog.setIndeterminate(true);

            return dialog;
        }
        return null;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        if (id == DIALOG_SET_DATA_SUBSCRIPTION_IN_PROGRESS) {
            // when the dialogs come up, we'll need to indicate that
            // we're in a busy state to disallow further input.
            getPreferenceScreen().setEnabled(false);
        }
    }

    // This is a method implemented for DialogInterface.OnDismissListener
    public void onDismiss(DialogInterface dialog) {
        Log.d(TAG, "onDismiss!");
    }

    // This is a method implemented for DialogInterface.OnClickListener.
    public void onClick(DialogInterface dialog, int which) {
        Log.d(TAG, "onClick!");
    }

    void displayAlertDialog(String msg) {
        if (!mIsForeground) {
            Log.d(TAG, "The activitiy is not in foreground. Do not display dialog!!!");
            return;
        }
        Log.d(TAG, "displayErrorDialog!" + msg);
        new AuroraAlertDialog.Builder(this).setMessage(msg)
               .setTitle(android.R.string.dialog_alert_title)
               .setIcon(android.R.drawable.ic_dialog_alert)
               .setPositiveButton(android.R.string.yes, this)
               .show()
               .setOnDismissListener(this);
        }

        private void updateTuneAwayStatus() {
            boolean tuneAwayValue = mTuneAway.isChecked();
            mTuneAwayValue = tuneAwayValue;
            Log.d(TAG," updateTuneAwayStatus change tuneAwayValue to: " + tuneAwayValue);
            Message setTuneAwayMsg = Message.obtain(mHandler, EVENT_SET_TUNE_AWAY_DONE, null);
            mPhone.setTuneAway(tuneAwayValue, setTuneAwayMsg);
        }

        private void updatePrioritySub(int priorityIndex) {
            Log.d(TAG, "updatePrioritySub change priority sub to: " + priorityIndex);
            Message setPrioritySubMsg = Message.obtain(mHandler,
                    EVENT_SET_PRIORITY_SUBSCRIPTION_DONE, null);
            mPhone.setPrioritySub(priorityIndex, setPrioritySubMsg);
        }

        private void updateVoiceSub(int subIndex) {
            Log.d(TAG, "updateVoiceSub change voice sub to: " + subIndex);
            Message setVoiceSubMsg = Message.obtain(mHandler,
                    EVENT_SET_VOICE_SUBSCRIPTION_DONE, null);
            mPhone.setDefaultVoiceSub(subIndex, setVoiceSubMsg);
        }

        private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;

            switch(msg.what) {
                case EVENT_SET_DATA_SUBSCRIPTION_DONE:
                    Log.d(TAG, "EVENT_SET_DATA_SUBSCRIPTION_DONE");
                    if (mIsForeground) {
                        dismissDialog(DIALOG_SET_DATA_SUBSCRIPTION_IN_PROGRESS);
                    }
                    getPreferenceScreen().setEnabled(true);
                    updateDataSummary();

                    ar = (AsyncResult) msg.obj;
                    String status;
                    if (ar.exception != null) {
                        status = getResources().getString(R.string.set_dds_error)
                                           + " " + ar.exception.getMessage();
                        displayAlertDialog(status);
                        break;
                    }

                    boolean result = (Boolean)ar.result;

                    Log.d(TAG, "SET_DATA_SUBSCRIPTION_DONE: result = " + result);
                    if (result == true) {
                        status = getResources().getString(R.string.set_dds_success);
                        Toast toast = Toast.makeText(getApplicationContext(), status,
                                Toast.LENGTH_LONG);
                        toast.show();
                    } else {
                        status = getResources().getString(R.string.set_dds_failed);
                        displayAlertDialog(status);
                    }

                    break;
                case EVENT_SUBSCRIPTION_ACTIVATED:
                case EVENT_SUBSCRIPTION_DEACTIVATED:
                    updateMultiSimEntriesForVoice();
                    updateMultiSimEntriesForSms();
                    break;

                case EVENT_SET_VOICE_SUBSCRIPTION:
                    updateVoiceSub(msg.arg1);
                    break;
                case EVENT_SET_VOICE_SUBSCRIPTION_DONE:
                    Log.d(TAG, "EVENT_SET_VOICE_SUBSCRIPTION_DONE");
                    ar = (AsyncResult) msg.obj;
                    String sub;
                    if (ar.exception != null) {
                        Log.e(TAG, "SET_VOICE_SUBSCRIPTION_DONE: returned Exception: "
                                + ar.exception);
                        int voiceSub = MSimPhoneFactory.getVoiceSubscription();
                        sub = Integer.toString(voiceSub);
                        mVoice.setValue(sub);
                        mVoice.setSummary(summaries[voiceSub]);
                        mVoiceSub = voiceSub;
                        break;
                    }
                    sub = Integer.toString(mVoiceSub);
                    mVoice.setValue(sub);
                    mVoice.setSummary(summaries[mVoiceSub]);
                    MSimPhoneFactory.setVoiceSubscription(mVoiceSub);
                    break;
                case EVENT_SET_SMS_SUBSCRIPTION:
                    updateSmsSummary();
                    break;
                case EVENT_SET_TUNE_AWAY:
                    updateTuneAwayStatus();
                    break;
                case EVENT_SET_TUNE_AWAY_DONE:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        Log.e(TAG, "SET_TUNE_AWAY_DONE: returned Exception: " + ar.exception);
                        updateTuneAwayState();
                        break;
                    }
                    Log.d(TAG, "SET_TUNE_AWAY_DONE: mTuneAwayValue = " + mTuneAwayValue);
                    mTuneAway.setChecked(mTuneAwayValue);
                    int taResId = mTuneAwayValue ? R.string.tune_away_enabled : R.string.tune_away_disabled;
//                    mTuneAway.setSummary(mTuneAwayValue ? "Enable" : "Disable");
                    mTuneAway.setSummary(getResources().getString(taResId));
                    MSimPhoneFactory.setTuneAway(mTuneAwayValue);
                    break;
                case EVENT_SET_PRIORITY_SUBSCRIPTION:
                    updatePrioritySub(msg.arg1);
                    break;
                case EVENT_SET_PRIORITY_SUBSCRIPTION_DONE:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        Log.e(TAG, "EVENT_SET_PRIORITY_SUBSCRIPTION_DONE: returned Exception: "
                                + ar.exception);
                        updatePrioritySubState();
                        break;
                    }
                    Log.d(TAG, "EVENT_SET_PRIORITY_SUBSCRIPTION_DONE : mPrioritySubValue "
                            + mPrioritySubValue);
                    mPrioritySub.setValue(Integer.toString(mPrioritySubValue));
                    mPrioritySub.setSummary(summaries[mPrioritySubValue]);
                    MSimPhoneFactory.setPrioritySubscription(mPrioritySubValue);
                    break;
                    default:
                        Log.w(TAG, "Unknown Event " + msg.what);
                        break;
            }
        }
    };
}
