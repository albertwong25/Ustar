package com.hkust.ustar.verticalscrollview;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

public class CenterLockVerticalScrollView extends ScrollView {
	Context context;
	int prevIndex = 0;
	
	public CenterLockVerticalScrollView(Context context, AttributeSet attrs) {
		 super(context, attrs);
		 this.context = context;
		 this.setSmoothScrollingEnabled(true);
	}
	
	public void setAdapter(Context context, CustomListAdapter mAdapter) {
		 try {
		     fillViewWithAdapter(mAdapter);
		 } catch (ZeroChildException e) {
		     e.printStackTrace();
		 }
	}
	
	private void fillViewWithAdapter(CustomListAdapter mAdapter) throws ZeroChildException {
		 if (getChildCount() != 0) {
			 // There is at least one item in the list
			 if (mAdapter == null)
			     return;
			 
			 ViewGroup parent = (ViewGroup) getChildAt(0);
			 parent.removeAllViews();
			
			 for (int i = 0; i < mAdapter.getCount(); i++) {
			     parent.addView(mAdapter.getView(i, null, parent));
			 }
		 }
	}
	
	public void setCenter(int index) {
		if(getChildCount() != 0) {
			 ViewGroup parent = (ViewGroup) getChildAt(0);
			 
			 View preView = parent.getChildAt(prevIndex);
			 if(preView != null)
				 preView.setBackgroundColor(Color.parseColor("#00000000"));
			
			 View view = parent.getChildAt(index);
			 view.setBackgroundColor(Color.parseColor("#80F45D5D"));
			
			 int screenHeight = ((Activity) context).getWindowManager()
			         .getDefaultDisplay().getHeight();
			
			 int scrollY = (view.getTop() - (screenHeight / 2))
			         + view.getHeight();
			 this.smoothScrollTo(0, scrollY);
			 prevIndex = index;
		}
	}
	
	public View getItem(int index) {
		ViewGroup parent = (ViewGroup) getChildAt(0);
		View view = parent.getChildAt(index);
		return view;
	}
}