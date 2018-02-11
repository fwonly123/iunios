package com.android.keyguard.view;

import com.android.keyguard.R;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.util.AttributeSet;
import android.util.Log;
import android.os.Handler;
import android.database.ContentObserver;
import android.provider.Settings;
import android.text.format.DateFormat;


import java.lang.ref.WeakReference;
import java.text.DateFormatSymbols;
import java.util.Calendar;


public class ClockImageView extends ImageView {

	public static final int[] TIME_KEY_WHITE = { R.drawable.time_key0, R.drawable.time_key1, R.drawable.time_key2,
		 R.drawable.time_key3, R.drawable.time_key4, R.drawable.time_key5, R.drawable.time_key6, 
		 R.drawable.time_key7, R.drawable.time_key8, R.drawable.time_key9, R.drawable.time_key_colon};
	
	public static final int[] TIME_KEY_BLACK = { R.drawable.time_key0_black, R.drawable.time_key1_black, R.drawable.time_key2_black,
		 R.drawable.time_key3_black, R.drawable.time_key4_black, R.drawable.time_key5_black, R.drawable.time_key6_black, 
		 R.drawable.time_key7_black, R.drawable.time_key8_black, R.drawable.time_key9_black, R.drawable.time_key_colon_black};
	
	private int[] TIME_KEY_RES = new int[11];
	private final static String M12 = "hh:mm";
	private final static String M24 = "kk:mm";

	private Context mContext;
	private String mFormat;
    private Calendar mCalendar;
	private final Handler mHandler = new Handler();
	private ContentObserver mFormatChangeObserver;
	private BroadcastReceiver mIntentReceiver;
	private int mAttached = 0; // for debugging - tells us whether attach/detach is unbalanced
	
    public ClockImageView(Context context) {
        super(context);
		mContext = context;
        initView();
    }

    public ClockImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
		mContext = context;
        initView();
    }

    public ClockImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
		mContext = context;
        initView();
    }

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();
		//mTimeView = (TextView) findViewById(R.id.clock_text);
		mCalendar = Calendar.getInstance();
		//setDateFormat();
		//updateTime();
	}

    private void initView() {
        setTimeImage(0);
    } 

    private static class TimeChangedReceiver extends BroadcastReceiver {
        private WeakReference<ClockImageView> mClock;
        private Context mContext;

        public TimeChangedReceiver(ClockImageView clock) {
            Log.v("baisha1", "ClockView TimeChangedReceiver");
            mClock = new WeakReference<ClockImageView>(clock);
            mContext = clock.getContext();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v("baisha1", "ClockView onReceiver");
            // Post a runnable to avoid blocking the broadcast.
            final boolean timezoneChanged =
                    intent.getAction().equals(Intent.ACTION_TIMEZONE_CHANGED);
            final ClockImageView clock = mClock.get();
            if (clock != null) {
                clock.mHandler.post(new Runnable() {
                    public void run() {
                        if (timezoneChanged) {
                            clock.mCalendar = Calendar.getInstance();
                        }
                        //clock.updateTime();
                    }
                });
            } else {
                try {
                    mContext.unregisterReceiver(this);
                } catch (RuntimeException e) {
                    // Shouldn't happen
                }
            }
        }
    };

