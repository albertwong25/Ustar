//package com.hkust.ustar;
//
//import android.content.Intent;
//import android.os.Bundle;
//import android.util.Log;
//
//import org.opencv.android.OpenCVLoader; 
//import org.opencv.android.BaseLoaderCallback;
//import org.opencv.android.LoaderCallbackInterface;
//
//import edu.washington.cs.touchfreelibrary.sensors.CameraGestureSensor;
//
//public class GestureActivity extends LocationBased implements CameraGestureSensor.Listener {
//	private static final String TAG = GestureActivity.class.getSimpleName();
//	
//	// Senses for left, right, up, and down gestures. Calls the appropriate 
//	// functions when the motions are recognized.
//	private CameraGestureSensor mGestureSensor;
//	
//	// True if the openCV library has been initiated. 
//	// False otherwise
//	private boolean mOpenCVInitiated;
//    
//	static {
//	    if (!OpenCVLoader.initDebug()) {
//	        // Handle initialization error
//	    }
//	}
//	
//	private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
//	    @Override
//	    public void onManagerConnected(int status) {
//	        switch (status) {
//	            case LoaderCallbackInterface.SUCCESS: {
//	                Log.i(TAG, "OpenCV loaded successfully");
//					mOpenCVInitiated = true; 
//					CameraGestureSensor.loadLibrary();
//					mGestureSensor.start();
//	            } break;
//	            default:
//	            {
//	                super.onManagerConnected(status);
//	            } break;
//	        }
//	    }
//	};
//	
//    // Called when the activity is first created.	
//	@Override
//	public void onCreate(Bundle savedInstanceState) {
//		super.onCreate(savedInstanceState);
//        
//        mGestureSensor = new CameraGestureSensor(this);
//		mGestureSensor.addGestureListener(this);
//		mGestureSensor.enableClickByColor(true);
//		mOpenCVInitiated = false;
//		
//		mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
//	}
//	
//	// Up gesture detected.
//	@Override
//	public void onGestureUp(CameraGestureSensor caller, long gestureLength) {
//		runOnUiThread(new Runnable() {
//			@Override
//			public void run() {   	  
//		        mSensorManager.unregisterListener(mStepDetector);
//		        
//				Intent intent = new Intent(getApplicationContext(), TouchImageViewActivity.class);
//				intent.putExtra("my_path", mPath);
//				if(mSourceNID != mDestinationNID) {
//					intent.putExtra("curr_position", mNodeConversionTable.get(mCurrIndex));
//					intent.putExtra("percentage_traveled", (1 - ((double)mStepsRemaining / mStepsToNextPoint)));
//					intent.putExtra("next_latitude", mNextTargetLocation.getLatitude());
//					intent.putExtra("next_longitude", mNextTargetLocation.getLongitude());
//				}
//				else {
//					intent.putExtra("curr_position", 0);			
//					intent.putExtra("percentage_traveled", 0);
//					intent.putExtra("next_latitude", mCurrentLatitude);
//					intent.putExtra("next_longitude", mCurrentLongitude);
//				}
//
//				startActivity(intent);
//			} 
//		});  
//	}
//	
//	 // Downwards gesture detected.
//	@Override
//	public void onGestureDown(CameraGestureSensor caller, long gestureLength) {
//		runOnUiThread(new Runnable() {
//			@Override
//			public void run() {  
//		        mSensorManager.unregisterListener(mStepDetector);
//		        
//				Intent intent = new Intent(getApplicationContext(), TouchImageViewActivity.class);
//				intent.putExtra("my_path", mPath);
//				if(mSourceNID != mDestinationNID) {
//					intent.putExtra("curr_position", mNodeConversionTable.get(mCurrIndex));
//					intent.putExtra("percentage_traveled", (1 - ((double)mStepsRemaining / mStepsToNextPoint)));
//					intent.putExtra("next_latitude", mNextTargetLocation.getLatitude());
//					intent.putExtra("next_longitude", mNextTargetLocation.getLongitude());
//				}
//				else {
//					intent.putExtra("curr_position", 0);			
//					intent.putExtra("percentage_traveled", 0);
//					intent.putExtra("next_latitude", mCurrentLatitude);
//					intent.putExtra("next_longitude", mCurrentLongitude);
//				}
//
//				startActivity(intent);
//			} 
//		});  
//	}
//	
//	// Leftwards gesture detected.
//	@Override	
//	public void onGestureLeft(CameraGestureSensor caller, long gestureLength) { 
//		// No action performed 
//	}
//
//	 // Rightwards gesture detected.
//	@Override
//	public void onGestureRight(CameraGestureSensor caller, long gestureLength) {  
//		// No action performed 
//	}
//  
//	// Called when the activity is resumed. The gesture detector is initialized.
//	@Override
//	public void onResume() {
//		super.onResume();
//		if(!mOpenCVInitiated)
//			return; 
//		mGestureSensor.start();
//	}
//	  
//	// Called when the activity is paused. The gesture detector is stopped
//	// so that the camera is no longer working to recognize gestures.
//	@Override
//	public void onPause() {
//		super.onPause();
//		if(!mOpenCVInitiated)
//			return; 
//		mGestureSensor.stop();
//	}
//}
