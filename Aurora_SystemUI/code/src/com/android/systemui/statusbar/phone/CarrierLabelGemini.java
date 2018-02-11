/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.Xlog;

/**
 * M: This class is used to present the carriers information of dual SIM.
 */
public class CarrierLabelGemini extends TextView {
    private static final String TAG = "CarrierLabelGemini";
    private int mSlotId = -1;
    private String mNetworkNameSeparator;

    public CarrierLabelGemini(Context context) {
        this(context, null);
    }

    public CarrierLabelGemini(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CarrierLabelGemini(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        updateNetworkName(false, null, false, null);
        mNetworkNameSeparator = getContext().getString(R.string.status_bar_network_name_separator);
    }

    public void setSlotId(int slotId) {
        this.mSlotId = slotId;
    }

    public int getSlotId() {
        return this.mSlotId;
    }

    void updateNetworkName(boolean showSpn, String spn, boolean showPlmn, String plmn) {
        Xlog.d(TAG, "updateNetworkName, showSpn=" + showSpn + " spn=" + spn + " showPlmn=" + showPlmn + " plmn=" + plmn);
        StringBuilder str = new StringBuilder();
        boolean something = false;
        if (showPlmn && plmn != null) {
            str.append(plmn);
            something = true;
        }
        if (showSpn && spn != null) {
            if (something) {
                str.append(mNetworkNameSeparator);
            }
            str.append(spn);
            something = true;
        }
        if (something) {
            setText(str.toString());
        } else {
            setText(com.aurora.R.string.lockscreen_carrier_default);
        }
    }
}
