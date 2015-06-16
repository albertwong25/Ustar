package com.hkust.ustar.camera;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SoundEffectConstants;
import android.widget.ImageView;

/**
 * A button designed to be used for the on-screen shutter button.
 */
public class ShutterButton extends ImageView {
	/**
	 * A callback to be invoked when a ShutterButton's pressed state changes.
	 */
	public interface OnShutterButtonListener {
		/**
		 * Called when a ShutterButton has been pressed.
		 */
		void onShutterButtonFocus(ShutterButton b, boolean pressed);

		void onShutterButtonClick(ShutterButton b);
	}

	private OnShutterButtonListener mListener;
	private boolean mOldPressed;

	public ShutterButton(Context context) {
		super (context);
	}

	public ShutterButton(Context context, AttributeSet attrs) {
		super (context, attrs);
	}

	public ShutterButton(Context context, AttributeSet attrs,
			int defStyle) {
		super (context, attrs, defStyle);
	}

	public void setOnShutterButtonListener(OnShutterButtonListener listener) {
		mListener = listener;
	}

	/**
	 * Hook into the drawable state changing to get changes to isPressed.
	 */
	 @Override
	 protected void drawableStateChanged() {
		 super .drawableStateChanged();
		 final boolean pressed = isPressed();
		 if (pressed != mOldPressed) {
			 if (!pressed) {
				 post(new Runnable() {
					 @Override
					public void run() {
						 callShutterButtonFocus(pressed);
					 }
				 });
			 } else {
				 callShutterButtonFocus(pressed);
			 }
			 mOldPressed = pressed;
		 }
	 }

	 private void callShutterButtonFocus(boolean pressed) {
		 if (mListener != null) {
			 mListener.onShutterButtonFocus(this , pressed);
		 }
	 }

	 @Override
	 public boolean performClick() {
		 boolean result = super.performClick();
		 playSoundEffect(SoundEffectConstants.CLICK);
		 if (mListener != null) {
			 mListener.onShutterButtonClick(this);
		 }
		 return result;
	 }
}