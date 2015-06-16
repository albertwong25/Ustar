package com.hkust.ustar;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.hkust.ustar.R;
import com.hkust.ustar.database.DatabaseHelper;
import com.metaio.sdk.jni.LLACoordinate;

public class TouchImageViewActivity extends Activity {

	private static final String TAG = TouchImageViewActivity.class.getSimpleName();
	private SQLiteDatabase mDatabase;
	private TouchImageView mMapTouchImageView;
    private Bitmap mMapBitmap;
    private Canvas mMapCanvas;
    private Paint mPaint;
    private String mPath;
    private int currFloor;
    private int mCurrIndex;
    private LLACoordinate mNextTargetLocation;
    private double mCurrentX;
    private double mCurrentY;
    private double mPercentageTraveled;
    private double maxLatitude;
    private double minLatitude;
    private double maxLongitude;
    private double minLongitude;
    private double maxMapX;
    private double maxMapY;
    private float userIconY;
    private float userIconX;
    private float destinationIconY;
    private float destinationIconX;
    
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // fetch the extras
        mPath = getIntent().getExtras().getString("my_path");
        currFloor = getIntent().getExtras().getInt("curr_floor");
        
        mCurrIndex = getIntent().getExtras().getInt("curr_position");
        mPercentageTraveled = getIntent().getExtras().getDouble("percentage_traveled");
        mNextTargetLocation = new LLACoordinate();
        mNextTargetLocation.setLatitude(getIntent().getExtras().getDouble("next_latitude"));
        mNextTargetLocation.setLongitude(getIntent().getExtras().getDouble("next_longitude"));
        
        setContentView(R.layout.image_view);
        mMapTouchImageView = (TouchImageView) findViewById(R.id.map_image);
        
	    // initiate database handler
	    try {
			DatabaseHelper mDatabaseHelper = DatabaseHelper.getInstance(this);
			mDatabase = mDatabaseHelper.getDatabase();
		} catch (IOException e2) {
			e2.printStackTrace();
		}
        
		initialize();
        
		// initialize paint to draw red path
        mPaint = new Paint();
    	mPaint.setAntiAlias(true);
        mPaint.setStrokeWidth(3f);
        mPaint.setColor(Color.RED);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeJoin(Paint.Join.MITER);
        
        String[] node_array = mPath.split(",");
		String[] travelled_node_array = new String[mCurrIndex+1];
		String[] remaining_node_array = new String[node_array.length-mCurrIndex];
		for(int i=0;i<node_array.length;i++) {
        	if(i<=mCurrIndex) {
        		travelled_node_array[i] = node_array[i];
        	}
        	if(i>=mCurrIndex) {
        		remaining_node_array[i-mCurrIndex] = node_array[i];
        	}
        }
		
		String temp_path = "";
		int count = 0;
		for(int i=0; i<travelled_node_array.length; i++)
		{
			Cursor c = mDatabase.rawQuery("SELECT * FROM Node WHERE _id = " + travelled_node_array[i], null);
			c.moveToFirst();
			if ( currFloor == c.getInt(c.getColumnIndex("floor")) )
			{
				temp_path += travelled_node_array[i] + ",";
				count++;
			}
			c.close();
		}
		if (count != 0) {
			String[] new_travelled_node_array = new String[count];
			if (temp_path.contains(","))
				new_travelled_node_array = temp_path.split(",");
			drawPath(new_travelled_node_array,false);
		}
		
		mPaint.setStrokeWidth(2f);
		mPaint.setPathEffect(new DashPathEffect(new float[] { 4, 2, 4, 2 }, 0));
		
		temp_path = "";
		count = 0;
		for(int i=0; i<remaining_node_array.length; i++)
		{
			Cursor c = mDatabase.rawQuery("SELECT * FROM Node WHERE _id = " + remaining_node_array[i], null);
			c.moveToFirst();
			if ( currFloor == c.getInt(c.getColumnIndex("floor")) )
			{
				temp_path += remaining_node_array[i] + ",";
				count++;
			}
			c.close();
		}
		if (count != 0) {
			String[] new_remaining_node_array = new String[count];
			if (temp_path.contains(","))
				new_remaining_node_array = temp_path.split(",");
			drawPath(new_remaining_node_array,true);
		}

		mMapTouchImageView.setImageBitmap(mMapBitmap);
		
