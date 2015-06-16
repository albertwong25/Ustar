package com.hkust.ustar;

import com.googlecode.leptonica.android.ReadFile;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.hkust.ustar.R;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * Class to send OCR requests to the OCR engine in a separate thread, send a success/failure message.
 */
final class OcrRecognizeAsyncTask extends AsyncTask<Void, Void, Boolean> {

  private CaptureActivity activity;
  private TessBaseAPI baseApi;
  private byte[] data;
  private int width;
  private int height;
  private OcrResult ocrResult;
  private long timeRequired;

  OcrRecognizeAsyncTask(CaptureActivity activity, TessBaseAPI baseApi, byte[] data, int width, int height) {
    this.activity = activity;
    this.baseApi = baseApi;
    this.data = data;
    this.width = width;
    this.height = height;
  }

  @Override
  protected Boolean doInBackground(Void... arg0) {
    long start = System.currentTimeMillis();
    Bitmap bitmap = activity.getCameraManager().buildLuminanceSource(data, width, height).renderCroppedGreyscaleBitmap();
    String textResult;

    try {     
      baseApi.setImage(ReadFile.readBitmap(bitmap));
      textResult = baseApi.getUTF8Text();
      timeRequired = System.currentTimeMillis() - start;

      // Check for failure to recognize text
      if (textResult == null || textResult.equals("")) {
        return false;
      }
      ocrResult = new OcrResult();
      ocrResult.setWordConfidences(baseApi.wordConfidences());
      ocrResult.setMeanConfidence( baseApi.meanConfidence());
      ocrResult.setRegionBoundingBoxes(baseApi.getRegions().getBoxRects());
      ocrResult.setTextlineBoundingBoxes(baseApi.getTextlines().getBoxRects());
      ocrResult.setWordBoundingBoxes(baseApi.getWords().getBoxRects());
      ocrResult.setStripBoundingBoxes(baseApi.getStrips().getBoxRects());
      ocrResult.setCharacterBoundingBoxes(baseApi.getCharacters().getBoxRects());
    } catch (RuntimeException e) {
      Log.e("OcrRecognizeAsyncTask", "Caught RuntimeException in request to Tesseract. Setting state to CONTINUOUS_STOPPED.");
      e.printStackTrace();
      try {
        baseApi.clear();
        activity.stopHandler();
      } catch (NullPointerException e1) {
        // Continue
      }
      return false;
    }
    timeRequired = System.currentTimeMillis() - start;
    ocrResult.setBitmap(bitmap);
    ocrResult.setText(textResult);
    ocrResult.setRecognitionTimeRequired(timeRequired);
    return true;
  }

  @Override
  protected void onPostExecute(Boolean result) {
    super.onPostExecute(result);

    Handler handler = activity.getHandler();
    if (handler != null) {
      // Send results for single-shot mode recognition.
      if (result) {
        Message message = Message.obtain(handler, R.id.ocr_decode_succeeded, ocrResult);
        message.sendToTarget();
      } else {
        Message message = Message.obtain(handler, R.id.ocr_decode_failed, ocrResult);
        message.sendToTarget();
      }
      activity.getProgressDialog().dismiss();
    }
    if (baseApi != null) {
      baseApi.clear();
    }
  }
}
