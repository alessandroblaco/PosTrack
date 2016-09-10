package com.example.postrack;
import android.content.Context;
import android.os.Environment;
import android.support.v4.app.Fragment;

import java.io.File;

public class Settings {
    private static final String DB_NAME = "lacells.db";


    private static final Object lock = new Object();
    private static Settings instance;

    private final Context context;

    private Settings(Context context) {
        this.context = context;
    }

    public static Settings with(Fragment fragment) {
        return with(fragment.getContext());
    }

    public static Settings with(Context context) {
        if(context == null) {
            throw new NullPointerException();
        }

        if(instance == null) {
            synchronized (lock) {
                if(instance == null) {
                    instance = new Settings(context);
                }
            }
        }

        return instance;
    }

    public File databaseDirectory() {
        File extDir = new File(Environment.getExternalStorageDirectory().getPath());

        if (extDir.exists() && extDir.isDirectory() && extDir.canRead() && extDir.canWrite()) {
            return extDir;
        }
        return context.getExternalFilesDir(null);
    }

    public File currentDatabaseFile() {
        return new File(databaseDirectory(), DB_NAME);
    }
}