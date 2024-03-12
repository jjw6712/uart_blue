package com.example.uart_blue.FileManager;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
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
            String zipFileName = combinedFileName.replace(".txt", ".zip"); // .txt를 .zip으로 변경
            Uri outputZipUri = FileManager.createOutputZipUri(context, directoryUri, zipFileName);
            if (outputZipUri != null) {
                FileManager.compressFile(context, combinedFileUri, outputZipUri); // 여기서 combinedFileUri는 .txt 파일을 가리킴
                // 임시 .txt 파일 삭제
                new File(combinedFileUri.getPath()).delete();
                // 네트워크 연결 정보를 가져옵니다.
                SharedPreferences sharedPreferences = context.getSharedPreferences("MySharedPrefs", MODE_PRIVATE);
                String serverIP = sharedPreferences.getString("ServerIP", "");
                String ZportStr = sharedPreferences.getString("ZPort", "");
                String TPortStr = sharedPreferences.getString("TPort", "");
                int Zport = ZportStr.isEmpty() ? -1 : Integer.parseInt(ZportStr);
                int TPort = TPortStr.isEmpty() ? -1 : Integer.parseInt(TPortStr);


                // 네트워크 연결 정보가 유효한 경우에만 TcpClient를 통해 파일 전송을 시도합니다.
                if (!serverIP.isEmpty() || ZportStr.isEmpty()||TPortStr.isEmpty()) {
                    new TcpClient(outputZipUri, context, true, zipFileName).execute();
                    Log.d(TAG, "서버로 압축파일 전송!!!!!!");
                } else {
                    // 네트워크 연결 정보가 유효하지 않은 경우, 로컬에서의 처리를 로그로 남깁니다.
                    Log.e(TAG, "네트워크 정보가 유효하지 않습니다. 로컬에서 파일 처리를 완료합니다.");
                }

                return outputZipUri;
            }
        }
        return null;
    }


    @Override
    protected void onPostExecute(Uri result) {
        super.onPostExecute(result);
        if (result != null) {
            Log.d("FileManager", "File processing completed: " + result.toString());
            // 여기서 결과 처리를 진행하십시오 (예: UI 업데이트).
        } else {
            Log.e("FileManager", "File processing failed.");
        }
    }
}
