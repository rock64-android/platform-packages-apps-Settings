/**
 * 
 */
package com.android.settings;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Scanner;

import android.content.Context;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.app.IActivityManager;
import com.android.internal.logging.MetricsLogger;
import android.app.ActivityManagerNative;
/**
 * @author GaoFei
 * DisplayOutput Settings
 *
 */
public class DisplayOutputSetting extends SettingsPreferenceFragment {
	
	public static final String TAG = DisplayOutputSetting.class.getSimpleName();
	/**
	 * display type
	 */
	public static final String DISPLAY_TYPE_DIR = "/sys/class/display";
	
	public static final String DOUBLE_SCREEN_CONFIG = android.provider.Settings.DUAL_SCREEN_MODE;
	
	public static final String KEY_FOR_DOUBLE_SCREEN_CONFIG = "double_screen_config";
	/**
	 * root preference screen
	 */
	private PreferenceScreen mRootPreferenceScreen;
	/**
	 * 
	 */
	private CheckBoxPreference mScreenConfigPreference;
	/**
	 * display manager
	 */
	private DisplayManager mDisplayManager;
	/**
	 * mutiltype 
	 */
	private DisplayListener mDisplayListener;
	/**
	 * observer for double screen flag
	 */
	private DoubleScreenObserver mDoubleObserver;
	
	/**
	 * property文件
	 */
	public static final String PROPERTY = "property";
	
	@Override
	protected int getMetricsCategory() {
		return MetricsLogger.DISPLAY;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i(TAG, "DisplayOutputSetting->getActivity()->class name:" + getActivity().getClass().getName());
		initData();
		buildPreferenceScreen();
		initEvent();
	}

	
	@Override
	public void onResume() {
		super.onResume();
		registerDisplayListener();
		registerContentChange();
	}
	
