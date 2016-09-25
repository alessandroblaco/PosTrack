package com.example.postrack;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
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
    static final int MSG_ASK_FOR_LOG = 3;
    static final int MSG_SET_LOG_MESSAGE = 3;
	static final int MSG_REGISTER_CLIENT = 4;
	static final int MSG_UNREGISTER_CLIENT = 5;
	static final int MSG_ASK_FOR_UPDATE = 6;
	static final int MSG_ASK_FOR_GSM_UPDATE = 7;

	final Messenger mMessenger = new Messenger(new IncomingHandler()); // Target we publish for clients to send messages to IncomingHandler.

	public static boolean isRunning()
	{
		return isRunning;
	}
    private List<String> appLog = new ArrayList<>();
    private static final int MAX_LOG_LINES = 80;
	SmsManager sms;

	@Override
	public IBinder onBind(Intent intent) {
		return mMessenger.getBinder();
	}


	private LinkedList<Location> locationHistory = new LinkedList<>();
	private Location lastBestLocation;
	private Date lastGsmUpdate;
	private Boolean GPSEnabled = true;
	private Boolean NWEnabled = true;
	private Boolean GSMEnabled = false;
	private Boolean lastnear;
	private Runnable willCheckForMovement = new Runnable() {
		@Override
		public void run() {
			checkForMovement();
		}
	};
	private final Handler handler = new Handler();
	private final Service as = this;



	private static final long MIN_DISTANCE_FOR_UPDATE = 100; // 100 meters
	private PhoneStateListener telephonyListener = new PhoneStateListener() {
		// When something changes in the telephone state, we update the GSM location
		public void onServiceStateChanged(ServiceState serviceState) {
			updateGSM();
		}
		public void onCellLocationChanged(CellLocation location) {
			updateGSM();
		}
		public void onCellInfoChanged(List<android.telephony.CellInfo> cellInfo) {
			updateGSM();
		}
	};


	@Override
	public void onCreate() {
        isRunning = true;
        super.onCreate();
        Log.i("AppLocationService", "Service Started.");

        locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
        telephonyManager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        telephonyHelper = new TelephonyHelper(this);
		telephonyHelper.setErrorLogger(
				// set error reporter for GSM locator, so as to show some errors in the app log
				new TelephonyHelper.GsmErrorReport() {
					@Override
					public void report(String result) {
						log("GSM Error: " + result);
					}
				}
		);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
		canUseProvider = preferences.getBoolean("use_android_provider", true);
		canUseGSM = preferences.getBoolean("position_service_enabled", true);
		lastnear = preferences.getBoolean("last_near", false);
		GpsProviderUpdateInterval = (int)(Float.valueOf(preferences.getString("nw_interval", "5")) * 60000f);
		NwProviderUpdateInterval = (int)(Float.valueOf(preferences.getString("nw_interval", "5")) * 60000f);
        updateProvidersConnection();
        preferences.registerOnSharedPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
		sms = SmsManager.getDefault();
    }
