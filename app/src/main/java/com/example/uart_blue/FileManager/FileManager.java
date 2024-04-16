package com.example.uart_blue.FileManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileManager {
    private static final String TAG = "파일압축매니저";

    public static List<Uri> findFilesInRange(Context context, Uri directoryUri, Date eventTime, long beforeMillis, long afterMillis) {
        Log.d(TAG, "eventTime: "+eventTime+"beforeMillis"+beforeMillis+"afterMillis"+afterMillis);
        List<Uri> matchedFiles = new ArrayList<>();
        // 날짜 형식을 "yyyy-MM-dd-HH-mm-ss"로 수정
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());

        DocumentFile directory = DocumentFile.fromTreeUri(context, directoryUri);
        if (directory != null && directory.exists()) {
            DocumentFile whDataFolder = directory.findFile("W.H.Data");
            if (whDataFolder != null && whDataFolder.exists()) {
                for (DocumentFile file : whDataFolder.listFiles()) {
                    try {
                        String fileName = file.getName();
                        // 파일 이름에서 날짜 부분만 추출
                        String dateString = fileName.substring(fileName.indexOf('-') + 1, fileName.lastIndexOf('.'));
                        Date fileDate = format.parse(dateString);
                        if (fileDate != null) {
                            if (fileDate.after(new Date(eventTime.getTime() - beforeMillis)) && fileDate.before(new Date(eventTime.getTime() + afterMillis))) {
                                matchedFiles.add(file.getUri());
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        Log.d(TAG, "matchedFiles: "+matchedFiles);
        return matchedFiles;
    }

    public static Uri createOutputZipUri(Context context, Uri directoryUri, String fileName) {
        try {
            DocumentFile directory = DocumentFile.fromTreeUri(context, directoryUri);
            if (directory != null && directory.exists()) {
                DocumentFile zipFile = directory.createFile("application/zip", fileName);
                if (zipFile != null) {
                    return zipFile.getUri();
                } else {
                    Log.e("FileManager", "ZIP 파일 생성 실패");
                    return null;
                }
            } else {
                Log.e("FileManager", "지정된 디렉토리가 존재하지 않음: " + directoryUri);
                return null;
            }
        } catch (Exception e) {
            Log.e("FileManager", "Error creating zip URI: ", e);
            return null;
        }
    }

    public static void compressFile(Context context, Uri inputFileUri, Uri outputZipUri) {
        try (InputStream inputStream = context.getContentResolver().openInputStream(inputFileUri);
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(context.getContentResolver().openOutputStream(outputZipUri)))) {
            zos.putNextEntry(new ZipEntry(inputFileUri.getLastPathSegment()));
            byte[] buffer = new byte[1024];
            int count;
            while ((count = inputStream.read(buffer)) != -1) {
                zos.write(buffer, 0, count);
            }
            zos.closeEntry();
            Log.e("FileManager", "파일 압축 완료11");
            SharedPreferences sharedPreferences = context.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("GPIO_28_ACTIVE", false);
            editor.apply();
        } catch (IOException e) {
            Log.e("FileManager", "Error compressing file", e);
        }
    }

    public static Uri combineTextFilesInRange(Context context, List<Uri> fileUris, String combinedFileName, Date eventTime, long beforeMillis, long afterMillis) {
        File combinedFile = new File(context.getCacheDir(), combinedFileName);
        SimpleDateFormat format = new SimpleDateFormat("yyyy, MM, dd, HH, mm, ss", Locale.getDefault());
        long startTime = eventTime.getTime() - beforeMillis;
        long endTime = eventTime.getTime() + afterMillis;
        List<LineEntry> entries = new ArrayList<>();

        // 파일 읽기 및 파싱
        for (Uri fileUri : fileUris) {
            try (InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        Date lineDate = format.parse(line.substring(0, line.lastIndexOf(',')));
                        if (lineDate != null && lineDate.getTime() >= startTime && lineDate.getTime() <= endTime) {
                            entries.add(new LineEntry(lineDate, line));
                        }
                    } catch (ParseException e) {
                        Log.e("FileManager", "Error parsing date in file: " + fileUri, e);
                    }
                }
            } catch (IOException e) {
                Log.e("FileManager", "Error reading file: " + fileUri, e);
            }
        }

        // 시간 순으로 정렬
        Collections.sort(entries, Comparator.comparing(LineEntry::getDate));

        // 정렬된 데이터 저장
        try (FileWriter writer = new FileWriter(combinedFile)) {
            for (LineEntry entry : entries) {
                writer.append(entry.getText()).append(System.lineSeparator());
            }
        } catch (IOException e) {
            Log.e("FileManager", "Error writing combined file", e);
            return null;
        }

        return Uri.fromFile(combinedFile);
    }


}

