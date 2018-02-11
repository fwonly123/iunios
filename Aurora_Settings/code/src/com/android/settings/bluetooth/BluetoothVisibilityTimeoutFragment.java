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

package com.android.settings.bluetooth;

import aurora.app.AuroraAlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import aurora.widget.AuroraEditText;

import com.android.internal.app.AlertController;
import com.android.settings.R;

/**
 * Dialog fragment for setting the discoverability timeout.
 */
public final class BluetoothVisibilityTimeoutFragment extends DialogFragment
        implements DialogInterface.OnClickListener {

    private final BluetoothDiscoverableEnabler mDiscoverableEnabler;

    public BluetoothVisibilityTimeoutFragment() {
        mDiscoverableEnabler = LocalBluetoothManager.getInstance(getActivity())
                .getDiscoverableEnabler();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        //Gionee:zhang_xin 2012-12-17 modify for CR00746738 start
        /*
        return new AuroraAlertDialog.Builder(getActivity())
                .setTitle(R.string.bluetooth_visibility_timeout)
                .setSingleChoiceItems(R.array.bluetooth_visibility_timeout_entries,
                        mDiscoverableEnabler.getDiscoverableTimeoutIndex(), this)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        */
        return new AuroraAlertDialog.Builder(getActivity())
                .setTitle(R.string.bluetooth_visibility_timeout)
                .setSingleChoiceItems(R.array.bluetooth_visibility_timeout_entries,
                        mDiscoverableEnabler.getDiscoverableTimeoutIndex(), this)
                .create();
        //Gionee:zhang_xin 2012-12-17 modify for CR00746738 end
    }

    public void onClick(DialogInterface dialog, int which) {
        mDiscoverableEnabler.setDiscoverableTimeout(which);
        dismiss();
    }
}
