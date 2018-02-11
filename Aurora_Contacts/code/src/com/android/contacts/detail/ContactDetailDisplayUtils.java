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

package com.android.contacts.detail;

import com.android.contacts.ContactLoader;
import com.android.contacts.ContactLoader.Result;
import com.android.contacts.ContactPhotoManager;
import com.android.contacts.GNContactsUtils;
import com.android.contacts.R;
import com.android.contacts.preference.ContactsPreferences;
import com.android.contacts.util.ContactBadgeUtil;
import com.android.contacts.util.HtmlUtils;
import com.android.contacts.util.StreamItemEntry;
import com.android.contacts.util.StreamItemPhotoEntry;
import com.google.common.annotations.VisibleForTesting;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Entity;
import android.content.Entity.NamedContentValues;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Bitmap.Config;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import gionee.provider.GnContactsContract;
import gionee.provider.GnContactsContract.CommonDataKinds.Note;
import gionee.provider.GnContactsContract.CommonDataKinds.Organization;
import gionee.provider.GnContactsContract.Data;
import gionee.provider.GnContactsContract.DisplayNameSources;
import gionee.provider.GnContactsContract.StreamItems;
import android.text.Html;
import android.text.Html.ImageGetter;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import aurora.widget.AuroraListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// gionee xuhz 20121124 add for GIUI2.0 start
import com.android.contacts.ContactsApplication;
import com.android.contacts.ContactsUtils;
import com.mediatek.contacts.ContactsFeatureConstants.FeatureOption;
import com.mediatek.contacts.simcontact.SIMInfoWrapper;
import com.mediatek.contacts.simcontact.SimCardUtils;
import com.mediatek.contacts.util.OperatorUtils;
// gionee xuhz 20121124 add for GIUI2.0 end
/**
 * This class contains utility methods to bind high-level contact details
 * (meaning name, phonetic name, job, and attribution) from a
 * {@link ContactLoader.Result} data object to appropriate {@link View}s.
 */
public class ContactDetailDisplayUtils {
    private static final String TAG = "ContactDetailDisplayUtils";

    private static final int PHOTO_FADE_IN_ANIMATION_DURATION_MILLIS = 100;

    /**
     * Tag object used for stream item photos.
     */
    public static class StreamPhotoTag {
        public final StreamItemEntry streamItem;
        public final StreamItemPhotoEntry streamItemPhoto;

        public StreamPhotoTag(StreamItemEntry streamItem, StreamItemPhotoEntry streamItemPhoto) {
            this.streamItem = streamItem;
            this.streamItemPhoto = streamItemPhoto;
        }

        public Uri getStreamItemPhotoUri() {
            final Uri.Builder builder = StreamItems.CONTENT_URI.buildUpon();
            ContentUris.appendId(builder, streamItem.getId());
            builder.appendPath(StreamItems.StreamItemPhotos.CONTENT_DIRECTORY);
            ContentUris.appendId(builder, streamItemPhoto.getId());
            return builder.build();
        }
    }

    private ContactDetailDisplayUtils() {
        // Disallow explicit creation of this class.
    }

    /**
     * Returns the display name of the contact, using the current display order setting.
     * Returns res/string/missing_name if there is no display name.
     */
    public static CharSequence getDisplayName(Context context, Result contactData) {
        CharSequence displayName = contactData.getDisplayName();
        CharSequence altDisplayName = contactData.getAltDisplayName();
        ContactsPreferences prefs = new ContactsPreferences(context);
        CharSequence styledName = "";
        if (!TextUtils.isEmpty(displayName) && !TextUtils.isEmpty(altDisplayName)) {
            if (prefs.getDisplayOrder() == GnContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
                styledName = displayName;
            } else {
                styledName = altDisplayName;
            }
        } else {
            styledName = context.getResources().getString(R.string.missing_name);
        }
        return styledName;
    }

