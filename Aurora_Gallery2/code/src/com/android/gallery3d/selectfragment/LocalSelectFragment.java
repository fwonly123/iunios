package com.android.gallery3d.selectfragment;

import java.util.ArrayList;
import java.util.List;

import android.app.Fragment;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.PopupWindow;
import aurora.app.AuroraActivity;
import aurora.widget.AuroraCheckBox;
import aurora.widget.AuroraListView;
import aurora.widget.AuroraTextView;

import com.android.gallery3d.R;
import com.android.gallery3d.xcloudalbum.CloudActivity;
import com.android.gallery3d.xcloudalbum.fragment.BasicFragment;
import com.android.gallery3d.xcloudalbum.inter.IBaiduinterface;
import com.android.gallery3d.xcloudalbum.tools.BaiduAlbumUtils;
import com.android.gallery3d.xcloudalbum.tools.LogUtil;
import com.android.gallery3d.xcloudalbum.tools.OperationUtil;
import com.android.gallery3d.xcloudalbum.tools.Utils;
import com.android.gallery3d.xcloudalbum.uploaddownload.UploadToXCloudListener;
import com.android.gallery3d.xcloudalbum.widget.LocalSelectPopupAdapter;
import com.baidu.xcloud.pluginAlbum.AlbumConfig;
import com.baidu.xcloud.pluginAlbum.bean.CommonFileInfo;
import com.android.gallery3d.app.AbstractGalleryActivity;

import android.text.TextUtils;
import android.util.Log;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.util.concurrent.ConcurrentHashMap;

public class LocalSelectFragment extends Fragment implements
		IBaiduinterface {
	private Context mContext;
	private CheckBox checkBox;
	private AuroraListView listView;
	private AuroraTextView titleView;
	private Button popCancel;
	private View mView;
	private List<CommonFileInfo> fileInfos;
	private LocalSelectPopupAdapter adapter;
	private static final String TAG = "LocalSelectPopupWindow";
	private OperationUtil operationUtil;
	private ArrayList<String> mFilePaths;
	private UploadToXCloudListener mListener;
	private final static int MSG_REFRESH_LIST = 0X01;//paul add for BUG #15522
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		//TODO Auto-generated method stub
		return inflater.inflate(R.layout.aurora_cloud_fragment, container, false);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		//TODO Auto-generated method stub
		super.onActivityCreated(savedInstanceState);
		initView();
	}
	
	@Override
	public void onResume() {
		//TODO Auto-generated method stub
		super.onResume();
		adapter = new LocalSelectPopupAdapter(getActivity(), fileInfos);
		listView.setAdapter(adapter);
	}
	
	public void initData( Context context,
			List<CommonFileInfo> fileInfos, OperationUtil operUtil,
			UploadToXCloudListener listener) {

		Log.i("SQF_LOG", "LocalSelectPopupWindow::LocalSelectPopupWindow");

		mContext = context;
		mListener = listener;
//		mView = LayoutInflater.from(context).inflate(R.layout.aurora_cloud_popup, null);
		
//		defaultMenu(mView, aniTabMenu);
		this.fileInfos = fileInfos;
		if (this.fileInfos == null) {
			this.fileInfos = new ArrayList<CommonFileInfo>();
		}
		/*
		 * if(context instanceof AbstractGalleryActivity){ operationUtil =
		 * ((AbstractGalleryActivity)context).getOperationUtil(); }
		 */
		operationUtil = operUtil;
		// add by JXH 2015-5-14 begin
//		LayoutParams layoutParams = listView.getLayoutParams();
//		if (fileInfos != null && fileInfos.size() > 3) {
//			layoutParams.height = (int) context.getResources().getDimension(
//					R.dimen.aurora_album_listview_max_height);
//			listView.setLayoutParams(layoutParams);
//		}
		// add by JXH 2015-5-14 end
	

		if (fileInfos == null || fileInfos.size() == 0) {
			// showLoading();
		}
	}

	public List<CommonFileInfo> getFileInfos() {
		return fileInfos;
	}

	public void requestNewFolderInfos() {
		Log.i("SQF_LOG", "LocalSelectPopupWindow::requestNewFolderInfos");
		BaiduAlbumUtils.getInstance(mContext).setBaiduinterface(this);
		BaiduAlbumUtils.getInstance(mContext).getFileListFromBaidu(
				AlbumConfig.REMOTEPATH, true, null);
	}

	public void setFilePaths(ArrayList<String> filePaths) {
		Log.i("SQF_LOG",
				"LocalSelectPopupWindow::setFilePaths " + filePaths.size());
		mFilePaths = filePaths;
	}

	public void setInfos(List<CommonFileInfo> fileInfos) {
		
		//wenyongzhe 2015.10.16 disable screenshot start
		for(CommonFileInfo mCommonFileInfo : fileInfos){
			if(mCommonFileInfo.path.contains(mContext.getResources().getString(R.string.aurora_system_screenshot)) 
//					||mCommonFileInfo.path.contains(mContext.getResources().getString(R.string.aurora_album_screenshots))//wenyongzhe 2015.11.19
					){//wenyongzhe 2015.11.10 language English bug
				fileInfos.remove(mCommonFileInfo);
				break;
			}
		}
		//wenyongzhe 2015.10.16 disable screenshot end
		
		this.fileInfos = fileInfos;//SQF ADDED ON 2015.6.16
		if (fileInfos == null) {
			Log.e(TAG, "ERROR: !!! LocalSelectPopupWindow::setInfos fileInfos is NULL!");
			return;
		}
		
		// add by JXH 2015-5-14 begin
//		LayoutParams layoutParams = listView.getLayoutParams();
//		if (fileInfos != null && fileInfos.size() > 3) {
//			layoutParams.height = (int) mContext.getResources().getDimension(
//					R.dimen.aurora_album_listview_max_height);
//			listView.setLayoutParams(layoutParams);
//		}
		// add by JXH 2015-5-14 end
		
		if(null == adapter){//paul add for BUG #15522
			adapter = new LocalSelectPopupAdapter(mContext, fileInfos);
			listView.setAdapter(adapter);
		}else{
			adapter.setInfos(fileInfos);
		}
		adapter.notifyDataSetChanged();
	}

