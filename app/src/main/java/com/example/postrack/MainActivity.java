package com.example.postrack;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.os.IBinder;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.text.method.ScrollingMovementMethod;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity{

    private Messenger mService = null;
    private Location lastloc;
    private List<Location> locationHistory;
    private SharedPreferences preferences;
    TextView t1;
    final Messenger mMessenger = new Messenger(new IncomingHandler());
    boolean mIsBound;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        t1=(TextView)findViewById(R.id.TextView01); 
        t1.setMovementMethod(new ScrollingMovementMethod());


		final Button button = (Button) findViewById(R.id.button1);
		button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
                if (locationHistory.size() >= 1) {
                    lastloc = locationHistory.get(locationHistory.size()-1);
                    String url = "geo:" + String.valueOf(lastloc.getLatitude()) + "," + String.valueOf(lastloc.getLongitude()) + "?q=" + String.valueOf(lastloc.getLatitude()) + "," + String.valueOf(lastloc.getLongitude());
                    Uri gmmIntentUri = Uri.parse(url);
                    Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                    mapIntent.setPackage("com.google.android.apps.maps");
                    if (mapIntent.resolveActivity(getPackageManager()) != null) {
                        startActivity(mapIntent);
                    }
                }
            }
		});


		final Button button2 = (Button) findViewById(R.id.button2);
		button2.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent myIntent = new Intent(MainActivity.this, MapsActivity.class);
				MainActivity.this.startActivity(myIntent);

			}
		});

		if (!AppLocationService.isRunning()) {
			startService(new Intent(MainActivity.this, AppLocationService.class));
		}
        doBindService();

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(sBindPreferenceSummaryToValueListener);


	}
    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putCharSequence("textview", t1.getText());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        t1.setText(savedInstanceState.getCharSequence("textview"));
    }

	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case AppLocationService.MSG_SET_LOG_MESSAGE:
					String str = msg.getData().getString("log");
                    t1.setText(str);
					break;
				case AppLocationService.MSG_SET_LOCATION_HISTORY:
                    locationHistory = msg.getData().getParcelableArrayList("history");
					break;
				default:
					super.handleMessage(msg);
			}
		}
	}
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i("postrack", String.valueOf(service==null));
            mService = new Messenger(service);
            try {
                Message msg = Message.obtain(null, AppLocationService.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
                msg = Message.obtain(null, AppLocationService.MSG_ASK_FOR_LOG, 0);
                msg.replyTo = mMessenger;
                mService.send(msg);
                msg = Message.obtain(null, AppLocationService.MSG_ASK_FOR_UPDATE, 0);
                msg.replyTo = mMessenger;
                mService.send(msg);
            }
            catch (RemoteException e) {
                // In this case the service has crashed before we could even do anything with it
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been unexpectedly disconnected - process crashed.
            mService = null;
            Log.i("postrack", "Disconnected.");
        }
    };

    void doBindService() {
        bindService(new Intent(this, AppLocationService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
        Log.i("postrack", "Binding.");
    }
    void doUnbindService() {
        if (mIsBound) {
            // If we have received the service, and hence registered with it, then now is the time to unregister.
            if (mService != null) {
                try {
                    Message msg = Message.obtain(null, AppLocationService.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                }
                catch (RemoteException e) {
                    // There is nothing special we need to do if the service has crashed.
                }
            }
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
            Log.i("postrack", "Unbinding.");
        }
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
            Intent myIntent = new Intent(MainActivity.this, SettingsActivity.class);
            MainActivity.this.startActivity(myIntent);
		    
		    
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            doUnbindService();
        }
        catch (Throwable t) {
            Log.e("postrack", "Failed to unbind from the service", t);
        }
    }

    private SharedPreferences.OnSharedPreferenceChangeListener sBindPreferenceSummaryToValueListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals("position_service_enabled")) {
                Boolean enable = sharedPreferences.getBoolean(key, false);
                Log.i("postrack", "Position service: " + String.valueOf(AppLocationService.isRunning()) + ", " + enable);
                if (AppLocationService.isRunning() && !enable) {
                    stopService(new Intent(MainActivity.this, AppLocationService.class));
                } else if (!AppLocationService.isRunning() && enable) {
                    startService(new Intent(MainActivity.this, AppLocationService.class));
                }
            }
        }
    };
}