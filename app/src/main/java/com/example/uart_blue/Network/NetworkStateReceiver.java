package com.example.uart_blue.Network;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.uart_blue.R;

public class NetworkStateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String state = intent.getStringExtra("networkState");

        Intent uiIntent = new Intent("com.example.uart_blue.UPDATE_UI_STATE");
        uiIntent.putExtra("networkState", state);
        LocalBroadcastManager.getInstance(context).sendBroadcast(uiIntent);
    }
}
