package com.example.postrack;

/**
 * Created by alessandro on 18/09/16.
 */
import android.app.Dialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TimePicker;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import android.os.Environment;

public class FilePreference extends DialogPreference {
    private static final String PARENT_DIR = "..";
    private Context activity;
    private Dialog dialog;
    private ListView list;
    private File temp_path;
    private String file_path;
    private String extension = ".db";

    public FilePreference(Context ctxt, AttributeSet attrs) {
        super(ctxt, attrs);
        activity = ctxt;
    }

    @Override
    protected View onCreateDialogView() {
        list = new ListView(activity);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int which, long id) {
                String fileChosen = (String) list.getItemAtPosition(which);
                File chosenFile = getChosenFile(fileChosen);
                if (chosenFile.isDirectory()) {
                    refresh(chosenFile);
                } else {
                    if (callChangeListener(chosenFile.getAbsolutePath())) {
                        persistString(chosenFile.getAbsolutePath());
                    }
                    getDialog().dismiss();
                }
                Log.v("postrack", "clicked " + fileChosen);
            }
        });
        refresh(Environment.getExternalStorageDirectory());

        return(list);
    }

    private File getChosenFile(String fileChosen) {
        if (fileChosen.equals(PARENT_DIR)) {
            return temp_path.getParentFile();
        } else {
            return new File(temp_path, fileChosen);
        }
    }
    /**
     * Sort, filter and display the files for the given path.
     */

    private void refresh(File path) {
        temp_path = path;
        if (path.exists()) {
            File[] dirs = path.listFiles(new FileFilter() {
                @Override public boolean accept(File file) {
                    return (file.isDirectory() && file.canRead());
                }
            });
            File[] files = path.listFiles(new FileFilter() {
                @Override public boolean accept(File file) {
                    if (!file.isDirectory()) {
                        if (!file.canRead()) {
                            return false;
                        } else if (extension == null) {
                            return true;
                        } else {
                            return file.getName().toLowerCase().endsWith(extension);
                        }
                    } else {
                        return false;
                    }
                }
            });

            // convert to an array
            int i = 0;
            String[] fileList;
            if (path.getParentFile() == null) {
                fileList = new String[dirs.length + files.length];
            } else {
                fileList = new String[dirs.length + files.length + 1];
                fileList[i++] = PARENT_DIR;
            }
            Arrays.sort(dirs);
            Arrays.sort(files);
            for (File dir : dirs) { fileList[i++] = dir.getName(); }
            for (File file : files ) { fileList[i++] = file.getName(); }

            // refresh the user interface

            list.setAdapter(new ArrayAdapter(activity,
                    android.R.layout.simple_list_item_1, fileList) {
                @Override public View getView(int pos, View view, ViewGroup parent) {
                    view = super.getView(pos, view, parent);
                    ((TextView) view).setSingleLine(true);
                    return view;
                }
            });
        }
    }

    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);

        //picker.setCurrentHour(lastHour);
        //picker.setCurrentMinute(lastMinute);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return(a.getString(index));
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        if (restoreValue) {
            file_path=getPersistedString(Environment.getExternalStorageDirectory().getPath() + "/lacells.db");
        }
        else {
            file_path=Environment.getExternalStorageDirectory().getPath() + "/lacells.db";
        }
        persistString(file_path);

    }

    public String toString() {
        return file_path;
    }
}