// this is called at startup and when some preferences changes.
// It mainly applies to changes the "Use location provided by Android" and "Look for position",
// "Update GPS every (minutes)" and "Update WiFi every (minutes)" settings

	public void updateProvidersConnection() {
		if (canUseProvider  && canUseGSM) {
			// if all providers are available, request updates from NW and GPS
            try {
				locationManager.removeUpdates(this);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
						NwProviderUpdateInterval, MIN_DISTANCE_FOR_UPDATE, this);
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
						GpsProviderUpdateInterval, MIN_DISTANCE_FOR_UPDATE, this);
            } catch (SecurityException e) {
                log("Permission error: " + e.toString());
            }



			// check if network provider is enabled and calls onProvider*abled accordingly.
			// If enabled update the position to the last one known by the provider
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


			// check if GPS provider is enabled and calls onProvider*abled accordingly.
			// If enabled update the position to the last one known by the provider
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
				// ask not to receive no more updates from NW and GPS
                locationManager.removeUpdates(this);
            } catch (SecurityException e) {
                log("Permission error: " + e.toString());
            }
            GPSEnabled = false;
            NWEnabled = false;
			if (canUseGSM) {
				// if can use GSM ("Look for position" settings) start or update position from GSM
				if (!GSMEnabled) {
					startGSM();
				} else {
					needGsmUpdate = true;
					updateGSM();
				}
			} else {
				// if cannot use GSM ("Look for position" settings) stop getting GSM updates
				stopGSM();
			}
        }
	}

    private OnSharedPreferenceChangeListener sBindPreferenceSummaryToValueListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
			// When one of these settings is changed, apply the new setting
            switch (key) {
                case "use_android_provider":
                    canUseProvider = sharedPreferences.getBoolean(key, false);
                    updateProvidersConnection();
                    break;
                case "gps_interval":
                    GpsProviderUpdateInterval = (int)(Float.valueOf(sharedPreferences.getString("gps_interval", "5")) * 60000f);
                    updateProvidersConnection();
                    break;
                case "nw_interval":
                    NwProviderUpdateInterval = (int)(Float.valueOf(sharedPreferences.getString("nw_interval", "5")) * 60000f);
                    updateProvidersConnection();
                    break;
                case "position_service_enabled":
                    canUseGSM = sharedPreferences.getBoolean(key, false);
                    updateProvidersConnection();
                    break;
			}
		}
    };
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i("AppLocationService", "Received start id " + startId + ": " + intent);
		return START_STICKY; // run until explicitly stopped.
	}

	private void sendLocationUpdateToUI() {
		// Notify a location update to the UI (map). The last 10 positions are provided
		for (int i=mClients.size()-1; i>=0; i--) {
			try {
				//Send data as a String
				Bundle b = new Bundle();
				Message msg = Message.obtain(null, MSG_SET_LOCATION_HISTORY);
                ArrayList<Location> al = new ArrayList<>();
                al.addAll(locationHistory);
                b.putParcelableArrayList ("history", al);

                msg.setData(b);
				mClients.get(i).send(msg);

			} catch (RemoteException e) {
				// The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
				mClients.remove(i);
			}
		}
	}

	private void sendLogMessageToUI(String tmsg) {
		// add a log in the UI
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
					// the client asks for the position history
                    sendLocationUpdateToUI();
                    break;
				case MSG_ASK_FOR_LOG:
					// the client asks for the whole log
					sendLog();
					break;
				case MSG_ASK_FOR_GSM_UPDATE:
					// the client asks for a forced GSM update
					needGsmUpdate = true;
					updateGSM();
					break;
				default:
					super.handleMessage(msg);
			}
		}
	}

	
	public AppLocationService() {
	}

	public void checkForMovement() {
		// check from location history whether the phone is leaving or is arriving (or none)
		handler.removeCallbacks(willCheckForMovement); // if there was a timeout waiting to check the movement, remove it
		// constructing a location representing the home
		Location home = new Location("home");
		home.setLatitude(Double.valueOf(preferences.getString("home_latitude", "45.5032028")));
		home.setLongitude(Double.valueOf(preferences.getString("home_longitude", "9.1561746")));
		float distance = lastBestLocation.distanceTo(home); // calculate the distance between home and the last known position of the phone
		float max_distance = Float.valueOf(preferences.getString("home_radius", "500.0"));
		String arrivo;
		Boolean near;
		// compare the distance with the boundary radius
		if (distance > max_distance) {
			// we are far from home
			log("\n" + "Distance from home is " + String.valueOf(distance) + "m: you are far away, " + (!lastnear ? "and" : "but") + " emonpi knows you are " + (lastnear ? "close" : "far away"));
			arrivo = "0";
			near = false;
		} else {
			// we are near home
			log("\n" + "Distance from home is " + String.valueOf(distance) + "m: You are close, " + (lastnear ? "and" : "but") + " emonpi knows you are " + (lastnear ? "close" : "far away"));
			arrivo = "1";
			near = true;
		}
		if (distance > max_distance && lastnear || distance <= max_distance && !lastnear) {
			log("So maybe we should tell emonpi...");
			// either
			// 		emonpi thinks we are far away but now we are close
			// or
			// 		emonpi thinks we are close but now we are out of the circle
			//
			// But maybe it's just something strange: we are on the boundary of the max_distance circle or the phone
			// has attached to a weird antenna.
			// We'd better check that either the last 3 positions confirm this change or that the last position is older than 100 seconds

			int poll = Integer.valueOf(preferences.getString("min_poll", "3")); // default is 3 times
			int min_time = Integer.valueOf(preferences.getString("min_time", "100")) * 1000; // default is 100 seconds
			long act_time = min_time;
			Date now = new Date();
			Location loc;
			Boolean changeForSure = false; // by default we do not change our mind. Only if the 3 times or the 100s conditions are verified we do it
			Iterator<Location> listIterator = locationHistory.descendingIterator(); // iterate from the most recent to the oldest
			Log.i("postrack", "Starting locationHistory iteration");
			// loop through the location history (from the most recent to the oldest) to check the 3 times and 100s condition
			while (listIterator.hasNext()) {
				loc = listIterator.next();
				Log.i("postrack", "lat:" + String.valueOf(loc.getLatitude()) + " lon:" + String.valueOf(loc.getLatitude()) + " prov:" + loc.getProvider() + " time:" + DateFormat.format("yyyy-MM-dd hh:mm:ss", loc.getTime()) + " poll:" + String.valueOf(poll));
				if (loc.distanceTo(home) > max_distance && near || loc.distanceTo(home) <= max_distance && !near) {
					// there is a position which is not coherent with the last one (before we reached any condition)
					Log.i("postrack", "dist: " + String.valueOf(loc.distanceTo(home)) + " <> " + String.valueOf(max_distance) + " -> no, break");
					// it makes no sense to go on, we exit from the cycle and let changeForSure be false
					break;
				} else {
					Log.i("postrack", "dist: " + String.valueOf(loc.distanceTo(home)) + " <> " + String.valueOf(max_distance) + " -> continue");
				}
				if (poll == 0 // if the 3 times condition has been satisfied
						|| // or
					loc.getTime() < now.getTime() - min_time // if the 100s condition has been satisfied
				) {
					// We are definitely near to Home: we set changeForSure to true and we exit the cycle
					changeForSure = true;
					Log.i("postrack", "change beacause of " + (poll == 0 ? "poll" : "time"));
					break;
				}
				poll--; // another location is coherent, maybe we get to 3 times
				act_time = min_time - now.getTime() + loc.getTime(); // the time we should wait with no new uncoherent
				// location to satisfy the 100s condition. It is used to set a timer to make another check if
				// no condition is satisfied
			}
			if (changeForSure) {
				// We just tell which is the satisfied condition
				if (poll == 0) {
					log("Ok, it's the " + preferences.getString("min_poll", "3") + "nth time it looks like that, so it must be it");
				} else {
					log("Ok, " + preferences.getString("min_time", "100") + "s passed and it's still like that, so it must be it");
				}
				// so we update the "last near status"
				lastnear = distance <= max_distance;
				// and we save it as a preference, so that if the app crashes/is killed this value is preserved
				SharedPreferences.Editor editor = preferences.edit();
				editor.putBoolean("last_near", lastnear);
				editor.apply();
				if (preferences.getBoolean("show_notifications", false)) {
					// if the user wants it, we show a notification in the phone
					notifyPass(lastnear, distance);
				}
				// we build the HTTP GET request url, using parameters arrivo and phone
				String url = preferences.getString("update_url", "http://emonbovisa.hostinggratis.it/app.php?arrivo=") + arrivo + "&phone=" + preferences.getString("device_name", "Asdrubale");
				if (preferences.getBoolean("update_url_enabled", true)) {
					// if the user is allowing for the update
					log("Calling " + url);
					// we create the request
					RequestQueue queue = Volley.newRequestQueue(this);
					StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
							new Response.Listener<String>() {
								@Override
								public void onResponse(String response) {
									// if it is successful we are done
									log("Response is: " + response);
								}
							}, new Response.ErrorListener() {
						@Override
						public void onErrorResponse(VolleyError error) {
							//if there is an error
							if (error.getCause() != null) {
								log("Network Error: " + error.getCause().getMessage());
							} else {
								log("Network Error: " + error.getMessage());
							}
							if (preferences.getBoolean("use_sms", false)) {
								// if the user wants to use the sms communication
								if (preferences.getString("sms_number", "").length() < 9) {
									//check for number validity
									log(preferences.getString("sms_number", "") + " is not a valid phone number");
								} else {
									// if the number is ok we build the SMS message: 131984_<<near>>_<<phone>>
									String sms_text = preferences.getString("sms_prefix", "") + (lastnear ? "1" : "0") + "_" + preferences.getString("device_name", "Asdrubale");
									log("Now sending SMS \"" + sms_text + "\" to " + preferences.getString("sms_number", ""));

									PendingIntent sentPI = PendingIntent.getBroadcast(as, 0, new Intent("SMS_SENT"), 0);

									registerReceiver(new BroadcastReceiver() {
										// build the receiver (check if sms was actually sent)
										@Override
										public void onReceive(Context arg0, Intent arg1) {
											int resultCode = getResultCode();
											switch (resultCode) {
												case Activity.RESULT_OK:
													log("SMS sent successfully");
													break;
												case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
													log("SMS error: generic failure");
													break;
												case SmsManager.RESULT_ERROR_NO_SERVICE:
													log("SMS error: no service");
													break;
												case SmsManager.RESULT_ERROR_NULL_PDU:
													log("SMS error: null pdu");
													break;
												case SmsManager.RESULT_ERROR_RADIO_OFF:
													log("SMS error: radio off");
													break;
											}
										}
									}, new IntentFilter("SMS_SENT"));
									// send sms
									sms.sendTextMessage(preferences.getString("sms_number", ""), null, sms_text, sentPI, null);
								}
							}
						}
					});
					// Add the request to the RequestQueue (run the request).
					queue.add(stringRequest);
				} else {
					// if the user has forbidden server updates
					log("Not calling url (disabled) -> " + arrivo);
				}
			} else {
				// we wait a reasonable amount of time, in case no position update happen in between
				log("We'd better wait " + String.valueOf((act_time / 1000) ) + "s, we aren't still sure enough");
				handler.postDelayed(willCheckForMovement, act_time);
			}
		} else {
			log("No need for update");
		}
		log("\n");
	}
	
	@Override
	public void onLocationChanged(Location location) {
		// check if location is valid
		if (location == null) {
			log("Null location");
		} else {
			log("Location changed from : " + location.getProvider());
			if (// check priority: if gps is enabled it makes no sense using gsm position
					(lastBestLocation != null && lastBestLocation.getProvider().equals("gsm"))
							||
							(location.getProvider().equals(LocationManager.GPS_PROVIDER))
							||
							(location.getProvider().equals(LocationManager.NETWORK_PROVIDER) && !GPSEnabled)
							||
							(location.getProvider().equals("gsm") && !NWEnabled && !GPSEnabled)
					) {

				locationHistory.addLast(location);
				// limit the location history to the last 7 positions
				if (locationHistory.size() > 7) {
					locationHistory.pollFirst();
				}
				lastBestLocation = location;
				// update the map
				sendLocationUpdateToUI();
				log("\n" + DateFormat.format("yyyy-MM-dd hh:mm:ss", location.getTime()) + ": New position from : " + location.getProvider());
				log("Lat: " + String.valueOf(location.getLatitude()) + ", Lon: " + String.valueOf(location.getLongitude()));
				// check if the position change is meaningfule (i.e. if the user is leaving or entering home
				checkForMovement();
			}
		}
	}

    public void notifyPass(Boolean near, Float distance) {
        Date now = new Date();
		// build the notification text
        String title, content = "At " + DateFormat.format("hh:mm", now) +  " you were " + String.valueOf(distance) + " meters away";
        if (near) {
            title = "You are coming back home!";
        } else {
            title = "You are going out";
        }
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(content);
        // Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(this, MainActivity.class);

        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your application to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(MainActivity.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // mId allows you to update the notification later on. We keep the same id so as to use always
		// the same notification
        mNotificationManager.notify(1, mBuilder.build());
    }
	
	@Override
	public void onProviderDisabled(String provider) {

        NWEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        GPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
		if (!NWEnabled && !GPSEnabled && canUseProvider && canUseGSM) {
			if (!GSMEnabled) {
				log("Disabled Android location provider");
			}
			// if GPS and NW are disabled and the user settings does not prevent it, we start GSM positioning
			startGSM();
		}
	}
	
	@Override
	public void onProviderEnabled(String provider) {
        NWEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        GPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if ((NWEnabled || GPSEnabled) && canUseProvider) {
			log("Enabled Android location provider: (" + (NWEnabled&&GPSEnabled ? "nw+gps" : (NWEnabled ? "nw only" : "gps only")) + ")");
            // GPS or NW were enabled and we are allowed to use them: we should no more need GSM positioning
			stopGSM();
        }
	}
	
	public void startGSM() {
		if (!GSMEnabled) {
			//check if it is not already started
			GSMEnabled = true;
			log("Started GSM");
			// binds event listener: when the phone status changes, updateGSM() will be called
			telephonyManager.listen(
				telephonyListener,
				PhoneStateListener.LISTEN_CELL_INFO |
				PhoneStateListener.LISTEN_CELL_LOCATION |
				PhoneStateListener.LISTEN_SERVICE_STATE
			);
			// force Gsm update lookup
            needGsmUpdate = true;
			//lets do an update right away
			updateGSM();
		}
	}
	
	public void stopGSM() {
		if (GSMEnabled) {
			// check it was not already disabled
			GSMEnabled = false;
			log("Stopped GSM");
			// remove event listeners
			telephonyManager.listen(
					telephonyListener,
					PhoneStateListener.LISTEN_NONE
				);
            if (canUseProvider && canUseGSM) {
				// if it is allowed, restart GPS and NW positioning
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
		// avoid multiple update within 5 seconds
		if (needGsmUpdate || lastGsmUpdate.getTime() + 500 < System.currentTimeMillis()) {
			lastGsmUpdate = new Date();
			if (needGsmUpdate || (lastBestLocation == null || lastBestLocation.getTime() + 5*1000.0 < System.currentTimeMillis())) {
				log("Update from GSM");
				//try {
					// use antennae and database to get location
					// to do this we use some code taken from
					// https://github.com/n76/Local-GSM-Backend/tree/master/app/src/main/java/org/fitchfamily/android/gsmlocation
					// in particular these classes where adopted and slightly modified:
					// TelephonyHelper, CellLocationDatabase, LocarionCalculator, LocationHelper,
					// QueryArgs, QueryCache, SqlWhereBuilder

					Location loc = telephonyHelper.getLocationEstimate();
					if (loc != null)  {
						// reset forced update
						needGsmUpdate = false;
						// deal with the new location
						onLocationChanged(loc);
					}
				//} catch (Exception e) {
				//	log("Error in GSM: " + e.toString());
				//}
			}
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
		// add the string to the array of log
        if (str.length() > 0) {
            appLog.add(str);
        }
        // remove the first line if log is too large
        if (appLog.size() >= MAX_LOG_LINES) {
            appLog.remove(0);
        }
		// update log in UI
        sendLog();
    }
    public void sendLog() {
		// array to text
        String log = "";
        for (String stra : appLog) {
            log += stra + "\n";
        }
		// ask ui update
		sendLogMessageToUI(log);
	}

}