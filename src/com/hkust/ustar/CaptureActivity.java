package com.hkust.ustar;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.AdapterView.OnItemSelectedListener;

import com.googlecode.tesseract.android.TessBaseAPI;
import com.hkust.ustar.BuildConfig;
import com.hkust.ustar.R;
import com.hkust.ustar.camera.CameraManager;
import com.hkust.ustar.camera.ShutterButton;
import com.hkust.ustar.database.DatabaseHelper;
import com.hkust.ustar.language.LanguageCodeHelper;
import com.metaio.sdk.MetaioDebug;
import com.metaio.tools.io.AssetsManager;

public final class CaptureActivity extends Activity implements SurfaceHolder.Callback, 
  ShutterButton.OnShutterButtonListener {

  private static final String TAG = CaptureActivity.class.getSimpleName();
  
  // These constants are overridden by values defined in preferences.xml.
  
  /** ISO 639-3 language code indicating the default recognition language. */
  public static final String DEFAULT_SOURCE_LANGUAGE_CODE = "eng";
  
  /** The default OCR engine to use. */
  public static final String DEFAULT_OCR_ENGINE_MODE = "Tesseract";
  
  /** The default page segmentation mode to use. */
  public static final String DEFAULT_PAGE_SEGMENTATION_MODE = "Auto";
  
  /** Whether to use autofocus by default. */
  public static final boolean DEFAULT_TOGGLE_AUTO_FOCUS = true;
  
  /** Whether to initially disable continuous-picture and continuous-video focus modes. */
  public static final boolean DEFAULT_DISABLE_CONTINUOUS_FOCUS = true;
  
  /** Whether to beep by default when the shutter button is pressed. */
  public static final boolean DEFAULT_TOGGLE_BEEP = false;
  
  /** Whether to initially show a looping, real-time OCR display. */
  public static final boolean DEFAULT_TOGGLE_CONTINUOUS = true;
  
  /** Whether to initially reverse the image returned by the camera. */
  public static final boolean DEFAULT_TOGGLE_REVERSED_IMAGE = false;
  
  /** Whether the light should be initially activated by default. */
  public static final boolean DEFAULT_TOGGLE_LIGHT = false;
  
  /** Flag to display the real-time recognition results at the top of the scanning screen. */
  private static final boolean CONTINUOUS_DISPLAY_RECOGNIZED_TEXT = true;
  
  /** Flag to display recognition-related statistics on the scanning screen. */
  private static final boolean CONTINUOUS_DISPLAY_METADATA = true;
  
  /** Flag to enable display of the on-screen shutter button. */
  private static final boolean DISPLAY_SHUTTER_BUTTON = true;
  
  /** Languages for which Cube data is available. */
  static final String[] CUBE_SUPPORTED_LANGUAGES = { 
    "eng", // English
  };

  /** Languages that require Cube, and cannot run using Tesseract. */
  private static final String[] CUBE_REQUIRED_LANGUAGES = {
  };
  
  /** Resource to use for data file downloads. */
  static final String DOWNLOAD_BASE = "http://tesseract-ocr.googlecode.com/files/";
  
  /** Download filename for orientation and script detection (OSD) data. */
  static final String OSD_FILENAME = "tesseract-ocr-3.01.osd.tar";
  
  /** Destination filename for orientation and script detection (OSD) data. */
  static final String OSD_FILENAME_BASE = "osd.traineddata";
  
  /** Minimum mean confidence score necessary to not reject single-shot OCR result. Currently unused. */
  static final int MINIMUM_MEAN_CONFIDENCE = 0; // 0 means don't reject any scored results
  
  // Context menu
  private static final int SETTINGS_ID = Menu.FIRST;
  
  // Options menu, for copy to clipboard
  private static final int OPTIONS_COPY_RECOGNIZED_TEXT_ID = Menu.FIRST;
  private static final int OPTIONS_SHARE_RECOGNIZED_TEXT_ID = Menu.FIRST + 1;

  private CameraManager cameraManager;
  private CaptureActivityHandler handler;
  private ViewfinderView viewfinderView;
  private SurfaceView surfaceView;
  private SurfaceHolder surfaceHolder;
  private TextView statusViewBottom;
  private TextView statusViewTop;
  private TextView ocrResultView;
  private View cameraButtonView;
  private View resultView;
  private View tutorialView;
  private View progressView;
  private ToggleButton flashToggleButton;
  private Spinner startingFloor;
  private Spinner startingSpinner;
  private TextView startingTextView;
  private Spinner destinationFloor;
  private Spinner destinationSpinner;
  private TextView destinationTextView;
  private Button confirmButton;
  private OcrResult lastResult;
  private Bitmap lastBitmap;
  private boolean hasSurface;
  private BeepManager beepManager;
  private TessBaseAPI baseApi; // Java interface for the Tesseract OCR engine
  private String sourceLanguageCodeOcr; // ISO 639-3 language code
  private String sourceLanguageReadable; // Language name, for example, "English"
  private int pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_AUTO_OSD;
  private int ocrEngineMode = TessBaseAPI.OEM_TESSERACT_ONLY;
  private ShutterButton shutterButton;
  private boolean isContinuousModeActive; // Whether we are doing OCR in continuous mode
  private SharedPreferences prefs;
  private OnSharedPreferenceChangeListener listener;
  private ProgressDialog dialog; // for initOcr - language download & unzip
  private ProgressDialog indeterminateDialog; // also for initOcr - init OCR engine
  private boolean isEngineReady;
  private static boolean isFirstLaunch; // True if this is the first time the app is being run
  private SQLiteDatabase mDatabase;
  private Boolean isPaused;
  private int mSourceNID;
  private int mDestinationNID;
  private double mSourceLatitude;
  private double mSourceLongitude;
  private HashMap <String,String> mLTHashMap = new HashMap <String,String>();
  private boolean PathFound;
  
  Handler getHandler() {
    return handler;
  }

  TessBaseAPI getBaseApi() {
    return baseApi;
  }
  
  CameraManager getCameraManager() {
    return cameraManager;
  }
  
  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
  	
    checkFirstLaunch();
    
    if (isFirstLaunch) {
      setDefaultPreferences();
    }
    
    // database handler
    try {
		DatabaseHelper mDatabaseHelper = DatabaseHelper.getInstance(this);
		mDatabase = mDatabaseHelper.getDatabase();
	} catch (IOException e2) {
		e2.printStackTrace();
	}
    
    Window window = getWindow();
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    setContentView(R.layout.capture);
    viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
    cameraButtonView = findViewById(R.id.camera_button_view);
    flashToggleButton = (ToggleButton) findViewById(R.id.flash_toggle);
    resultView = findViewById(R.id.result_view);
    tutorialView = findViewById(R.id.tutorial_view);
    
    // populate starting and destination spinner
    startingFloor =  (Spinner) findViewById(R.id.starting_floor);
    startingSpinner = (Spinner) findViewById(R.id.starting_spinner);
    destinationFloor =  (Spinner) findViewById(R.id.destination_floor);
    destinationSpinner = (Spinner) findViewById(R.id.destination_spinner);
    try {
		loadSpinnerData();
	} catch (IOException e1) {
		e1.printStackTrace();
	}
    
    statusViewBottom = (TextView) findViewById(R.id.status_view_bottom);
    registerForContextMenu(statusViewBottom);
    
    statusViewTop = (TextView) findViewById(R.id.status_view_top);
    registerForContextMenu(statusViewTop);
    
    startingTextView = (TextView) findViewById(R.id.starting_textview);
    
    destinationTextView = (TextView) findViewById(R.id.destination_textview);
    
    confirmButton = (Button) findViewById(R.id.confirm_button2);
    
    handler = null;
    lastResult = null;
    hasSurface = false;
    beepManager = new BeepManager(this);
    
    // Camera shutter button
    if (DISPLAY_SHUTTER_BUTTON) {
      shutterButton = (ShutterButton) findViewById(R.id.shutter_button);
      shutterButton.setOnShutterButtonListener(this);
    }
   
    ocrResultView = (TextView) findViewById(R.id.ocr_result_text_view);
    registerForContextMenu(ocrResultView);
    
    progressView = findViewById(R.id.indeterminate_progress_indicator_view);

    cameraManager = new CameraManager(getApplication());
    
    // flash toggle button listener
    flashToggleButton.setOnCheckedChangeListener(new OnCheckedChangeListener(){
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			if(isChecked)
	        {
				cameraManager.setFlashlight(true);
	        }
	        else
	        {
	        	cameraManager.setFlashlight(false);            
	        }
		}
    });
    
    viewfinderView.setCameraManager(cameraManager);
    
    // Set listener to change the size of the viewfinder rectangle.
    viewfinderView.setOnTouchListener(new View.OnTouchListener() {
      int lastX = -1;
      int lastY = -1;

      @Override
      public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
          lastX = -1;
          lastY = -1;
          return true;
        case MotionEvent.ACTION_MOVE:
          int currentX = (int) event.getX();
          int currentY = (int) event.getY();

          try {
            Rect rect = cameraManager.getFramingRect();

            final int BUFFER = 50;
            final int BIG_BUFFER = 60;
            if (lastX >= 0) {
              // Adjust the size of the viewfinder rectangle. Check if the touch event occurs in the corner areas first, because the regions overlap.
              if (((currentX >= rect.left - BIG_BUFFER && currentX <= rect.left + BIG_BUFFER) || (lastX >= rect.left - BIG_BUFFER && lastX <= rect.left + BIG_BUFFER))
                  && ((currentY <= rect.top + BIG_BUFFER && currentY >= rect.top - BIG_BUFFER) || (lastY <= rect.top + BIG_BUFFER && lastY >= rect.top - BIG_BUFFER))) {
                // Top left corner: adjust both top and left sides
                cameraManager.adjustFramingRect( 2 * (lastX - currentX), 2 * (lastY - currentY));
                viewfinderView.removeResultText();
              } else if (((currentX >= rect.right - BIG_BUFFER && currentX <= rect.right + BIG_BUFFER) || (lastX >= rect.right - BIG_BUFFER && lastX <= rect.right + BIG_BUFFER)) 
                  && ((currentY <= rect.top + BIG_BUFFER && currentY >= rect.top - BIG_BUFFER) || (lastY <= rect.top + BIG_BUFFER && lastY >= rect.top - BIG_BUFFER))) {
                // Top right corner: adjust both top and right sides
                cameraManager.adjustFramingRect( 2 * (currentX - lastX), 2 * (lastY - currentY));
                viewfinderView.removeResultText();
              } else if (((currentX >= rect.left - BIG_BUFFER && currentX <= rect.left + BIG_BUFFER) || (lastX >= rect.left - BIG_BUFFER && lastX <= rect.left + BIG_BUFFER))
                  && ((currentY <= rect.bottom + BIG_BUFFER && currentY >= rect.bottom - BIG_BUFFER) || (lastY <= rect.bottom + BIG_BUFFER && lastY >= rect.bottom - BIG_BUFFER))) {
                // Bottom left corner: adjust both bottom and left sides
                cameraManager.adjustFramingRect(2 * (lastX - currentX), 2 * (currentY - lastY));
                viewfinderView.removeResultText();
              } else if (((currentX >= rect.right - BIG_BUFFER && currentX <= rect.right + BIG_BUFFER) || (lastX >= rect.right - BIG_BUFFER && lastX <= rect.right + BIG_BUFFER)) 
                  && ((currentY <= rect.bottom + BIG_BUFFER && currentY >= rect.bottom - BIG_BUFFER) || (lastY <= rect.bottom + BIG_BUFFER && lastY >= rect.bottom - BIG_BUFFER))) {
                // Bottom right corner: adjust both bottom and right sides
                cameraManager.adjustFramingRect(2 * (currentX - lastX), 2 * (currentY - lastY));
                viewfinderView.removeResultText();
              } else if (((currentX >= rect.left - BUFFER && currentX <= rect.left + BUFFER) || (lastX >= rect.left - BUFFER && lastX <= rect.left + BUFFER))
                  && ((currentY <= rect.bottom && currentY >= rect.top) || (lastY <= rect.bottom && lastY >= rect.top))) {
                // Adjusting left side: event falls within BUFFER pixels of left side, and between top and bottom side limits
                cameraManager.adjustFramingRect(2 * (lastX - currentX), 0);
                viewfinderView.removeResultText();
              } else if (((currentX >= rect.right - BUFFER && currentX <= rect.right + BUFFER) || (lastX >= rect.right - BUFFER && lastX <= rect.right + BUFFER))
                  && ((currentY <= rect.bottom && currentY >= rect.top) || (lastY <= rect.bottom && lastY >= rect.top))) {
                // Adjusting right side: event falls within BUFFER pixels of right side, and between top and bottom side limits
                cameraManager.adjustFramingRect(2 * (currentX - lastX), 0);
                viewfinderView.removeResultText();
              } else if (((currentY <= rect.top + BUFFER && currentY >= rect.top - BUFFER) || (lastY <= rect.top + BUFFER && lastY >= rect.top - BUFFER))
                  && ((currentX <= rect.right && currentX >= rect.left) || (lastX <= rect.right && lastX >= rect.left))) {
                // Adjusting top side: event falls within BUFFER pixels of top side, and between left and right side limits
                cameraManager.adjustFramingRect(0, 2 * (lastY - currentY));
                viewfinderView.removeResultText();
              } else if (((currentY <= rect.bottom + BUFFER && currentY >= rect.bottom - BUFFER) || (lastY <= rect.bottom + BUFFER && lastY >= rect.bottom - BUFFER))
                  && ((currentX <= rect.right && currentX >= rect.left) || (lastX <= rect.right && lastX >= rect.left))) {
                // Adjusting bottom side: event falls within BUFFER pixels of bottom side, and between left and right side limits
                cameraManager.adjustFramingRect(0, 2 * (currentY - lastY));
                viewfinderView.removeResultText();
              }     
            }
          } catch (NullPointerException e) {
            Log.e(TAG, "Framing rect not available", e);
          }
          v.invalidate();
          lastX = currentX;
          lastY = currentY;
          return true;
        case MotionEvent.ACTION_UP:
          lastX = -1;
          lastY = -1;
          return true;
        }
        return false;
      }
    });
    
    isEngineReady = false;
    
    startingFloor.setOnItemSelectedListener(new startingOnItemSelectedListener());
    destinationFloor.setOnItemSelectedListener(new destinationOnItemSelectedListener());
    
