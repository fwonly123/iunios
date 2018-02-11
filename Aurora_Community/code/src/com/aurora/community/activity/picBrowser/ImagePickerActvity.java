package com.aurora.community.activity.picBrowser;

import java.io.File;
import java.util.ArrayList;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.aurora.community.CommunityApp;
import com.aurora.community.R;
import com.aurora.community.activity.BaseActivity;
import com.aurora.community.activity.fragment.ImageAlbumFragment;
import com.aurora.community.activity.fragment.ImageAlbumFragment.ImageAlbumItemController;
import com.aurora.community.activity.fragment.ImagePickFragment;
import com.aurora.community.activity.twitter.TwitterNoteActivity;
import com.aurora.community.bean.AlbumInfo;
import com.aurora.community.bean.PhotoInfo;
import com.aurora.community.bean.PhotoSerializable;
import com.aurora.community.totalCount.TotalCount;
import com.aurora.community.utils.FileLog;
import com.aurora.community.utils.FragmentHelper;
import com.aurora.community.utils.Globals;
import com.aurora.community.utils.ImageLoaderHelper;
import com.umeng.analytics.MobclickAgent;

public class ImagePickerActvity extends BaseActivity implements
		FragmentHelper.IFragmentController,
		ImageAlbumItemController {

	private static final String TAG = "ImagePickerActvity";

	private Fragment ImagePickFragment;

	private Fragment ImageAlbumFragment;

	private Fragment currentFragment;

	private FragmentManager manager;

	private final String FRAGMENT_DEFAULT_PIC_TAG = "pic";
	private final String FRAGMENT_DEFAULT_ALBUM_TAG = "album";

	private TextView mPickPreview;

	private TextView mSelectedDone;

	private ImageButton mStartCapture;

	private static final String IMAGE_CAPTURE_PREFIX = ImageLoaderHelper
			.getDirectory().toString();

	private String fileName;

	private int currentLeft = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		Log.e("linp", "#####################onCreate");
		CommunityApp.getInstance().addActivity(this);
		/** in order to get twitter note activity's grid left space */
		if (((Integer) getIntent().getIntExtra("left", 0)) != null) {
			Log.e("linp", "#############left="+((Integer) getIntent().getIntExtra("left", 0)));
			setCurrentLeft(((Integer) getIntent().getIntExtra("left", 0)));
		}

		setContentView(R.layout.activity_image_picker_layout);
		setupViews();

		manager = getFragmentManager();

		initFragment();

		mPickPreview.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				/** list for all photo list */

				ArrayList<PhotoInfo> list = ((ImagePickFragment) ImagePickFragment)
						.getSelectedList();
				if (list.size() == 0) {
					Toast.makeText(ImagePickerActvity.this, "请选择图片！",
							Toast.LENGTH_SHORT).show();
					return;
				}
				PhotoSerializable photoSerializable = new PhotoSerializable();
				Bundle args = new Bundle();
				photoSerializable.setList(list);
				args.putSerializable("list", photoSerializable);

				// List<PhotoInfo> selectedList =
				// ((ImagePickFragment)ImagePickFragment).getSelectedList();
				// PhotoSerializable photoSelectedSerializable = new
				// PhotoSerializable();
				// photoSelectedSerializable.setList(selectedList);
				// args.putSerializable("select", photoSelectedSerializable);

				// args.putInt("current", getCurrentPosition());
				args.putInt("current", 0);
				args.putInt("left", -1);
				Intent intent = new Intent();
				intent.setClass(ImagePickerActvity.this,
						ImagePreviewActivity.class);
				intent.putExtras(args);
				startActivity(intent);
			}
		});

		mStartCapture.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
				fileName = IMAGE_CAPTURE_PREFIX + "/"
						+ System.currentTimeMillis() + ".jpg";
				intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT,
						Uri.fromFile(new File(fileName)));
				startActivityForResult(intent, 10);
				ImagePickerActvity.this.setResult(3);
			}
		});

	}

	
	@Override
	public void setupAuroraActionBar() {
		// TODO Auto-generated method stub
		super.setupAuroraActionBar();
		setCustomerActionBar(R.layout.activity_image_picker_custom_actionbar_layout);
		findViewById(R.id.tv_actionbar_title).setOnClickListener(onClickListener);
		findViewById(R.id.tv_actionbar_done).setOnClickListener(onClickListener);
	}
		
	

    private OnClickListener onClickListener = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.tv_actionbar_title:
				switchContent();
				break;
			case R.id.tv_actionbar_done:
				selectedDone();
				break;
			default:
				break;
			}
			
		}
	};    

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// TODO Auto-generated method stub
		if (requestCode == 10 && resultCode == RESULT_OK) {

			Intent flush = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE); // ,
																				// MediaStore.Images.Media.EXTERNAL_CONTENT_URI
			Uri uri = Uri.fromFile(new File(fileName));
			flush.setData(uri);
			this.sendBroadcast(flush);

			ArrayList<String> urlList = new ArrayList<String>();
			String p = "file:///" + fileName;
			urlList.add(p);
			Bundle d = new Bundle();
			Intent intent = new Intent(this, TwitterNoteActivity.class);
			PhotoSerializable photoSerializable = new PhotoSerializable();
			photoSerializable.setUrlList(urlList);
			d.putSerializable("list", photoSerializable);
			intent.putExtras(d);
			intent.putExtra("preview", false);
			startActivity(intent);
			/** 拍照时算一张 */

			this.finish();
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub

		super.onDestroy();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		// TODO Auto-generated method stub
       
	  //从preview获取列表。然后for更改内容。
		String key  = (String)intent.getStringExtra("key");
		if(key!=null){
			Log.e("linp", "ImagePickerActivity onNewIntent key not null!");
	        if(key.equals("imagePreview")){
	    		Bundle extras = intent.getExtras(); 

	    		PhotoSerializable photoSerializable = (PhotoSerializable) extras.getSerializable("list");

    		ArrayList<PhotoInfo> result =photoSerializable.getList();
    		ArrayList<PhotoInfo> orgList = ((ImagePickFragment)ImagePickFragment).getPhotoList();
    		ArrayList<PhotoInfo> selectedList = ((ImagePickFragment)ImagePickFragment).getSelectedList();
    		Log.e("linp", "##########ImagePickerActivity onNewIntent selectedList.size="+selectedList.size()+";"+"result="+result.size());
   		    for(PhotoInfo info : result){
   		    	if(orgList.contains(info)){
   		    		orgList.get(orgList.indexOf(info)).setChoose(info.isChoose());
            		if(!info.isChoose())
            		{
            			selectedList.remove(info);
            		}else if(!selectedList.contains(info)){
            			selectedList.add(info);
            		}
   		    	}
            }
  	      if(((ImagePickFragment)ImagePickFragment).adapter!=null){
            	((ImagePickFragment)ImagePickFragment).adapter.notifyDataSetChanged();
            }
      
        }
	}else{
		if (intent!=null) {
			int left = ((Integer) intent.getIntExtra("left",0));
			setCurrentLeft(left);
			if(((ImagePickFragment)ImagePickFragment)!=null){
				if(((ImagePickFragment)ImagePickFragment).adapter!=null){
					((ImagePickFragment)ImagePickFragment).adapter.setCurrentLeft(getCurrentLeft());
					((ImagePickFragment)ImagePickFragment).adapter.clear();
				}else{
					Log.e("linp", "ImagePickerActivity 1");
				}
			}else{
				Log.e("linp", "ImagePickerActivity 2");
			}
			
		}
		ArrayList<PhotoInfo> selectedList = ((ImagePickFragment)ImagePickFragment).getPhotoList();
		for(PhotoInfo info : selectedList){
			info.setChoose(false);
		}
	      if(((ImagePickFragment)ImagePickFragment).adapter!=null){
          	((ImagePickFragment)ImagePickFragment).adapter.notifyDataSetChanged();
          }
		Log.e("linp", "ImagePickerActivity onNewIntent key null!");
	}
	

		
		super.onNewIntent(intent);
	}

	@Override
	public void setupViews() {
		// TODO Auto-generated method stub
		mPickPreview = (TextView) findViewById(R.id.tv_pic_preview);
		mStartCapture = (ImageButton) findViewById(R.id.ib_start_camera);
	}

	@Override
	public void initFragment() {
		// TODO Auto-generated method stub
		FragmentTransaction t = getFragmentManager().beginTransaction();
		ImagePickFragment = new ImagePickFragment();
		currentFragment = ImagePickFragment;
		t.add(R.id.photo_content, currentFragment, FRAGMENT_DEFAULT_PIC_TAG);
		t.commit();

		// FragmentHelper.getInstantce().setCurrentContent(ImagePickFragment);
	}

	public void switchContent() {
		// TODO Auto-generated method stub
		if (ImageAlbumFragment == null) {
			Log.e("linp", "~~~~~~~~~~~switchContent");

			Bundle args = new Bundle();
			ArrayList<AlbumInfo> list = ((ImagePickFragment) ImagePickFragment)
					.getAlbumList();
			PhotoSerializable photoSerializable = new PhotoSerializable();
			photoSerializable.setAlbumList(list);
			args.putSerializable("list", photoSerializable);
			ImageAlbumFragment = new ImageAlbumFragment();
			ImageAlbumFragment.setArguments(args);

			FragmentTransaction t = manager.beginTransaction();
			t.hide(ImagePickFragment).commit();

			t = manager.beginTransaction();
			currentFragment = ImageAlbumFragment;
			t.add(R.id.photo_content, currentFragment,
					FRAGMENT_DEFAULT_ALBUM_TAG);
			t.commit();

		} else {
			if (currentFragment.getTag().equals(FRAGMENT_DEFAULT_PIC_TAG)) {
				switchContent(ImageAlbumFragment);
			} else {
				switchContent(ImagePickFragment);
			}
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// TODO Auto-generated method stub
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (currentFragment.getTag().equals(FRAGMENT_DEFAULT_PIC_TAG)) {
				/*Intent intent = new Intent(this, MainActivity.class);
				startActivity(intent);*/
				this.finish();
			} else {
				switchContent(ImagePickFragment);
				return true;
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onItemClick(ArrayList<PhotoInfo> l, String str) {
		// TODO Auto-generated method stub

		((ImagePickFragment) ImagePickFragment).setAblumItemPhotoList(l);
		switchContent(ImagePickFragment);
		setTitleText(str);
	}

	public void selectedDone() {
		// TODO Auto-generated method stub
		if(((ImagePickFragment) ImagePickFragment).adapter!=null){
			ArrayList<PhotoInfo> list = ((ImagePickFragment) ImagePickFragment)
					.getSelectedList();
			ArrayList<String> urlList = new ArrayList<String>();
			for (PhotoInfo info : list) {
				urlList.add(info.getPath_file());
			}
		/*	new TotalCount(ImagePickerActvity.this, "300", "005", 1).CountData();*/
			MobclickAgent.onEvent(ImagePickerActvity.this, Globals.PREF_TIMES_FINISH);
			Bundle d = new Bundle();
			Intent intent = new Intent(this, TwitterNoteActivity.class);
			PhotoSerializable photoSerializable = new PhotoSerializable();
			photoSerializable.setUrlList(urlList);
			d.putSerializable("list", photoSerializable);
			intent.putExtra("preview", false);
			intent.putExtras(d);
			startActivity(intent);
			this.finish();
		}else
			FileLog.e("linp", "ImagePickerActivity on selectedDone that ImagePickFragment adapter cause NullPointer!");

	}

	public int getCurrentPosition() {
		return ((ImagePickFragment) ImagePickFragment)
				.getCurrentClickPosition();
	}

	public void setCurrentLeft(int left) {
		this.currentLeft = left;
	}

	public int getCurrentLeft() {
		return this.currentLeft;
	}

	public void switchContent(Fragment fragment) {
		if (currentFragment == fragment) {
			return;
		}
		FragmentTransaction t = getFragmentManager().beginTransaction();
		t.hide(currentFragment);
		currentFragment = fragment;
		t.show(currentFragment);
		t.commit();
	}


}
