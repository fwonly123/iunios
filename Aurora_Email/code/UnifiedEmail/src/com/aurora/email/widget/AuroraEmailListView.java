package com.aurora.email.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View.MeasureSpec;
import android.widget.ListView;

public class AuroraEmailListView extends ListView {

	public AuroraEmailListView(Context context) {
		this(context, null);

	}

	public AuroraEmailListView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);

	}

	public AuroraEmailListView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

	}

	/**
	 * 设置不滚动
	 */
	public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int expandSpec = MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE >> 2,
				MeasureSpec.AT_MOST);
		super.onMeasure(widthMeasureSpec, expandSpec);

	}
}