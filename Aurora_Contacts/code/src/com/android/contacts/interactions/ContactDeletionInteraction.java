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

package com.android.contacts.interactions;

import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsApplication;
import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.model.AccountType;
import com.android.contacts.model.AccountTypeManager;
import com.google.android.collect.Sets;
import com.google.common.annotations.VisibleForTesting;

import android.app.Activity;
import aurora.app.AuroraAlertDialog; // import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnDismissListener;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import gionee.provider.GnContactsContract.Contacts;
import gionee.provider.GnContactsContract.Contacts.Entity;
import android.util.Log;
import android.widget.Toast;

import java.util.HashSet;
import android.os.Build;

/**
 * An interaction invoked to delete a contact.
 */
public class ContactDeletionInteraction extends Fragment
        implements LoaderCallbacks<Cursor>, OnDismissListener {

    private static final String FRAGMENT_TAG = "deleteContact";

    private static final String KEY_ACTIVE = "active";
    private static final String KEY_CONTACT_URI = "contactUri";
    private static final String KEY_FINISH_WHEN_DONE = "finishWhenDone";
    public static final String ARG_CONTACT_URI = "contactUri";

    private static final String[] ENTITY_PROJECTION = new String[] {
        Entity.RAW_CONTACT_ID, //0
        Entity.ACCOUNT_TYPE, //1
        Entity.DATA_SET, // 2
        Entity.CONTACT_ID, // 3
        Entity.LOOKUP_KEY, // 4
    };

    private static final int COLUMN_INDEX_RAW_CONTACT_ID = 0;
    private static final int COLUMN_INDEX_ACCOUNT_TYPE = 1;
    private static final int COLUMN_INDEX_DATA_SET = 2;
    private static final int COLUMN_INDEX_CONTACT_ID = 3;
    private static final int COLUMN_INDEX_LOOKUP_KEY = 4;

    private boolean mActive;
    private Uri mContactUri;
    private boolean mFinishActivityWhenDone;
    private Context mContext;
    private AuroraAlertDialog mDialog;

    /** This is a wrapper around the fragment's loader manager to be used only during testing. */
    private TestLoaderManager mTestLoaderManager;

    @VisibleForTesting
    int mMessageId;

    /**
     * Starts the interaction.
     *
     * @param activity the activity within which to start the interaction
     * @param contactUri the URI of the contact to delete
     * @param finishActivityWhenDone whether to finish the activity upon completion of the
     *        interaction
     * @return the newly created interaction
     */
    public static ContactDeletionInteraction start(
            Activity activity, Uri contactUri, boolean finishActivityWhenDone) {
        return startWithTestLoaderManager(activity, contactUri, finishActivityWhenDone, null);
    }

    /**
     * Starts the interaction and optionally set up a {@link TestLoaderManager}.
     *
     * @param activity the activity within which to start the interaction
     * @param contactUri the URI of the contact to delete
     * @param finishActivityWhenDone whether to finish the activity upon completion of the
     *        interaction
     * @param testLoaderManager the {@link TestLoaderManager} to use to load the data, may be null
     *        in which case the default {@link LoaderManager} is used
     * @return the newly created interaction
     */
    @VisibleForTesting
    static ContactDeletionInteraction startWithTestLoaderManager(
            Activity activity, Uri contactUri, boolean finishActivityWhenDone,
            TestLoaderManager testLoaderManager) {
        if (contactUri == null) {
            return null;
        }

        FragmentManager fragmentManager = activity.getFragmentManager();
        ContactDeletionInteraction fragment =
                (ContactDeletionInteraction) fragmentManager.findFragmentByTag(FRAGMENT_TAG);
        if (fragment == null) {
            fragment = new ContactDeletionInteraction();
            fragment.setTestLoaderManager(testLoaderManager);
            fragment.setContactUri(contactUri);
            fragment.setFinishActivityWhenDone(finishActivityWhenDone);
            fragmentManager.beginTransaction().add(fragment, FRAGMENT_TAG)
                    .commitAllowingStateLoss();
        } else {
            fragment.setTestLoaderManager(testLoaderManager);
            fragment.setContactUri(contactUri);
            fragment.setFinishActivityWhenDone(finishActivityWhenDone);
        }
        return fragment;
    }

    @Override
    public LoaderManager getLoaderManager() {
        // Return the TestLoaderManager if one is set up.
        LoaderManager loaderManager = super.getLoaderManager();
        if (mTestLoaderManager != null) {
            // Set the delegate: this operation is idempotent, so let's just do it every time.
            mTestLoaderManager.setDelegate(loaderManager);
            return mTestLoaderManager;
        } else {
            return loaderManager;
        }
    }

    /** Sets the TestLoaderManager that is used to wrap the actual LoaderManager in tests. */
    private void setTestLoaderManager(TestLoaderManager mockLoaderManager) {
        mTestLoaderManager = mockLoaderManager;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.setOnDismissListener(null);
            mDialog.dismiss();
            mDialog = null;
        }
    }

    public void setContactUri(Uri contactUri) {
        mContactUri = contactUri;
        mActive = true;
        if (isStarted()) {
            Bundle args = new Bundle();
            args.putParcelable(ARG_CONTACT_URI, mContactUri);
            getLoaderManager().restartLoader(R.id.dialog_delete_contact_loader_id, args, this);
        }
    }

    private void setFinishActivityWhenDone(boolean finishActivityWhenDone) {
        this.mFinishActivityWhenDone = finishActivityWhenDone;

    }

    /* Visible for testing */
    boolean isStarted() {
        return isAdded();
    }

    @Override
    public void onStart() {
        if (mActive) {
            Bundle args = new Bundle();
            args.putParcelable(ARG_CONTACT_URI, mContactUri);
            getLoaderManager().initLoader(R.id.dialog_delete_contact_loader_id, args, this);
        }
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mDialog != null) {
            mDialog.hide();
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Uri contactUri = args.getParcelable(ARG_CONTACT_URI);
        return new CursorLoader(mContext,
                Uri.withAppendedPath(contactUri, Entity.CONTENT_DIRECTORY), ENTITY_PROJECTION,
                null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }

        if (!mActive) {
            return;
        }

        long contactId = 0;
        String lookupKey = null;

        // This cursor may contain duplicate raw contacts, so we need to de-dupe them first
        HashSet<Long>  readOnlyRawContacts = Sets.newHashSet();
        HashSet<Long>  writableRawContacts = Sets.newHashSet();

        AccountTypeManager accountTypes = AccountTypeManager.getInstance(getActivity());
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            final long rawContactId = cursor.getLong(COLUMN_INDEX_RAW_CONTACT_ID);
            final String accountType = cursor.getString(COLUMN_INDEX_ACCOUNT_TYPE);
            final String dataSet = cursor.getString(COLUMN_INDEX_DATA_SET);
            contactId = cursor.getLong(COLUMN_INDEX_CONTACT_ID);
            lookupKey = cursor.getString(COLUMN_INDEX_LOOKUP_KEY);
            AccountType type = accountTypes.getAccountType(accountType, dataSet);
            boolean writable = type == null || type.areContactsWritable();
            if (writable) {
                writableRawContacts.add(rawContactId);
            } else {
                readOnlyRawContacts.add(rawContactId);
            }
        }

        int readOnlyCount = readOnlyRawContacts.size();
        int writableCount = writableRawContacts.size();
        if (readOnlyCount > 0 && writableCount > 0) {
            mMessageId = R.string.readOnlyContactDeleteConfirmation;
        } else if (readOnlyCount > 0 && writableCount == 0) {
            mMessageId = R.string.readOnlyContactWarning;
        } else if (readOnlyCount == 0 && writableCount > 1) {
            mMessageId = R.string.multipleContactDeleteConfirmation;
        } else {
            mMessageId = R.string.deleteConfirmation;
        }

        final Uri contactUri = Contacts.getLookupUri(contactId, lookupKey);
        showDialog(mMessageId, contactUri);

        // We don't want onLoadFinished() calls any more, which may come when the database is
        // updating.
        getLoaderManager().destroyLoader(R.id.dialog_delete_contact_loader_id);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    private void showDialog(int messageId, final Uri contactUri) {
        mDialog = new AuroraAlertDialog.Builder(getActivity(), AuroraAlertDialog.THEME_AMIGO_FULLSCREEN)
                .setTitle(R.string.delete)
//                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setMessage(messageId)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton) {
                            doDeleteContact(contactUri);
                        }
                    }
                )
                .create();

        mDialog.setOnDismissListener(this);
        mDialog.show();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        mActive = false;
        mDialog = null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_ACTIVE, mActive);
        outState.putParcelable(KEY_CONTACT_URI, mContactUri);
        outState.putBoolean(KEY_FINISH_WHEN_DONE, mFinishActivityWhenDone);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            mActive = savedInstanceState.getBoolean(KEY_ACTIVE);
            mContactUri = savedInstanceState.getParcelable(KEY_CONTACT_URI);
            mFinishActivityWhenDone = savedInstanceState.getBoolean(KEY_FINISH_WHEN_DONE);
        }
    }

    protected void doDeleteContact(Uri contactUri) {
    	Log.v("ContactDetailActivity","doDeleteContact is start+mSimUri="+mSimUri);
    	int error=0;
    //aurora add zhouxiaobing 20131228 start   	
    	String toastChar;
    	String[] tokens = mSimWhere.split("AND");
    	String[] pair = tokens[0].split("=", 2);
    	String name = pair[1].trim();
    	if(Build.VERSION.SDK_INT < 21) {
	    	String s="鳚";
	    	name=name.replace(s.toCharArray()[0],' ' );//aurora add zhouxiaobing 20140305 for delete space
    	}
   //aurora add zhouxiaobing 20131228 end     
        if (mSimUri != null) {
            if ((error=getActivity().getContentResolver().delete(mSimUri, mSimWhere, null)) <= 0) {
            	Log.v("ContactDetailActivity","doDeleteContact is faile error="+error);
//aurora add zhouxiaobing 20131228 start  
    			toastChar = ContactsApplication.getInstance().
                        getResources().getString(R.string.notifier_fail_delete_title);
    			Toast.makeText(ContactsApplication.getInstance().getApplicationContext(),
                        toastChar, Toast.LENGTH_SHORT).show();            	
//aurora add zhouxiaobing 20131228 end  
                getActivity().finish(); 
                return;
            }
        }                        
        mContext.startService(ContactSaveService.createDeleteContactIntent(mContext, contactUri));
//aurora add zhouxiaobing 20131228 start        
		toastChar = ContactsApplication.getInstance().
                getResources().getString(R.string.aurora_delete_one_contact_toast, 
                		Build.VERSION.SDK_INT < 21 ? name : mName);
		Toast.makeText(ContactsApplication.getInstance().getApplicationContext(),
                toastChar, Toast.LENGTH_SHORT).show();
//aurora add zhouxiaobing 20131228 end    		
        if (isAdded() && mFinishActivityWhenDone) {
            getActivity().finish();
        }
        
    }


    // The following lines are provided and maintained by Mediatek Inc. 
    
    public static ContactDeletionInteraction start(Activity activity, Uri contactUri, 
                                boolean finishActivityWhenDone, Uri simUri, String simWhere) {
        ContactDeletionInteraction deletion = startWithTestLoaderManager(activity, contactUri, finishActivityWhenDone, null);        
        deletion.mSimUri = simUri;
        deletion.mSimWhere = simWhere;
        return deletion;
    }

    public static ContactDeletionInteraction start(Activity activity, Uri contactUri, 
                                boolean finishActivityWhenDone, Uri simUri, String simWhere, String name) {
        ContactDeletionInteraction deletion = startWithTestLoaderManager(activity, contactUri, finishActivityWhenDone, null);        
        deletion.mSimUri = simUri;
        deletion.mSimWhere = simWhere;
        deletion.mName = name;
        return deletion;
    }

    private Uri mSimUri = null;
    private String mSimWhere = null;
    private String mName = "";
    // The previous lines are provided and maintained by Mediatek Inc.
}