    /**
     * Returns the phonetic name of the contact or null if there isn't one.
     */
    public static String getPhoneticName(Context context, Result contactData) {
        String phoneticName = contactData.getPhoneticName();
        if (!TextUtils.isEmpty(phoneticName)) {
            return phoneticName;
        }
        return null;
    }

    /**
     * Returns the attribution string for the contact, which may specify the contact directory that
     * the contact came from. Returns null if there is none applicable.
     */
    public static String getAttribution(Context context, Result contactData) {
        if (contactData.isDirectoryEntry()) {
            String directoryDisplayName = contactData.getDirectoryDisplayName();
            String directoryType = contactData.getDirectoryType();
            String displayName = !TextUtils.isEmpty(directoryDisplayName)
                    ? directoryDisplayName
                    : directoryType;
            return context.getString(R.string.contact_directory_description, displayName);
        }
        return null;
    }
	// aurora <ukiliu> <2013-9-25> modify for aurora ui begin
    /**
     * Returns the organization of the contact. If several organizations are given,
     * the first one is used. Returns null if not applicable.
     */
    public static String getCompany(Context context, Result contactData) {
        final boolean displayNameIsOrganization = contactData.getDisplayNameSource()
                == DisplayNameSources.ORGANIZATION;
        for (Entity entity : contactData.getEntities()) {
            for (NamedContentValues subValue : entity.getSubValues()) {
                final ContentValues entryValues = subValue.values;
                final String mimeType = entryValues.getAsString(Data.MIMETYPE);

                if (Organization.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    final String company = entryValues.getAsString(Organization.COMPANY);
//                    final String title = entryValues.getAsString(Organization.TITLE);
                    String combined = null;
                    // We need to show company and title in a combined string. However, if the
                    // DisplayName is already the organization, it mirrors company or (if company
                    // is empty title). Make sure we don't show what's already shown as DisplayName
                    if (!TextUtils.isEmpty(company)) {
                        combined = displayNameIsOrganization ? null : company;
                    } else {
//                        if (TextUtils.isEmpty(title)) {
//                            combined = displayNameIsOrganization ? null : company;
//                        } else {
//                            if (displayNameIsOrganization) {
//                                combined = title;
//                            } else {
//                                combined = context.getString(
//                                        R.string.organization_company_and_title,
//                                        company, title);
//                            }
//                        }
                    }

                    if (!TextUtils.isEmpty(combined)) {
                        return combined;
                    }
                }
            }
        }
        return null;
    }
    //AURORA-START
    public static String getPosition(Context context, Result contactData) {
    	final boolean displayNameIsOrganization = contactData.getDisplayNameSource()
                == DisplayNameSources.ORGANIZATION;
        for (Entity entity : contactData.getEntities()) {
            for (NamedContentValues subValue : entity.getSubValues()) {
                final ContentValues entryValues = subValue.values;
                final String mimeType = entryValues.getAsString(Data.MIMETYPE);

                if (Organization.CONTENT_ITEM_TYPE.equals(mimeType)) {
                	final String company = entryValues.getAsString(Organization.COMPANY);
                    final String title = entryValues.getAsString(Organization.TITLE);
                    if (!TextUtils.isEmpty(title)) {
                    	if (!TextUtils.isEmpty(company)) {
                    		return title;
                    	} else if (!displayNameIsOrganization) {
                    		return title;
                    	}
                    }
                }
            }
        }
    	return null;
    }
    
