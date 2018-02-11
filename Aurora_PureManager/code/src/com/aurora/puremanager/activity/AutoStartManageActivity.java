package com.aurora.puremanager.activity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import com.aurora.puremanager.R;
import com.aurora.puremanager.adapter.AutoStartManageAdapter;
import com.aurora.puremanager.data.AppInfo;
import com.aurora.puremanager.data.AppsInfo;
import com.aurora.puremanager.data.AutoStartData;
import com.aurora.puremanager.data.BaseData;
import com.aurora.puremanager.interfaces.Subject;
import com.aurora.puremanager.model.AutoStartModel;
import com.aurora.puremanager.model.ConfigModel;
import com.aurora.puremanager.provider.open.AutoStartAppProvider;
import com.aurora.puremanager.totalCount.TotalCount;
import com.aurora.puremanager.utils.ActivityUtils;
import com.aurora.puremanager.utils.ApkUtils;
import com.aurora.puremanager.utils.LogUtils;
import com.aurora.puremanager.utils.Utils;
import com.aurora.puremanager.utils.mConfig;
import com.aurora.puremanager.utils.ActivityUtils.LoadCallback;
import com.aurora.puremanager.view.InfoDialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ListView;
import android.widget.TextView;
import aurora.app.AuroraActivity;
import aurora.widget.AuroraActionBar;
import aurora.widget.AuroraActionBar.OnAuroraActionBarItemClickListener;
import aurora.widget.AuroraActionBarItem;

