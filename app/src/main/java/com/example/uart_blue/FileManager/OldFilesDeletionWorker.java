package com.example.uart_blue.FileManager;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class OldFilesDeletionWorker extends Worker {
    private static final String TAG = "1시간 이전의 파일 삭제";

    public OldFilesDeletionWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE);
        String directoryUriStr = prefs.getString("directoryUri", "");
        if (!directoryUriStr.isEmpty()) {
            Uri directoryUri = Uri.parse(directoryUriStr);
            deleteOldFiles(directoryUri); // 구현된 파일 삭제 로직
        }
        // 작업이 완료된 후, 다시 이 작업을 스케줄링하여 반복
        //scheduleNextRun();
        return Result.success();
    }

    private void deleteOldFiles(Uri directoryUri) {
        DocumentFile directory = DocumentFile.fromTreeUri(getApplicationContext(), directoryUri);
        if (directory != null && directory.isDirectory()) {
            DocumentFile whDataDirectory = directory.findFile("W.H.Data");
            if (whDataDirectory != null && whDataDirectory.isDirectory()) {
                DocumentFile[] files = whDataDirectory.listFiles();
                if (files.length == 0) {
                    Log.e(TAG, "W.H.Data 디렉토리에 파일이 없습니다.");
                    return;
                }
                for (DocumentFile file : files) {
                    // 작업이 취소되었는지 확인
                    if (isStopped()) {
                        Log.e(TAG, "작업이 취소됨: 파일 삭제 작업 중단");
                        return; // 작업 중단
                    }

                    // 파일이 정상적이고, 파일 이름이 null이 아니며, .txt로 끝나는 경우에만 처리
                    if (file.isFile()) {
                        String fileName = file.getName();
                        if (fileName != null && fileName.endsWith(".txt")) {
                            long fileCreationTime = file.lastModified();
                            long oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000); // 1시간 전
                            if (fileCreationTime < oneHourAgo) {
                                boolean deleted = file.delete(); // 파일 삭제 시도
                                if (deleted) {
                                    Log.e(TAG, "1시간보다 오래된 파일 삭제 완료: " + fileName);
                                } else {
                                    Log.e(TAG, "파일 삭제 실패: " + fileName);
                                }
                            }
                        }
                    }
                }
            } else {
                Log.e(TAG, "W.H.Data 디렉토리를 찾을 수 없습니다.");
            }
        } else {
            Log.e(TAG, "지정된 경로에서 디렉토리를 찾을 수 없습니다.");
        }
    }



    private void scheduleNextRun() {
        OneTimeWorkRequest nextRunRequest = new OneTimeWorkRequest.Builder(OldFilesDeletionWorker.class)
                // 딜레이 없이 바로 다음 작업을 스케줄링
                .addTag("deleteOldFiles")
                .build();
        WorkManager.getInstance(getApplicationContext()).enqueue(nextRunRequest);
    }
}

