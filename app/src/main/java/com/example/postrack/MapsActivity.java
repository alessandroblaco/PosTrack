package com.example.postrack;

import android.graphics.Color;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
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
    public void onMapReady(GoogleMap mMap) {
        Bundle extras = getIntent().getExtras();
        // Add a marker in Sydney and move the camera
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(45.461, 9.191), 15));// Instantiates a new Polyline object and adds points to define a rectangle
        PolylineOptions rectOptions = new PolylineOptions();
        if (extras != null) {
            String single = extras.getString("positions");
            try {
                List<String> history = new ArrayList<>(Arrays.asList(single.split(";")));
                for (int i = 0; i < history.size(); i++) {
                    List<String> position = new ArrayList<>(Arrays.asList(history.get(i).split(",")));
                    LatLng center = new LatLng(Double.valueOf(position.get(0)), Double.valueOf(position.get(1)));
                    rectOptions.add(center);
                    if (i == history.size() - 1) {
                        mMap.addMarker(new MarkerOptions().position(center).title("Ultima posizione (+- " + String.valueOf(position.get(2)) + "m)"));
                    }
                    mMap.addCircle(new CircleOptions()
                            .center(center)
                            .radius(Double.valueOf(position.get(2)))
                            .strokeColor(Color.argb(220, 0, 206, 209))
                            .strokeWidth(3));
                }
            } catch (NullPointerException e) {
                Log.v("Map", "Error: " + e.toString());
            }
            //The key argument here must match that used in the other activity
        }
        mMap.addPolyline(rectOptions
                .width(6).color(Color.argb(80, 0, 0, 0)));
    }
}
