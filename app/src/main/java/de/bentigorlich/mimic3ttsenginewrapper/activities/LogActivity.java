package de.bentigorlich.mimic3ttsenginewrapper.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import de.bentigorlich.mimic3ttsenginewrapper.R;
import de.bentigorlich.mimic3ttsenginewrapper.entities.LogEntity;

public class LogActivity extends AppCompatActivity {
    Timer refreshLogInterval;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        Activity parent = this;
        refreshLogInterval = new Timer();
        refreshLogInterval.schedule(new TimerTask() {
            @Override
            public void run() {
                parent.runOnUiThread(() -> populateLogView());
            }
        }, 0, 10000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        refreshLogInterval.cancel();
    }

    private void populateLogView() {
        Gson gson = new Gson();
        List<LogEntity> logs = new ArrayList<>();
        for (File logFile : getLogFiles()) {
            try {
                BufferedReader br = new BufferedReader(new FileReader(logFile));
                br.lines().forEach(line -> {
                    LogEntity lr = gson.fromJson(line, LogEntity.class);
                    logs.add(lr);
                });
                br.close();
            } catch (IOException ignored) {
            }
        }

        logs.sort(Comparator.comparing(l -> l.Timestamp));

        LinearLayout logView = findViewById(R.id.logContainer);
        logView.removeAllViews();
        for (LogEntity curr : logs) {
            TextView t = new TextView(this);
            t.setText(curr.GetText(false));
            t.setTextColor(getLevelColor(curr.Level));
            t.setTextIsSelectable(true);
            logView.addView(t);
        }
    }

    private int getLevelColor(String level) {
        switch (level.toLowerCase()) {
            case "info":
            default:
                return getResources().getColor(com.google.android.material.R.color.design_default_color_surface, getTheme());

            case "warning":
                return getResources().getColor(R.color.colorTextWarning, getTheme());

            case "severe":
                return getResources().getColor(R.color.colorTextSevere, getTheme());
        }
    }

    private List<File> getLogFiles() {
        File cacheDir = getDataDir();
        List<File> logList = new ArrayList<>();
        File[] fileList = cacheDir.listFiles();
        for (File f: fileList) {
            String fileName = f.getName();
            int pointIndex = fileName.lastIndexOf(".");
            if(f.exists() && pointIndex > 0) {
                String ext = fileName.substring(pointIndex);
                if(ext.equals(".log")) {
                    logList.add(f);
                }
            }
        }
        return logList;
    }

    public void onMenuItemClick(@NonNull MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.menu_logs_delete) {
            for(File f : getLogFiles()) {
                try {
                    FileWriter fw = new FileWriter(f.getAbsolutePath(), false);
                    fw.write("");
                    fw.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            populateLogView();
        }
    }
}