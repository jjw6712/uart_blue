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
            try {
                deleteOldFiles(directoryUri);
            } catch (Exception e) {
                // 예외 발생 시 로그 출력 대신 적절한 예외 처리를 하거나, 필요한 경우 사용자에게 알림 등을 보냄
                // 예: 오류 메시지를 사용자에게 보여주기, 오류 리포팅 등
                Log.d(TAG, "파일 삭제 실패");
            }
        }
        return Result.success();
    }

    private void deleteOldFiles(Uri directoryUri) throws Exception {
        DocumentFile directory = DocumentFile.fromTreeUri(getApplicationContext(), directoryUri);
        if (directory == null || !directory.isDirectory()) {
            throw new Exception("지정된 경로에서 디렉토리를 찾을 수 없습니다.");
        }

        DocumentFile whDataDirectory = directory.findFile("W.H.Data");
        if (whDataDirectory == null || !whDataDirectory.isDirectory()) {
            throw new Exception("W.H.Data 디렉토리를 찾을 수 없습니다.");
        }

        DocumentFile[] files = whDataDirectory.listFiles();
        if (files.length == 0) {
            throw new Exception("W.H.Data 디렉토리에 파일이 없습니다.");
        }

        for (DocumentFile file : files) {
            if (isStopped()) {
                throw new Exception("작업이 취소됨: 파일 삭제 작업 중단");
            }

            if (!file.isFile() || file.getName() == null || !file.getName().endsWith(".txt")) {
                continue; // 조건에 맞지 않는 파일은 건너뛰기
            }

            long fileCreationTime = file.lastModified();
            long oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000); // 1시간 전
            if (fileCreationTime < oneHourAgo) {
                if (!file.delete()) {
                    throw new Exception("파일 삭제 실패: " + file.getName());
                }
            }
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

