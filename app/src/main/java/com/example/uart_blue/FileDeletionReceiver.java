package com.example.uart_blue;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

public class FileDeletionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // 디렉토리 URI를 SharedPreferences에서 가져옵니다.
        SharedPreferences prefs = context.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE);
        String directoryUriStr = prefs.getString("directoryUri", "");
        Log.d(TAG, "파일삭제 URI: "+ directoryUriStr);
        if (!directoryUriStr.isEmpty()) {
            Uri directoryUri = Uri.parse(directoryUriStr);
            // 파일 삭제 로직을 실행합니다.
            deleteWHDataDirectory(context, directoryUri);
        }
    }

    private void deleteWHDataDirectory(Context context, Uri directoryUri) {
        DocumentFile directory = DocumentFile.fromTreeUri(context, directoryUri);
        if (directory != null && directory.isDirectory()) {
            DocumentFile whDataDirectory = directory.findFile("W.H.Data");
            if (whDataDirectory != null && whDataDirectory.isDirectory()) {
                // "W.H.Data" 폴더가 존재하면 폴더를 삭제합니다.
                whDataDirectory.delete();
            }
        }
    }
}