		// perform automatic zoom on the source after 1 second
		Handler handler = new Handler();
		handler.postDelayed(new Runnable(){
		@Override
		      public void run(){
				mMapTouchImageView.performZoom(calculateViewSize(userIconX, true), calculateViewSize(userIconY, false));
			}
		}, 1000);
    }
    
    private void initialize() {
        InputStream filepath;
		try {
			filepath = getAssets().open("images/hkust_map" + currFloor + ".png");
			mMapBitmap = BitmapFactory.decodeStream(filepath);
			mMapBitmap = mMapBitmap.copy(Bitmap.Config.ARGB_8888, true);
			maxMapX = mMapBitmap.getWidth();
			maxMapY = mMapBitmap.getHeight();
            mMapCanvas = new Canvas(mMapBitmap);
		} catch (IOException e) {
			Log.e(TAG, "Framing rect not available", e);
		}
    }
    
    private void drawPath(String[] node_array, boolean remaining) {
    	int numOfLines = node_array.length;
    	if(numOfLines > 0) {
    		// retrieve the first node from the path
    		Cursor node_cursor = mDatabase.rawQuery("SELECT * FROM Node WHERE _id=" + node_array[0], null);
    		node_cursor.moveToFirst();
    		double startX = node_cursor.getDouble(node_cursor.getColumnIndex("x_index"));
    		double startY = node_cursor.getDouble(node_cursor.getColumnIndex("y_index"));
    		node_cursor.close();
	    	
	    	if(remaining) {
    			startX = mCurrentX;
    			startY = mCurrentY;
    		}
	    	
	    	double endX = 0;
	    	double endY = 0;

	    	for(int i=1; i<numOfLines; i++) {
				// retrieve the next node from the path
				node_cursor = mDatabase.rawQuery("SELECT * FROM Node WHERE _id=" + node_array[i], null);
				node_cursor.moveToFirst();
		    	endX = node_cursor.getDouble(node_cursor.getColumnIndex("x_index"));
		    	endY = node_cursor.getDouble(node_cursor.getColumnIndex("y_index"));
		    	node_cursor.close();
		    	
		    	// draw line with start and end coordinates
		    	drawLine(startX, startY, endX, endY);
		    	
		    	startX = endX;
		    	startY = endY;
			}
			
			if(!remaining) {
				// draw line using percentage traveled
				node_cursor = mDatabase.rawQuery("SELECT * FROM Node WHERE latitude=" + mNextTargetLocation.getLatitude() + " AND longitude=" + mNextTargetLocation.getLongitude(), null);
				node_cursor.moveToFirst();
				mCurrentX = startX + (node_cursor.getDouble(node_cursor.getColumnIndex("x_index")) - startX) * mPercentageTraveled;
				mCurrentY = startY + (node_cursor.getDouble(node_cursor.getColumnIndex("y_index")) - startY) * mPercentageTraveled;
				node_cursor.close();
		    	
				drawLine(startX, startY, mCurrentX, mCurrentY);
			}
			
			if(remaining) {
				// draw destination icon with the end coordinate
				drawDestinationIcon(endX, endY);
				// draw source icon
				drawUserIcon(mCurrentX, mCurrentY);
			}
    	}
    }
    
    private void drawLine(double startX, double startY, double endX, double endY) {
    	float startXF = (float) startX;
        float startYF = (float) startY;
        float endXF = (float) endX;
        float endYF = (float) endY;
        Log.d("path", "(" + startXF + "," + startYF + ") , (" + endXF + "," + endYF + ")");
    	mMapCanvas.drawLine(startXF, startYF, endXF, endYF, mPaint);
    }
       
    private void drawUserIcon(double x, double y) {
    	// draw user icon on the map
        userIconX = (float) x;
        userIconY = (float) y;
        Bitmap sourceIcon = BitmapFactory.decodeResource(getResources(), R.drawable.user_icon);
        sourceIcon = Bitmap.createScaledBitmap(sourceIcon, 50, 67, false);
        mMapCanvas.drawBitmap(sourceIcon, userIconX - 50/2, userIconY - 67, null);
    }
    
    private void drawDestinationIcon(double x, double y) {
    	// draw destination icon on the map
        destinationIconX = (float) x;
    	destinationIconY = (float) y;
 
        Bitmap destinationIcon = BitmapFactory.decodeResource(getResources(), R.drawable.destination_icon);
        destinationIcon = Bitmap.createScaledBitmap(destinationIcon, 50, 67, false);
        mMapCanvas.drawBitmap(destinationIcon, destinationIconX - 50/2, destinationIconY - 67, null);
    }
    
    private int calculateViewSize(double size, boolean width) {
    	if(width)
    		return (int)((size / maxMapX) * mMapTouchImageView.getWidth());
    	else
    		return (int)((size / maxMapY) * mMapTouchImageView.getHeight());    			
    }
    
    /**
     * For debugging purpose
     */
    private void drawDot() {
    	double latitude;
    	double longitude;
    	for(int i=1;i<88;i++) {
    		// retrieve the first node from the path
    		Cursor node_cursor = mDatabase.rawQuery("SELECT * FROM Node WHERE _id=" + i, null);
    		node_cursor.moveToFirst(); 			
	    	latitude = node_cursor.getDouble(node_cursor.getColumnIndex("latitude"));
	    	longitude = node_cursor.getDouble(node_cursor.getColumnIndex("longitude"));
	    	// draw from starting point to ending point
	        float startY = (float) (maxMapY-((longitude - minLongitude)/(maxLongitude - minLongitude)) * maxMapY);
	        float startX = (float) (maxMapX-((latitude - minLatitude)/(maxLatitude - minLatitude)) * maxMapX);
	    	mMapCanvas.drawCircle(startX,startY, 3, mPaint);
	    	node_cursor.close();
    	}
    }
}