package com.dlawoals2713.yamaha;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public class dlawoals2713App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}