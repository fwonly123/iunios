package com.aurora.puremanager.activity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import aurora.app.AuroraActivity;
import aurora.widget.AuroraActionBar;
import aurora.widget.AuroraListView;

import com.aurora.puremanager.R;
import com.aurora.puremanager.data.AppInfo;
import com.aurora.puremanager.data.AppsInfo;
import com.aurora.puremanager.data.BaseData;
import com.aurora.puremanager.imageloader.ImageCallback;
import com.aurora.puremanager.imageloader.ImageLoader;
import com.aurora.puremanager.interfaces.Observer;
import com.aurora.puremanager.interfaces.Subject;
import com.aurora.puremanager.model.ConfigModel;
import com.aurora.puremanager.provider.open.FreezedAppProvider;
import com.aurora.puremanager.utils.ActivityUtils;
import com.aurora.puremanager.utils.ActivityUtils.LoadCallback;
import com.aurora.puremanager.utils.ApkUtils;
import com.aurora.puremanager.utils.Utils;
import com.aurora.puremanager.utils.mConfig;
import com.aurora.puremanager.viewcache.FreezeAppCache;
//import com.aurora.puremanager.adapter.FreezeAppAdapter;
//import com.aurora.puremanager.adapter.FreezedAppAdapter;

public class FreezeAppActivity extends AuroraActivity implements Observer {
	private List<BaseData> FreezedAppList;
	private List<BaseData> AppList;
	private List<BaseData> AllAppList;
	private FreezeAppAdapter adapter;
	private FreezedAppAdapter freezedadapter;
	private ListView ListView;
	private ListView FreezedListView;