    public static String getNote(Context context, Result contactData) {
    	for (Entity entity : contactData.getEntities()) {
            for (NamedContentValues subValue : entity.getSubValues()) {
                final ContentValues entryValues = subValue.values;
                final String mimeType = entryValues.getAsString(Data.MIMETYPE);

                if (Note.CONTENT_ITEM_TYPE.equals(mimeType)) {
                	final String note = entryValues.getAsString(Note.NOTE);
                    if (!TextUtils.isEmpty(note)) {
                    	return note;
                    }
                }
            }
        }
    	return null;
    }
	// aurora <ukiliu> <2013-9-25> modify for aurora ui end
    /**
     * Sets the contact photo to display in the given {@link ImageView}. If bitmap is null, the
     * default placeholder image is shown.
     */
    public static void setPhoto(Context context, Result contactData, ImageView photoView) {
        if (contactData.isLoadingPhoto()) {
            photoView.setImageBitmap(null);
            return;
        }
        byte[] photo = contactData.getPhotoBinaryData();
        Bitmap bitmap = photo != null ? BitmapFactory.decodeByteArray(photo, 0, photo.length)
                : ContactBadgeUtil.loadDefaultAvatarPhoto(context, true, false);
        boolean fadeIn = contactData.isDirectoryEntry();
        if (photoView.getDrawable() == null && fadeIn) {
            AlphaAnimation animation = new AlphaAnimation(0, 1);
            animation.setDuration(PHOTO_FADE_IN_ANIMATION_DURATION_MILLIS);
            animation.setInterpolator(new AccelerateInterpolator());
            photoView.startAnimation(animation);
        }
        
        photoView.setImageBitmap(bitmap);        
    }


	// gionee xuhz 20121124 add for GIUI2.0 start
    /**
     * Sets the contact photo to display in the given {@link ImageView}. If bitmap is null, the
     * default placeholder image is shown.
     */
    public static void setPhotoForDetail(Context context, Result contactData, ImageView photoView, boolean mIsPrivacyContact) {
    	long photoId = contactData.getPhotoId();
        if (photoId == 0) {
            int indicatePhoneSim = contactData.getIndicate();
            if(indicatePhoneSim > 0){
                int slot = SIMInfoWrapper.getDefault().getSimSlotById(indicatePhoneSim);
                // Gionee:wangth 20130313 add for CR00778745 begin
                if (!FeatureOption.MTK_GEMINI_SUPPORT) {
                    slot = 0;
                }
                // Gionee:wangth 20130313 add for CR00778745 end
                /// Gionee <xuhz> <2013-08-16> add for CR00858149 begin
                //old:if (OperatorUtils.getOptrProperties().equals("OP02")) {
                if (OperatorUtils.getActualOptrProperties().equals("OP02")) {
                // Gionee <xuhz> <2013-08-16> add for CR00858149 end
                    Log.i(TAG,"[setPhotoForDetail] OP02 mSlot : "+slot);
                    photoId = (slot == 0) ? -3 : -4;
                //Gionee <wangth><2013-04-25> modify for CR00801918 begin
                } else if ((FeatureOption.MTK_GEMINI_SUPPORT 
                        || (GNContactsUtils.isOnlyQcContactsSupport() && GNContactsUtils.isMultiSimEnabled()))
                        && (ContactsUtils.mIsGnShowSlotSupport || ContactsUtils.mIsGnShowDigitalSlotSupport)) {
                    if (slot == 0) {
                        photoId = -3;
                    } else if (slot == 1) {
                        photoId = -4;
                    }
                } else {
                    boolean isUsim = SimCardUtils.isSimUsimType(slot);
                    if (isUsim) {
                        photoId = -2;
                    } else {
                        photoId = -1;
                    }
                }
                //Gionee <wangth><2013-04-25> modify for CR00801918 end
            }
    	}
    
        //Gionee:huangzy 20130121 add for CR00762466 start
        ContactPhotoManager.getInstance(context).refreshCache();
        //Gionee:huangzy 20130121 add for CR00762466 end
        
        // aurora wangth 20140609 add for click begin
        if (photoId == 0) {
        	photoView.setImageDrawable(null);
        	if (mIsPrivacyContact) {
        		photoView.setBackgroundResource(R.drawable.aurora_privacy_contact_default_header);
        	} else {
        		photoView.setBackgroundResource(R.drawable.aurora_contact_default_header);
        	}
        	
        	return;
        }
        // aurora wangth 20140609 add for click end
        
        if (ContactsApplication.sIsGnDarkStyle) {
            ContactPhotoManager.getInstance(context).loadPhoto(
            		photoView, photoId, false, true);
        } else {
            ContactPhotoManager.getInstance(context).loadPhoto(
            		photoView, photoId, false, false);
        }
    }
	// gionee xuhz 20121124 add for GIUI2.0 end
    
