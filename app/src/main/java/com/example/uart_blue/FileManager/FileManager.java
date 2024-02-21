package com.example.uart_blue.FileManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
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
        } catch (IOException e) {
            Log.e("FileManager", "Error compressing file", e);
        }
    }

    public static Uri combineTextFilesInRange(Context context, List<Uri> fileUris, String combinedFileName, Date eventTime, long beforeMillis, long afterMillis) {
        StringBuilder combinedContent = new StringBuilder();
        // 날짜 형식을 변경하여 각 행의 정확한 날짜와 시간을 파싱할 수 있도록 함
        SimpleDateFormat format = new SimpleDateFormat("yyyy, MM, dd, HH, mm, ss, SSS", Locale.getDefault());

        // eventTime 기준으로 실제 시간 범위 계산
        long startTime = eventTime.getTime() - beforeMillis;
        long endTime = eventTime.getTime() + afterMillis;

        for (Uri fileUri : fileUris) {
            try (InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        // 각 행의 날짜와 시간 정보 파싱
                        Date lineDate = format.parse(line.substring(0, line.lastIndexOf(',')));
                        if (lineDate != null && lineDate.getTime() >= startTime && lineDate.getTime() <= endTime) {
                            // 해당 범위 내 데이터만 combinedContent에 추가
                            combinedContent.append(line).append(System.lineSeparator());
                        }
                    } catch (ParseException e) {
                        Log.e("FileManager", "Error parsing date in file: " + fileUri, e);
                    }
                }
            } catch (IOException e) {
                Log.e("FileManager", "Error reading file: " + fileUri, e);
            }
        }

        // combinedContent에 저장된 내용을 실제 파일로 쓰기
        if (combinedContent.length() > 0) {
            File combinedFile = new File(context.getCacheDir(), combinedFileName);
            try (FileWriter writer = new FileWriter(combinedFile)) {
                writer.write(combinedContent.toString());
                return Uri.fromFile(combinedFile);
            } catch (IOException e) {
                Log.e("FileManager", "Error writing combined file", e);
                return null;
            }
        }

        return null;
    }



}

