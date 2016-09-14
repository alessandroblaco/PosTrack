package com.example.postrack;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

public class AppLocationService extends Service implements LocationListener {

	IBinder mBinder = new LocalBinder();
	protected LocationManager locationManager;
	private List<Location> locationHistory = new ArrayList<Location>();
	private Location lastBestLocation;
	private Boolean GPSEnabled = true;
	private Boolean NWEnabled = true;
	private Boolean GSMEnabled = false;
	private TextView t;
    private Boolean lastnear = false;
    private Context mcontext;


	private static final long MIN_DISTANCE_FOR_UPDATE = 100; // 100 meters
	private static final long MIN_TIME_FOR_UPDATE = 1000 * 30 * 1; // 5 minutes
    private TelephonyManager telephonyManager;
    private PhoneStateListener telephonyListener = new PhoneStateListener() {
        public void onServiceStateChanged(ServiceState serviceState) {
        	log("onServiceStateChanged");
            updateGSM();
        }
        public void onCellLocationChanged(CellLocation location) {
        	log("onCellLocationChanged: " + String.valueOf(((GsmCellLocation) location).getCid()));
            updateGSM();
        }
        public void onCellInfoChanged(List<android.telephony.CellInfo> cellInfo) {
        	log("onCellInfoChanged");
            updateGSM();
        }
    };
    
    
    private TelephonyHelper telephonyHelper;
	
	public AppLocationService() {
	}

