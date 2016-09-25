package com.example.postrack;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Point;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
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
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
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

        // textview for displaying log
        t1=(TextView)findViewById(R.id.TextView01);
        // enable scrolling
        t1.setMovementMethod(new ScrollingMovementMethod());


		final Button button = (Button) findViewById(R.id.button1);
        // when the first button is clicked
		button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
                /*// if there is a location history
                if (locationHistory.size() >= 1) {
                    // get the last location
                    lastloc = locationHistory.get(locationHistory.size()-1);
                    // and open it on google maps
                    String url = "geo:" + String.valueOf(lastloc.getLatitude()) + "," + String.valueOf(lastloc.getLongitude()) + "?q=" + String.valueOf(lastloc.getLatitude()) + "," + String.valueOf(lastloc.getLongitude());
                    Uri gmmIntentUri = Uri.parse(url);
                    Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                    mapIntent.setPackage("com.google.android.apps.maps");
                    if (mapIntent.resolveActivity(getPackageManager()) != null) {
                        startActivity(mapIntent);
                    }
                }*/
                try {
                    File direct = new File(Environment.getExternalStorageDirectory() + "/PosTrack");

                    if(!direct.exists())
                    {
                        if(direct.mkdir())
                        {
                            //directory is created;
                        }

                    }
                    File sd = Environment.getExternalStorageDirectory();
                    File data = Environment.getDataDirectory();

                    if (sd.canWrite()) {
                        String  currentDBPath= "//data//" + "com.example.postrack"
                                + "//databases//" + "UnknownCells.db";
                        String backupDBPath  = "/PosTrack/UnknownCells.db";
                        File currentDB = new File(data, currentDBPath);
                        File backupDB = new File(sd, backupDBPath);

                        FileChannel src = new FileInputStream(currentDB).getChannel();
                        FileChannel dst = new FileOutputStream(backupDB).getChannel();
                        dst.transferFrom(src, 0, src.size());
                        src.close();
                        dst.close();
                        Toast.makeText(getBaseContext(), backupDB.toString(),
                                Toast.LENGTH_LONG).show();

                    }
                } catch (Exception e) {

                    Toast.makeText(getBaseContext(), e.toString(), Toast.LENGTH_LONG)
                            .show();

                }
            }
		});


        final Button button2 = (Button) findViewById(R.id.button2);
        // when the second button is clicked
        button2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // open the map
                Intent myIntent = new Intent(MainActivity.this, MapsActivity.class);
                MainActivity.this.startActivity(myIntent);

            }
        });


        final Button button3 = (Button) findViewById(R.id.button3);
        // when the third button is clicked
        button3.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // ask the location service to force a GSM update
                try {
                    Message msg = Message.obtain(null, AppLocationService.MSG_ASK_FOR_GSM_UPDATE, 0);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                    // In this case the service has crashed before we could even do anything with it
                }
            }
        });

        // if the location service is not already running
		if (!AppLocationService.isRunning()) {
            // start it
			startService(new Intent(MainActivity.this, AppLocationService.class));
		}
        doBindService();

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
        String db_path = preferences.getString("database_file", Environment.getExternalStorageDirectory().getPath() + "/lacells.db");
        File db_file = new File(db_path);
        // check if the GSM antennae database file exists and is readable
        if (db_file.exists() && db_file.canRead()) {
            //ok
        } else {
            // if not alert the user with a dialog
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

            // set title
            alertDialogBuilder.setTitle(getResources().getString(R.string.db_not_found_dialog_title));

            // set dialog message
            alertDialogBuilder
                    .setMessage(getResources().getString(R.string.db_not_found_dialog_text).replace("[db]", db_path))
                    .setCancelable(false)
                    .setPositiveButton(getResources().getString(R.string.db_not_found_dialog_positive),new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,int id) {
                            // redirect the user to a page which explain how to build the database
                            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sobrus/FastLacellsGenerator"));
                            startActivity(i);
                            dialog.cancel();
                        }
                    })
                    .setNegativeButton(getResources().getString(R.string.db_not_found_dialog_negative),new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog,int id) {
                            // if this button is clicked, just close
                            // the dialog box and do nothing
                            dialog.cancel();
                        }
                    });

            // create alert dialog
            AlertDialog alertDialog = alertDialogBuilder.create();

            // show it
            alertDialog.show();
        }


	}
    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        // save status of application before redraw
        savedInstanceState.putCharSequence("textview", t1.getText());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        // restore status of application after redraw
        t1.setText(savedInstanceState.getCharSequence("textview"));
    }

	class IncomingHandler extends Handler {
        // listen to messages from the location service
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case AppLocationService.MSG_SET_LOG_MESSAGE:
                    // an update of the log is arrived
					String str = msg.getData().getString("log");
                    // set the content of the textview
                    t1.setText(str);
					break;
				case AppLocationService.MSG_SET_LOCATION_HISTORY:
                    // a new location history has been sent. Save it to use in the first button
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
                // when we connect to the service we register (we are the client) so as
                // to receive future updates
                Message msg = Message.obtain(null, AppLocationService.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
                // immediately ask for the log text
                msg = Message.obtain(null, AppLocationService.MSG_ASK_FOR_LOG, 0);
                msg.replyTo = mMessenger;
                mService.send(msg);
                // and for the location history
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
                    // we ask not to receive future updates
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
		// when clicked will start the preferences menu
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
            // when we exit the app, unbind from the location service
            doUnbindService();
        }
        catch (Throwable t) {
            Log.e("postrack", "Failed to unbind from the service", t);
        }
    }

    private SharedPreferences.OnSharedPreferenceChangeListener sBindPreferenceSummaryToValueListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            // when a preference is changed
            if (key.equals("position_service_enabled")) {
                // if it regards the enabling/disabling of the location service
                Boolean enable = sharedPreferences.getBoolean(key, false);
                Log.i("postrack", "Position service: " + String.valueOf(AppLocationService.isRunning()) + ", " + enable);
                if (AppLocationService.isRunning() && !enable) {
                    // disable
                    stopService(new Intent(MainActivity.this, AppLocationService.class));
                } else if (!AppLocationService.isRunning() && enable) {
                    // or enable it accordingly
                    startService(new Intent(MainActivity.this, AppLocationService.class));
                }
            }
        }
    };
}