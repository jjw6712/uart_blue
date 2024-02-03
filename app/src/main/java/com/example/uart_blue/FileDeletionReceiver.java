package com.example.uart_blue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

public class FileDeletionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // 디렉토리 URI를 SharedPreferences에서 가져옵니다.
        SharedPreferences prefs = context.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE);
        String directoryUriStr = prefs.getString("directoryUri", "");
        if (!directoryUriStr.isEmpty()) {
            Uri directoryUri = Uri.parse(directoryUriStr);
            // 파일 삭제 로직을 실행합니다.
            deleteFilesInDirectory(context, directoryUri);
        }
    }

    private void deleteFilesInDirectory(Context context, Uri directoryUri) {
        DocumentFile directory = DocumentFile.fromTreeUri(context, directoryUri);
        if (directory != null && directory.isDirectory()) {
            for (DocumentFile file : directory.listFiles()) {
                if (file.isFile() && file.getName().endsWith(".txt")) { // 텍스트 파일만 삭제
                    file.delete();
                }
            }
        }
    }
}