	public void setTextView(TextView t1) {
		t = t1;
		t.setTextIsSelectable(true);
	}
	public void setContext(Context context) {
        mcontext = context;
	    locationManager = (LocationManager) context
	            .getSystemService(LOCATION_SERVICE);
	    telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        telephonyHelper = new TelephonyHelper(context);

        try {
			locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
					MIN_TIME_FOR_UPDATE, MIN_DISTANCE_FOR_UPDATE, this);

        	locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
        		MIN_TIME_FOR_UPDATE, MIN_DISTANCE_FOR_UPDATE, this);
		} catch (SecurityException e) {
			log("Permission error: " + e.toString());
		}
	    
	    if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
			try {
	    		onLocationChanged(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER));
			} catch (SecurityException e) {
				log("Permission error: " + e.toString());
			}
		    onProviderEnabled(LocationManager.NETWORK_PROVIDER);
	    } else {
		    onProviderDisabled(LocationManager.NETWORK_PROVIDER);
	    }
	    
	    if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			try {
	    		onLocationChanged(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER));
			} catch (SecurityException e) {
				log("Permission error: " + e.toString());
			}
		    onProviderEnabled(LocationManager.GPS_PROVIDER);
	    } else {
		    onProviderDisabled(LocationManager.GPS_PROVIDER);
	    }
	}
	public class LocalBinder extends Binder {
		public AppLocationService getServerInstance() {
			return AppLocationService.this;
		}
	}

	public Location getLocation(String provider) {
		return lastBestLocation;
	}
	public List<Location> getLocationHistory() {
		return locationHistory;
	}
	
	@Override
	public void onLocationChanged(Location location) {
		if (location == null) {
			log("Null location");
		} else {
			log("Location changed from : " + location.getProvider());
			if (
					(lastBestLocation != null && lastBestLocation.getProvider().equals("gsm"))
							||
							(location.getProvider().equals(LocationManager.GPS_PROVIDER))
							||
							(location.getProvider().equals(LocationManager.NETWORK_PROVIDER) && GPSEnabled == false)
							||
							(location.getProvider().equals("gsm") && NWEnabled == false && GPSEnabled == false)
					) {
				locationHistory.add(location);
				lastBestLocation = location;
				log("\n" + "New position from : " + location.getProvider());
				log("Lat: " + String.valueOf(location.getLatitude()) + ", Lon: " + String.valueOf(location.getLongitude()) + "\n");
				Location locationB = new Location("point B");

				locationB.setLatitude(45.5032028);
				locationB.setLongitude(9.1561746);
				float distance = location.distanceTo(locationB);

				log("Distance from Bovisa is " + String.valueOf(distance) +  "m");
				String par;
				if (distance > 100) {
					log("You are fare away");
					par = "0";
				} else {
					log("You are close to Bovisa");
					par = "1";
				}


                try
                {
                    TelephonyManager telephonyService = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

                    Method setMobileDataEnabledMethod = telephonyService.getClass().getDeclaredMethod("setDataEnabled", boolean.class);

                    if (null != setMobileDataEnabledMethod)
                    {
                        setMobileDataEnabledMethod.invoke(telephonyService, true);
                        Log.v("postrack", "Mobile data enabled");
                    }
                }
                catch (Exception ex)
                {
                    Log.e("postrack", "Error setting mobile data state", ex);
                }


                if (true){//par.equals("1") && !lastnear || par.equals("0") && lastnear) {
                    RequestQueue queue = Volley.newRequestQueue(this);
                    String url = "http://milano.cngei.it/iot/app.php?arrivo=" + par;
                    log("Calling " + url);

                    // Request a string response from the provided URL.
                    StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                            new Response.Listener<String>() {
                                @Override
                                public void onResponse(String response) {
                                    Log.v("postrack", "Response is: " + response);
                                }
                            }, new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            Log.v("postrack", "Error: " + error.toString());
                        }
                    });
                    // Add the request to the RequestQueue.
                    queue.add(stringRequest);
                } else {
                    log("No need for update");
                }
			}

		}
	}
	
	@Override
	public void onProviderDisabled(String provider) {
		log("Disabled provider: " + provider);
	
		if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
			NWEnabled = false;
		} else if (provider.equals(LocationManager.GPS_PROVIDER)) {
			GPSEnabled = false;
		}
		if (NWEnabled == false && GPSEnabled == false) {
			startGSM();
		}
	}
	
	@Override
	public void onProviderEnabled(String provider) {
		log("Enabled provider: " + provider);
		if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
			NWEnabled = true;
		} else if (provider.equals(LocationManager.GPS_PROVIDER)) {
			GPSEnabled = true;
		}
		stopGSM();
	}
	
	public void startGSM() {
		if (GSMEnabled == false) {
			GSMEnabled = true;
			log("Started GSM");
			telephonyManager.listen(
				telephonyListener,
				PhoneStateListener.LISTEN_CELL_INFO |
				PhoneStateListener.LISTEN_CELL_LOCATION |
				PhoneStateListener.LISTEN_SERVICE_STATE
			);
			updateGSM();
		}
	}
	
	public void stopGSM() {
		if (GSMEnabled == true) {
			GSMEnabled = false;
			log("Stopped GSM");
			telephonyManager.listen(
					telephonyListener,
					PhoneStateListener.LISTEN_NONE
				);
            try {
                locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, this, null);
            } catch (SecurityException e) {
                log("Permission error: " + e.toString());
            }
            try {
                locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, null);
            } catch (SecurityException e) {
                log("Permission error: " + e.toString());
            }
			
		}
	}
	
	public void updateGSM() {
		if (lastBestLocation == null || lastBestLocation.getTime() + 5*1000.0 < System.currentTimeMillis()) {
			log("Update from GSM");
			try {
				Location loc = telephonyHelper.getLocationEstimate();
				if (loc == null) {
					log("Failed...");
				} else {
					onLocationChanged(loc);
				}
			} catch (Exception e) {
				log("Error in GSM: " + e.toString());
			}
		} else {
			log("No update because too soon");
		}
	}
	
	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
	}


	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
	
	@Override
	public void onCreate() {
		log("Service onCreate");
	}
	
	@Override
	public void onStart(Intent intent, int startId) {
		// Perform your long running operations here.
		log("Service Started");
	}
	
	@Override
	public void onDestroy() {
	}
	
	public void log(String str) {
		if (t != null) {
			Date d = new Date();
			t.append("\n" + DateFormat.format("yyyy-MM-dd hh:mm:ss", d.getTime()) + ": " + str);
            Log.v("postrack", str);
		}
	}

}