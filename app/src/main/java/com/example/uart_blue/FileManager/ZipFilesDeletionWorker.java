package com.example.uart_blue.FileManager;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class ZipFilesDeletionWorker extends Worker {
    public ZipFilesDeletionWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE);
        String directoryUriStr = prefs.getString("directoryUri", "");
        if (!directoryUriStr.isEmpty()) {
            Uri directoryUri = Uri.parse(directoryUriStr);
            deleteZipFiles(directoryUri); // 구현된 .zip 파일 삭제 로직
        }
        return Result.success();
    }

    private void deleteZipFiles(Uri directoryUri) {
        DocumentFile directory = DocumentFile.fromTreeUri(this.getApplicationContext(), directoryUri);
        if (directory != null && directory.exists()) {
            for (DocumentFile file : directory.listFiles()) {
                if (file.getName().endsWith(".zip")) { // .zip 확장자를 가진 파일만 삭제
                    file.delete();
                }
            }
        }
    }
}