    /**
     * Sets the starred state of this contact.
     */
    public static void setStarred(Result contactData, CheckBox starredView) {
        // Check if the starred state should be visible
        if (!contactData.isDirectoryEntry() && !contactData.isUserProfile()) {
            starredView.setVisibility(View.VISIBLE);
            starredView.setChecked(contactData.getStarred());
        } else {
            starredView.setVisibility(View.GONE);
        }
    }

    /**
     * Set the social snippet text. If there isn't one, then set the view to gone.
     */
    public static void setSocialSnippet(Context context, Result contactData, TextView statusView,
            ImageView statusPhotoView) {
        if (statusView == null) {
            return;
        }

        CharSequence snippet = null;
        String photoUri = null;
        if (!contactData.getStreamItems().isEmpty()) {
            StreamItemEntry firstEntry = contactData.getStreamItems().get(0);
            snippet = HtmlUtils.fromHtml(context, firstEntry.getText());
            if (!firstEntry.getPhotos().isEmpty()) {
                StreamItemPhotoEntry firstPhoto = firstEntry.getPhotos().get(0);
                photoUri = firstPhoto.getPhotoUri();

                // If displaying an image, hide the snippet text.
                snippet = null;
            }
        }
        setDataOrHideIfNone(snippet, statusView);
        if (photoUri != null) {
            ContactPhotoManager.getInstance(context).loadPhoto(
                    statusPhotoView, Uri.parse(photoUri), true, false,
                    ContactPhotoManager.DEFAULT_BLANK);
            statusPhotoView.setVisibility(View.VISIBLE);
        } else {
            statusPhotoView.setVisibility(View.GONE);
        }
    }

    /** Creates the view that represents a stream item. */
    public static View createStreamItemView(LayoutInflater inflater, Context context,
            StreamItemEntry streamItem, LinearLayout parent,
            View.OnClickListener photoClickListener) {
        View container = inflater.inflate(R.layout.stream_item_container, parent, false);
        ViewGroup contentTable = (ViewGroup) container.findViewById(R.id.stream_item_content);

        ContactPhotoManager contactPhotoManager = ContactPhotoManager.getInstance(context);
        List<StreamItemPhotoEntry> photos = streamItem.getPhotos();
        final int photoCount = photos.size();

        // This stream item only has text.
        if (photoCount == 0) {
            View textOnlyContainer = inflater.inflate(R.layout.stream_item_row_text, contentTable,
                    false);
            addStreamItemText(context, streamItem, textOnlyContainer);
            contentTable.addView(textOnlyContainer);
        } else {
            // This stream item has text and photos. Process the photos, two at a time.
            for (int index = 0; index < photoCount; index += 2) {
                final StreamItemPhotoEntry firstPhoto = photos.get(index);
                if (index + 1 < photoCount) {
                    // Put in two photos, side by side.
                    final StreamItemPhotoEntry secondPhoto = photos.get(index + 1);
                    View photoContainer = inflater.inflate(R.layout.stream_item_row_two_images,
                            contentTable, false);
                    loadPhoto(contactPhotoManager, streamItem, firstPhoto, photoContainer,
                            R.id.stream_item_first_image, photoClickListener);
                    loadPhoto(contactPhotoManager, streamItem, secondPhoto, photoContainer,
                            R.id.stream_item_second_image, photoClickListener);
                    contentTable.addView(photoContainer);
                } else {
                    // Put in a single photo
                    View photoContainer = inflater.inflate(
                            R.layout.stream_item_row_one_image, contentTable, false);
                    loadPhoto(contactPhotoManager, streamItem, firstPhoto, photoContainer,
                            R.id.stream_item_first_image, photoClickListener);
                    contentTable.addView(photoContainer);
                }
            }

            // Add text, comments, and attribution if applicable
            View textContainer = inflater.inflate(R.layout.stream_item_row_text, contentTable,
                    false);
            // Add extra padding between the text and the images
            int extraVerticalPadding = context.getResources().getDimensionPixelSize(
                    R.dimen.detail_update_section_between_items_vertical_padding);
            textContainer.setPadding(textContainer.getPaddingLeft(),
                    textContainer.getPaddingTop() + extraVerticalPadding,
                    textContainer.getPaddingRight(),
                    textContainer.getPaddingBottom());
            addStreamItemText(context, streamItem, textContainer);
            contentTable.addView(textContainer);
        }

        if (parent != null) {
            parent.addView(container);
        }

        return container;
    }

