package com.hkust.ustar;
import android.app.Activity;
import android.app.Dialog;
import android.content.*;
import android.view.GestureDetector;
import android.view.Window;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.widget.TableLayout;
import android.view.View;
import android.view.View.OnTouchListener;
import android.os.Bundle;

import com.hkust.ustar.verticalscrollview.*;

public class TestDialog extends Dialog{
	
	private Activity myAct;
	private CenterLockVerticalScrollView parentCenterLockVerticalScrollView;
	private int parentMCurrIndex;
	
	public TestDialog(Activity a, CenterLockVerticalScrollView centerLockVerticalScrollView, int mCurrIndex, int style) {
	    super(a, style);
	    // TODO Auto-generated constructor stub
	    this.myAct = a;
	    parentCenterLockVerticalScrollView = centerLockVerticalScrollView;
	    parentMCurrIndex = mCurrIndex;
	    setContentView(R.layout.custom_dialog);
	    TableLayout tl = (TableLayout) this.findViewById(R.id.tableLayouttest);
	    tl.setOnTouchListener(new OnSwipeTouchListener(myAct, this));
	    //requestWindowFeature(Window.FEATURE_NO_TITLE);
	}
	
	protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	}
    
    public class OnSwipeTouchListener implements OnTouchListener {

        private final GestureDetector gestureDetector;
        Dialog myDialog;

        public OnSwipeTouchListener (Context ctx, Dialog d){
            gestureDetector = new GestureDetector(ctx, new GestureListener());
            myDialog = d;
        }

        private final class GestureListener extends SimpleOnGestureListener {

            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;
            
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                boolean result = false;
                try {
                    float diffY = e2.getY() - e1.getY();
                    float diffX = e2.getX() - e1.getX();
                    if (Math.abs(diffX) > Math.abs(diffY)) {
                        if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                            if (diffX > 0) {
                                onSwipeRight();
                            } else {
                                onSwipeLeft();
                            }
                        }
                        result = true;
                    } 
                    /*
                    else if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                            if (diffY > 0) {
                                onSwipeBottom();
                            } else {
                                onSwipeTop();
                            }
                        }
                        result = true;
					*/
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
                return result;
            }
        }

        public void onSwipeRight() {
        }

        public void onSwipeLeft() {
        	parentCenterLockVerticalScrollView.getItem(parentMCurrIndex + 1).performClick();
        	myDialog.dismiss();
        }

        public void onSwipeTop() {
        }

        public void onSwipeBottom() {
        }

		@Override
		public boolean onTouch(View v, MotionEvent event) {
		    return gestureDetector.onTouchEvent(event);
		}
    }   
}
