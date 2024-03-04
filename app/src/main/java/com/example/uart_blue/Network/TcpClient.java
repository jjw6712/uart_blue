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
        int port = Integer.parseInt(isZip ? sharedPreferences.getString("ZPort", "") : sharedPreferences.getString("TPort", ""));
        int tenMinutes = 600000; // 10분을 밀리초 단위로

        try {
            Socket socket = new Socket();
            // 서버 주소와 포트 번호를 사용하여 연결을 시도하고 타임아웃을 설정
            socket.connect(new InetSocketAddress(serverIP, port), tenMinutes);
            try (OutputStream outputStream = socket.getOutputStream()) {
                if (isZip && fileUri != null) {
                    // .zip 파일 전송 로직
                    try (InputStream fileInputStream = context.getContentResolver().openInputStream(fileUri)) {
                        // 파일 이름을 서버로 전송
                        outputStream.write((fileName + "\n").getBytes());
                        outputStream.flush(); // 파일 이름 전송 후 버퍼 비우기

                        // 파일 내용 전송
                        byte[] buffer = new byte[4096];
                        int length;
                        while ((length = fileInputStream.read(buffer)) > 0) {
                            outputStream.write(buffer, 0, length);
                        }
                        outputStream.flush(); // 파일 전송 완료 후 버퍼 비우기
                    }
                } else if (binaryData != null) {
                    // 바이너리 데이터 전송 로직
                    outputStream.write(binaryData);
                    outputStream.flush(); // 바이너리 데이터 전송 완료 후 버퍼 비우기
                }
                return true; // 전송 성공
            } catch (IOException e) {
                e.printStackTrace();
                return false; // 전송 실패
            } finally {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false; // 연결 실패
        }
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