    /** Loads a photo into an image view. The image view is identified by the given id. */
    private static void loadPhoto(ContactPhotoManager contactPhotoManager,
            final StreamItemEntry streamItem, final StreamItemPhotoEntry streamItemPhoto,
            View photoContainer, int imageViewId, View.OnClickListener photoClickListener) {
        final View frame = photoContainer.findViewById(imageViewId);
        final View pushLayerView = frame.findViewById(R.id.push_layer);
        final ImageView imageView = (ImageView) frame.findViewById(R.id.image);
        if (photoClickListener != null) {
            pushLayerView.setOnClickListener(photoClickListener);
            pushLayerView.setTag(new StreamPhotoTag(streamItem, streamItemPhoto));
            pushLayerView.setFocusable(true);
            pushLayerView.setEnabled(true);
        } else {
            pushLayerView.setOnClickListener(null);
            pushLayerView.setTag(null);
            pushLayerView.setFocusable(false);
            // setOnClickListener makes it clickable, so we need to overwrite it
            pushLayerView.setClickable(false);
            pushLayerView.setEnabled(false);
        }
        contactPhotoManager.loadPhoto(imageView, Uri.parse(streamItemPhoto.getPhotoUri()), true,
                false, ContactPhotoManager.DEFAULT_BLANK);
    }

    @VisibleForTesting
    static View addStreamItemText(Context context, StreamItemEntry streamItem, View rootView) {
        TextView htmlView = (TextView) rootView.findViewById(R.id.stream_item_html);
        TextView attributionView = (TextView) rootView.findViewById(
                R.id.stream_item_attribution);
        TextView commentsView = (TextView) rootView.findViewById(R.id.stream_item_comments);
        ImageGetter imageGetter = new DefaultImageGetter(context.getPackageManager());

        // Stream item text
        setDataOrHideIfNone(HtmlUtils.fromHtml(context, streamItem.getText(), imageGetter, null),
                htmlView);
        // Attribution
        setDataOrHideIfNone(ContactBadgeUtil.getSocialDate(streamItem, context),
                attributionView);
        // Comments
        setDataOrHideIfNone(HtmlUtils.fromHtml(context, streamItem.getComments(), imageGetter,
                null), commentsView);
        return rootView;
    }

    /**
     * Sets the display name of this contact to the given {@link TextView}. If
     * there is none, then set the view to gone.
     */
    public static void setDisplayName(Context context, Result contactData, TextView textView) {
        if (textView == null) {
            return;
        }
        setDataOrHideIfNone(getDisplayName(context, contactData), textView);
    }

    /**
     * Sets the company and job title of this contact to the given {@link TextView}. If
     * there is none, then set the view to gone.
     */
    public static void setCompanyName(Context context, Result contactData, TextView textView) {
        if (textView == null) {
            return;
        }
        setDataOrHideIfNone(getCompany(context, contactData), textView);
    }

