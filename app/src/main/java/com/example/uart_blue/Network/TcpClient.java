package com.example.uart_blue.Network;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class TcpClient implements Runnable {
    private static final String TAG = "TCP 클라이언트";
    private Context context;
    private Uri fileUri;
    private boolean isZip;
    private String fileName;
    private byte[] binaryData;
    private volatile boolean running;
    private Socket socket;

    public TcpClient(Uri fileUri, Context context, boolean isZip, String fileName) {
        this.fileUri = fileUri;
        this.context = context;
        this.isZip = isZip;
        this.fileName = fileName;
        this.binaryData = null;
    }

    public TcpClient(Context context, byte[] binaryData) {
        this.context = context;
        this.binaryData = binaryData;
        this.isZip = false;
        this.fileUri = null;
        this.fileName = null;
    }

    public void startConnection() {
        running = true;
        Executors.newSingleThreadExecutor().submit(this);
    }

    public void stopConnection() {
        running = false;
        closeSocket();
    }

    @Override
    public void run() {
        SharedPreferences sharedPreferences = context.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE);
        String serverIP = sharedPreferences.getString("ServerIP", "");
        String ZportStr = sharedPreferences.getString("ZPort", "");
        String TPortStr = sharedPreferences.getString("TPort", "");
        int Zport = ZportStr.isEmpty() ? -1 : Integer.parseInt(ZportStr);
        int TPort = TPortStr.isEmpty() ? -1 : Integer.parseInt(TPortStr);
        int port = isZip ? Zport : TPort;

        if (serverIP.isEmpty() || port == -1) {
            handleLocalOperation();
            return;
        }
        int retryInterval = 10000; // 재시도 간격 10초
        int maxRetries = Integer.MAX_VALUE; // 최대 재시도 횟수 (무한대)
        int fileOffset = 0; // 파일 전송 위치 추적을 위한 변수
        while (running) {
            for (int retryCount = 0; retryCount < maxRetries; retryCount++) {
                try (Socket socket = new Socket()) {
                    this.socket = socket;
                    socket.connect(new InetSocketAddress(serverIP, port), retryInterval);

                    if (!running) return;

                    try (OutputStream outputStream = socket.getOutputStream()) {
                        if (isZip && fileUri != null) {
                            try (InputStream fileInputStream = context.getContentResolver().openInputStream(fileUri)) {
                                outputStream.write((fileName + "\n").getBytes());
                                outputStream.flush();

                                // 파일의 현재 위치로 이동
                                fileInputStream.skip(fileOffset);

                                byte[] buffer = new byte[4096];
                                int length;
                                while ((length = fileInputStream.read(buffer)) > 0) {
                                    if (!running) return;
                                    outputStream.write(buffer, 0, length);
                                    fileOffset += length; // 전송한 만큼 파일 위치 업데이트
                                }
                                outputStream.flush();
                            }
                        } else if (binaryData != null) {
                            outputStream.write(binaryData);
                            outputStream.flush();
                        }
                        broadcastNetworkState(true);
                        return;
                    }
                } catch (IOException e) {
                    if (!running || "No route to host".equals(e.getMessage())) {
                        // 예외 무시 및 로그 생략
                        closeSocket();
                        break;
                    }
                }

                try {
                    Thread.sleep(retryInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        broadcastNetworkState(false);
    }

    private void handleLocalOperation() {
        Log.e(TAG, "네트워크 연결을 위한 정보가 올바르게 입력되지 않았습니다.");
    }

    private void broadcastNetworkState(boolean success) {
        Intent intent = new Intent("com.example.uart_blue.UPDATE_NETWORK_STATE");
        intent.putExtra("networkState", success ? "연결됨" : "연결안됨");
        context.sendBroadcast(intent);
        if (success) {
            Log.d(TAG, "파일 전송 완료: " + binaryData);
        } else {
            Log.e(TAG, "파일 전송 실패");
        }
    }

    private void closeSocket() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket close error", e);
            } finally {
                socket = null;
            }
        }
    }
}