	@Override
	public void onPause() {
		super.onPause();
		unRegisterDisplayListener();
		unRegisterContentChange();
	}
	
	
	public void initData(){
		mDisplayManager = (DisplayManager)getActivity().getSystemService(Context.DISPLAY_SERVICE);
		mDisplayListener = new MultiypeDisplayListener();
		mDoubleObserver = new DoubleScreenObserver(new Handler());
	}
	
	
	/**
	 * 初始化事件
	 */
	public void initEvent(){
		mScreenConfigPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				Boolean isEnable = (Boolean)newValue;
				Utils.saveValueToSharedPreference(getActivity(), KEY_FOR_DOUBLE_SCREEN_CONFIG, "" + (isEnable ? 1: 0));
				android.provider.Settings.System.putInt(getActivity().getContentResolver(), DOUBLE_SCREEN_CONFIG, isEnable? 1:0);
				return true;
			}
		});
	}
	
	/**
	 * 构建Preference列表
	 */
	public void buildPreferenceScreen(){
		if(mRootPreferenceScreen == null)
			mRootPreferenceScreen = getPreferenceManager().createPreferenceScreen(getActivity());
		else
			mRootPreferenceScreen.removeAll();
		//add double screen config
		if(mScreenConfigPreference == null){
			mScreenConfigPreference = new CheckBoxPreference(getActivity());
			mScreenConfigPreference.setTitle(getString(R.string.double_display_different_content));
			mScreenConfigPreference.setChecked("1".equals(Utils.getValueFromSharedPreference(getActivity(), KEY_FOR_DOUBLE_SCREEN_CONFIG)));
		}
		
		//mScreenConfigPreference.setPersistent(true);
		mRootPreferenceScreen.addPreference(mScreenConfigPreference);
		android.view.Display[] displays = mDisplayManager.getDisplays();
		mScreenConfigPreference.setEnabled(displays.length > 1);
		
		File displayTypeFile = new File(DISPLAY_TYPE_DIR);
		String[] displayTypes = new String[]{};
		if(displayTypeFile != null && displayTypeFile.exists()){
			displayTypes = displayTypeFile.list();
		}
		if(displayTypes != null && displayTypes.length > 0){
			for(String displayType : displayTypes){
				Preference displayPreference = new Preference(getActivity());
				displayPreference.setKey(displayType);
				displayPreference.setTitle(displayType);
				Bundle bundle = displayPreference.getExtras();
				bundle.putString(AllDisplaySetting.EXTRA_BUNDLE_DISPLAY_TYPE, displayType);
				//bundle.putString(key, value)
				displayPreference.setFragment("com.android.settings.AllDisplaySetting");
				try{
					File propertyFile = new File(DISPLAY_TYPE_DIR + "/" + displayType + "/" + PROPERTY);
					Scanner scanner = new Scanner(propertyFile);
					int property = scanner.nextInt();
					Log.i(TAG, "buildPreferenceScreen->property:" + property);
					//获取主屏，还是次屏
					String displayFlag = property == 0? getString(R.string.primary): getString(R.string.second);
					displayPreference.setTitle(displayPreference.getTitle().toString() + "(" + displayFlag + ")");
					bundle.putInt(AllDisplaySetting.EXTRAL_DISPLAY_FLAG, property);
				}catch(Exception e){
					Log.i(TAG, "read property error:" + e);
				}
				mRootPreferenceScreen.addPreference(displayPreference);
			}
		}
		
		
		for(int i = 1; i < mRootPreferenceScreen.getPreferenceCount(); ++i){
			Preference preference = mRootPreferenceScreen.getPreference(i);
			String preferenceKey = preference.getKey();
			File enableFile = new File(DISPLAY_TYPE_DIR + "/" + preferenceKey + "/enable");
			File connectFile = new File(DISPLAY_TYPE_DIR + "/" + preferenceKey + "/connect");
			try{
				BufferedReader enableBufferedReader = new BufferedReader(new FileReader(enableFile));
				BufferedReader connectBufferedReader = new BufferedReader(new FileReader(connectFile));
				String enableStr = enableBufferedReader.readLine();
				String connectStr = connectBufferedReader.readLine();
				if("1".equals(enableStr) && "1".equals(connectStr))
					preference.setEnabled(true);
				else
					preference.setEnabled(false);
				enableBufferedReader.close();
				enableBufferedReader = null;
				connectBufferedReader.close();
				connectBufferedReader = null;
			}catch (Exception e){
				e.printStackTrace();
			}
		
		}
		setPreferenceScreen(mRootPreferenceScreen);
	}
	

	public void registerDisplayListener(){
		mDisplayManager.registerDisplayListener(mDisplayListener, null);
	}
	

	public void unRegisterDisplayListener(){
		mDisplayManager.unregisterDisplayListener(mDisplayListener);
	}
	
	public void registerContentChange(){
		getActivity().getContentResolver().registerContentObserver(android.provider.Settings.System.getUriFor(DOUBLE_SCREEN_CONFIG), false, mDoubleObserver);
		
	}
	
	public void unRegisterContentChange(){
		getActivity().getContentResolver().unregisterContentObserver(mDoubleObserver);
	}

	class MultiypeDisplayListener implements DisplayListener{

		@Override
		public void onDisplayAdded(int displayId) {
			Log.i(TAG, "onDisplayAdded");
			buildPreferenceScreen();
		}

		@Override
		public void onDisplayRemoved(int displayId) {
			Log.i(TAG, "onDisplayRemoved");
			buildPreferenceScreen();
		}

		@Override
		public void onDisplayChanged(int displayId) {
			
		}
		
	}
	
	
	class DoubleScreenObserver extends ContentObserver{

		public DoubleScreenObserver(Handler handler) {
			super(handler);
		}
		
		@Override
		public void onChange(boolean selfChange) {
			boolean isEnabe = false;
			try{
				isEnabe = android.provider.Settings.System.getInt(getActivity().getContentResolver(), DOUBLE_SCREEN_CONFIG) == 1;
			}catch (Exception e){
				Log.i("DualScreen", "DoubleScreenObserver->onChangeException:" + e);
			}
			
			try {
				IActivityManager am = ActivityManagerNative.getDefault();
				Configuration config = am.getConfiguration();

				// Will set userSetLocale to indicate this isn't some passing
				// default - the user
				// wants this remembered
				config.setDualScreenFlag(isEnabe);

				am.updateConfiguration(config);
				// Trigger the dirty bit for the Settings Provider.
				// BackupManager.dataChanged("com.android.providers.settings");
			} catch (RemoteException e) {
				// Intentionally left blank
				Log.i("DualScreen", "DoubleScreenObserver->onChangeException2:" + e);
			}

		}
	}
	
}