    // aurora <ukiliu> <2013-8-31> add for auroro ui begin
    public static void setNoteName(Context context, Result contactData, TextView textView) {
        if (textView == null) {
            return;
        }
        setDataOrHideIfNone(getNote(context, contactData), textView);
    }
    // aurora <ukiliu> <2013-8-31> add for auroro ui begin
    
    /**
     * Sets the phonetic name of this contact to the given {@link TextView}. If
     * there is none, then set the view to gone.
     */
    public static void setPhoneticName(Context context, Result contactData, TextView textView) {
        if (textView == null) {
            return;
        }
        setDataOrHideIfNone(getPhoneticName(context, contactData), textView);
    }

    /**
     * Sets the attribution contact to the given {@link TextView}. If
     * there is none, then set the view to gone.
     */
    public static void setAttribution(Context context, Result contactData, TextView textView) {
        if (textView == null) {
            return;
        }
        setDataOrHideIfNone(getAttribution(context, contactData), textView);
    }

    /**
     * Helper function to display the given text in the {@link TextView} or
     * hides the {@link TextView} if the text is empty or null.
     */
    private static void setDataOrHideIfNone(CharSequence textToDisplay, TextView textView) {
        if (!TextUtils.isEmpty(textToDisplay)) {
            textView.setText(textToDisplay);
            textView.setVisibility(View.VISIBLE);
        } else {
            textView.setText(null);
            textView.setVisibility(View.GONE);
        }
    }

    /** Fetcher for images from resources to be included in HTML text. */
    private static class DefaultImageGetter implements Html.ImageGetter {
        /** The scheme used to load resources. */
        private static final String RES_SCHEME = "res";

        private final PackageManager mPackageManager;

        public DefaultImageGetter(PackageManager packageManager) {
            mPackageManager = packageManager;
        }

        @Override
        public Drawable getDrawable(String source) {
            // Returning null means that a default image will be used.
            Uri uri;
            try {
                uri = Uri.parse(source);
            } catch (Throwable e) {
                Log.d(TAG, "Could not parse image source: " + source);
                return null;
            }
            if (!RES_SCHEME.equals(uri.getScheme())) {
                Log.d(TAG, "Image source does not correspond to a resource: " + source);
                return null;
            }
            // The URI authority represents the package name.
            String packageName = uri.getAuthority();

            Resources resources = getResourcesForResourceName(packageName);
            if (resources == null) {
                Log.d(TAG, "Could not parse image source: " + source);
                return null;
            }

            List<String> pathSegments = uri.getPathSegments();
            if (pathSegments.size() != 1) {
                Log.d(TAG, "Could not parse image source: " + source);
                return null;
            }

            final String name = pathSegments.get(0);
            final int resId = resources.getIdentifier(name, "drawable", packageName);

            if (resId == 0) {
                // Use the default image icon in this case.
                Log.d(TAG, "Cannot resolve resource identifier: " + source);
                return null;
            }

            try {
                return getResourceDrawable(resources, resId);
            } catch (NotFoundException e) {
                Log.d(TAG, "Resource not found: " + source, e);
                return null;
            }
        }

        /** Returns the drawable associated with the given id. */
        private Drawable getResourceDrawable(Resources resources, int resId)
                throws NotFoundException {
            Drawable drawable = resources.getDrawable(resId);
            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            return drawable;
        }

        /** Returns the {@link Resources} of the package of the given resource name. */
        private Resources getResourcesForResourceName(String packageName) {
            try {
                return mPackageManager.getResourcesForApplication(packageName);
            } catch (NameNotFoundException e) {
                Log.d(TAG, "Could not find package: " + packageName);
                return null;
            }
        }
    }

