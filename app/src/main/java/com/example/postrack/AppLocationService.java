package com.example.postrack;

import java.util.ArrayList;
import java.util.List;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import android.os.Messenger;
import android.os.Message;
import android.os.RemoteException;

public class AppLocationService extends Service implements LocationListener {
	private static boolean isRunning = false;
	private LocationManager locationManager;
	private TelephonyManager telephonyManager;
	private TelephonyHelper telephonyHelper;
    private Boolean needGsmUpdate = false;
    private SharedPreferences preferences;
	private Boolean canUseProvider = true;
	private Boolean canUseGSM = true;
	private int GpsProviderUpdateInterval = 5 * 1000 * 60; //minutes
	private int NwProviderUpdateInterval = 5 * 1000 * 60; //minutes
	ArrayList<Messenger> mClients = new ArrayList<>(); // Keeps track of all current registered clients.

	static final int MSG_SET_LOCATION_HISTORY = 1;
	static final int MSG_SET_LOG_MESSAGE = 3;
	static final int MSG_REGISTER_CLIENT = 4;
	static final int MSG_UNREGISTER_CLIENT = 5;
	static final int MSG_ASK_FOR_UPDATE = 6;
	final Messenger mMessenger = new Messenger(new IncomingHandler()); // Target we publish for clients to send messages to IncomingHandler.

	public static boolean isRunning()
	{
		return isRunning;
	}

	@Override
	public void onCreate() {
        isRunning = true;
        super.onCreate();
        Log.i("AppLocationService", "Service Started.");

        locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
        telephonyManager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        telephonyHelper = new TelephonyHelper(this);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
		canUseProvider = preferences.getBoolean("use_android_provider", true);
		canUseGSM = preferences.getBoolean("position_service_enabled", true);
		GpsProviderUpdateInterval = (int)(Float.valueOf(preferences.getString("nw_interval", "5")) * 60000f);
		NwProviderUpdateInterval = (int)(Float.valueOf(preferences.getString("nw_interval", "5")) * 60000f);
        updateProvidersConnection();
        preferences.registerOnSharedPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
    }

