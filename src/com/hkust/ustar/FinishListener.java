package com.hkust.ustar;


import android.app.Activity;
import android.content.DialogInterface;

/**
 * Simple listener used to exit the app in a few cases.
 */
final class FinishListener
    implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener, Runnable {

  private final Activity activityToFinish;

  FinishListener(Activity activityToFinish) {
    this.activityToFinish = activityToFinish;
  }

  @Override
public void onCancel(DialogInterface dialogInterface) {
    run();
  }

  @Override
public void onClick(DialogInterface dialogInterface, int i) {
    run();
  }

  @Override
public void run() {
    activityToFinish.finish();
  }

}