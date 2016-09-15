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
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AppLocationService.MSG_SET_LOCATION_HISTORY:
                    locationHistory = msg.getData().getParcelableArrayList("history");
                    doUpdateMap();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = new Messenger(service);
            try {
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
        mapFragment.getMapAsync(this);
        if (!AppLocationService.isRunning()) {
            startService(new Intent(MapsActivity.this, AppLocationService.class));
        }
        doBindService();
    }

    private void askLocationUpdate() {
        if (mIsBound) {
            if (mService != null) {
                try {
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
        if (mMap != null) {
            mMap.clear();
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            Log.v("postrack", "lat: " + preferences.getString("home_latitude", "45.5032028"));
            mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(Double.valueOf(preferences.getString("home_latitude", "45.5032028")),Double.valueOf(preferences.getString("home_longitude", "9.1561746"))))
                    .title("Home")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
            PolylineOptions rectOptions = new PolylineOptions();

            for (int i = 0; i < locationHistory.size(); i++) {
                Location loc = locationHistory.get(i);
                LatLng center = new LatLng(loc.getLatitude(), loc.getLongitude());
                rectOptions.add(center);
                if (i == locationHistory.size() - 1) {
                    mMap.addMarker(new MarkerOptions().position(center).title("Last location by " + loc.getProvider() + " (+- " + String.valueOf(loc.getAccuracy()) + "m)"));
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 15));// Instantiates a new Polyline object and adds points to define a rectangle
                }
                mMap.addCircle(new CircleOptions()
                        .center(center)
                        .radius(loc.getAccuracy())
                        .strokeColor(Color.argb(220, 0, 206, 209))
                        .strokeWidth(3));
            }
            mMap.addPolyline(rectOptions
                    .width(6).color(Color.argb(80, 0, 0, 0)));

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
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap gmMap) {
        mMap = gmMap;
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
