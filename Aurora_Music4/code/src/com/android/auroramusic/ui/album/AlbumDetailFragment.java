package com.android.auroramusic.ui.album;

import java.util.ArrayList;
import java.util.List;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;
import aurora.app.AuroraAlertDialog;
import aurora.widget.AuroraCheckBox;
import aurora.widget.AuroraListView;
import aurora.widget.AuroraListView.AuroraBackOnClickListener;
import aurora.widget.AuroraListView.AuroraDeleteItemListener;

import com.android.auroramusic.adapter.AuroraTrackListAdapter;
import com.android.auroramusic.ui.AuroraTrackBrowserActivity;
import com.android.auroramusic.util.AuroraAlbum;
import com.android.auroramusic.util.AuroraListItem;
import com.android.auroramusic.util.AuroraMusicUtil;
import com.android.auroramusic.util.DataConvertUtil;
import com.android.auroramusic.util.DialogUtil;
import com.android.auroramusic.util.Globals;
import com.android.auroramusic.util.DialogUtil.OnAddPlaylistSuccessListener;
import com.android.music.MediaPlaybackService;
import com.android.music.MusicUtils;
import com.android.music.R;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.display.SimpleBitmapDisplayer;

/**
 * A fragment representing a single Album detail screen. This fragment is either
 * contained in a {@link AlbumListActivity} in two-pane mode (on tablets) or a
 * {@link AlbumDetailActivity} on handsets.
 */
@SuppressLint("NewApi")
public class AlbumDetailFragment extends Fragment implements OnItemClickListener {

	private static String TAG = "AlbumDetailFragment";
	private AuroraAlbum mItem;
	private String mArtistId;
	private String mAlbumId;
	private int numTrack = 0;

	private Resources mResources;
	private String mUnknownAlbum;
	private String mUnknown;

	private BitmapDrawable mDefaultAlbumIcon;

	TextView tvAlbumName;
	TextView tvAlbumTracks;
	TextView tvAlbumReleaseDate;
	ImageView ivAlbumArt;
	ImageView ivPlayNow;
	ImageView ivFlyingPlayIndicator;

	private AlbumQueryHandler mAlbumQueryHandler;
	private AlbumDetailActivity mActivity;
	DisplayImageOptions mOptions;
	// Data
	ArrayList<AuroraListItem> tracks = new ArrayList<AuroraListItem>();
	AuroraListView mListView;
	AuroraTrackListAdapter mAdapter;
	AuroraListItem selectedTrack;
	private int selectedNumAlbum = 0;
	private View ivPlayAll;
	private TextView tvAlbumTrack;
	ObjectAnimator aima;
	private int temp_albumindex;
	private MyHandler mHandler;
	private int[] flystartPoint = new int[2];
	private int[] flyendPoint = new int[2];
	private boolean isStop;
	private List<String> mPathsXml = new ArrayList<String>();
	private static final String IMAGE_CACHE_DIR = "AuroraAlbum";

	@SuppressLint("HandlerLeak")
	private class MyHandler extends Handler {
		final int play_all = 1;
		final int play_one = 2;

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case play_all:
				MusicUtils.playAll(mActivity, tracks, 0, 2);
				AuroraMusicUtil.setCurrentPlaylist(mActivity, -1);// add by
																	// chenhl
																	// 20140825
				break;
			case play_one:
				int pos = msg.arg1;
				MusicUtils.playAll(mActivity, tracks, pos, 0);
				AuroraMusicUtil.setCurrentPlaylist(mActivity, -1);// add by
																	// chenhl
																	// 20140825
				break;

