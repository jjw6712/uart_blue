package com.example.uart_blue.Network;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;


public class TcpClient extends AsyncTask<Void, Void, Boolean> {

    private static final String TAG = "TCP 클라이언트";
    private Context context;
    private Uri fileUri; // .zip 파일 URI
    private boolean isZip;
    private String fileName; // .zip 파일 이름
    // 바이너리 데이터 전송을 위한 추가 필드
    private byte[] binaryData;

    // .zip 파일 전송을 위한 생성자
    public TcpClient(Uri fileUri, Context context, boolean isZip, String fileName) {
        this.fileUri = fileUri;
        this.context = context;
        this.isZip = isZip;
        this.fileName = fileName;
        this.binaryData = null; // 바이너리 데이터는 null로 초기화
    }

    // 바이너리 데이터 전송을 위한 생성자
    public TcpClient(Context context, byte[] binaryData) {
        this.context = context;
        this.binaryData = binaryData;
        this.isZip = false; // 바이너리 데이터 전송을 나타냄
        this.fileUri = null; // .zip 파일 관련 필드는 사용하지 않음
        this.fileName = null;
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("MySharedPrefs", MODE_PRIVATE);
        String serverIP = sharedPreferences.getString("ServerIP", "");
        int Zport = Integer.parseInt(sharedPreferences.getString("ZPort", ""));
        int TPort = Integer.parseInt(sharedPreferences.getString("TPort", ""));
        int port = Integer.parseInt(String.valueOf(isZip ? Zport : TPort));

        int retryInterval = 10000; // 재시도 간격 10초
        int maxRetries = Integer.MAX_VALUE; // 최대 재시도 횟수 (무한대)

        for (int retryCount = 0; retryCount < maxRetries; retryCount++) {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(serverIP, port), retryInterval);
                try (OutputStream outputStream = socket.getOutputStream()) {
                    if (isZip && fileUri != null) {
                        try (InputStream fileInputStream = context.getContentResolver().openInputStream(fileUri)) {
                            outputStream.write((fileName + "\n").getBytes());
                            outputStream.flush();

                            byte[] buffer = new byte[4096];
                            int length;
                            while ((length = fileInputStream.read(buffer)) > 0) {
                                outputStream.write(buffer, 0, length);
                            }
                            outputStream.flush();
                        }
                    } else if (binaryData != null) {
                        outputStream.write(binaryData);
                        outputStream.flush();
                    }
                    return true;
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            // 재시도 간격만큼 대기
            try {
                Thread.sleep(retryInterval);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return false;
            }
        }

        return false; // 최대 재시도 횟수를 초과하면 실패
    }



    @Override
    protected void onPostExecute(Boolean success) {
        Intent intent = new Intent("com.example.uart_blue.UPDATE_NETWORK_STATE");
        intent.putExtra("networkState", success ? "연결됨" : "연결안됨");
        context.sendBroadcast(intent);
        if (success) {
            Log.d("TcpClientTask", "파일 전송 완료: " + binaryData);
        } else {
            Log.e("TcpClientTask", "파일 전송 실패");
        }
    }
}
