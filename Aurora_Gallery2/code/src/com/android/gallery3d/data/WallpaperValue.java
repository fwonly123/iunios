package com.android.gallery3d.data;

import android.net.Uri;

public class WallpaperValue {
	public static final String WALLPAPER_PATH = "iuni/wallpaper/save/";
	public static final String WALLPAPER_ID = "_id";
	public static final String WALLPAPER_MODIFIED = "modified";
	public static final String WALLPAPER_OLDPATH = "oldpath";
	public static final String WALLPAPER_FILENAME = "filename";
	public static final String WALLPAPER_SELECTED = "seleted";
	public static final Uri LOCAL_WALLPAPER_URI = Uri.parse("content://com.aurora.change.provider/wallpaper");
	
	public static final String ACTION_WALLPAPER_SET = "com.aurora.action.WALLPAPER_SET";
}