	// private TextView mAppNum;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (mConfig.isNative) {
			setContentView(R.layout.freeze_activity);
		} else {
			setAuroraContentView(R.layout.freeze_activity,
					AuroraActionBar.Type.Normal);
			getAuroraActionBar().setTitle(R.string.app_freezeon);
		}
		ConfigModel.getInstance(this).getAppInfoModel().attach(this);
		initView();
		ActivityUtils.sleepForloadScreen(100, new LoadCallback() {
			@Override
			public void loaded() {
				updateViewHandler.sendEmptyMessage(0);
			}
		});
	}

	@Override
	protected void onPause() {
		if (ListView != null) {
			((AuroraListView) ListView).auroraOnPause();
		}
		if (FreezedListView != null) {
			((AuroraListView) FreezedListView).auroraOnPause();
		}
		super.onPause();
	}

	@Override
	protected void onResume() {
		if (ListView != null) {
			((AuroraListView) ListView).auroraOnResume();
		}
		if (FreezedListView != null) {
			((AuroraListView) FreezedListView).auroraOnResume();
		}
		super.onResume();
	}

	private void initView() {
		FreezedListView = (ListView) findViewById(R.id.freezed_ListView);
		ListView = (ListView) findViewById(R.id.ListView);
		// mAppNum = (TextView) findViewById(R.id.app_num);
	}

	private final Handler updateViewHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			initOrUpdateListData();
		}
	};

	/**
	 * 更新数据
	 */
	public void initOrUpdateListData() {
		if (ListView == null || FreezedListView == null) {
			return;
		}

		if (AllAppList == null) {
			AllAppList = new ArrayList<BaseData>();
		} else {
			AllAppList.clear();
		}

		if (AppList == null) {
			AppList = new ArrayList<BaseData>();
		} else {
			AppList.clear();
		}

		if (FreezedAppList == null) {
			FreezedAppList = new ArrayList<BaseData>();
		} else {
			FreezedAppList.clear();
		}
		HashSet<String> freezedApps = FreezedAppProvider
				.loadFreezedAppListInDB(getApplicationContext());

		AppsInfo userAppsInfo = ConfigModel.getInstance(this).getAppInfoModel()
				.getThirdPartyAppsInfo();
		if (userAppsInfo == null) {
			return;
		}

		for (int i = 0; i < userAppsInfo.size(); i++) {
			AppInfo appInfo = (AppInfo) userAppsInfo.get(i);
			if (appInfo == null || !appInfo.getIsInstalled()) {
				continue;
			}

			ApkUtils.initAppNameInfo(this, appInfo);
			AllAppList.add(appInfo);
			if (freezedApps.contains(appInfo.getPackageName())) {
				ApkUtils.initAppNameInfo(this, appInfo);
				FreezedAppList.add(appInfo);
			} else {
				AppList.add(appInfo);
			}
		}

		sortList(FreezedAppList);
		sortList(AppList);

		if (freezedadapter == null) {
			freezedadapter = new FreezedAppAdapter(this, FreezedAppList);
			FreezedListView.setAdapter(freezedadapter);
		} else {
			freezedadapter.notifyDataSetChanged();
		}

		if (adapter == null) {
			adapter = new FreezeAppAdapter(this, AppList);
			ListView.setAdapter(adapter);
		} else {
			adapter.notifyDataSetChanged();
		}

		if (AllAppList.size() == 0) {
			findViewById(R.id.NoAppLayout).setVisibility(View.VISIBLE);
			findViewById(R.id.freezed_layout).setVisibility(View.GONE);
			findViewById(R.id.freeze_layout).setVisibility(View.GONE);
			findViewById(R.id.ListView).setVisibility(View.GONE);
			findViewById(R.id.freezed_ListView).setVisibility(View.GONE);
			findViewById(R.id.empty_view).setVisibility(View.GONE);
			findViewById(R.id.line_view).setVisibility(View.GONE);
			findViewById(R.id.empty_view2).setVisibility(View.GONE);
			findViewById(R.id.line_view2).setVisibility(View.GONE);
		} else {
			if (AppList.size() == 0) {
				findViewById(R.id.freezed_layout).setVisibility(View.VISIBLE);
				findViewById(R.id.freezed_ListView).setVisibility(View.VISIBLE);
				findViewById(R.id.empty_view).setVisibility(View.VISIBLE);
				findViewById(R.id.line_view).setVisibility(View.VISIBLE);

				findViewById(R.id.freeze_layout).setVisibility(View.GONE);
				findViewById(R.id.ListView).setVisibility(View.GONE);
				findViewById(R.id.empty_view2).setVisibility(View.GONE);
				findViewById(R.id.line_view2).setVisibility(View.GONE);
			} else {
				if (FreezedAppList.size() == 0) {
					findViewById(R.id.freezed_layout).setVisibility(View.GONE);
					findViewById(R.id.freezed_ListView)
							.setVisibility(View.GONE);
					findViewById(R.id.empty_view).setVisibility(View.GONE);
					findViewById(R.id.line_view).setVisibility(View.GONE);

					findViewById(R.id.ListView).setVisibility(View.VISIBLE);
					findViewById(R.id.freeze_layout)
							.setVisibility(View.VISIBLE);
					findViewById(R.id.empty_view2).setVisibility(View.VISIBLE);
					findViewById(R.id.line_view2).setVisibility(View.VISIBLE);
				} else {
					findViewById(R.id.freezed_layout).setVisibility(
							View.VISIBLE);
					findViewById(R.id.freeze_layout)
							.setVisibility(View.VISIBLE);
					findViewById(R.id.ListView).setVisibility(View.VISIBLE);
					findViewById(R.id.freezed_ListView).setVisibility(
							View.VISIBLE);
					findViewById(R.id.empty_view).setVisibility(View.VISIBLE);
					findViewById(R.id.line_view).setVisibility(View.VISIBLE);
					findViewById(R.id.empty_view2).setVisibility(View.VISIBLE);
					findViewById(R.id.line_view2).setVisibility(View.VISIBLE);
				}
			}
		}
	}

	private void sortList(List<BaseData> appsList) {
		Collections.sort(appsList, new Comparator<BaseData>() {
			public int compare(BaseData s1, BaseData s2) {
				return Utils.compare(((AppInfo) s1).getAppNamePinYin(),
						((AppInfo) s2).getAppNamePinYin());
			}
		});
	}

	@Override
	public void onBackPressed() {
		exitSelf();
	}

	@Override
	protected void onDestroy() {
		releaseObject();
		super.onDestroy();
	}

	public void exitSelf() {
		finish();
	}

	/**
	 * 释放不需要用的对象所占用的堆内存
	 */
	private void releaseObject() {
		if (AppList != null) {
			AppList.clear();
		}
		if (FreezedAppList != null) {
			FreezedAppList.clear();
		}
		if (AllAppList != null) {
			AllAppList.clear();
		}
	}

	public class FreezeAppAdapter extends ArrayAdapter<BaseData> implements
			OnClickListener {
		private Activity activity;

		public FreezeAppAdapter(Activity activity, List<BaseData> listData) {
			super(activity, 0, listData);
			this.activity = activity;
		}

		@Override
		public View getView(int position, View convertView,
				final ViewGroup parent) {
			FreezeAppCache holder;
			if (convertView == null) {
				LayoutInflater inflater = activity.getLayoutInflater();
				convertView = inflater.inflate(R.layout.freeze_app_list_item,
						parent, false);
				holder = new FreezeAppCache(convertView);
				convertView.setTag(holder);
			} else {
				holder = (FreezeAppCache) convertView.getTag();
			}

			if (getCount() <= position) {
				return convertView;
			}

			AppInfo item = (AppInfo) getItem(position);
			holder.getAppName().setText(item.getAppName());
			holder.getFreezeBtn().setTag(position);
			holder.getFreezeBtn().setBackgroundResource(
					R.drawable.freeze_btn_bg);
			holder.getFreezeBtn().setText(R.string.text_freeze_btn);
			holder.getFreezeBtn().setTextColor(
					R.color.permission_detail_item_text);
			holder.getFreezeBtn().setOnClickListener(this);

			String iconViewTag = item.getPackageName() + "@app_icon";
			holder.getAppIcon().setTag(iconViewTag);
			Drawable cachedImage = ImageLoader.getInstance(getContext())
					.displayImage(holder.getAppIcon(), item.getPackageName(),
							iconViewTag, new ImageCallback() {
								public void imageLoaded(Drawable imageDrawable,
										Object viewTag) {
									if (parent == null || imageDrawable == null
											|| viewTag == null) {
										return;
									}
									ImageView imageViewByTag = (ImageView) parent
											.findViewWithTag(viewTag);
									if (imageViewByTag != null) {
										imageViewByTag
												.setImageDrawable(imageDrawable);
									}
								}
							});
			if (cachedImage != null) {
				holder.getAppIcon().setImageDrawable(cachedImage);
			} else {
				holder.getAppIcon().setImageResource(R.drawable.def_app_icon);
			}

			return convertView;
		}

		@Override
		public void onClick(View v) {
			Object tagObject = v.getTag();
			if (tagObject != null) {
				int position = Integer.parseInt(tagObject.toString());
				if (getCount() <= position) {
					return;
				}

				AppInfo item = (AppInfo) getItem(position);
				switch (v.getId()) {
				case R.id.freezeBtn:
					String packageName = item.getPackageName();
					// 冻结
					try {
						activity.getPackageManager()
								.setApplicationEnabledSetting(
										packageName,
										PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
										PackageManager.DONT_KILL_APP);
						FreezedAppProvider.freezedApp(activity, packageName);
					} catch (Exception e) {
						// TODO: handle exception
						e.printStackTrace();
					}
					notifyDataSetChanged();
					updateViewHandler.sendEmptyMessage(0);
					break;
				}
			}
		}
	}

	public class FreezedAppAdapter extends ArrayAdapter<BaseData> implements
			OnClickListener {
		private Activity activity;

		public FreezedAppAdapter(Activity activity, List<BaseData> listData) {
			super(activity, 0, listData);
			this.activity = activity;
		}

		@Override
		public View getView(int position, View convertView,
				final ViewGroup parent) {
			FreezeAppCache holder;
			if (convertView == null) {
				LayoutInflater inflater = activity.getLayoutInflater();
				convertView = inflater.inflate(R.layout.freeze_app_list_item,
						parent, false);
				holder = new FreezeAppCache(convertView);
				convertView.setTag(holder);
			} else {
				holder = (FreezeAppCache) convertView.getTag();
			}

			if (getCount() <= position) {
				return convertView;
			}

			AppInfo item = (AppInfo) getItem(position);
			holder.getAppName().setText(item.getAppName());
			holder.getFreezeBtn().setTag(position);
			holder.getFreezeBtn().setBackgroundResource(
					R.drawable.freezed_btn_bg);
			holder.getFreezeBtn().setText(R.string.text_freezed_btn);
			holder.getFreezeBtn().setTextColor(
					R.color.uninstall_list_item_btn_color);
			holder.getFreezeBtn().setOnClickListener(this);

			String iconViewTag = item.getPackageName() + "@app_icon";
			holder.getAppIcon().setTag(iconViewTag);
			Drawable cachedImage = ImageLoader.getInstance(getContext())
					.displayImage(holder.getAppIcon(), item.getPackageName(),
							iconViewTag, new ImageCallback() {
								public void imageLoaded(Drawable imageDrawable,
										Object viewTag) {
									if (parent == null || imageDrawable == null
											|| viewTag == null) {
										return;
									}
									ImageView imageViewByTag = (ImageView) parent
											.findViewWithTag(viewTag);
									if (imageViewByTag != null) {
										imageViewByTag
												.setImageDrawable(imageDrawable);
									}
								}
							});
			if (cachedImage != null) {
				holder.getAppIcon().setImageDrawable(cachedImage);
			} else {
				holder.getAppIcon().setImageResource(R.drawable.def_app_icon);
			}

			return convertView;
		}

		@Override
		public void onClick(View v) {
			Object tagObject = v.getTag();
			if (tagObject != null) {
				int position = Integer.parseInt(tagObject.toString());
				if (getCount() <= position) {
					return;
				}

				AppInfo item = (AppInfo) getItem(position);
				switch (v.getId()) {
				case R.id.freezeBtn:
					String packageName = item.getPackageName();
					// 解冻
					try {
						activity.getPackageManager()
								.setApplicationEnabledSetting(
										packageName,
										PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
										0);
						FreezedAppProvider.freezeApp(activity, packageName);
					} catch (Exception e) {
						// TODO: handle exception
					}
					notifyDataSetChanged();
					updateViewHandler.sendEmptyMessage(0);
					break;
				}
			}
		}
	}

	@Override
	public void updateOfInit(Subject subject) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateOfInStall(Subject subject, String pkgName) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateOfCoverInStall(Subject subject, String pkgName) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateOfUnInstall(Subject subject, String pkgName) {
		// TODO Auto-generated method stub
		FreezedAppProvider.freezeApp(FreezeAppActivity.this, pkgName);
	}

	@Override
	public void updateOfRecomPermsChange(Subject subject) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateOfExternalAppAvailable(Subject subject,
			List<String> pkgList) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateOfExternalAppUnAvailable(Subject subject,
			List<String> pkgList) {
		// TODO Auto-generated method stub
		
	}

}