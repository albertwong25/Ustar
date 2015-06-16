package com.hkust.ustar;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class FacilityDialog extends AlertDialog {
	private TextView myAwesomeTextView;

	public FacilityDialog(Context context) {
		super(context);
	}

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.facilitydialogv2);
	}
	
	public void setTimeAva(int r, String text) {
		myAwesomeTextView = (TextView)findViewById(r);
		myAwesomeTextView.setText(text);
	}
	
	public void setFDialogTitle(String text) {
		myAwesomeTextView = (TextView)findViewById(R.id.fdialog_title);
		myAwesomeTextView.setText(text);
	}
	
}
