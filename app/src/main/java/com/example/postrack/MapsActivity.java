package com.example.postrack;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Location;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {
    boolean mIsBound;
    private Messenger mService = null;
    final Messenger mMessenger = new Messenger(new IncomingHandler());
    private List<Location> locationHistory;
    private GoogleMap mMap;

    class IncomingHandler extends Handler {
        // receive messages from location service
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AppLocationService.MSG_SET_LOCATION_HISTORY:
                    // receive the update of the location history, save it
                    locationHistory = msg.getData().getParcelableArrayList("history");
                    // and update the map
                    doUpdateMap();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // when we connect to the service
            mService = new Messenger(service);
            try {
                // register as client, so as to receive future updates
                Message msg = Message.obtain(null, AppLocationService.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
                askLocationUpdate();
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        // load map
        mapFragment.getMapAsync(this);
        // if service is not running (quite strange), start it
        if (!AppLocationService.isRunning()) {
            startService(new Intent(MapsActivity.this, AppLocationService.class));
        }
        doBindService();
    }

    private void askLocationUpdate() {
        // ask for a location history update
        if (mIsBound) {
            if (mService != null) {
                try {
                    // send the request of updating the location history to the service
                    Message msg = Message.obtain(null, AppLocationService.MSG_ASK_FOR_UPDATE, 0);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                }
                catch (RemoteException e) {
                    // the service has crashed
                }
            }
        }
    }

    void doUpdateMap() {
        // let's update the map with the (new) location history, if the map is already loaded
        if (mMap != null) {
            mMap.clear(); // clear everything that was previously drawn on the map
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            // get the home latitude and longitude
            Log.v("postrack", "lat: " + preferences.getString("home_latitude", "45.5032028"));
            // and create the home LatLng
            LatLng home = new LatLng(Double.valueOf(preferences.getString("home_latitude", "45.5032028")),Double.valueOf(preferences.getString("home_longitude", "9.1561746")));
            // add a marker at home
            mMap.addMarker(new MarkerOptions()
                    .position(home) // home LatLng
                    .title("Home") // marker title
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))); // make it yellow
            // add a red circle representing the boundary
            mMap.addCircle(new CircleOptions()
                    .center(home) // center it to home
                    .radius(Double.valueOf(preferences.getString("home_radius", "2000"))) // set radius
                    .strokeColor(Color.argb(220, 209, 132, 0)) // set color
                    .strokeWidth(3));

            //Let's build the line connecting the last positions
            PolylineOptions polylineOptions = new PolylineOptions();

            for (int i = 0; i < locationHistory.size(); i++) {
                // for each position in location history
                Location loc = locationHistory.get(i);
                // create a LatLng
                LatLng center = new LatLng(loc.getLatitude(), loc.getLongitude());
                // add a point to the line
                polylineOptions.add(center);
                if (i == locationHistory.size() - 1) {
                    // if it is the last known position, then also add a marker centered to
                    // it with some info about accuracy and location provider
                    mMap.addMarker(new MarkerOptions().position(center).title("Last location by " + loc.getProvider() + " (+- " + String.valueOf(loc.getAccuracy()) + "m)"));
                    // and move the map to that point
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 15));
                }
                // add a blue circle around each location, representing the accuracy
                mMap.addCircle(new CircleOptions()
                        .center(center) // the center of the circle is the position itself
                        .radius(loc.getAccuracy()) // the radius is the accuracy of the position
                        .strokeColor(Color.argb(220, 0, 206, 209)) // light blue
                        .strokeWidth(3));
            }
            // the polilyne is completed, draw it on the map
            mMap.addPolyline(polylineOptions
                    .width(6).color(Color.argb(80, 0, 0, 0))); // gray

        }
    }

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


    /**
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap gmMap) {
        // when the map is loaded
        mMap = gmMap;
        // update it
        askLocationUpdate();
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
}
