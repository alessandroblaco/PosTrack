package com.example.postrack;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.os.IBinder;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.text.method.ScrollingMovementMethod;

import com.example.postrack.AppLocationService.LocalBinder;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity{

    private TelephonyHelper th;
    private Location lastloc;
    TextView t1;
	
	AppLocationService appLocationService;
	boolean mBounded;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		Context context = getApplicationContext();



        th = new TelephonyHelper(context);
        t1=(TextView)findViewById(R.id.TextView01); 
        t1.setMovementMethod(new ScrollingMovementMethod());


		final Button button = (Button) findViewById(R.id.button1);
		button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				lastloc = appLocationService.getLocation("caio");
				String url = "geo:" + String.valueOf(lastloc.getLatitude()) + "," + String.valueOf(lastloc.getLongitude()) + "?q=" + String.valueOf(lastloc.getLatitude()) + "," + String.valueOf(lastloc.getLongitude());
				Uri gmmIntentUri = Uri.parse(url);
				Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
				mapIntent.setPackage("com.google.android.apps.maps");
				if (mapIntent.resolveActivity(getPackageManager()) != null) {
					startActivity(mapIntent);
				}
			}
		});


		final Button button2 = (Button) findViewById(R.id.button2);
		button2.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent myIntent = new Intent(MainActivity.this, MapsActivity.class);
				List<String> data = new ArrayList<String>();
				List<Location> history = appLocationService.getLocationHistory();
				for (int i = 0; i < history.size(); i++) {
					data.add(String.valueOf(history.get(i).getLatitude()) + "," + String.valueOf(history.get(i).getLongitude()) + "," + String.valueOf(history.get(i).getAccuracy()));
					System.out.println(history.get(i));
				}
				myIntent.putExtra("positions", TextUtils.join(";", data)); //Optional parameters
				MainActivity.this.startActivity(myIntent);

			}
		});

		ServiceConnection mConnection = new ServiceConnection() {
			
			public void onServiceDisconnected(ComponentName name) {
				Toast.makeText(MainActivity.this, "Service is disconnected", Toast.LENGTH_SHORT).show();
				mBounded = false;
				appLocationService = null;
			}
			
			public void onServiceConnected(ComponentName name, IBinder service) {
				Toast.makeText(MainActivity.this, "Service is connected", Toast.LENGTH_SHORT).show();
				mBounded = true;
				LocalBinder mLocalBinder = (LocalBinder)service;
				appLocationService = mLocalBinder.getServerInstance();
				appLocationService.setTextView(t1);
				appLocationService.setContext(MainActivity.this);
			}
		};
		Intent mIntent = new Intent(this, AppLocationService.class);
		bindService(mIntent, mConnection, BIND_AUTO_CREATE);
		
		
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
			Toast.makeText(MainActivity.this, "Bu!", Toast.LENGTH_SHORT).show();
		    
		    
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