    public void updateProvidersConnection() {
        if (canUseProvider  && canUseGSM) {
            try {
				locationManager.removeUpdates(this);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
						NwProviderUpdateInterval, MIN_DISTANCE_FOR_UPDATE, this);
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
						GpsProviderUpdateInterval, MIN_DISTANCE_FOR_UPDATE, this);
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
        } else {
            try {
                locationManager.removeUpdates(this);
            } catch (SecurityException e) {
                log("Permission error: " + e.toString());
            }
            GPSEnabled = false;
            NWEnabled = false;
			if (canUseGSM) {
				if (!GSMEnabled) {
					startGSM();
				} else {
					needGsmUpdate = true;
					updateGSM();
				}
			} else {
				stopGSM();
			}
        }
	}

    private OnSharedPreferenceChangeListener sBindPreferenceSummaryToValueListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals("use_android_provider")) {
                Boolean enabled = sharedPreferences.getBoolean(key, false);
                log("Setting key " + key + " to " + String.valueOf(enabled));
                canUseProvider = enabled;
                updateProvidersConnection();
            } else if (key.equals("gps_interval")) {
				GpsProviderUpdateInterval = (int)(Float.valueOf(sharedPreferences.getString("gps_interval", "5")) * 60000f);
				updateProvidersConnection();
			} else if (key.equals("nw_interval")) {
				NwProviderUpdateInterval = (int)(Float.valueOf(sharedPreferences.getString("nw_interval", "5")) * 60000f);
				updateProvidersConnection();
			} else if (key.equals("position_service_enabled")) {
				Boolean enabled = sharedPreferences.getBoolean(key, false);
				canUseGSM = enabled;
				updateProvidersConnection();
			}
		}
    };
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i("AppLocationService", "Received start id " + startId + ": " + intent);
		return START_STICKY; // run until explicitly stopped.
	}

	private void sendLocationUpdateToUI() {
		for (int i=mClients.size()-1; i>=0; i--) {
			try {
				//Send data as a String
				Bundle b = new Bundle();
				/*b.putString("history", new Gson().toJson(locationHistory));*/
				Message msg = Message.obtain(null, MSG_SET_LOCATION_HISTORY);
                b.putParcelableArrayList ("history", (ArrayList)locationHistory);

                msg.setData(b);
				mClients.get(i).send(msg);

			} catch (RemoteException e) {
				// The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
				mClients.remove(i);
			}
		}
	}

	private void sendLogMessageToUI(String tmsg) {
		for (int i=mClients.size()-1; i>=0; i--) {
			try {
				//Send data as a String
				Bundle b = new Bundle();
				b.putString("log", tmsg);
				Message msg = Message.obtain(null, MSG_SET_LOG_MESSAGE);
				msg.setData(b);
				mClients.get(i).send(msg);

			} catch (RemoteException e) {
				// The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
				mClients.remove(i);
			}
		}
	}
	class IncomingHandler extends Handler { // Handler of incoming messages from clients.
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case MSG_REGISTER_CLIENT:
					mClients.add(msg.replyTo);
					break;
				case MSG_UNREGISTER_CLIENT:
					mClients.remove(msg.replyTo);
					break;
				case MSG_ASK_FOR_UPDATE:
					sendLocationUpdateToUI();
					break;
				default:
					super.handleMessage(msg);
			}
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mMessenger.getBinder();
	}


	IBinder mBinder = new LocalBinder();
	private List<Location> locationHistory = new ArrayList<>();
	private Location lastBestLocation;
	private Boolean GPSEnabled = true;
	private Boolean NWEnabled = true;
	private Boolean GSMEnabled = false;
    private Boolean lastnear = false;


	private static final long MIN_DISTANCE_FOR_UPDATE = 100; // 100 meters
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
    

	
	public AppLocationService() {
	}
	public class LocalBinder extends Binder {
		public AppLocationService getServerInstance() {
			return AppLocationService.this;
		}
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
							(location.getProvider().equals(LocationManager.NETWORK_PROVIDER) && !GPSEnabled)
							||
							(location.getProvider().equals("gsm") && !NWEnabled && !GPSEnabled)
					) {
				locationHistory.add(location);
				lastBestLocation = location;
				sendLocationUpdateToUI();
				log("\n" + DateFormat.format("yyyy-MM-dd hh:mm:ss", location.getTime()) + ": New position from : " + location.getProvider());
				log("Lat: " + String.valueOf(location.getLatitude()) + ", Lon: " + String.valueOf(location.getLongitude()) + "\n");
				Location locationB = new Location("home");

                locationB.setLatitude(Double.valueOf(preferences.getString("home_latitude", "45.5032028")));
                locationB.setLongitude(Double.valueOf(preferences.getString("home_longitude", "9.1561746")));
				float distance = location.distanceTo(locationB);
                float max_distance = Float.valueOf(preferences.getString("home_radius", "500.0"));

				log("Distance from Bovisa is " + String.valueOf(distance) +  "m");
				String arrivo;
				if (distance > max_distance) {
					log("You are fare away");
					arrivo = "0";
				} else {
					log("You are close to Bovisa");
					arrivo = "1";
				}


                if (distance > max_distance && lastnear || distance <= max_distance && !lastnear) {
                    String url = preferences.getString("update_url", "http://emonbovisa.it/app.php?arrivo=") + arrivo;
                    if (preferences.getBoolean("update_url_enabled", true)) {
						log("Calling " + url);
						RequestQueue queue = Volley.newRequestQueue(this);
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
						log("Not calling url (disabled) -> " + arrivo);
					}
                } else {
                    log("No need for update");
                }
			}

		}
	}
	
	@Override
	public void onProviderDisabled(String provider) {
		log("Disabled provider: " + provider);

        NWEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        GPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
		if (!NWEnabled && !GPSEnabled && canUseProvider && canUseGSM) {
			startGSM();
		}
	}
	
	@Override
	public void onProviderEnabled(String provider) {
		log("Enabled provider: " + provider);
        NWEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        GPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if ((NWEnabled || GPSEnabled) && canUseProvider) {
            stopGSM();
        }
	}
	
	public void startGSM() {
		if (!GSMEnabled) {
			GSMEnabled = true;
			log("Started GSM");
			telephonyManager.listen(
				telephonyListener,
				PhoneStateListener.LISTEN_CELL_INFO |
				PhoneStateListener.LISTEN_CELL_LOCATION |
				PhoneStateListener.LISTEN_SERVICE_STATE
			);
            needGsmUpdate = true;
			updateGSM();
		}
	}
	
	public void stopGSM() {
		if (GSMEnabled) {
			GSMEnabled = false;
			log("Stopped GSM");
			telephonyManager.listen(
					telephonyListener,
					PhoneStateListener.LISTEN_NONE
				);
            if (canUseProvider && canUseGSM) {
                try {
                    locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, this, null);
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
							NwProviderUpdateInterval, MIN_DISTANCE_FOR_UPDATE, this);
                } catch (SecurityException e) {
                    log("Permission error: " + e.toString());
                }
                try {
                    locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, null);
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
							GpsProviderUpdateInterval, MIN_DISTANCE_FOR_UPDATE, this);
                } catch (SecurityException e) {
                    log("Permission error: " + e.toString());
                }
            }
		}
	}
	
	public void updateGSM() {
		if (needGsmUpdate || (lastBestLocation == null || lastBestLocation.getTime() + 5*1000.0 < System.currentTimeMillis())) {
			log("Update from GSM");
			try {
				Location loc = telephonyHelper.getLocationEstimate();
				if (loc == null) {
					log("Failed...");
				} else {
                    needGsmUpdate = false;
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
        if (status == LocationProvider.AVAILABLE) {
            log("status changed for " + provider);
        }
	}




	@Override
	public void onDestroy() {
		super.onDestroy();
		// TODO stop location pursuit
		Log.i("MyService", "Service Stopped.");
		isRunning = false;
	}
	
	public void log(String str) {
		Log.v("postrack", str);
		sendLogMessageToUI(str);
	}

}