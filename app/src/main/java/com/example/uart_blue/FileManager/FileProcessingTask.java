package com.example.uart_blue.FileManager;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.example.uart_blue.Network.TcpClient;

import java.io.File;
import java.util.Date;
import java.util.List;

public class FileProcessingTask extends AsyncTask<Void, Void, Uri> {
    private static final String TAG = "파일압축 서비스";
    private Context context;
    private List<Uri> fileUris;
    private String combinedFileName;
    private Date eventTime;
    private long beforeMillis, afterMillis;
    private Uri directoryUri;
    private BroadcastReceiver networkStateReceiver;
    TcpClient tcpClient;

    public FileProcessingTask(Context context, List<Uri> fileUris, String combinedFileName, Date eventTime, long beforeMillis, long afterMillis, Uri directoryUri) {
        this.context = context;
        this.fileUris = fileUris;
        this.combinedFileName = combinedFileName;
        this.eventTime = eventTime;
        this.beforeMillis = beforeMillis;
        this.afterMillis = afterMillis;
        this.directoryUri = directoryUri;
    }

    @SuppressLint("WrongThread")
    @Override
    protected Uri doInBackground(Void... voids) {
        Uri combinedFileUri = FileManager.combineTextFilesInRange(context, fileUris, combinedFileName, eventTime, beforeMillis, afterMillis);
        if (combinedFileUri != null) {
            String zipFileName = combinedFileName.replace(".txt", ".zip");
            Uri outputZipUri = FileManager.createOutputZipUri(context, directoryUri, zipFileName);
            if (outputZipUri != null) {
                FileManager.compressFile(context, combinedFileUri, outputZipUri);
                new File(combinedFileUri.getPath()).delete();
                return outputZipUri;
            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(Uri outputZipUri) {
        if (outputZipUri != null) {
            Log.d(TAG, "ZIP 파일이 성공적으로 생성되었습니다: " + outputZipUri.getPath());
            // ZIP 파일이 생성된 후 네트워크 상태 브로드캐스트 수신자 등록
            registerNetworkStateReceiver(outputZipUri);
            // TCP 클라이언트 실행하여 네트워크 상태 체크
            executeTcpClient(outputZipUri);
        } else {
            Log.e(TAG, "Failed to create or compress the file.");
        }
    }

    private void registerNetworkStateReceiver(Uri outputZipUri) {
        networkStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String networkState = intent.getStringExtra("networkState");
                if ("연결됨".equals(networkState)) {
                    sendFileToServer(outputZipUri);
                } else {
                    Log.e(TAG, "Unable to connect to server. ZIP file saved locally.");
                }
                context.unregisterReceiver(networkStateReceiver); // Unregister the receiver once done
            }
        };
        IntentFilter filter = new IntentFilter("com.example.uart_blue.UPDATE_NETWORK_STATE");
        context.registerReceiver(networkStateReceiver, filter);
    }

    private void executeTcpClient(Uri outputZipUri) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE);
        String serverIP = sharedPreferences.getString("ServerIP", "");
        String ZportStr = sharedPreferences.getString("ZPort", "");
        String TPortStr = sharedPreferences.getString("TPort", "");

        if (!serverIP.isEmpty() && !ZportStr.isEmpty() && !TPortStr.isEmpty()) {
            String zipFileName = combinedFileName.replace(".txt", ".zip");
            tcpClient = new TcpClient(outputZipUri, context, true, zipFileName);
            tcpClient.startConnection();

        } else {
            Log.e(TAG, "Network information is invalid. File processing completed locally.");
        }
    }

    private void sendFileToServer(Uri outputZipUri) {
        // TCP 클라이언트를 통해 네트워크 상태를 이미 확인했으므로, 직접 서버로 전송
        Log.d(TAG, "Sending file to server: " + outputZipUri.getPath());
        tcpClient = new TcpClient(outputZipUri, context, true, combinedFileName.replace(".txt", ".zip"));
        tcpClient.startConnection();
    }
}

