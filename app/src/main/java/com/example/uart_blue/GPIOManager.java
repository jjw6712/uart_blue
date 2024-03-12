package com.example.uart_blue;

import android.content.Context;

public class GPIOManager {
    private static GPIOActivity gpioActivity = null;

    public static synchronized GPIOActivity getInstance(Context context) throws IllegalAccessException, InstantiationException {
        if (gpioActivity == null) {
            gpioActivity = new GPIOActivity(OptionActivity.class.newInstance(), context.getApplicationContext());
        }
        return gpioActivity;
    }
}


