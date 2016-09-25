package com.example.postrack.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by alessandro on 25/09/16.
 */
public class UnknownCellDb {
    private Context context;
    private FeedReaderDbHelper mDbHelper;
    public UnknownCellDb(Context c) {
        context = c;
        mDbHelper = new FeedReaderDbHelper(context);
    };

    /* Inner class that defines the table contents */
    public static class CellEntry implements BaseColumns {
        public static final String TABLE_NAME = "cells";
        public static final String COLUMN_NAME_MCC = "mcc";
        public static final String COLUMN_NAME_MNC = "mnc";
        public static final String COLUMN_NAME_CID = "cid";
        public static final String COLUMN_NAME_LAC = "lac";
        public static final String COLUMN_NAME_TIME = "time";
    }

    public Format formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static final String INT_TYPE = " INTEGER";
    private static final String TEXT_TYPE = " TEXT";
    private static final String COMMA_SEP = ",";
    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + CellEntry.TABLE_NAME + " (" +
                    CellEntry._ID + " INTEGER PRIMARY KEY," +
                    CellEntry.COLUMN_NAME_TIME + TEXT_TYPE + COMMA_SEP +
                    CellEntry.COLUMN_NAME_MCC + INT_TYPE + COMMA_SEP +
                    CellEntry.COLUMN_NAME_MNC + INT_TYPE + COMMA_SEP +
                    CellEntry.COLUMN_NAME_CID + INT_TYPE + COMMA_SEP +
                    CellEntry.COLUMN_NAME_LAC + INT_TYPE + ", UNIQUE(" +
                    CellEntry.COLUMN_NAME_MCC + COMMA_SEP +
                    CellEntry.COLUMN_NAME_MNC + COMMA_SEP +
                    CellEntry.COLUMN_NAME_CID + COMMA_SEP +
                    CellEntry.COLUMN_NAME_LAC + ") ON CONFLICT REPLACE )";


    private static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + CellEntry.TABLE_NAME;
    public class FeedReaderDbHelper extends SQLiteOpenHelper {
        // If you change the database schema, you must increment the database version.
        public static final int DATABASE_VERSION = 1;
        public static final String DATABASE_NAME = "UnknownCells.db";

        public FeedReaderDbHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SQL_CREATE_ENTRIES);
        }
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // This database is only a cache for online data, so its upgrade policy is
            // to simply to discard the data and start over
            db.execSQL(SQL_DELETE_ENTRIES);
            onCreate(db);
        }
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onUpgrade(db, oldVersion, newVersion);
        }
    }
    public void addUnknownCell(final Integer mcc, final Integer mnc, final int cid, final int lac) {
        // later we could check on unwiredlabs.com or combain.com
        if (mcc > 0 && mnc > 0 && cid > 0 && lac > 0) {
            // Gets the data repository in write mode
            SQLiteDatabase db = mDbHelper.getWritableDatabase();

            // Create a new map of values, where column names are the keys
            ContentValues values = new ContentValues();
            values.put(CellEntry.COLUMN_NAME_MCC, mcc);
            values.put(CellEntry.COLUMN_NAME_MNC, mnc);
            values.put(CellEntry.COLUMN_NAME_CID, cid);
            values.put(CellEntry.COLUMN_NAME_LAC, lac);
            values.put(CellEntry.COLUMN_NAME_TIME, formatter.format(new Date()));

            // Insert the new row, returning the primary key value of the new row
            db.insertWithOnConflict(CellEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

}
