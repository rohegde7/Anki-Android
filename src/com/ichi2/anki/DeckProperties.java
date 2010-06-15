package com.ichi2.anki;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.tomgibara.android.veecheck.util.PrefSettings;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.WindowManager;

public class DeckProperties extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	static final String TAG = "AnkiDroid";
	
	
	private boolean notificationBar;
	
	
	/**
	 * Broadcast that informs us when the sd card is about to be unmounted
	 */
	private BroadcastReceiver mUnmountReceiver = null;
	
	public class DeckPreferenceHack implements SharedPreferences
	{

		protected Map<String, String> values = new HashMap<String, String>();

		public DeckPreferenceHack()
		{
			this.cacheValues();

		}

		protected void cacheValues()
		{
			Log.i(TAG, "DeckPreferences - CacheValues");
			String syncName = String.valueOf(AnkiDroidApp.deck().getSyncName());
			if(!"".equalsIgnoreCase(syncName) && !"null".equalsIgnoreCase(syncName))
			{
				values.put("isSyncOn", "true");
				values.put("syncName", String.valueOf(AnkiDroidApp.deck().getSyncName()));
			}
			else
			{
				values.put("isSyncOn", "false");
				values.put("syncName", "");
			}
		}

		public class Editor implements SharedPreferences.Editor
		{

			public ContentValues update = new ContentValues();

			public SharedPreferences.Editor clear()
			{
				Log.d( TAG, "clear()" );
				update = new ContentValues();
				return this;
			}

			public boolean commit()
			{
				Log.d( TAG, "DeckPreferences - commit() changes back to database" );

				// make sure we refresh the parent cached values
				// cacheValues();

				for ( Entry<String, Object> entry : update.valueSet() )
				{
					if(entry.getKey().equals("syncName"))
					{
						AnkiDroidApp.deck().setSyncName(entry.getValue().toString());
					}
					else if(entry.getKey().equals("isSyncOn"))
					{
						if("false".equalsIgnoreCase(entry.getValue().toString()))
						{
							AnkiDroidApp.deck().setSyncName("");
						}
					}
				}
				// make sure we refresh the parent cached values
				cacheValues();

				// and update any listeners
				for ( OnSharedPreferenceChangeListener listener : listeners )
				{
					listener.onSharedPreferenceChanged( DeckPreferenceHack.this, null );
				}

				return true;
			}

			public android.content.SharedPreferences.Editor putBoolean( String key, boolean value )
			{
				return this.putString( key, Boolean.toString( value ) );
			}

			public android.content.SharedPreferences.Editor putFloat( String key, float value )
			{
				return this.putString( key, Float.toString( value ) );
			}

			public android.content.SharedPreferences.Editor putInt( String key, int value )
			{
				return this.putString( key, Integer.toString( value ) );
			}

			public android.content.SharedPreferences.Editor putLong( String key, long value )
			{
				return this.putString( key, Long.toString( value ) );
			}

			public android.content.SharedPreferences.Editor putString( String key, String value )
			{
				Log.d( this.getClass().toString(), String.format("Editor.putString(key=%s, value=%s)", key, value ) );
				update.put( key, value );
				return this;
			}

			public android.content.SharedPreferences.Editor remove( String key )
			{
				Log.d( this.getClass().toString(), String.format( "Editor.remove(key=%s)", key ) );
				update.remove( key );
				return this;
			}

		}

		public boolean contains( String key )
		{
			return values.containsKey(key);
		}

		public Editor edit()
		{
			return new Editor();
		}

		public Map<String, ?> getAll()
		{
			return values;
		}

		public boolean getBoolean( String key, boolean defValue )
		{
			return Boolean.valueOf( this.getString( key, Boolean.toString( defValue ) ) );
		}

		public float getFloat( String key, float defValue )
		{
			return Float.valueOf( this.getString( key, Float.toString( defValue ) ) );
		}

		public int getInt( String key, int defValue )
		{
			return Integer.valueOf( this.getString( key, Integer.toString( defValue ) ) );
		}

		public long getLong( String key, long defValue )
		{
			return Long.valueOf( this.getString( key, Long.toString( defValue ) ) );
		}

		public String getString( String key, String defValue )
		{
			Log.d( this.getClass().toString(), String.format( "getString(key=%s, defValue=%s)", key, defValue ) );

			if ( !values.containsKey( key ) )
				return defValue;
			return values.get( key );
		}

		public List<OnSharedPreferenceChangeListener> listeners = new LinkedList<OnSharedPreferenceChangeListener>();

		public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener)
		{
			listeners.add( listener );
		}

		public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener)
		{
			listeners.remove( listener );
		}

	}

	protected DeckPreferenceHack pref;

	@Override
	public SharedPreferences getSharedPreferences( String name, int mode )
	{
		Log.d( this.getClass().toString(), String.format( "getSharedPreferences(name=%s)", name ) );
		return this.pref;
	}

	@Override
	public void onCreate( Bundle icicle )
	{
		super.onCreate( icicle );

		if ( AnkiDroidApp.deck() == null )
		{
			Log.i( TAG, "DeckPreferences - Selected Deck is NULL" );
			finish();
		}
		else
		{
			
			restorePreferences();
			// Remove the status bar and make title bar progress available
			if (notificationBar==false) {
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
			}
			
			registerExternalStorageListener();
			this.pref = new DeckPreferenceHack();
			this.pref.registerOnSharedPreferenceChangeListener( this );

			this.addPreferencesFromResource( R.layout.deck_properties );
			this.updateSummaries();
		}
	}

	@Override
    public void onDestroy()
    {
    	super.onDestroy();
    	if(mUnmountReceiver != null)
    		unregisterReceiver(mUnmountReceiver);
    }
	
	/**
     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications.
     * The intent will call closeExternalStorageFiles() if the external media
     * is going to be ejected, so applications can clean up any files they have open.
     */
    public void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                    	finishNoStorageAvailable();
                    } 
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addDataScheme("file");
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }

    private void finishNoStorageAvailable()
    {
    	setResult(StudyOptions.CONTENT_NO_EXTERNAL_STORAGE);
		finish();
    }
    
	public void onSharedPreferenceChanged( SharedPreferences sharedPreferences, String key )
	{
		// update values on changed preference
		this.updateSummaries();
	}

	protected void updateSummaries()
	{
		// for all text preferences, set summary as current database value
		for ( String key : this.pref.values.keySet() )
		{
			Preference pref = this.findPreference( key );
			if ( pref == null )
				continue;
			if ( pref instanceof CheckBoxPreference )
				continue;
			pref.setSummary( this.pref.getString( key, "" ) );
		}
	}

	
	private SharedPreferences restorePreferences()
	{
		SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
		notificationBar = preferences.getBoolean("notificationBar", false);
		
		return preferences;
	}
	
}
