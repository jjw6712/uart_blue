package com.example.uart_blue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private StringBuilder usbInfo;
    private StringBuilder displayInfo;
    private String SelectDevice = "";
    private static final String TAG = "MyTag";
    private TextView textViewUSBStorageInfo;
    private ImageButton buttonOption;
    private final Handler handler = new Handler();
    private TextView tvStop, tvDrive, tvWH;
    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            updateUSBStorageInfo();
            //handler.postDelayed(this, 1000); // 10초마다 실행
        }
    };

    // BroadcastReceiver 정의
    private final BroadcastReceiver updateUIReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Intent에서 데이터를 추출합니다.
            int wh = intent.getIntExtra("wh", 0);
            //int humidity = intent.getIntExtra("humidity", 0);
            int battery = intent.getIntExtra("battery", 0);
            int blackout = intent.getIntExtra("blackout", 0);

            // 추출된 데이터를 UI에 반영합니다.
            TextView textViewWHCounterValue = findViewById(R.id.textViewWHCounterValue);
            TextView textViewHumidityValue = findViewById(R.id.textViewHuminityValue);
            TextView textViewInnerBatteryValue = findViewById(R.id.textViewInnerBaterryValue);
            TextView textViewEternalPowerValue = findViewById(R.id.textViewEternalPowerValue);

            textViewWHCounterValue.setText(String.valueOf(wh));
            //textViewHumidityValue.setText(String.format("%d%%", humidity));
            textViewInnerBatteryValue.setText(String.format("%d", battery));
            textViewEternalPowerValue.setText(blackout == 0 ? "ON" : "OFF");

            tvStop = findViewById(R.id.tvStop);
            tvDrive = findViewById(R.id.tvDrive);
            tvWH = findViewById(R.id.tvWH);
            //GPIO 동작 상태 체크를 위해 GPIOActivity에서 각 핀들의 활
            boolean is138Active = intent.getBooleanExtra("GPIO_138_ACTIVE", false);
            boolean is139Active = intent.getBooleanExtra("GPIO_139_ACTIVE", false);
            boolean is28Active = intent.getBooleanExtra("GPIO_28_ACTIVE", false);
            if(is138Active) {
                // GPIO 138이 활성화되었을 때의 동작 (TextView 활성화 등)
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvDrive.setVisibility(View.VISIBLE);
                        tvStop.setVisibility(View.INVISIBLE);
                        tvWH.setVisibility(View.INVISIBLE);
                    }
                });
            }
            if(is139Active){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvStop.setVisibility(View.VISIBLE);
                        tvDrive.setVisibility(View.INVISIBLE);
                        tvWH.setVisibility(View.INVISIBLE);
                    }
                });
            }
            if(is28Active){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvWH.setVisibility(View.VISIBLE);
                    }
                });
            }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonOption = findViewById(R.id.buttonOption);
        // BroadcastReceiver 등록
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.example.uart_blue.ACTION_UPDATE_UI");
        registerReceiver(updateUIReceiver, filter);

        buttonOption.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent optionIntent = new Intent(MainActivity.this, OptionActivity.class);
                optionIntent.putExtra("storageInfo", usbInfo.toString());
                startActivity(optionIntent);
            }
        });
        Intent getintent = getIntent();
        SelectDevice = getintent.getStringExtra("selectedStorage");
        //Log.d(TAG, "optionactivity에서 온 저장매체: "+ SelectDevice);

        textViewUSBStorageInfo = findViewById(R.id.textViewDatasizeValue); //저장소 용량tv
        handler.post(runnable); // 첫 실행 및 주기적 업데이트 시작
        updateUSBStorageInfo();
    }
    // 데이터 업데이트 메소드


    private void updateUSBStorageInfo() {
        StorageManager storageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
        usbInfo = new StringBuilder();
        displayInfo = new StringBuilder(); // Initialize displayInfo

        List<StorageVolume> storageVolumes = storageManager.getStorageVolumes();
        for (StorageVolume volume : storageVolumes) {
            if (volume.isRemovable()) {
                String description = volume.getDescription(this);
                //Log.d(TAG, "실제 물리 저장매체 정보: " + description);

                File path = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    path = volume.getDirectory();
                }
                if (path != null && path.exists()) {
                    StatFs statFs = new StatFs(path.getPath());
                    long freeBytes = statFs.getAvailableBytes();
                    double freeGB = freeBytes / 1073741824.0; // Convert bytes to GB

                    // Update usbInfo with all storage volumes
                    usbInfo.append(description)
                            .append(": ")
                            .append(String.format(Locale.US, "%.2fGB\n", freeGB));

                    // Update displayInfo based on the selected device
                    if (SelectDevice == null || SelectDevice.isEmpty() || SelectDevice.equals(description)) {
                        displayInfo.append(description)
                                .append(": ")
                                .append(String.format(Locale.US, "%.2fGB\n", freeGB));
                    }
                }
            }
        }
        //Log.d(TAG, "displayInfo: "+displayInfo);
        // Display either the selected device or all devices
        if (SelectDevice == null) {
            textViewUSBStorageInfo.setText("선택된 저장매체가 없습니다.");
        }else if (displayInfo.length() == 0) {
            textViewUSBStorageInfo.setText("연결된 저장매체가 없습니다.");
        } else {
            textViewUSBStorageInfo.setText(displayInfo.toString());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(runnable); // 활동이 파괴될 때 업데이트 중지
        // BroadcastReceiver 해제
        unregisterReceiver(updateUIReceiver);
    }
}
