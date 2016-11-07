/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings;

import android.util.Log;
import android.content.Context;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.RemoteException;
import android.os.IPowerManager;
import android.os.ServiceManager;
import android.preference.SeekBarPreference;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.SeekBarDialogPreference;
import android.os.SystemProperties;

import java.util.Map;
import java.io.*;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.DisplayOutputManager;
import android.graphics.Rect;

public class HdmiScreenZoomPreference extends SeekBarDialogPreference implements
		SeekBar.OnSeekBarChangeListener {

	private static final String TAG = "HdmiScreenZoomPreference";
	private static final int MINIMUN_SCREEN_SCALE = 0;
	private static final int MAXIMUN_SCREEN_SCALE = 20;
	private SeekBar mSeekBar;
	private int mOldScale = 0;
	private int mValue = 0;
	// for save hdmi config
	private Context context;
	private SharedPreferences preferences;
	private DisplayOutputManager mDisplayManagement = null;
	private HdmiScaleTask mScaleTask;
	/**
	 *之前的OverScan
	 */
	private Rect mOldOverScan;
	/**
	 * 显示标记，表示主屏或副屏
	 */
	private int mDisplayFlag;
	public HdmiScreenZoomPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		Log.i(TAG, "HdmiScreenZoomPreference->context:" + context.getClass().getName());
		this.context = context;
		setDialogLayoutResource(R.layout.preference_dialog_screen_scale);
		setDialogIcon(R.drawable.ic_settings_screen_scale);
		preferences = context.getSharedPreferences(
				"HdmiSettings", context.MODE_PRIVATE);
		try {
			mDisplayManagement = new DisplayOutputManager();
		}catch (RemoteException doe) {
			mDisplayManagement = null;
		}
		
		if (mDisplayManagement != null &&
		    mDisplayManagement.getDisplayNumber() == 0)
			mDisplayManagement = null;		
	}

	protected void setHdmiScreenScale(int value) {
		/*if (mDisplayFlag != 0)
			mDisplayManagement.setOverScan(mDisplayManagement.AUX_DISPLAY, mDisplayManagement.DISPLAY_OVERSCAN_ALL, value);
		else
			mDisplayManagement.setOverScan(mDisplayManagement.MAIN_DISPLAY, mDisplayManagement.DISPLAY_OVERSCAN_ALL,value);*/
		if(mScaleTask != null && mScaleTask.getStatus() == Status.RUNNING)
			mScaleTask.cancel(true);
		mScaleTask =new HdmiScaleTask();
		mScaleTask.execute(value);
	}
	

	@Override
	protected void onBindDialogView(View view) {
		super.onBindDialogView(view);
		mSeekBar = getSeekBar(view);
		mSeekBar.setMax(MAXIMUN_SCREEN_SCALE);
		Rect overscan;
		if(mDisplayManagement != null){
			if (mDisplayFlag != 0)
				overscan = mDisplayManagement.getOverScan(mDisplayManagement.AUX_DISPLAY);
			else
				overscan = mDisplayManagement.getOverScan(mDisplayManagement.MAIN_DISPLAY);
			mOldScale = overscan.left - 80;
			mOldOverScan = overscan;
		}
		if(mOldScale < 0)
			mOldScale = 0;
		mSeekBar.setProgress(mOldScale);
		mSeekBar.setOnSeekBarChangeListener(this);
	}

	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromTouch) {
		mValue = progress + 80;
		if (mValue > 100) {
			mValue = 100;
		}
		setHdmiScreenScale(mValue);
	}

	public void onStartTrackingTouch(SeekBar seekBar) {
		// If start tracking, record the initial position
	}

	public void onStopTrackingTouch(SeekBar seekBar) {
		setHdmiScreenScale(mValue);
	}

	@Override
	protected void onDialogClosed(boolean positiveResult) {
		super.onDialogClosed(positiveResult);
		// for save config

		if (positiveResult) {
			int value = mSeekBar.getProgress() + 80;
			setHdmiScreenScale(value);
			//editor.putString("scale_set", String.valueOf(value));
		} else {
			String overscan = "overscan "+mOldOverScan.left+","+mOldOverScan.top+","+mOldOverScan.right+","+mOldOverScan.bottom;
			//Log.d("xzj","---scale value= "+value+" overscan = "+overscan);
			if(mDisplayFlag == 0)
				SystemProperties.set("persist.sys.overscan.main",overscan);
			else
				SystemProperties.set("persist.sys.overscan.aux",overscan);
			
		}
		//editor.commit();
	}
	
	private class HdmiScaleTask extends AsyncTask<Integer, Void, Void>{

		@Override
		protected Void doInBackground(Integer... params) {
			int value = params[0];
			if (mDisplayFlag != 0)
				mDisplayManagement.setOverScan(mDisplayManagement.AUX_DISPLAY, mDisplayManagement.DISPLAY_OVERSCAN_ALL, value);
			else
				mDisplayManagement.setOverScan(mDisplayManagement.MAIN_DISPLAY, mDisplayManagement.DISPLAY_OVERSCAN_ALL,value);
			return null;

		}
		
	}
	
	public void setDisplayFlag(int displayFlag){
		mDisplayFlag = displayFlag;
	}

	
}
