package com.example.postrack.database;

import com.example.postrack.Settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;


public class CellLocationDatabase {

    private static final String TABLE_CELLS = "cells";
    private static final String COL_LATITUDE = "latitude";
    private static final String COL_LONGITUDE = "longitude";
    private static final String COL_ACCURACY = "accuracy";
    private static final String COL_SAMPLES = "samples";
    private static final String COL_MCC = "mcc";
    private static final String COL_MNC = "mnc";
    private static final String COL_LAC = "lac";
    private static final String COL_CID = "cid";

    private static final String[] COLUMNS = new String[] {
            COL_LATITUDE,
            COL_LONGITUDE,
            COL_ACCURACY,
            COL_SAMPLES,
            COL_MCC,
            COL_MNC,
            COL_LAC,
            COL_CID
    };

    // the indexes are always the same because we always use the COLUMNS array
    private static final int INDEX_LATITUDE = 0;
    private static final int INDEX_LONGITUDE = 1;
    private static final int INDEX_ACCURACY = 2;
    private static final int INDEX_SAMPLES = 3;
    private static final int INDEX_MCC = 4;
    private static final int INDEX_MNC = 5;
    private static final int INDEX_LAC = 6;
    private static final int INDEX_CID = 7;
    private static final String TAG = "database";
    private static final boolean DEBUG = true;

    private SQLiteDatabase database;

    private QueryCache queryCache = new QueryCache();

    private SharedPreferences preferences;
    private UnknownCellDb unknownCellDb;
    private Context context;

    public CellLocationDatabase(Context c) {
        context = c;
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }


    private void openDatabase() {
        if (database == null) {
           String db_path = preferences.getString("database_file", Environment.getExternalStorageDirectory().getPath() + "/lacells.db");
           Log.i("database", "Attempting to open database.");
           Log.i("database", "Using db " + db_path);
            File db_file = new File(db_path);

            if (db_file.exists() && db_file.canRead()) {
                try {
                    database = SQLiteDatabase.openDatabase(db_file.getAbsolutePath(),
                            null,
                            SQLiteDatabase.NO_LOCALIZED_COLLATORS);
                    unknownCellDb = new UnknownCellDb(context);
                } catch (Exception e) {
                    Log.e("database", "Error opening database: "+ e.getMessage());

                    database = null;
                   
                }
            } else {
                Log.e(TAG, "Unable to open database " + db_path);

                database = null;
            }
        }
    }

    public synchronized Location query(final Integer mcc, final Integer mnc, final int cid, final int lac) {
        SqlWhereBuilder queryBuilder = new SqlWhereBuilder();

        // short circuit duplicate calls
        QueryArgs args = new QueryArgs(mcc, mnc, cid, lac);

        if(queryCache.contains(args)) {
            return queryCache.get(args);
        }

        openDatabase();
        if (database == null) {
            if (DEBUG) {
                Log.i(TAG, "Unable to open cell tower database file.");
            }

            return null;
        }

        // Build up where clause and arguments based on what we were passed
        if (mcc != null) {
            queryBuilder
                    .columnIs(COL_MCC, String.valueOf(mcc))
                    .and();
        }

        if (mnc != null) {
            queryBuilder
                    .columnIs(COL_MNC, String.valueOf(mnc))
                    .and();
        }

        queryBuilder
                .columnIs(COL_LAC, String.valueOf(lac))
                .and()
                .columnIs(COL_CID, String.valueOf(cid));

        Cursor cursor =
                database.query(TABLE_CELLS, COLUMNS,
                        queryBuilder.selection(), queryBuilder.selectionArgs(), null, null, null);

        try {
            if (cursor != null) {
                if (cursor.getCount() > 0) {
                    LocationCalculator locationCalculator = new LocationCalculator();
                    // Get weighted average of tower locations and coverage
                    // range from reports by the various providers (OpenCellID,
                    // Mozilla location services, etc.)
                    while (!cursor.isLast()) {
                        cursor.moveToNext();
                        int db_mcc = cursor.getInt(INDEX_MCC);
                        int db_mnc = cursor.getInt(INDEX_MNC);
                        int db_lac = cursor.getInt(INDEX_LAC);
                        int db_cid = cursor.getInt(INDEX_CID);
                        double thisLat = cursor.getDouble(INDEX_LATITUDE);
                        double thisLng = cursor.getDouble(INDEX_LONGITUDE);
                        double thisRng = cursor.getDouble(INDEX_ACCURACY);
                        int thisSamples = cursor.getInt(INDEX_SAMPLES);

                        if (DEBUG) {
                            Log.i(TAG, "query result: " +
                                    db_mcc + ", " + db_mnc + ", " + db_lac + ", " + db_cid + ", " +
                                    thisLat + ", " + thisLng + ", " + thisRng + ", " + thisSamples);
                        }

                        locationCalculator.add(thisLat, thisLng, thisSamples, thisRng);
                    }
                    if (DEBUG) {
                        Log.i(TAG, "Final result: " + locationCalculator);
                    }

                    Location cellLocInfo = locationCalculator.toLocation();
                    queryCache.put(args, cellLocInfo);

                    if (DEBUG) {
                        Log.i(TAG, "Cell info found: " + args.toString());
                    }

                    return cellLocInfo;
                } else {
                    Log.i(TAG, "DB Cursor empty for: " + args.toString());
                    queryCache.putUnresolved(args);
                    unknownCellDb.addUnknownCell(mcc, mnc, cid, lac);
                }
            } else {
                Log.i(TAG, "DB Cursor null for: " + args.toString());
                queryCache.putUnresolved(args);
                unknownCellDb.addUnknownCell(mcc, mnc, cid, lac);
            }

            return null;
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
    }
}