			default:
				break;
			}
		}

		public void playAll() {
			removeMessages(play_all);
			sendEmptyMessage(play_all);
		}

		public void playOne() {
			removeMessages(play_one);
			sendEmptyMessage(play_one);
		}
	}

	// private int mDeleteCount;

	class AlbumQueryHandler extends AsyncQueryHandler {

		AlbumQueryHandler(ContentResolver res) {
			super(res);
		}

		@Override
		protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
			// Log.i("@@@", "query complete");
			if (cursor != null) {
				init(cursor);
				if (AlbumContent.ITEMS != null && AlbumContent.ITEMS.size() > 1 && temp_albumindex < AlbumContent.ITEMS.size()) {
					getQueryCursor(mAlbumQueryHandler, null);
				}
			}

		}
	}

	private DialogUtil.OnAddPlaylistSuccessListener mAddPlaylistSuccessListener = new OnAddPlaylistSuccessListener() {

		@Override
		public void OnAddPlaylistSuccess() {
			quitEditMode();
		}
	};
	protected boolean isShowAnimator = false;

	/**
	 * Mandatory empty constructor for the fragment manager to instantiate the
	 * fragment (e.g. upon screen orientation changes).
	 */
	public AlbumDetailFragment() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (getArguments().containsKey(Globals.KEY_ALBUM_ITEM)) {
			mItem = (AuroraAlbum) getArguments().getParcelable(Globals.KEY_ALBUM_ITEM);
			mAlbumId = String.valueOf(mItem.getAlbumId());
		} else if (AlbumContent.ITEMS != null && AlbumContent.ITEMS.size() == 1) {
			mItem = AlbumContent.ITEMS.get(0);
			mAlbumId = String.valueOf(mItem.getAlbumId());
		} else {
			mItem = null;
			mAlbumId = null;
		}
		initImageCacheParams();
		mPathsXml = AuroraMusicUtil.doParseXml(getActivity(), "paths.xml");
		if (getArguments().containsKey(Globals.KEY_ARTIST_ID)) {
			mArtistId = getArguments().getString(Globals.KEY_ARTIST_ID);
		}
		mHandler = new MyHandler();
		mAlbumQueryHandler = new AlbumQueryHandler(getActivity().getContentResolver());
		temp_albumindex = 0;
		getQueryCursor(mAlbumQueryHandler, null);
		if (mItem == null) {
			mAdapter = new AuroraTrackListAdapter(mActivity, tracks, AlbumContent.ITEMS);
		} else {
			mAdapter = new AuroraTrackListAdapter(mActivity, tracks);
		}

		mResources = getActivity().getResources();
		mUnknownAlbum = mResources.getString(R.string.unknown_album_name);
		mUnknown = mResources.getString(R.string.unknown_time);

		Bitmap b = BitmapFactory.decodeResource(mResources, R.drawable.album_art_default);
		mDefaultAlbumIcon = new BitmapDrawable(mResources, b);
		mDefaultAlbumIcon.setFilterBitmap(false);
		mDefaultAlbumIcon.setDither(false);
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		if (activity instanceof AlbumDetailActivity) {
			mActivity = (AlbumDetailActivity) activity;
		} else {
			throw new IllegalStateException("Activity must extend AlbumDetailActivity.");
		}
	}

	private Cursor getQueryCursor(AsyncQueryHandler async, String filter) {

		String[] cols = DataConvertUtil.trackCols;

		Cursor ret = null;

		String albumCondition = "";
		String artistCondition = "";

		if (null != mAlbumId) {
			albumCondition = MediaStore.Audio.Media.ALBUM_ID + "=" + mAlbumId;
		} else if (AlbumContent.ITEMS != null && temp_albumindex < AlbumContent.ITEMS.size()) {
			albumCondition = MediaStore.Audio.Media.ALBUM_ID + "=" + AlbumContent.ITEMS.get(temp_albumindex++).getAlbumId();
		}

		if (null != mArtistId) {
			artistCondition = MediaStore.Audio.Media.ARTIST_ID + "=" + mArtistId;
		}
		Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		StringBuilder where = new StringBuilder();
		where.append(Globals.QUERY_SONG_FILTER + AuroraMusicUtil.getFileString(mActivity));// MediaStore.Audio.Media.IS_MUSIC
																							// +
																							// "=1 AND "
																							// +
		if (!albumCondition.isEmpty()) {
			where.append(" AND " + albumCondition);
		}
		if (!artistCondition.isEmpty()) {
			where.append(" AND " + artistCondition);
		}
		if (async != null) {
			async.startQuery(0, null, uri, cols, where.toString(), null, MediaStore.Audio.Media.ALBUM_KEY + "," + MediaStore.Audio.Media.TRACK);
		} else {
			ret = MusicUtils.query(getActivity(), uri, cols, where.toString(), null, MediaStore.Audio.Media.ALBUM_KEY + "," + MediaStore.Audio.Media.TRACK);
		}
		return ret;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_album_detail, container, false);

		View albumListHeader = rootView.findViewById(R.id.activity_album_list_header);

		ivPlayAll = albumListHeader.findViewById(R.id.iv_playall);
		tvAlbumTrack = (TextView) albumListHeader.findViewById(R.id.tv_tracknumber);

		View albumHeader = rootView.findViewById(R.id.album_item);
		albumHeader.setPadding(36, 0, 36, 0);

		tvAlbumName = (TextView) rootView.findViewById(R.id.album_name);
		tvAlbumTracks = (TextView) rootView.findViewById(R.id.album_numtrack);
		tvAlbumReleaseDate = (TextView) rootView.findViewById(R.id.album_release_date);
		ivAlbumArt = (ImageView) rootView.findViewById(R.id.album_art);
		ivPlayNow = (ImageView) rootView.findViewById(R.id.play_now);
		ivFlyingPlayIndicator = (ImageView) rootView.findViewById(R.id.aurora_flying_indicator);
		if (mItem != null) {
			initAlbumHeader();
			albumListHeader.setVisibility(View.GONE);
		} else {
			albumHeader.setVisibility(View.GONE);
			initAlbumListHeader();
			albumListHeader.setVisibility(View.VISIBLE);
		}
		// mListView = (StickyListHeadersListView)
		// rootView.findViewById(R.id.track_list);
		mListView = (AuroraListView) rootView.findViewById(R.id.track_list);
		mListView.setAdapter(mAdapter);
		mAdapter.setListView(mListView);
		return rootView;
	}

	private void initAlbumListHeader() {

		ivPlayAll.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mHandler.playAll();
			}
		});
		updateHeader(1, tracks.size());

	}

	private void updateHeader(int numAlbums, int numTracks) {
		tvAlbumTrack.setText(getResources().getString(R.string.num_albums_num_songs, numAlbums, numTracks));

	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {

		super.onViewCreated(view, savedInstanceState);

		if (null != mListView) {

			mListView.setVisibility(View.GONE);
			mListView.auroraSetNeedSlideDelete(true);
			mListView.auroraSetSelectorToContentBg(true);

			mListView.auroraSetAuroraBackOnClickListener(new AuroraBackOnClickListener() {

				@Override
				public void auroraPrepareDraged(int arg0) {
				}

				@Override
				public void auroraOnClick(int position) {
					AuroraListItem item = tracks.get(position);
					int title = R.string.delete;
					String message = getResources().getString(R.string.dialog_delete_track_con_message, item.getTitle());
					AuroraAlertDialog mDeleteConDialog = new AuroraAlertDialog.Builder(mActivity, AuroraAlertDialog.THEME_AMIGO_FULLSCREEN).setTitle(title).setMessage(message)
							.setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog, int which) {
									mAdapter.setDeleteAction(true);
									mListView.auroraDeleteSelectedItemAnim();
									mListView.auroraSetRubbishBack();
								}

							}).create();
					mDeleteConDialog.show();

				}

				@Override
				public void auroraDragedUnSuccess(int arg0) {
				}

				@Override
				public void auroraDragedSuccess(int arg0) {
				}
			});

			mListView.auroraSetDeleteItemListener(new AuroraDeleteItemListener() {

				@Override
				public void auroraDeleteItem(View v, int position) {
					AuroraListItem track = tracks.get(position);
					long trackid = track.getSongId();
					AlbumContent.deleteItem(track.getAlbumId());
					long[] list = { trackid };
					MusicUtils.deleteTracks(mActivity, list);
					tracks.remove(position);
					isNoContent();
					// mAdapter.notifyDataSetChanged();
					mAdapter.deleteNotify(position);// add by chnehl 20140825
					updateAlbumNumTracks(--numTrack);
					updateHeader(mAdapter.getHeaderCount(), numTrack);
				}
			});

			// 长按事件
			mListView.setOnItemLongClickListener(new OnItemLongClickListener() {

				@Override
				public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
					if (!mAdapter.isEditMode()) {

						enterEditMode();
						mAdapter.setNeedin(position);
						mActivity.changeMenuState();
						return true;
					}
					return false;
				}

			});

			mListView.setOnItemClickListener(this);
			// 以下设置list各种属性
		}
	}

	void initAlbumHeader() {
		String name = mItem.getAlbumName();
		numTrack = mItem.getTrackNumber();
		boolean unknown = name == null || name.equals(MediaStore.UNKNOWN_STRING);
		if (unknown) {
			name = mUnknownAlbum;
		}
		tvAlbumName.setText(name);
		tvAlbumTracks.setText(mResources.getString(R.string.num_songs_of_album, numTrack));

		String releaseDate = mItem.getReleaseDate();
		if (releaseDate == null || releaseDate.equals(MediaStore.UNKNOWN_STRING)) {
			releaseDate = mUnknown;
			tvAlbumReleaseDate.setVisibility(View.GONE);
		} else {
			tvAlbumReleaseDate.setVisibility(View.VISIBLE);
		}

		tvAlbumReleaseDate.setText(mResources.getString(R.string.release_date_of_album, releaseDate));

		String art = mItem.getAlbumArt();
		final long aid = mItem.getAlbumId();
		if (unknown || art == null || art.length() == 0) {
			ivAlbumArt.setImageDrawable(null);
		} else {
			Drawable d = MusicUtils.getCachedArtwork(getActivity(), aid, mDefaultAlbumIcon);
			ivAlbumArt.setImageDrawable(d);
			ivAlbumArt.setScaleType(ScaleType.CENTER_CROP);
		}

		ivPlayNow.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				mHandler.playAll();
			}
		});
	}

	void updateAlbumNumTracks(int numTracks) {
		tvAlbumTracks.setText(mResources.getString(R.string.num_songs_of_album, numTracks));
	}

	private void init(Cursor cursor) {
		if (tracks == null) {
			tracks = new ArrayList<AuroraListItem>();
		}
		ArrayList<AuroraListItem> items = DataConvertUtil.ConvertToTrack(cursor, mPathsXml, getActivity().getApplication());
		if (temp_albumindex != 0) {
			if (items != null) {
				tracks.addAll(items);
			}

		} else {
			tracks = items;
		}

		numTrack = tracks == null ? 0 : tracks.size();
		if ((temp_albumindex == 0 || temp_albumindex == AlbumContent.ITEMS.size()) && numTrack <= 0) {
			isNoContent();
			return;
		}
		refreshAlbum();
		mAdapter.setDataList(tracks);
		if (mItem == null) {
			updateHeader(mAdapter.getHeaderCount(), numTrack);
		} else {
			updateAlbumNumTracks(numTrack);
		}
		mAdapter.setCurrentTrackId(MusicUtils.getCurrentAudioId());
		updatePlayingPosition();
		mAdapter.notifyDataSetChanged();
		mListView.setVisibility(View.VISIBLE);

	}

	@Override
	public void onPause() {
		mListView.auroraOnPause();
		AuroraMusicUtil.clearflyWindown();
		isStop = true;
		super.onPause();
	}

	@Override
	public void onResume() {
		mListView.auroraOnResume();
		isStop = false;
		super.onResume();
	}

	public void enterEditMode() {
		if (!mActivity.getAuroraActionBar().auroraIsEntryEditModeAnimRunning()) {
			mActivity.getAuroraActionBar().setShowBottomBarMenu(true);
			mActivity.getAuroraActionBar().showActionBarDashBoard();
		}
		ivPlayAll.setEnabled(false);
		ivPlayNow.setVisibility(View.GONE);
		mListView.auroraSetNeedSlideDelete(false);
		mAdapter.setEditMode(true);
	}

	public void quitEditMode() {
		if (!mActivity.getAuroraActionBar().auroraIsExitEditModeAnimRunning()) {
			mActivity.getAuroraActionBar().setShowBottomBarMenu(false);
			mActivity.getAuroraActionBar().showActionBarDashBoard();
		}
		((TextView) mActivity.btn_selectAll).setText(mActivity.selectAll);
		ivPlayAll.setEnabled(true);
		ivPlayNow.setVisibility(View.VISIBLE);
		getAdapter().setEditMode(false);
		getAdapter().notifyDataSetChanged();
		new Handler().postDelayed(new Runnable() {

			@Override
			public void run() {
				mActivity.setPlayAnimation();
			}
		}, 500);
		// mAuroraHandler.sendEmptyMessageDelayed(MSG_SHOW_ANIMATION, 500);
		mListView.auroraSetNeedSlideDelete(true);
	}

	public AuroraTrackListAdapter getAdapter() {
		return mAdapter;
	}

	public AuroraListView getAuroraListView() {
		return mListView;
	}

	public void updateAlbumCursor() {
		getQueryCursor(mAlbumQueryHandler, null);
	}

	public ArrayList<Long> getCheckedIds() {
		selectedNumAlbum = 0;

		ArrayList<Long> checkedIds = new ArrayList<Long>();
		int numTrack = mAdapter.getCount();
		for (int index = 0; index < numTrack; index++) {
			Log.e("liumx", "index:" + index + "---" + mAdapter.getCheckedArrayValue(index));
			if (mAdapter.getCheckedArrayValue(index)) {
				selectedNumAlbum++;
				long selecteTrackId = mAdapter.getItem(index).getSongId();
				Log.e("liumx", "position:" + index + "--------selecteTrackId :" + "-----" + selecteTrackId);
				checkedIds.add(selecteTrackId);
			}
		}
		return checkedIds;
	}

	public boolean deleteAlbums() {
		AuroraAlertDialog mDeleteDialog = new AuroraAlertDialog.Builder(mActivity, AuroraAlertDialog.THEME_AMIGO_FULLSCREEN).setTitle(R.string.delete)
				.setMessage(getResources().getString(R.string.deleteMessage, mAdapter.getCheckedCount())).setNegativeButton(android.R.string.cancel, null)
				.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						quitEditMode();
						numTrack = numTrack - selectedNumAlbum;
						updateAlbumNumTracks(numTrack);
						realRemoveAlbums();
						// isNoContent();
						mAdapter.notifyDataSetChanged();
					}
				}).create();
		mDeleteDialog.show();
		return true;
	}

	// 实质批量删除
	private void realRemoveAlbums() {
		int count = getCheckedIds().size();
		long[] list = new long[count];
		for (int i = 0; i < count; i++) {
			list[i] = getCheckedIds().get(i);
		}
		MusicUtils.deleteTracks(mActivity, list);
		reloadData();
	}

	public boolean addToPlaylist() {
		ArrayList<Long> idList = getCheckedIds();
		ArrayList<String> list = new ArrayList<String>();
		for (int i = 0; i < idList.size(); i++) {
			list.add(String.valueOf(idList.get(i)));
		}
		DialogUtil.showAddDialog(mActivity, list, mAddPlaylistSuccessListener);
		return true;
	}

	public void isNoContent() {
		mActivity.setResult(Globals.RESULT_CODE_MODIFY);
		if (tracks == null || tracks.isEmpty()) {
			Log.e("liumx", "--------------tracks is empty-----------");
			mActivity.finish();
		}
	}

	@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
		Object ob = mListView.getAdapter().getItem(position);
		if (ob instanceof AuroraListItem) {

			// AuroraListItem track = (AuroraListItem) ob;
			if (!mAdapter.isEditMode()) {
				startPlayAnimation(position);
			} else {
				if (mListView.getChildAt(position) != null) {
					AuroraCheckBox checkBox = (AuroraCheckBox) mListView.getChildAt(position).findViewById(com.aurora.R.id.aurora_list_left_checkbox);
					if (null != checkBox) {
						if (checkBox.isChecked()) {
							checkBox.auroraSetChecked(false, true);
							mAdapter.setCheckedArrayValue(position, false);
							mActivity.changeMenuState();
						} else {
							checkBox.auroraSetChecked(true, true);
							mAdapter.setCheckedArrayValue(position, true);
							mActivity.changeMenuState();
						}
					}
				}
			}

		}
	}

	public void updatePlayingPosition() {
		long currentAudioId = MusicUtils.getCurrentAudioId();
		if (currentAudioId >= 0 && mAdapter != null && tracks != null) {
			if (mArtistId != null && Long.parseLong(mArtistId) == MusicUtils.getCurrentArtistId()) {
				for (int i = 0; i < tracks.size(); i++) {
					if (tracks.get(i).getSongId() == currentAudioId) {
						mAdapter.setCurrentTrackId(MusicUtils.getCurrentAudioId());
						mAdapter.setSelected(i);
						mListView.invalidateViews();
					}
				}

			} else {
				mAdapter.setSelected(-1);
				mListView.invalidateViews();
			}
		}
	}

	public void reloadData() {
		Log.e("liumx", "reloadData()-----------------------");
		// AlbumContent.initData();
		temp_albumindex = 0;
		if (tracks != null) {
			tracks.clear();
		} else {
			tracks = new ArrayList<AuroraListItem>();
		}
		getQueryCursor(mAlbumQueryHandler, null);
	}

	public void startPlayAnimation(final int clickingPosition) {
		if (aima != null && aima.isStarted()) {
			mAdapter.setSelected(clickingPosition);
			aima.end();
		}

		final int targetY, sourceY;// 都是相对于列表父组件上边缘的距离。
		int distance = 0; // 移动距离
		final int mIndicatorHeight = mActivity.getResources().getDimensionPixelSize(R.dimen.aurora_track_item_height);
		int currentPosition = mAdapter.getCurrentPlayPosition();
		View arg1 = mListView.getChildAt(clickingPosition - mListView.getFirstVisiblePosition());
		if (arg1 == null) {
			mAdapter.setSelected(clickingPosition);
			mListView.invalidateViews();
			AuroraMusicUtil.setCurrentPlaylist(mActivity, -1);// add by chenhl
																// 20140825
			MusicUtils.playAll(mActivity, tracks, clickingPosition, 0);
			return;
		}
		targetY = arg1.getBottom();
		// Log.e(TAG, "targetView.Height():" + arg1.getHeight() + " 下边缘 y坐标:"
		// + targetY);

		if (currentPosition < 0) {
			// 无动画
			mAdapter.setSelected(clickingPosition);
			mListView.invalidateViews();
			AuroraMusicUtil.setCurrentPlaylist(mActivity, -1);// add by chenhl
																// 20140825
			MusicUtils.playAll(mActivity, tracks, clickingPosition, 0);
			arg1.getLocationInWindow(flystartPoint);

			if (mAdapter.getCount() > 1 && mAdapter.getCount() <= 3 && (AlbumDetailActivity.albumCount > 1))
				flystartPoint[1] += mActivity.getResources().getDimensionPixelSize(R.dimen.aurora_album_cover_bg_height)
						+ mActivity.getResources().getDimensionPixelSize(R.dimen.aurora_online_margintop);
			startFly();
			return;
		} else if (currentPosition < mListView.getFirstVisiblePosition()) {
			// 从最上面飞进来
			ivFlyingPlayIndicator.setY(-mIndicatorHeight);
			distance = targetY - mIndicatorHeight;
		} else if (currentPosition > mListView.getLastVisiblePosition()) {
			// 从最下面飞进来
			ivFlyingPlayIndicator.setY(mListView.getHeight());
			distance = mListView.getHeight() - targetY + mIndicatorHeight;
		} else {
			// 起始与终止位置皆可见
			View view = mListView.getChildAt(currentPosition - mListView.getFirstVisiblePosition());
			sourceY = view.getBottom();
			// Log.e(TAG, "startView.Height():" + view.getHeight() + " 下边缘 y坐标:"
			// + sourceY);
			ivFlyingPlayIndicator.setY(sourceY - mIndicatorHeight);
			distance = Math.abs(sourceY - targetY);
		}

		aima = ObjectAnimator.ofFloat(ivFlyingPlayIndicator, "y", targetY - mIndicatorHeight);
		aima.addListener(new AnimatorListener() {

			@Override
			public void onAnimationStart(Animator arg0) {
				ivFlyingPlayIndicator.setVisibility(View.VISIBLE);
				mAdapter.setSelected(-1);
				mListView.invalidateViews();
				isShowAnimator = true;

			}

			@Override
			public void onAnimationRepeat(Animator arg0) {

			}

			@Override
			public void onAnimationEnd(Animator arg0) {

				isShowAnimator = false;
				new Handler().post(new Runnable() {

					@Override
					public void run() {
						mAdapter.setSelected(clickingPosition);
						mListView.invalidateViews();
						ivFlyingPlayIndicator.setVisibility(View.GONE);
						MusicUtils.playAll(mActivity, tracks, clickingPosition, 0);
						AuroraMusicUtil.setCurrentPlaylist(mActivity, -1);// add
																			// by
																			// chenhl
																			// 20140825
					}
				});
				if (mListView != null) {
					mListView.postDelayed(new Runnable() {

						@Override
						public void run() {
							// TODO Auto-generated method stub
							ivFlyingPlayIndicator.getLocationInWindow(flystartPoint);
							startFly();
						}
					}, 10);
				}

			}

			@Override
			public void onAnimationCancel(Animator arg0) {

			}
		});

		if (distance < 300) {
			aima.setDuration(150);
		} else {
			aima.setDuration(200);
		}

		aima.start();
	}

	// private AuroraProgressDialog mProgressDialog;
	// class DeleteTask extends AsyncTask<String, String, String> {
	//
	// long[] tracklist;
	//
	// public DeleteTask(long[] list) {
	// tracklist = list;
	// }
	//
	// @Override
	// protected void onPreExecute() {
	// // TODO Auto-generated method stub
	// mProgressDialog.setMax(tracklist.length);
	// mProgressDialog
	// .setProgressStyle(AuroraProgressDialog.STYLE_HORIZONTAL);
	// if (mProgressDialog != null && mProgressDialog.isShowing())
	// mProgressDialog.dismiss();
	// // mProgressDialog.setCancelable(false);
	// mProgressDialog.show();
	// super.onPreExecute();
	// }
	//
	// @Override
	// protected String doInBackground(String... params) {
	// // TODO Auto-generated method stub
	// MusicUtils.deleteTracks(mActivity, tracklist,
	// mActivity);
	// return null;
	// }
	//
	// protected void onPostExecute(String result) {
	// if (mProgressDialog != null && mProgressDialog.isShowing()) {
	// mProgressDialog.dismiss();
	// }
	// String message = getResources().getQuantityString(
	// R.plurals.NNNtracksdeleted, tracklist.length,
	// tracklist.length);
	//
	// Toast.makeText(mActivity, message,
	// Toast.LENGTH_SHORT).show();
	// for (int j = mArrayList.size() - 1; j >= 0; j--) {
	// if (mAdapter.isItemChecked(j)) {
	// mArrayList.remove(j);
	// mAdapter.setItemChecked(j, false);
	// }
	// }
	// mAdapter.notifyDataSetChanged();
	// if (tracks.size() == 0) {
	// actionBar_play.clearAnimation();
	// actionBar_play
	// .setBackgroundResource(R.drawable.aurora_left_bar_clicked);
	// showNavtitle(true);
	// } else {
	// showNavtitle(false);
	// }
	// showDeleteAnimation();
	// exitEidtMode();
	// };
	//
	// }
	//
	// private DeleteTask mDeleteTask;
	//
	// @Override
	// public void OnDeleteFileSuccess() {
	// mDeleteCount++;
	// mProgressDialog.setProgress(mDeleteCount);
	// }
	private void startFly() {
		if (isStop)
			return;
		flyendPoint = mActivity.getFlyendPoint();
		if (flyendPoint[1] <= 0) {
			return;
		}
		AuroraMusicUtil.startFly(mActivity, flystartPoint[0], flyendPoint[0], flystartPoint[1], flyendPoint[1], false);
	}

	// add by tangjie 2014/09/10
	private void refreshAlbum() {
		if (mItem != null && (AlbumDetailActivity.albumCount == 1)) {
			final long aid = mItem.getAlbumId();
			if (MusicUtils.hasArtwork(getActivity(), aid)) {
				Drawable d = MusicUtils.getCachedArtwork(getActivity(), aid, mDefaultAlbumIcon);
				ivAlbumArt.setImageDrawable(d);
				ivAlbumArt.setScaleType(ScaleType.CENTER_CROP);
			} else {
				// mImageResizer.loadImage(tracks.get(0), ivAlbumArt, 2);
				ImageLoader.getInstance().displayImage(tracks.get(0).getAlbumImgUri(), tracks.get(0).getSongId(), ivAlbumArt, mOptions, null, null);
			}
		}
	}

	private void initImageCacheParams() {
		mOptions = new DisplayImageOptions.Builder().showImageOnLoading(R.drawable.default_music_icon2).showImageForEmptyUri(R.drawable.default_music_icon2)
				.showImageOnFail(R.drawable.default_music_icon2).cacheInMemory(true).cacheOnDisk(true).considerExifParams(true).displayer(new SimpleBitmapDisplayer()).build();
	}

	// add end
}