    /**
     * Sets an alpha value on the view.
     */
    public static void setAlphaOnViewBackground(View view, float alpha) {
        if (view != null) {
            // Convert alpha layer to a black background HEX color with an alpha value for better
            // performance (i.e. use setBackgroundColor() instead of setAlpha())
            view.setBackgroundColor((int) (alpha * 255) << 24);
        }
    }

    /**
     * Returns the top coordinate of the first item in the {@link ListView}. If the first item
     * in the {@link ListView} is not visible or there are no children in the list, then return
     * Integer.MIN_VALUE. Note that the returned value will be <= 0 because the first item in the
     * list cannot have a positive offset.
     */
    public static int getFirstListItemOffset(AuroraListView listView) {
        if (listView == null || listView.getChildCount() == 0 ||
                listView.getFirstVisiblePosition() != 0) {
            return Integer.MIN_VALUE;
        }
        return listView.getChildAt(0).getTop();
    }

    /**
     * Tries to scroll the first item in the list to the given offset (this can be a no-op if the
     * list is already in the correct position).
     * @param listView that should be scrolled
     * @param offset which should be <= 0
     */
    public static void requestToMoveToOffset(AuroraListView listView, int offset) {
        // We try to offset the list if the first item in the list is showing (which is presumed
        // to have a larger height than the desired offset). If the first item in the list is not
        // visible, then we simply do not scroll the list at all (since it can get complicated to
        // compute how many items in the list will equal the given offset). Potentially
        // some animation elsewhere will make the transition smoother for the user to compensate
        // for this simplification.
        if (listView == null || listView.getChildCount() == 0 ||
                listView.getFirstVisiblePosition() != 0 || offset > 0) {
            return;
        }

        // As an optimization, check if the first item is already at the given offset.
        if (listView.getChildAt(0).getTop() == offset) {
            return;
        }

        listView.setSelectionFromTop(0, offset);
    }
    
    //aurora add liguangyu 20131102 for clip photo start
    /** 
    * 转换图片成圆形 
    * @param bitmap 传入Bitmap对象 
    * @return 
    */ 
    public static Bitmap toRoundBitmap(Bitmap bitmap) { 
	    int width = bitmap.getWidth(); 
	    int height = bitmap.getHeight(); 
	    float roundPx; 
	    float left,top,right,bottom,dst_left,dst_top,dst_right,dst_bottom; 
	    if (width <= height) { 
		    roundPx = width / 2; 
		    top = (height-width)/2; 
		    bottom = width + (height-width)/2; 
		    left = 0; 
		    right = width; 
		    height = width; 
		    dst_left = 0; 
		    dst_top = 0; 
		    dst_right = width; 
		    dst_bottom = width; 
	    } else { 
		    roundPx = height / 2; 
		    float clip = (width - height) / 2; 
		    left = clip; 
		    right = width - clip; 
		    top = 0; 
		    bottom = height; 
		    width = height; 
		    dst_left = 0; 
		    dst_top = 0; 
		    dst_right = height; 
		    dst_bottom = height; 
	    } 
	    Bitmap output = Bitmap.createBitmap(width, 
	    height, Config.ARGB_8888); 
	    Canvas canvas = new Canvas(output); 
	    final int color = 0xff424242; 
	    final Paint paint = new Paint(); 
	    final Rect src = new Rect((int)left, (int)top, (int)right, (int)bottom); 
	    final Rect dst = new Rect((int)dst_left, (int)dst_top, (int)dst_right, (int)dst_bottom); 
	    final RectF rectF = new RectF(dst); 
	    paint.setAntiAlias(true); 
	    canvas.drawARGB(0, 0, 0, 0); 
	    paint.setColor(color); 
	    canvas.drawRoundRect(rectF, roundPx, roundPx, paint); 
	    paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN)); 	    
	    canvas.drawBitmap(bitmap, src, dst, paint); 
	    return output; 
    } 

  //aurora add liguangyu 20131102 for clip photo end
}