//	public void setTitleText(String title) {
//		if (titleView != null) {
//			titleView.setText(title);
//		}
//	}

	public void setOperationUtil(OperationUtil operationUtil) {
		this.operationUtil = operationUtil;
	}

	private void initView() {
//			checkBox = (CheckBox) getView().findViewById(R.id.pop_checkbox);
			listView = (AuroraListView) getView().findViewById(R.id.pop_listView);
//			titleView = (AuroraTextView) getView().findViewById(R.id.pop_title);
//			popCancel = (Button) getView().findViewById(R.id.pop_cancel);
//			popCancel.setOnClickListener(new OnClickListener() {
//
//				@Override
//				public void onClick(View v) {
//					mListener.uploadFinished(false);
//					//dismiss();
//				}
//			});
			listView.setOnItemClickListener(new OnItemClickListener() {

				@Override
				public void onItemClick(AdapterView<?> parent, View view,
						int position, long id) {
					LogUtil.d(TAG, "onItemClick::" + position);
//					if (position == 0) {
//						operationUtil.setMoveCreateAlbum(true);
//						operationUtil.setMoveCheckBox(!false);
//						operationUtil.createAlbum(
//								R.string.aurora_move_new_album,
//								(AuroraActivity) mContext, fileInfos);
//						return;
//					}
					CommonFileInfo fileInfo = (CommonFileInfo) adapter
							.getItem(position);
					Log.i("SQF_LOG", "onItemClick::" + position);
					// operationUtil.moveOrCopyPhoto(fileInfo,
					// !checkBox.isChecked());
					operationUtil.uploadToAlbum(mFilePaths, fileInfo);
					mListener.uploadFinished(true);
//					dismiss();
				}
			});
//			hideCheckBoxContainer();// SQF ADDED
	}

//	protected void defaultMenu(View view, int aniTabMenu) {
//		// 设置默认项
//		this.setContentView(view);
//		this.setWidth(LayoutParams.WRAP_CONTENT);
//		this.setHeight(LayoutParams.WRAP_CONTENT);
//		this.setBackgroundDrawable(new BitmapDrawable());// 设置Menu菜单背景
//		this.setAnimationStyle(aniTabMenu);
//		this.setFocusable(true);
//		this.setOutsideTouchable(false);
//	}
	
	//paul add for BUG #15522
	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			if(msg.what == MSG_REFRESH_LIST) {
				adapter.notifyDataSetChanged();
			}
		}
	};

	@Override
	public void baiduPhotoList(List<CommonFileInfo> list, boolean isDirPath,
			CommonFileInfo info) {
		// stopLoading();
		//paul add for BUG #15522
		ConcurrentHashMap<CommonFileInfo, List<CommonFileInfo>> hashMap = ((AbstractGalleryActivity)mContext).getConcurrentHashMap();
		if(isDirPath){
			hashMap.clear();
			if(null == list) return;
			setInfos(list);
			for (CommonFileInfo commonFileInfo : list) {
				if (commonFileInfo.isDir && !TextUtils.isEmpty(commonFileInfo.path)) {
					BaiduAlbumUtils.getInstance(mContext).getFileListFromBaidu(commonFileInfo.path, false, commonFileInfo);
				}
			}
		} else {
			if (hashMap.contains(info)) {
				hashMap.replace(info, list);
			} else {
				hashMap.put(info, list);
			}
			mHandler.removeMessages(MSG_REFRESH_LIST);
			mHandler.sendEmptyMessageDelayed(MSG_REFRESH_LIST, 300);
		}
	}

	@Override
	public void loginComplete(boolean success) {

	}

	private void hideCheckBoxContainer() {
		View view = getView().findViewById(R.id.pop_temp_linear);
		LayoutParams layoutParams = view.getLayoutParams();
		layoutParams.height = Utils.dip2px(mContext, 20);
		view.setLayoutParams(layoutParams);
		view.setVisibility(View.INVISIBLE);
	}
}
