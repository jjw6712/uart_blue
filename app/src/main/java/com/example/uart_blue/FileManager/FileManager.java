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
    public static List<Uri> findFilesInRange(Context context, Uri directoryUri, Date eventTime, long beforeMillis, long afterMillis) {
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
                            long diff = eventTime.getTime() - fileDate.getTime();
                            if (diff >= -beforeMillis && diff <= afterMillis) {
                                matchedFiles.add(file.getUri());
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return matchedFiles;
    }



    public static void combineAndZipFiles(Context context, List<Uri> fileUris, Uri outputZipUri) {
        Log.d("FileManager", "파일 압축을 시작합니다.");
        byte[] buffer = new byte[1024];

        try {
            OutputStream outputStream = context.getContentResolver().openOutputStream(outputZipUri);
            ZipOutputStream zos = new ZipOutputStream(outputStream);

            for (Uri fileUri : fileUris) {
                DocumentFile file = DocumentFile.fromSingleUri(context, fileUri);
                if (file != null) {
                    ZipEntry zipEntry = new ZipEntry(file.getName());
                    zos.putNextEntry(zipEntry);
                    try (InputStream inputStream = context.getContentResolver().openInputStream(fileUri)) {
                        int length;
                        while ((length = inputStream.read(buffer)) > 0) {
                            zos.write(buffer, 0, length);
                        }
                        zos.closeEntry();
                    } catch (IOException e) {
                        Log.e("FileManager", "Error zipping file: " + file.getName(), e);
                    }
                }
            }
            zos.close();
        } catch (IOException e) {
            Log.e("FileManager", "Error creating zip file: ", e);
        }
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

    public static void compressFilesInBackground(Context context, List<Uri> fileUris, Uri outputZipUri) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            combineAndZipFiles(context, fileUris, outputZipUri);
            Log.d("FileManager", "파일 압축이 백그라운드에서 완료되었습니다.");
            // 필요한 경우, UI 스레드에서 작업을 수행하기 위해 Handler를 사용할 수 있습니다.
            // new Handler(Looper.getMainLooper()).post(() -> {
            //     // UI 업데이트 작업...
            // });
        });
        executor.shutdown(); // 작업 완료 후 ExecutorService 종료
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

    public static Uri combineTextFiles(Context context, List<Uri> fileUris, String combinedFileName) {
        StringBuilder combinedContent = new StringBuilder();
        for (Uri fileUri : fileUris) {
            try (InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    combinedContent.append(line).append(System.lineSeparator());
                }
            } catch (IOException e) {
                Log.e("FileManager", "Error reading file: " + fileUri, e);
            }
        }

        // 임시 파일에 내용 저장
        try {
            File combinedFile = new File(context.getCacheDir(), combinedFileName);
            try (FileWriter writer = new FileWriter(combinedFile)) {
                writer.write(combinedContent.toString());
            }
            return Uri.fromFile(combinedFile);
        } catch (IOException e) {
            Log.e("FileManager", "Error writing combined file", e);
            return null;
        }
    }

}

