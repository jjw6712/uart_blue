package com.example.uart_blue.FileManager;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

public class ZipFilesDeletionWorker extends Worker {
    public ZipFilesDeletionWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d("ZipFilesDeletionWorker", "ZIPFileWorker 실행됨");
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE);

        long lastRunTime = prefs.getLong("lastRunTimeZipDeletion", 0);
        long currentTime = System.currentTimeMillis();
        int days = prefs.getInt("intervalDays", 0); // 사용자 설정 기본값 21일
        Log.d("ZipFilesDeletionWorker", String.valueOf(days));
        long intervalMillis = TimeUnit.DAYS.toMillis(days);

        if (currentTime - lastRunTime >= intervalMillis) {
            String directoryUriStr = prefs.getString("directoryUri", "");
            if (!directoryUriStr.isEmpty()) {
                Uri directoryUri = Uri.parse(directoryUriStr);
                deleteZipFiles(directoryUri); // 구현된 .zip 파일 삭제 로직
            }

            // 마지막 실행 시간 업데이트
            prefs.edit().putLong("lastRunTimeZipDeletion", currentTime).apply();
            return Result.success();
        } else {
            Log.d("ZipFilesDeletionWorker", "아직 삭제할 시간이 아닙니다.");
            return Result.success();
        }
    }

    private void deleteZipFiles(Uri directoryUri) {
        DocumentFile directory = DocumentFile.fromTreeUri(getApplicationContext(), directoryUri);
        if (directory != null && directory.exists()) {
            for (DocumentFile file : directory.listFiles()) {
                if (file.getName() != null && file.getName().endsWith(".zip")) { // .zip 확장자를 가진 파일만 삭제
                    file.delete();
                }
            }
        }
    }
}