//    tutorial(1);
  }
  
  @Override
  protected void onResume() {
    super.onResume();   
    resetStatusView();
   
    String previousSourceLanguageCodeOcr = sourceLanguageCodeOcr;
    int previousOcrEngineMode = ocrEngineMode;
    
    retrievePreferences();
    
    // Set up the camera preview surface.
    surfaceView = (SurfaceView) findViewById(R.id.preview_view);
    surfaceHolder = surfaceView.getHolder();
    if (!hasSurface) {
      surfaceHolder.addCallback(this);
      surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }
    
    // Comment out the following block to test non-OCR functions without an SD card
    
    // Do OCR engine initialization, if necessary
    boolean doNewInit = (baseApi == null) || !sourceLanguageCodeOcr.equals(previousSourceLanguageCodeOcr) || 
        ocrEngineMode != previousOcrEngineMode;
    if (doNewInit) {
      // Initialize the OCR engine
      File storageDirectory = getStorageDirectory();
      if (storageDirectory != null) {
        initOcrEngine(storageDirectory, sourceLanguageCodeOcr, sourceLanguageReadable);
      }
    } else {
      // We already have the engine initialized, so just start the camera.
      resumeOCR();
    }
  }
  
  /** 
   * Method to start or restart recognition after the OCR engine has been initialized.
   */
  void resumeOCR() {
    Log.d(TAG, "resumeOCR()");
    
    // Called when Tesseract has already been successfully initialized
    isEngineReady = true;
    
    isPaused = false;

    if (handler != null) {
      handler.resetState();
    }
    if (baseApi != null) {
      baseApi.setPageSegMode(pageSegmentationMode);
      baseApi.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "");
      baseApi.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789ABCDE");
    }

    if (hasSurface) {
      // The activity was paused but not stopped, so the surface still exists.
      initCamera(surfaceHolder);
    }
  }
  
  // Shutter button is pressed in continuous mode.
  void onShutterButtonPressContinuous() {
    isPaused = true;
    handler.stop();  
    beepManager.playBeepSoundAndVibrate();
    if (lastResult != null) {
      handleOcrDecode(lastResult);
    } else {
      Toast toast = Toast.makeText(this, "OCR failed. Try again.", Toast.LENGTH_SHORT);
      toast.show();
      resumeContinuousDecoding();
    }
  }

  // Resume recognition in continuous mode.
  @SuppressWarnings("unused")
  void resumeContinuousDecoding() {
    isPaused = false;
    resetStatusView();
    setStatusViewForContinuous();
    DecodeHandler.resetDecodeState();
    handler.resetState();
    if (shutterButton != null && DISPLAY_SHUTTER_BUTTON) {
      shutterButton.setVisibility(View.VISIBLE);
    }
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    Log.d(TAG, "surfaceCreated()");
    
    if (holder == null) {
      Log.e(TAG, "surfaceCreated gave us a null surface");
    }
    
    // Only initialize the camera if the OCR engine is ready to go.
    if (!hasSurface && isEngineReady) {
      Log.d(TAG, "surfaceCreated(): calling initCamera()...");
      initCamera(holder);
    }
    hasSurface = true;
  }
  
  /** Initializes the camera and starts the handler to begin previewing. */
  private void initCamera(SurfaceHolder surfaceHolder) {
    Log.d(TAG, "initCamera()");
    if (surfaceHolder == null) {
      throw new IllegalStateException("No SurfaceHolder provided");
    }
    try {

      // Open and initialize the camera
      cameraManager.openDriver(surfaceHolder);
      
      // Creating the handler starts the preview, which can also throw a RuntimeException.
      handler = new CaptureActivityHandler(this, cameraManager, isContinuousModeActive);
      
    } catch (IOException ioe) {
      showErrorMessage("Error", "Could not initialize camera. Please try restarting device.");
    } catch (RuntimeException e) {
      // Barcode Scanner has seen crashes in the wild of this variety:
      showErrorMessage("Error", "Could not initialize camera. Please try restarting device.");
    }   
  }
  
  @Override
  protected void onPause() {
    if (handler != null) {
      handler.quitSynchronously();
    }
    
    // Stop using the camera, to avoid conflicting with other camera-based apps
    cameraManager.closeDriver();

    if (!hasSurface) {
      SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
      SurfaceHolder surfaceHolder = surfaceView.getHolder();
      surfaceHolder.removeCallback(this);
    }
    super.onPause();
  }

  void stopHandler() {
    if (handler != null) {
      handler.stop();
    }
  }

  @Override
  protected void onDestroy() {
    if (baseApi != null) {
      baseApi.end();
    }
    super.onDestroy();
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
  	  //Ask the user if they want to quit
        new AlertDialog.Builder(this)
        .setIcon(android.R.drawable.ic_dialog_alert)
        .setTitle("Exit")
        .setMessage("Do you want to close Ustar?")
        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //Stop the activity
                CaptureActivity.this.finish();
                System.exit(0);
            }

        })
        .setNegativeButton("No", null)
        .show();
  
    } else if (keyCode == KeyEvent.KEYCODE_CAMERA) {
      if (isContinuousModeActive) {
        onShutterButtonPressContinuous();
      } else {
        handler.hardwareShutterButtonClick();
      }
      return true;
    } else if (keyCode == KeyEvent.KEYCODE_FOCUS) {      
      // Only perform autofocus if user is not holding down the button.
      if (event.getRepeatCount() == 0) {
        cameraManager.requestAutoFocus(500L);
      }
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    menu.add(0, SETTINGS_ID, 0, "Settings").setIcon(android.R.drawable.ic_menu_preferences);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    Intent intent;
    switch (item.getItemId()) {
    case SETTINGS_ID: {
      intent = new Intent().setClass(this, PreferencesActivity.class);
      startActivity(intent);
      break;
    }
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
public void surfaceDestroyed(SurfaceHolder holder) {
    hasSurface = false;
  }

  @Override
public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
  }

  // Sets the necessary language code values for the given OCR language.
  private boolean setSourceLanguage(String languageCode) {
    sourceLanguageCodeOcr = languageCode;
    sourceLanguageReadable = LanguageCodeHelper.getOcrLanguageName(this, languageCode);
    return true;
  }

  // Finds the proper location on the SD card where we can save files.
  private File getStorageDirectory() {
    //Log.d(TAG, "getStorageDirectory(): API level is " + Integer.valueOf(android.os.Build.VERSION.SDK_INT));
    
    String state = null;
    try {
      state = Environment.getExternalStorageState();
    } catch (RuntimeException e) {
      Log.e(TAG, "Is the SD card visible?", e);
      showErrorMessage("Error", "Required external storage (such as an SD card) is unavailable.");
    }
    
    if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {

      // We can read and write the media
      //    	if (Integer.valueOf(android.os.Build.VERSION.SDK_INT) > 7) {
      // For Android 2.2 and above
      
      try {
        return getExternalFilesDir(Environment.MEDIA_MOUNTED);
      } catch (NullPointerException e) {
        // We get an error here if the SD card is visible, but full
        Log.e(TAG, "External storage is unavailable");
        showErrorMessage("Error", "Required external storage (such as an SD card) is full or unavailable.");
      }
      
    } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
    	// We can only read the media
    	Log.e(TAG, "External storage is read-only");
      showErrorMessage("Error", "Required external storage (such as an SD card) is unavailable for data storage.");
    } else {
    	// Something else is wrong. It may be one of many other states, but all we need
      // to know is we can neither read nor write
    	Log.e(TAG, "External storage is unavailable");
    	showErrorMessage("Error", "Required external storage (such as an SD card) is unavailable or corrupted.");
    }
    return null;
  }

  /**
   * Requests initialization of the OCR engine with the given parameters.
   */
  private void initOcrEngine(File storageRoot, String languageCode, String languageName) {    
    isEngineReady = false;
    
    // Set up the dialog box for the thermometer-style download progress indicator
    if (dialog != null) {
      dialog.dismiss();
    }
    dialog = new ProgressDialog(this);
    
    // If we have a language that only runs using Cube, then set the ocrEngineMode to Cube
    if (ocrEngineMode != TessBaseAPI.OEM_CUBE_ONLY) {
      for (String s : CUBE_REQUIRED_LANGUAGES) {
        if (s.equals(languageCode)) {
          ocrEngineMode = TessBaseAPI.OEM_CUBE_ONLY;
          SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
          prefs.edit().putString(PreferencesActivity.KEY_OCR_ENGINE_MODE, getOcrEngineModeName()).commit();
        }
      }
    }

    // If our language doesn't support Cube, then set the ocrEngineMode to Tesseract
    if (ocrEngineMode != TessBaseAPI.OEM_TESSERACT_ONLY) {
      boolean cubeOk = false;
      for (String s : CUBE_SUPPORTED_LANGUAGES) {
        if (s.equals(languageCode)) {
          cubeOk = true;
        }
      }
      if (!cubeOk) {
        ocrEngineMode = TessBaseAPI.OEM_TESSERACT_ONLY;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putString(PreferencesActivity.KEY_OCR_ENGINE_MODE, getOcrEngineModeName()).commit();
      }
    }
    
    // Display the name of the OCR engine we're initializing in the indeterminate progress dialog box
    indeterminateDialog = new ProgressDialog(this);
    indeterminateDialog.setTitle("Please wait");
    String ocrEngineModeName = getOcrEngineModeName();
    if (ocrEngineModeName.equals("Both")) {
      indeterminateDialog.setMessage("Initializing Cube and Tesseract OCR engines for " + languageName + "...");
    } else {
      indeterminateDialog.setMessage("Initializing " + ocrEngineModeName + " OCR engine for " + languageName + "...");
    }
    indeterminateDialog.setCancelable(false);
    indeterminateDialog.show();
    
    if (handler != null) {
      handler.quitSynchronously();     
    }

    // Disable continuous mode if we're using Cube. This will prevent bad states for devices 
    // with low memory that crash when running OCR with Cube, and prevent unwanted delays.
    if (ocrEngineMode == TessBaseAPI.OEM_CUBE_ONLY || ocrEngineMode == TessBaseAPI.OEM_TESSERACT_CUBE_COMBINED) {
      Log.d(TAG, "Disabling continuous preview");
      isContinuousModeActive = false;
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
      prefs.edit().putBoolean(PreferencesActivity.KEY_CONTINUOUS_PREVIEW, false);
    }
    
    // Start AsyncTask to install language data and init OCR
    baseApi = new TessBaseAPI();
    new OcrInitAsyncTask(this, baseApi, dialog, indeterminateDialog, languageCode, languageName, ocrEngineMode)
      .execute(storageRoot.toString());
  }
  
  /**
   * Displays information relating to the result of OCR.
   */
  boolean handleOcrDecode(OcrResult ocrResult) {
	// Remove all the spaces
	ocrResult.setText(ocrResult.getText().replaceAll("\\s",""));
    lastResult = ocrResult;
    String result = ocrResult.getText();
    
    // Test whether the result is null
    if (result == null || result.equals("")) {
      Toast toast = Toast.makeText(this, "OCR failed. Try again.", Toast.LENGTH_SHORT);
      toast.show();
      return false;
    }
    
	Cursor c = mDatabase.rawQuery("SELECT * FROM Facility WHERE fname = '" + result + "'", null);
	c.moveToFirst();
    int count = c.getCount();
    if(count != 0) {
    	// recognized result is in the database
    	mSourceNID = c.getInt(c.getColumnIndex("nid"));
    	c = mDatabase.rawQuery("SELECT * FROM Node WHERE _id = " + mSourceNID, null);
    	c.moveToFirst();
    	count = c.getCount();
    	if(count != 0) {
    		mSourceLatitude = c.getDouble(c.getColumnIndex("latitude"));
    		mSourceLongitude = c.getDouble(c.getColumnIndex("longitude"));
    	}
    	c.close();
    	// Turn off capture-related UI elements
        shutterButton.setVisibility(View.GONE);
        statusViewBottom.setVisibility(View.GONE);
        statusViewTop.setVisibility(View.GONE);
        cameraButtonView.setVisibility(View.GONE);
        flashToggleButton.setVisibility(View.GONE);
        startingTextView.setVisibility(View.GONE);
        startingFloor.setVisibility(View.GONE);
        startingSpinner.setVisibility(View.GONE);
        destinationTextView.setVisibility(View.GONE);
        destinationFloor.setVisibility(View.GONE);
        destinationSpinner.setVisibility(View.GONE);
        confirmButton.setVisibility(View.GONE);
        viewfinderView.setVisibility(View.GONE);
        resultView.setVisibility(View.VISIBLE);

        ImageView bitmapImageView = (ImageView) findViewById(R.id.image_view);
        lastBitmap = ocrResult.getBitmap();
        if (lastBitmap == null) {
          bitmapImageView.setImageBitmap(BitmapFactory.decodeResource(getResources(),
              R.drawable.ic_launcher));
        } else {
          bitmapImageView.setImageBitmap(lastBitmap);
        }

        // Display the recognized text
        TextView ocrResultTextView = (TextView) findViewById(R.id.ocr_result_text_view);
        ocrResultTextView.setText(result);
        // Crudely scale betweeen 22 and 32 -- bigger font for shorter text
        int scaledSize = Math.max(22, 32 - result.length() / 4);
        ocrResultTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSize);
    	progressView.setVisibility(View.GONE);
    	setProgressBarVisibility(false);
        return true;
    }
    
    // recongnized result is not found in database
	Toast toast = Toast.makeText(this, "Unknown room number. Try again.", Toast.LENGTH_SHORT);
    toast.show();
    resumeContinuousDecoding();
    return false;
  }
  
  /**
   * Displays information relating to the results of a successful real-time OCR request.
   */
  void handleOcrContinuousDecode(OcrResult ocrResult) {
	// Remove all the spaces
	ocrResult.setText(ocrResult.getText().replaceAll("\\s",""));
    lastResult = ocrResult;
    
    // Send an OcrResultText object to the ViewfinderView for text rendering
    viewfinderView.addResultText(new OcrResultText(ocrResult.getText(), 
                                                   ocrResult.getWordConfidences(),
                                                   ocrResult.getMeanConfidence(),
                                                   ocrResult.getBitmapDimensions(),
                                                   ocrResult.getRegionBoundingBoxes(),
                                                   ocrResult.getTextlineBoundingBoxes(),
                                                   ocrResult.getStripBoundingBoxes(),
                                                   ocrResult.getWordBoundingBoxes(),
                                                   ocrResult.getCharacterBoundingBoxes()));

    Integer meanConfidence = ocrResult.getMeanConfidence();
    
    if (CONTINUOUS_DISPLAY_RECOGNIZED_TEXT) {
      // Display the recognized text on the screen
      statusViewTop.setText(ocrResult.getText());
      int scaledSize = Math.max(22, 32 - ocrResult.getText().length() / 4);
      statusViewTop.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSize);
      statusViewTop.setTextColor(Color.parseColor("#F45D5D"));
      statusViewTop.setBackgroundResource(R.color.status_top_text_background);

      statusViewTop.getBackground().setAlpha(meanConfidence * (255 / 100));
    }

    if (CONTINUOUS_DISPLAY_METADATA) {
      // Display recognition-related metadata at the bottom of the screen
      long recognitionTimeRequired = ocrResult.getRecognitionTimeRequired();
      statusViewBottom.setTextSize(14);
      statusViewBottom.setText("OCR Mean confidence: " + 
          meanConfidence.toString() + " - Time required: " + recognitionTimeRequired + " ms");
    }
  }
  
  /**
   * Displays a failure message for failed OCR requests.
   */
  void handleOcrContinuousDecode(OcrResultFailure obj) {
    lastResult = null;
    viewfinderView.removeResultText();
    
    // Reset the text in the recognized text box.
    statusViewTop.setText("");

    if (CONTINUOUS_DISPLAY_METADATA) {
      // Color text delimited by '-' as red.
      statusViewBottom.setTextSize(14);
      CharSequence cs = setSpanBetweenTokens("OCR failed - Time required: " 
          + obj.getTimeRequired() + " ms", "-", new ForegroundColorSpan(0xFFFF0000));
      statusViewBottom.setText(cs);
    }
  }
  
  /**
   * Apply the given CharacterStyle to the span between the tokens.
   */
  private CharSequence setSpanBetweenTokens(CharSequence text, String token,
      CharacterStyle... cs) {
    // Start and end refer to the points where the span will apply
    int tokenLen = token.length();
    int start = text.toString().indexOf(token) + tokenLen;
    int end = text.toString().indexOf(token, start);

    if (start > -1 && end > -1) {
      // Copy the spannable string to a mutable spannable string
      SpannableStringBuilder ssb = new SpannableStringBuilder(text);
      for (CharacterStyle c : cs)
        ssb.setSpan(c, start, end, 0);
      text = ssb;
    }
    return text;
  }
  
  @Override
  public void onCreateContextMenu(ContextMenu menu, View v,
      ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    if (v.equals(ocrResultView)) {
      menu.add(Menu.NONE, OPTIONS_COPY_RECOGNIZED_TEXT_ID, Menu.NONE, "Copy recognized text");
      menu.add(Menu.NONE, OPTIONS_SHARE_RECOGNIZED_TEXT_ID, Menu.NONE, "Share recognized text");
    } 
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    switch (item.getItemId()) {

    case OPTIONS_COPY_RECOGNIZED_TEXT_ID:
        clipboardManager.setText(ocrResultView.getText());
      if (clipboardManager.hasText()) {
        Toast toast = Toast.makeText(this, "Text copied.", Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.BOTTOM, 0, 0);
        toast.show();
      }
      return true;
    case OPTIONS_SHARE_RECOGNIZED_TEXT_ID:
    	Intent shareRecognizedTextIntent = new Intent(android.content.Intent.ACTION_SEND);
    	shareRecognizedTextIntent.setType("text/plain");
    	shareRecognizedTextIntent.putExtra(android.content.Intent.EXTRA_TEXT, ocrResultView.getText());
    	startActivity(Intent.createChooser(shareRecognizedTextIntent, "Share via"));
    	return true;
    default:
      return super.onContextItemSelected(item);
    }
  }

  /**
   * Resets view elements.
   */
  private void resetStatusView() {
    resultView.setVisibility(View.GONE);
    tutorialView.setVisibility(View.GONE);
    if (CONTINUOUS_DISPLAY_METADATA) {
      statusViewBottom.setText("");
      statusViewBottom.setTextSize(14);
      statusViewBottom.setTextColor(getResources().getColor(R.color.status_text));
      statusViewBottom.setVisibility(View.VISIBLE);
    }
    if (CONTINUOUS_DISPLAY_RECOGNIZED_TEXT) {
      statusViewTop.setText("");
      statusViewTop.setTextSize(14);
      statusViewTop.setVisibility(View.VISIBLE);
    }
    viewfinderView.setVisibility(View.VISIBLE);
    cameraButtonView.setVisibility(View.VISIBLE);
    flashToggleButton.setVisibility(View.VISIBLE);
    startingTextView.setVisibility(View.VISIBLE);
    startingFloor.setVisibility(View.VISIBLE);
    startingSpinner.setVisibility(View.VISIBLE);
    destinationTextView.setVisibility(View.VISIBLE);
    destinationFloor.setVisibility(View.VISIBLE);
    destinationSpinner.setVisibility(View.VISIBLE);
    confirmButton.setVisibility(View.VISIBLE);
    if (DISPLAY_SHUTTER_BUTTON) {
      shutterButton.setVisibility(View.VISIBLE);
    }
    lastResult = null;
    viewfinderView.removeResultText();
  }
  
  /**
   * Displays an initial message.
   */
  void setStatusViewForContinuous() {
    viewfinderView.removeResultText();
    if (CONTINUOUS_DISPLAY_METADATA) {
      statusViewBottom.setText("Waiting for OCR...");
    }
  }
  
  void setButtonVisibility(boolean visible) {
    if (shutterButton != null && visible == true && DISPLAY_SHUTTER_BUTTON) {
      shutterButton.setVisibility(View.VISIBLE);
      flashToggleButton.setVisibility(View.VISIBLE);
      startingTextView.setVisibility(View.VISIBLE);
      startingFloor.setVisibility(View.VISIBLE);
      startingSpinner.setVisibility(View.VISIBLE);
      destinationTextView.setVisibility(View.VISIBLE);
      destinationFloor.setVisibility(View.VISIBLE);
      destinationSpinner.setVisibility(View.VISIBLE);
      confirmButton.setVisibility(View.VISIBLE);
      Toast toast = Toast.makeText(this, "Capture a nearby room number.", Toast.LENGTH_LONG);
      toast.show();
    } else if (shutterButton != null) {
      shutterButton.setVisibility(View.GONE);
      flashToggleButton.setVisibility(View.GONE);
      startingTextView.setVisibility(View.VISIBLE);
      startingFloor.setVisibility(View.VISIBLE);
      startingSpinner.setVisibility(View.VISIBLE);
      destinationTextView.setVisibility(View.VISIBLE);
      destinationFloor.setVisibility(View.VISIBLE);
      destinationSpinner.setVisibility(View.VISIBLE);
      confirmButton.setVisibility(View.VISIBLE);
    }
  }
  
  /**
   * Enables/disables the shutter button to prevent double-clicks on the button.
   */
  void setShutterButtonClickable(boolean clickable) {
    shutterButton.setClickable(clickable);
  }

  /** Request the viewfinder to be invalidated. */
  void drawViewfinder() {
    viewfinderView.drawViewfinder();
  }
  
  @Override
  public void onShutterButtonClick(ShutterButton b) {
    if (isContinuousModeActive) {
      onShutterButtonPressContinuous();
    } else {
      if (handler != null) {
        handler.shutterButtonClick();
      }
    }
  }

  @Override
  public void onShutterButtonFocus(ShutterButton b, boolean pressed) {
    requestDelayedAutoFocus();
  }
  
  /**
   * Requests autofocus after a 350ms delay.
   */
  private void requestDelayedAutoFocus() {
    cameraManager.requestAutoFocus(350L);
  }
  
  static boolean getFirstLaunch() {
    return isFirstLaunch;
  }
  
  /**
   * Check android:versionCode from the manifest, and compare it to a value stored as a preference.
   */
  public boolean checkFirstLaunch() {
    try {
      PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
      int currentVersion = info.versionCode;
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
      int lastVersion = prefs.getInt(PreferencesActivity.KEY_HELP_VERSION_SHOWN, 0);
      if (lastVersion == 0) {
        isFirstLaunch = true;
      } else {
        isFirstLaunch = false;
      }
      if (currentVersion > lastVersion) {
        // Record the last version for which we last displayed the What's New (Help) page
        prefs.edit().putInt(PreferencesActivity.KEY_HELP_VERSION_SHOWN, currentVersion).commit();
        return true;
      }
    } catch (PackageManager.NameNotFoundException e) {
      Log.w(TAG, e);
    }
    return false;
  }
  
  /**
   * Returns a string that represents which OCR engine is to be run.
   */
  String getOcrEngineModeName() {
    String ocrEngineModeName = "";
    String[] ocrEngineModes = getResources().getStringArray(R.array.ocrenginemodes);
    if (ocrEngineMode == TessBaseAPI.OEM_TESSERACT_ONLY) {
      ocrEngineModeName = ocrEngineModes[0];
    } else if (ocrEngineMode == TessBaseAPI.OEM_CUBE_ONLY) {
      ocrEngineModeName = ocrEngineModes[1];
    } else if (ocrEngineMode == TessBaseAPI.OEM_TESSERACT_CUBE_COMBINED) {
      ocrEngineModeName = ocrEngineModes[2];
    }
    return ocrEngineModeName;
  }
  
  /**
   * Gets values from shared preferences and sets the corresponding data members in this activity.
   */
  private void retrievePreferences() {
      prefs = PreferenceManager.getDefaultSharedPreferences(this);
      
      // Retrieve from preferences, and set in this Activity, the language preferences
      PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
      setSourceLanguage(prefs.getString(PreferencesActivity.KEY_SOURCE_LANGUAGE_PREFERENCE, CaptureActivity.DEFAULT_SOURCE_LANGUAGE_CODE));

      // Retrieve from preferences, and set in this Activity, the capture mode preference
      if (prefs.getBoolean(PreferencesActivity.KEY_CONTINUOUS_PREVIEW, CaptureActivity.DEFAULT_TOGGLE_CONTINUOUS)) {
        isContinuousModeActive = true;
      } else {
        isContinuousModeActive = false;
      }

      // Retrieve from preferences, and set in this Activity, the page segmentation mode preference
      String[] pageSegmentationModes = getResources().getStringArray(R.array.pagesegmentationmodes);
      String pageSegmentationModeName = prefs.getString(PreferencesActivity.KEY_PAGE_SEGMENTATION_MODE, pageSegmentationModes[0]);
      if (pageSegmentationModeName.equals(pageSegmentationModes[0])) {
        pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_AUTO_OSD;
      } else if (pageSegmentationModeName.equals(pageSegmentationModes[1])) {
        pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_AUTO;
      } else if (pageSegmentationModeName.equals(pageSegmentationModes[2])) {
        pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK;
      } else if (pageSegmentationModeName.equals(pageSegmentationModes[3])) {
        pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_SINGLE_CHAR;
      } else if (pageSegmentationModeName.equals(pageSegmentationModes[4])) {
        pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_SINGLE_COLUMN;
      } else if (pageSegmentationModeName.equals(pageSegmentationModes[5])) {
        pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_SINGLE_LINE;
      } else if (pageSegmentationModeName.equals(pageSegmentationModes[6])) {
        pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_SINGLE_WORD;
      } else if (pageSegmentationModeName.equals(pageSegmentationModes[7])) {
        pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK_VERT_TEXT;
      } else if (pageSegmentationModeName.equals(pageSegmentationModes[8])) {
        pageSegmentationMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT;
      }
      
      // Retrieve from preferences, and set in this Activity, the OCR engine mode
      String[] ocrEngineModes = getResources().getStringArray(R.array.ocrenginemodes);
      String ocrEngineModeName = prefs.getString(PreferencesActivity.KEY_OCR_ENGINE_MODE, ocrEngineModes[0]);
      if (ocrEngineModeName.equals(ocrEngineModes[0])) {
        ocrEngineMode = TessBaseAPI.OEM_TESSERACT_ONLY;
      } else if (ocrEngineModeName.equals(ocrEngineModes[1])) {
        ocrEngineMode = TessBaseAPI.OEM_CUBE_ONLY;
      } else if (ocrEngineModeName.equals(ocrEngineModes[2])) {
        ocrEngineMode = TessBaseAPI.OEM_TESSERACT_CUBE_COMBINED;
      }
      
      prefs.registerOnSharedPreferenceChangeListener(listener);
      
      beepManager.updatePrefs();
  }
  
  /**
   * Sets default values for preferences. (called app is run for the first time)
   */
  private void setDefaultPreferences() {
    prefs = PreferenceManager.getDefaultSharedPreferences(this);

    // Continuous preview
    prefs.edit().putBoolean(PreferencesActivity.KEY_CONTINUOUS_PREVIEW, CaptureActivity.DEFAULT_TOGGLE_CONTINUOUS).commit();

    // Recognition language
    prefs.edit().putString(PreferencesActivity.KEY_SOURCE_LANGUAGE_PREFERENCE, CaptureActivity.DEFAULT_SOURCE_LANGUAGE_CODE).commit();

    // OCR Engine
    prefs.edit().putString(PreferencesActivity.KEY_OCR_ENGINE_MODE, CaptureActivity.DEFAULT_OCR_ENGINE_MODE).commit();

    // Autofocus
    prefs.edit().putBoolean(PreferencesActivity.KEY_AUTO_FOCUS, CaptureActivity.DEFAULT_TOGGLE_AUTO_FOCUS).commit();
    
    // Disable problematic focus modes
    prefs.edit().putBoolean(PreferencesActivity.KEY_DISABLE_CONTINUOUS_FOCUS, CaptureActivity.DEFAULT_DISABLE_CONTINUOUS_FOCUS).commit();
    
    // Beep
    prefs.edit().putBoolean(PreferencesActivity.KEY_PLAY_BEEP, CaptureActivity.DEFAULT_TOGGLE_BEEP).commit();
    
    // Page segmentation mode
    prefs.edit().putString(PreferencesActivity.KEY_PAGE_SEGMENTATION_MODE, CaptureActivity.DEFAULT_PAGE_SEGMENTATION_MODE).commit();

    // Reversed camera image
    prefs.edit().putBoolean(PreferencesActivity.KEY_REVERSE_IMAGE, CaptureActivity.DEFAULT_TOGGLE_REVERSED_IMAGE).commit();

    // Light
    prefs.edit().putBoolean(PreferencesActivity.KEY_TOGGLE_LIGHT, CaptureActivity.DEFAULT_TOGGLE_LIGHT).commit();
  }
  
  void displayProgressDialog() {
    // Set up the indeterminate progress dialog box
    indeterminateDialog = new ProgressDialog(this);
    indeterminateDialog.setTitle("Please wait");        
    String ocrEngineModeName = getOcrEngineModeName();
    if (ocrEngineModeName.equals("Both")) {
      indeterminateDialog.setMessage("Performing OCR using Cube and Tesseract...");
    } else {
      indeterminateDialog.setMessage("Performing OCR using " + ocrEngineModeName + "...");
    }
    indeterminateDialog.setCancelable(false);
    indeterminateDialog.show();
  }
  
  ProgressDialog getProgressDialog() {
    return indeterminateDialog;
  }
  
  /**
   * Displays an error message dialog box to the user on the UI thread.
   */
  void showErrorMessage(String title, String message) {
	  new AlertDialog.Builder(this)
	    .setTitle(title)
	    .setMessage(message)
	    .setOnCancelListener(new FinishListener(this))
	    .setPositiveButton( "Done", new FinishListener(this))
	    .show();
  }
  
  /**
   * Function to load the spinner data from SQLite database
   * */
  private void loadSpinnerData() throws IOException {
	  // populate hash map
	  mLTHashMap.put("1106", "LT-A");
	  mLTHashMap.put("1108", "LT-B");
	  mLTHashMap.put("1110", "LT-C");
	  mLTHashMap.put("1112", "LT-D");
	  mLTHashMap.put("1114", "LT-E");
	  mLTHashMap.put("1406", "LT-F");
	  mLTHashMap.put("1507", "LT-G");
	  mLTHashMap.put("1514", "LT-H");
	  mLTHashMap.put("LT-A", "1106");
	  mLTHashMap.put("LT-B", "1108");
	  mLTHashMap.put("LT-C", "1110");
	  mLTHashMap.put("LT-D", "1112");
	  mLTHashMap.put("LT-E", "1114");
	  mLTHashMap.put("LT-F", "1406");
	  mLTHashMap.put("LT-G", "1507");
	  mLTHashMap.put("LT-H", "1514");
	  
      List<String> mList = new ArrayList<String>();
      Cursor c = mDatabase.rawQuery("SELECT * FROM Facility", null);
      c.moveToFirst();
      int count = c.getCount();
      for (int i=0; i<count; i++) {
    	  String fname = c.getString(c.getColumnIndex("fname")).trim();
    	  int ftype = c.getInt(c.getColumnIndex("ftype"));
    	  
    	  if(ftype == 1 && fname.length() == 4)
    		  mList.add(mLTHashMap.get(fname));
    	  else if(ftype == 2)
    		  mList.add("Room " + fname);
    	  else if(ftype != 4)
    		  mList.add(fname);
    	  
    	  c.moveToNext();
      }
      c.close();
      
      // sort the destinations by name
      Collections.sort(mList);
      
      // Creating adapter for spinner
      ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, R.layout.simple_destination_item, mList);

      // Drop down layout style - list view with radio button
      dataAdapter.setDropDownViewResource(R.layout.simple_destination_item);

      // attaching data adapter to spinner
      startingSpinner.setAdapter(dataAdapter);
      
      // default prompt
      startingSpinner.setPrompt("Select Your Starting point");
      
      // reverse the destinations by name
      List<String> mList2 = new ArrayList<String>();
      mList2.addAll(mList);
      Collections.reverse(mList2);
      
      // Creating adapter for spinner
      dataAdapter = new ArrayAdapter<String>(this, R.layout.simple_destination_item, mList2);

      // Drop down layout style - list view with radio button
      dataAdapter.setDropDownViewResource(R.layout.simple_destination_item);

      // attaching data adapter to spinner
      destinationSpinner.setAdapter(dataAdapter);
      
      // default prompt
      destinationSpinner.setPrompt("Select Your Destination");
      
      List<String> mFloor = new ArrayList<String>();
      for (int i = 1; i <= 7; i++) {
    	  mFloor.add(i + "/F");
      }
      ArrayAdapter<String> floorAdapter = new ArrayAdapter<String>(this, R.layout.simple_destination_item, mFloor);
      floorAdapter.setDropDownViewResource(R.layout.simple_destination_item);
      startingFloor.setAdapter(floorAdapter);
      startingFloor.setPrompt("Select Your Starting floor");
      destinationFloor.setAdapter(floorAdapter);
      destinationFloor.setPrompt("Select Your Destination floor");
  }
  
  public void onBackButtonClick(View v) {
	  onResume();
  }
  
  public void onConfirmButtonClick(View v) {
	  	String destination = destinationSpinner.getSelectedItem().toString().replace("Room ", "");
	  	if(destination.contains("LT-"))
	  		destination = mLTHashMap.get(destination);
	  	
	  	Cursor c = mDatabase.rawQuery("SELECT * FROM Facility WHERE fname = '" + destination + "'", null);
		c.moveToFirst();
		mDestinationNID = c.getInt(c.getColumnIndex("nid"));
		c.close();
		
		indeterminateDialog = ProgressDialog.show(CaptureActivity.this,"Please wait", "Finding the suitable path for you ...",true);
		indeterminateDialog.setCancelable(false);
		  
		  	new Thread(new Runnable(){
	            @Override
	            public void run() {
	            	boolean PathFound = false;
	                try{
							String mPath = "";
							if(mSourceNID != mDestinationNID) {
								// calculate new path
								PathFinder mPathFinder = new PathFinder(CaptureActivity.this);	
								try {
									mPath = mPathFinder.getPathString(mSourceNID, mDestinationNID);
									//mPath = "2099,2100,2420,2418,2089,2091,2419,2092,4385,4195,4209,4474,4475,4476,4214,4215,4477,4478,4226,4479,4235,4236,4480,4241,4242,4481,4257,4258,4259,4261";
								} catch (Exception e) {
									e.printStackTrace();
								} 
								finally
						        {
									if ( mPath == "" )
									{
										Log.i("LLA", "No path between them!");
										Toast toast = Toast.makeText(CaptureActivity.this, "No path found!", Toast.LENGTH_SHORT);
										toast.show();
									}
									else
									{
										PathFound = true;
							    	  	new AssetsExtracter().execute(0);
							    	  	Intent intent = new Intent(getApplicationContext(), LocationBased.class);
							    	  	intent.putExtra("my_destination", mDestinationNID);
							    		intent.putExtra("my_source", mSourceNID);
							    		intent.putExtra("my_source_latitude", mSourceLatitude);
							    		intent.putExtra("my_source_longitude", mSourceLongitude);
							    		intent.putExtra("my_path", mPath);
							    		intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
							    	  	startActivity(intent);
									}
						        }
							}
							else {
								// the source and destination ids are equal
								mPath = String.valueOf(mSourceNID);
							  	new AssetsExtracter().execute(0);
							  	Intent intent = new Intent(getApplicationContext(), LocationBased.class);
							  	intent.putExtra("my_destination", mDestinationNID);
								intent.putExtra("my_source", mSourceNID);
								intent.putExtra("my_source_latitude", mSourceLatitude);
								intent.putExtra("my_source_longitude", mSourceLongitude);
								intent.putExtra("my_path", mPath);
								intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
							  	startActivity(intent);
							}
			
	                }
	                catch(Exception e){
	                    e.printStackTrace();
	                }
	                finally{
	                	indeterminateDialog.dismiss();
	                	if (!PathFound)
	                	{
	                		Toast toast = Toast.makeText(CaptureActivity.this, "No Path between them", Toast.LENGTH_SHORT);
	                	    toast.show();
	                	}
	                		
	                }
	            } 
		  	}).start();
  }
  
  public void onConfirmButton2Click(View v) {
	  	String starting = startingSpinner.getSelectedItem().toString().replace("Room ", "");
	  	if(starting.contains("LT-"))
	  		starting = mLTHashMap.get(starting);
	  	
		
	  	Cursor c = mDatabase.rawQuery("SELECT * FROM Facility WHERE fname = '" + starting + "'", null);
		c.moveToFirst();
		mSourceNID = c.getInt(c.getColumnIndex("nid"));
	    c = mDatabase.rawQuery("SELECT * FROM Node WHERE _id = " + mSourceNID, null);
	    c.moveToFirst();
	    mSourceLatitude = c.getDouble(c.getColumnIndex("latitude"));
	    mSourceLongitude = c.getDouble(c.getColumnIndex("longitude"));
		
	  	String destination = destinationSpinner.getSelectedItem().toString().replace("Room ", "");
	  	if(destination.contains("LT-"))
	  		destination = mLTHashMap.get(destination);
	  	
	  	c = mDatabase.rawQuery("SELECT * FROM Facility WHERE fname = '" + destination + "'", null);
		c.moveToFirst();
		mDestinationNID = c.getInt(c.getColumnIndex("nid"));
		c.close();

	  indeterminateDialog = ProgressDialog.show(CaptureActivity.this,"Please wait", "Finding the suitable path for you ...",true);
	  indeterminateDialog.setCancelable(false);
	  
	  
	  	new Thread(new Runnable(){
            @Override
            public void run() {
            	boolean Found = false;
                try{
						String mPath = "";
						if(mSourceNID != mDestinationNID) {
							// calculate new path
							PathFinder mPathFinder = new PathFinder(CaptureActivity.this);	
							try {
								mPath = mPathFinder.getPathString(mSourceNID, mDestinationNID);
								//mPath = "2099,2100,2420,2418,2089,2091,2419,2092,4385,4195,4209,4474,4475,4476,4214,4215,4477,4478,4226,4479,4235,4236,4480,4241,4242,4481,4257,4258,4259,4261";
							} catch (Exception e) {
								e.printStackTrace();
							} 
							finally
					        {
								if ( mPath == "" )
								{
									Log.i("LLA", "No path between them!");
								}
								else
								{
									Found = true;
						    	  	new AssetsExtracter().execute(0);
						    	  	Intent intent = new Intent(getApplicationContext(), LocationBased.class);
						    	  	intent.putExtra("my_destination", mDestinationNID);
						    		intent.putExtra("my_source", mSourceNID);
						    		intent.putExtra("my_source_latitude", mSourceLatitude);
						    		intent.putExtra("my_source_longitude", mSourceLongitude);
						    		intent.putExtra("my_path", mPath);
						    		intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
						    	  	startActivity(intent);
								}
					        }
						}
						else {
							// the source and destination ids are equal
							Found = true;
							mPath = String.valueOf(mSourceNID);
						  	new AssetsExtracter().execute(0);
						  	Intent intent = new Intent(getApplicationContext(), LocationBased.class);
						  	intent.putExtra("my_destination", mDestinationNID);
							intent.putExtra("my_source", mSourceNID);
							intent.putExtra("my_source_latitude", mSourceLatitude);
							intent.putExtra("my_source_longitude", mSourceLongitude);
							intent.putExtra("my_path", mPath);
							intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
						  	startActivity(intent);
						}
		
                }
                catch(Exception e){
                    e.printStackTrace();
                }
                finally{
                	indeterminateDialog.dismiss();
                	PathFound = Found;
                }
            } 
	  	}).start();
	  	/*
    	if (!PathFound)
    	{
    		AlertDialog.Builder MyAlertDialog = new AlertDialog.Builder(this);
    		MyAlertDialog.setTitle("Path Finder");
    		MyAlertDialog.setMessage("No Path between them.");
    		MyAlertDialog.show();
    	}
    	PathFound = true;
		*/
	    
}
  
  @Override
  protected void onNewIntent(Intent intent) {
      super.onNewIntent(intent);
      setIntent(intent);
  }
  
  private class AssetsExtracter extends AsyncTask<Integer, Integer, Boolean>
	{
		@Override
		protected Boolean doInBackground(Integer... params) 
		{
			try 
			{
				// Extract all assets and overwrite existing files if debug build
				AssetsManager.extractAllAssets(getApplicationContext(), BuildConfig.DEBUG);
			} 
			catch (IOException e) 
			{
				MetaioDebug.printStackTrace(Log.ERROR, e);
				return false;
			}
	
			return true;
		}
	}
  
  private class startingOnItemSelectedListener implements OnItemSelectedListener {          
      @Override  
      public void onItemSelected(AdapterView<?> adapter, View view, int position, long id) {
          String info = adapter.getItemAtPosition(position).toString();
    	  info = info.replace("/F", "");
    	  int floor = Integer.parseInt(info);
    	  Log.d("test2", info);
    	  
          List<String> mList = new ArrayList<String>();
          Cursor c = mDatabase.rawQuery("SELECT Facility.fname FROM Facility, Node WHERE Facility.nid=Node._id AND (Facility.ftype=2 OR Facility.ftype=8) AND Node.floor=" + floor, null);
          c.moveToFirst();
          int count = c.getCount();
          for (int i=0; i<count; i++) {
        	  String fname = c.getString(c.getColumnIndex("fname")).trim();
        	  mList.add("Room " + fname);
        	  c.moveToNext();
          }
          c.close();
          
          // sort the destinations by name
          Collections.sort(mList);
          
          // Creating adapter for spinner
          ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(CaptureActivity.this, R.layout.simple_destination_item, mList);

          // Drop down layout style - list view with radio button
          dataAdapter.setDropDownViewResource(R.layout.simple_destination_item);

          // attaching data adapter to spinner
          startingSpinner.setAdapter(dataAdapter);
      }  

      @Override  
      public void onNothingSelected(AdapterView<?> arg0) {  
          
      }  
  }
  
  private class destinationOnItemSelectedListener implements OnItemSelectedListener {          
      @Override  
      public void onItemSelected(AdapterView<?> adapter, View view, int position, long id) {
          String info = adapter.getItemAtPosition(position).toString();
    	  info = info.replace("/F", "");
    	  int floor = Integer.parseInt(info);
    	  
          List<String> mList = new ArrayList<String>();
          Cursor c = mDatabase.rawQuery("SELECT Facility.fname FROM Facility, Node WHERE Facility.nid=Node._id AND (Facility.ftype=2 OR Facility.ftype=8) AND Node.floor=" + floor, null);
          c.moveToFirst();
          int count = c.getCount();
          for (int i=0; i<count; i++) {
        	  String fname = c.getString(c.getColumnIndex("fname")).trim();
        	  mList.add("Room " + fname);
        	  c.moveToNext();
          }
          c.close();
          
          // sort the destinations by name
          Collections.sort(mList);
          
          // Creating adapter for spinner
          ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(CaptureActivity.this, R.layout.simple_destination_item, mList);

          // Drop down layout style - list view with radio button
          dataAdapter.setDropDownViewResource(R.layout.simple_destination_item);

          // attaching data adapter to spinner
          destinationSpinner.setAdapter(dataAdapter);
      }  

      @Override  
      public void onNothingSelected(AdapterView<?> arg0) {  
          
      }
  }
  
//  public void tutorial(int cases) {
//      resultView.setVisibility(View.VISIBLE);
//      shutterButton.setVisibility(View.GONE);
//	  switch(cases) {
//	      case 1:
//	          statusViewBottom.setVisibility(View.GONE);
//	          statusViewTop.setVisibility(View.GONE);
//	          cameraButtonView.setVisibility(View.GONE);
//	          flashToggleButton.setVisibility(View.GONE);
//	          viewfinderView.setVisibility(View.GONE);
//	          break;
//	      case 2:
//	          startingTextView.setVisibility(View.GONE);
//	          startingFloor.setVisibility(View.GONE);
//	          startingSpinner.setVisibility(View.GONE);
//	          confirmButton.setVisibility(View.GONE);
//	          break;
//	  }
//  }

}