/*    private static class FormatChangeObserver extends ContentObserver {
        private WeakReference<ClockImageView> mClock;
        private Context mContext;
        public FormatChangeObserver(ClockImageView clock) {
            super(new Handler());
            mClock = new WeakReference<ClockImageView>(clock);
            mContext = clock.getContext();
            Log.v("baisha1", "ClockView FormatChangeObserver");
        }
        @Override
        public void onChange(boolean selfChange) {
            Log.v("baisha1", "ClockView FormatChangeObserver, onChange");
            ClockImageView digitalClock = mClock.get();
            if (digitalClock != null) {
                //digitalClock.setDateFormat();
                digitalClock.updateTime();
            } else {
                try {
                    mContext.getContentResolver().unregisterContentObserver(this);
                } catch (RuntimeException e) {
                    // Shouldn't happen
                }
            }
        }
    }
*/
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        Log.v("baisha1", "ClockView onAttachedToWindow()");
        mAttached++;

        /* monitor time ticks, time changed, timezone */
        if (mIntentReceiver == null) {
            mIntentReceiver = new TimeChangedReceiver(this);
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            mContext.registerReceiver(mIntentReceiver, filter);
        }

        /* monitor 12/24-hour display preference */
 /*       if (mFormatChangeObserver == null) {
            mFormatChangeObserver = new FormatChangeObserver(this);
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.CONTENT_URI, true, mFormatChangeObserver);
        }
*/
        updateTime();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        Log.v("baisha1", "ClockView onDetachedFromWindow()");
        mAttached--;

        if (mIntentReceiver != null) {
            mContext.unregisterReceiver(mIntentReceiver);
        }
/*        if (mFormatChangeObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(
                    mFormatChangeObserver);
        }

        mFormatChangeObserver = null;
*/      mIntentReceiver = null;
    }

	public void updateTime() {
		Log.v("baisha1", "ClockView updateTime 2");
        setTimeImage(0);
	}

    private int timeToResource(int nowTime) {
		if (nowTime >= 48) {
			nowTime -= 48;
		}
		
		if (nowTime < 0 || nowTime > 9) {
			return TIME_KEY_RES[0];
		}
		return TIME_KEY_RES[nowTime];
    	/*switch (nowTime) {
			case 0:
	     		return R.drawable.time_key0;
			case 1:
				return R.drawable.time_key1;
			case 2:
				return R.drawable.time_key2;
			case 3:
				return R.drawable.time_key3;
			case 4:
				return R.drawable.time_key4;
			case 5:
				return R.drawable.time_key5;
			case 6:
				return R.drawable.time_key6;
			case 7:
				return R.drawable.time_key7;
			case 8:
				return R.drawable.time_key8;
			case 9:
				return R.drawable.time_key9;
			default:
				return R.drawable.time_key0;
		}*/		
    }
	
    private void setTimeImage(int timeValue) {

        mCalendar = Calendar.getInstance();
		//Log.v("baisha1", "hour = " + calendar.get(10) + " mintue = " + calendar.get(12)); //calendar.get(22)
		int hour = mCalendar.get(Calendar.HOUR);
        if (android.text.format.DateFormat.is24HourFormat(getContext())) {
            hour = mCalendar.get(Calendar.HOUR_OF_DAY);
        } else {
            if (hour == 0) {
				hour += 12;
            }
        }
		
        int min = mCalendar.get(Calendar.MINUTE);
		Log.v("baisha1", "hour = " + hour + " min = " + min);

        int image1Id = TIME_KEY_RES[0];
		int image2Id = TIME_KEY_RES[0];
		if (hour < 10) {
			image1Id = TIME_KEY_RES[0];
		} else {
            image1Id = timeToResource(hour/10);
		}
		image2Id = timeToResource(hour%10);

		int image3Id = TIME_KEY_RES[10];
        int image4Id = TIME_KEY_RES[0];
		int image5Id = TIME_KEY_RES[0];
		if (min < 10) {
			image4Id = TIME_KEY_RES[0];
		} else {
            image4Id = timeToResource(min/10);
		}
		image5Id = timeToResource(min%10);		

        if (getId() == R.id.clock_image_1) {
            setImageResource(image1Id);
        } else if (getId() == R.id.clock_image_2) {
            setImageResource(image2Id);
        } else if (getId() == R.id.clock_image_3) {
            setImageResource(image3Id);
        } else if (getId() == R.id.clock_image_4) {
            setImageResource(image4Id);
        } else if (getId() == R.id.clock_image_5) {
            setImageResource(image5Id);
        } else {
            setImageResource(TIME_KEY_RES[0]);
        }    
    }
	public void setBlackStyle(boolean bool) {
		if (bool) {
			TIME_KEY_RES = TIME_KEY_BLACK;
		}else {
			TIME_KEY_RES = TIME_KEY_WHITE;
		}
	}
}