public class AutoStartManageActivity extends AuroraActivity implements 
                                OnClickListener{
	
	private final int REQUEST_CODE_OF_ADD_APP =1;     
	private List<BaseData> autoStartAppList;	
	private AutoStartManageAdapter adapter;
	private ListView ListView;	
	private TextView autoStartAppNum;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(mConfig.isNative){
        	setContentView(R.layout.auto_start_manage_activity);
        }else{
        	setAuroraContentView(R.layout.auto_start_manage_activity,
            		AuroraActionBar.Type.Normal);
            getAuroraActionBar().setTitle(R.string.auto_start_manage);
            getAuroraActionBar().addItem(AuroraActionBarItem.Type.Add,R.id.add_menu);     
            
            getAuroraActionBar().setOnAuroraActionBarListener(new OnAuroraActionBarItemClickListener(){
    			@Override
    			public void onAuroraActionBarItemClicked(int arg0) {
    				new TotalCount(AutoStartManageActivity.this, "13", 1).CountData();
    				Intent intent = new Intent(AutoStartManageActivity.this,AddAutoStartAppActivity.class);
    				startActivityForResult(intent, REQUEST_CODE_OF_ADD_APP);
    			}}) ; 
        }
        initView();        
        ActivityUtils.sleepForloadScreen(100,new LoadCallback(){
			@Override
			public void loaded() {
				AutoStartModel.getInstance(AutoStartManageActivity.this).attach(updateViewHandler);
//				AuroraPrivacyManageModel.getInstance(AutoStartManageActivity.this).
//				   attach(AutoStartManageActivity.this);
				updateViewHandler.sendEmptyMessage(0);
			}       	
        }); 
    }
       
    private void initView(){
    	findViewById(R.id.exclamationImg).setOnClickListener(this);	 	
    	ListView = (ListView)findViewById(R.id.ListView);
    	autoStartAppNum = (TextView)findViewById(R.id.autoStartAppNum);
    }
    
    private final Handler updateViewHandler = new Handler() {
    	@Override
	    public void handleMessage(Message msg) {
    		initOrUpdatetListData();
	    }
    };
    
    /**
     * a new version of initOrUpdatetListData
     * According to new requirement, auto start app list should be loaded from database
     * Vulcan created this method in 2015年1月13日 下午2:46:52 .
     */
    public void initOrUpdatetListData2() {

    	if(ListView == null){
    		return ;
    	}
    	if(autoStartAppList == null){
    		autoStartAppList = new ArrayList<BaseData>();
    	}else{
    		autoStartAppList.clear();
    	}
    	
    	HashSet<String> autostartApps = AutoStartAppProvider.loadAutoStartAppListInDB(getApplicationContext());
    	
    	for(String app: autostartApps) {
    		LogUtils.printWithLogCat("vautostart", "initOrUpdatetListData2: =====autostartList: " + app);
    	}
    	
    	AppsInfo userAppsInfo = ConfigModel.getInstance(this).getAppInfoModel().getThirdPartyAppsInfo();
    	if(userAppsInfo == null){
    		return ;
    	}
    	
    	for(int i=0;i<userAppsInfo.size();i++){
    		AppInfo appInfo = (AppInfo)userAppsInfo.get(i);
    		if(appInfo == null || !appInfo.getIsInstalled()){
    			continue;
    		}
    		
    		AutoStartData autoStartData = AutoStartModel.getInstance(this).
    				getAutoStartData(appInfo.getPackageName());
    		if(autoStartData == null){
    			continue ;
    		}
    		
    		if(autostartApps.contains(appInfo.getPackageName())) {
    			ApkUtils.initAppNameInfo(this, appInfo);
    			autoStartAppList.add(appInfo);
    		}
    	}
    	
    	sortList(autoStartAppList);
    	
    	if(adapter == null){
    		adapter = new AutoStartManageAdapter(this,autoStartAppList);
        	ListView.setAdapter(adapter);
    	}else{
    		adapter.notifyDataSetChanged();
    	} 
    	
    	autoStartAppNum.setText(""+autoStartAppList.size());
    	
    	if(autoStartAppList.size() == 0){
    		findViewById(R.id.NoAppLayout).setVisibility(View.VISIBLE);
    		findViewById(R.id.HaveAppLayout).setVisibility(View.GONE);
    	}else{
    		findViewById(R.id.NoAppLayout).setVisibility(View.GONE);
    		findViewById(R.id.HaveAppLayout).setVisibility(View.VISIBLE);
    	}
    
    }
    
    /**
     * 
     * Vulcan created this method in 2015年1月13日 下午5:06:27 .
     */
    public void initOrUpdatetListData1(){
    	if(ListView == null){
    		return ;
    	}
    	if(autoStartAppList == null){
    		autoStartAppList = new ArrayList<BaseData>();
    	}else{
    		autoStartAppList.clear();
    	}
    	
    	AppsInfo userAppsInfo = ConfigModel.getInstance(this).getAppInfoModel().getThirdPartyAppsInfo();
    	if(userAppsInfo == null){
    		return ;
    	}
    	
    	for(int i=0;i<userAppsInfo.size();i++){
    		AppInfo appInfo = (AppInfo)userAppsInfo.get(i);
    		if(appInfo == null || !appInfo.getIsInstalled()){
    			continue;
    		}
    		
    		AutoStartData autoStartData = AutoStartModel.getInstance(this).
    				getAutoStartData(appInfo.getPackageName());
    		if(autoStartData == null){
    			continue ;
    		}
    		
			if(autoStartData.getIsOpen()){
				ApkUtils.initAppNameInfo(this, appInfo);
				autoStartAppList.add(appInfo);
			}		
    	}
    	
    	sortList(autoStartAppList);
    	
    	if(adapter == null){
    		adapter = new AutoStartManageAdapter(this,autoStartAppList);
        	ListView.setAdapter(adapter);
    	}else{
    		adapter.notifyDataSetChanged();
    	} 
    	
    	autoStartAppNum.setText(""+autoStartAppList.size());
    	
    	if(autoStartAppList.size() == 0){
    		findViewById(R.id.NoAppLayout).setVisibility(View.VISIBLE);
    		findViewById(R.id.HaveAppLayout).setVisibility(View.GONE);
    	}else{
    		findViewById(R.id.NoAppLayout).setVisibility(View.GONE);
    		findViewById(R.id.HaveAppLayout).setVisibility(View.VISIBLE);
    	}
    }
    
    /**
     * 更新数据
     */
    public void initOrUpdatetListData(){
    	initOrUpdatetListData2();
    }
    
    private void sortList(List<BaseData> appsList){
		Collections.sort(appsList, new Comparator<BaseData>(){
		   public int compare(BaseData s1, BaseData s2) {
			   return Utils.compare(((AppInfo)s1).getAppNamePinYin(),((AppInfo)s2).getAppNamePinYin());
		   }
		});     
	}
    
	@Override
	public void onClick(View v) {
		Intent intent;
		switch(v.getId()){		
		case R.id.addBtn:
			intent = new Intent(this,AddAutoStartAppActivity.class);
			startActivityForResult(intent, REQUEST_CODE_OF_ADD_APP);
			break;
		case R.id.exclamationImg:
			InfoDialog.showDialog(this,
					R.string.what_is_auto_start,
					R.string.auto_start_explain, 
					R.string.sure);
			break;
		}		
	}

	@Override
	protected void onDestroy() {
		AutoStartModel.getInstance(this).detach(updateViewHandler);
		releaseObject();
		super.onDestroy();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch(requestCode){
		case REQUEST_CODE_OF_ADD_APP:
			initOrUpdatetListData();		
			break;
		}
	}
	
    /**
     * 释放不需要用的对象所占用的堆内存
     */
	private void releaseObject(){
		if(autoStartAppList != null){
			autoStartAppList.clear();
		}
	}
}