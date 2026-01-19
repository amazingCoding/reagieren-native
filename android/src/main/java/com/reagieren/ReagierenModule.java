package com.reagieren;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.modules.network.OkHttpClientProvider;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ReagierenModule extends ReactContextBaseJavaModule {
  private final ReactApplicationContext reactContext;
  private static final String PREFS_NAME = "reagieren_prefs";
  private static final String DEVICE_ID_KEY = "device_id";
  private static final String VERSIONS_DIR = "reagieren_versions";

  public ReagierenModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return "Reagieren";
  }

  @ReactMethod
  public void update(String apiKey, String projectId, Promise promise) {
    String deviceId = getDeviceId();
    String nativeVersion = getNativeVersion();

    String url = "https://api.reagieren.com/api/projects/" + projectId + "/versions/latest";

    HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
    urlBuilder.addQueryParameter("platform", "android");
    urlBuilder.addQueryParameter("min_native_version", nativeVersion);

    Request request = new Request.Builder()
        .url(urlBuilder.build())
        .addHeader("X-API-Key", apiKey)
        .build();

    OkHttpClient client = OkHttpClientProvider.getOkHttpClient();
    client.newCall(request).enqueue(new Callback() {
      @Override
      public void onFailure(Call call, IOException e) {
        promise.reject("NETWORK_ERROR", e.getMessage());
      }

      @Override
      public void onResponse(Call call, Response response) throws IOException {
        if (!response.isSuccessful()) {
          promise.reject("API_ERROR", "Unexpected code " + response);
          return;
        }

        try {
          String jsonString = response.body().string();
          JSONObject json = new JSONObject(jsonString);
          if (json.has("download_url")) {
            String downloadUrl = json.getString("download_url");
            downloadAndUnzip(downloadUrl, deviceId, promise);
          } else {
            promise.reject("PARSE_ERROR", "No download_url in response");
          }
        } catch (Exception e) {
          promise.reject("JSON_ERROR", e.getMessage());
        }
      }
    });
  }

  private String getDeviceId() {
    SharedPreferences prefs = reactContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    String deviceId = prefs.getString(DEVICE_ID_KEY, null);
    if (deviceId == null) {
      deviceId = UUID.randomUUID().toString();
      prefs.edit().putString(DEVICE_ID_KEY, deviceId).apply();
    }
    return deviceId;
  }

  private String getNativeVersion() {
    try {
      PackageInfo pInfo = reactContext.getPackageManager().getPackageInfo(reactContext.getPackageName(), 0);
      return pInfo.versionName;
    } catch (PackageManager.NameNotFoundException e) {
      return "0.0.0";
    }
  }

  private void downloadAndUnzip(String downloadUrl, String deviceId, Promise promise) {
    // Prepare URL with query params if needed
    HttpUrl parsed = HttpUrl.parse(downloadUrl);
    if (parsed == null) {
      promise.reject("URL_ERROR", "Invalid download url");
      return;
    }

    HttpUrl.Builder urlBuilder = parsed.newBuilder();
    // Check if params exist, if not add them
    if (parsed.queryParameter("deviceId") == null && downloadUrl.contains("/api/download")) {
      urlBuilder.addQueryParameter("deviceId", deviceId);
    }
    if (parsed.queryParameter("platform") == null && downloadUrl.contains("/api/download")) {
      urlBuilder.addQueryParameter("platform", "android");
    }

    Request request = new Request.Builder().url(urlBuilder.build()).build();
    OkHttpClient client = OkHttpClientProvider.getOkHttpClient();

    client.newCall(request).enqueue(new Callback() {
      @Override
      public void onFailure(Call call, IOException e) {
        promise.reject("DOWNLOAD_ERROR", e.getMessage());
      }

      @Override
      public void onResponse(Call call, Response response) throws IOException {
        if (!response.isSuccessful()) {
          promise.reject("DOWNLOAD_API_ERROR", "Failed to download " + response);
          return;
        }

        File versionsDir = new File(reactContext.getFilesDir(), VERSIONS_DIR);
        if (!versionsDir.exists()) {
          versionsDir.mkdirs();
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        File destDir = new File(versionsDir, timestamp);

        try {
          // Save zip temporarily
          File tempZip = new File(versionsDir, timestamp + ".zip");
          FileOutputStream fos = new FileOutputStream(tempZip);
          fos.write(response.body().bytes());
          fos.close();

          // Unzip
          unzip(tempZip, destDir);

          // Delete zip
          tempZip.delete();

          // Cleanup old versions
          cleanupOldVersions(versionsDir);

          WritableMap result = Arguments.createMap();
          result.putString("path", destDir.getAbsolutePath());
          promise.resolve(result);

        } catch (Exception e) {
          promise.reject("UNZIP_ERROR", e.getMessage());
        }
      }
    });
  }

  private void unzip(File zipFile, File targetDirectory) throws IOException {
    ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));
    try {
      ZipEntry ze;
      int count;
      byte[] buffer = new byte[8192];
      while ((ze = zis.getNextEntry()) != null) {
        File file = new File(targetDirectory, ze.getName());
        File dir = ze.isDirectory() ? file : file.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs())
          throw new IOException("Failed to ensure directory: " + dir.getAbsolutePath());
        if (ze.isDirectory())
          continue;

        FileOutputStream fout = new FileOutputStream(file);
        try {
          while ((count = zis.read(buffer)) != -1)
            fout.write(buffer, 0, count);
        } finally {
          fout.close();
        }
      }
    } finally {
      zis.close();
    }
  }

  private void cleanupOldVersions(File versionsDir) {
    File[] files = versionsDir.listFiles(File::isDirectory);
    if (files == null || files.length <= 3)
      return;

    Arrays.sort(files, Comparator.comparingLong(File::lastModified));

    // Remove oldest
    for (int i = 0; i < files.length - 3; i++) {
      deleteRecursive(files[i]);
    }
  }

  private void deleteRecursive(File fileOrDirectory) {
    if (fileOrDirectory.isDirectory())
      for (File child : fileOrDirectory.listFiles())
        deleteRecursive(child);
    fileOrDirectory.delete();
  }

  @ReactMethod
  public void restart() {
    android.app.Activity currentActivity = getCurrentActivity();
    if (currentActivity == null) {
      return;
    }

    reactContext.runOnUiQueueThread(new Runnable() {
      @Override
      public void run() {
        try {
          // Create intent to restart the app
          PackageManager pm = currentActivity.getPackageManager();
          android.content.Intent intent = pm.getLaunchIntentForPackage(currentActivity.getPackageName());
          if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);

            // Use AlarmManager to trigger the restart after a short delay
            int flags = android.app.PendingIntent.FLAG_CANCEL_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
              flags |= android.app.PendingIntent.FLAG_IMMUTABLE;
            }

            android.app.PendingIntent mPendingIntent = android.app.PendingIntent.getActivity(
                currentActivity,
                123456,
                intent,
                flags);

            android.app.AlarmManager mgr = (android.app.AlarmManager) currentActivity
                .getSystemService(Context.ALARM_SERVICE);
            mgr.set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);

            // Kill current process
            System.exit(0);
          }
        } catch (Exception e) {
          // Fallback or log
          e.printStackTrace();
        }
      }
    });
  }

  public static File getLatestBundle(Context context) {
    File versionsDir = new File(context.getFilesDir(), VERSIONS_DIR);
    if (!versionsDir.exists() || !versionsDir.isDirectory())
      return null;

    File[] files = versionsDir.listFiles(File::isDirectory);
    if (files == null || files.length == 0)
      return null;

    // Sort by modification time (newest first)
    Arrays.sort(files, new Comparator<File>() {
      @Override
      public int compare(File f1, File f2) {
        return Long.compare(f2.lastModified(), f1.lastModified());
      }
    });

    File newestFolder = files[0];
    // Note: Adjust "index.android.bundle" if your unzipped structure is different
    File bundleFile = new File(newestFolder, "index.android.bundle");

    return bundleFile.exists() ? bundleFile : null;
  }
}
