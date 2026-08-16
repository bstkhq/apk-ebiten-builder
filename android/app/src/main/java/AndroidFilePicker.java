package @@APP_ID@@;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Android's system document picker exposed through the optional gomobile bridge. */
final class AndroidFilePicker implements OptionalFilePickerBridge.Services {
  private static final String DEFAULT_MIME_TYPE = "*/*";

  private final AppCompatActivity activity;
  private final OptionalFilePickerBridge.ResultSink resultSink;
  private final ActivityResultLauncher<Intent> launcher;
  private final ExecutorService copies = Executors.newSingleThreadExecutor();
  private final AtomicBoolean requestPending = new AtomicBoolean();
  private final AtomicBoolean closed = new AtomicBoolean();

  AndroidFilePicker(
      AppCompatActivity activity,
      OptionalFilePickerBridge.ResultSink resultSink) {
    this.activity = activity;
    this.resultSink = resultSink;
    launcher = activity.registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
          requestPending.set(false);
          if (closed.get()) {
            return;
          }

          Intent data = result.getData();
          if (result.getResultCode() != Activity.RESULT_OK
              || data == null
              || data.getData() == null) {
            dispatchResult("", "");
            return;
          }

          Uri uri = data.getData();
          try {
            copies.execute(() -> copySelection(uri));
          } catch (RejectedExecutionException e) {
            if (!closed.get()) {
              dispatchError(e);
            }
          }
        });
  }

  @Override
  public void open(String mimeType) {
    String requestedType = mimeType == null ? "" : mimeType.trim();
    if (requestedType.isEmpty()) {
      requestedType = DEFAULT_MIME_TYPE;
    }
    final String resolvedType = requestedType;

    activity.runOnUiThread(() -> {
      if (closed.get()) {
        return;
      }
      if (!requestPending.compareAndSet(false, true)) {
        dispatchResult("", "a file picker request is already in progress");
        return;
      }

      try {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(resolvedType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        launcher.launch(intent);
      } catch (RuntimeException e) {
        requestPending.set(false);
        dispatchError(e);
      }
    });
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    requestPending.set(false);
    launcher.unregister();
    copies.shutdownNow();
  }

  private void copySelection(Uri uri) {
    File output = null;
    try {
      File directory = new File(activity.getCacheDir(), "picked-files");
      if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
        throw new IOException("cannot create file picker cache directory");
      }

      output = File.createTempFile("picked-", suffixFor(uri), directory);
      ContentResolver resolver = activity.getContentResolver();
      try (InputStream input = resolver.openInputStream(uri);
           FileOutputStream target = new FileOutputStream(output)) {
        if (input == null) {
          throw new IOException("document provider returned no input stream");
        }
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
          if (closed.get() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("file picker closed while copying selection");
          }
          target.write(buffer, 0, read);
        }
      }

      if (closed.get()) {
        deleteQuietly(output);
        return;
      }
      dispatchSelection(output);
    } catch (Exception e) {
      deleteQuietly(output);
      if (!closed.get()) {
        dispatchError(e);
      }
    }
  }

  private String suffixFor(Uri uri) {
    String mimeType = activity.getContentResolver().getType(uri);
    if (mimeType == null) {
      return ".media";
    }
    int slash = mimeType.indexOf('/');
    if (slash < 0 || slash == mimeType.length() - 1) {
      return ".media";
    }
    String subtype = mimeType.substring(slash + 1).toLowerCase(Locale.ROOT);
    int suffix = subtype.indexOf('+');
    if (suffix >= 0) {
      subtype = subtype.substring(0, suffix);
    }
    if (!subtype.matches("[a-z0-9]{1,12}")) {
      return ".media";
    }
    return "." + subtype;
  }

  private void dispatchResult(String path, String message) {
    if (closed.get()) {
      return;
    }
    activity.runOnUiThread(() -> {
      if (!closed.get()) {
        resultSink.onResult(path, message);
      }
    });
  }

  private void dispatchSelection(File output) {
    if (closed.get()) {
      deleteQuietly(output);
      return;
    }
    activity.runOnUiThread(() -> {
      if (closed.get()) {
        deleteQuietly(output);
      } else {
        resultSink.onResult(output.getAbsolutePath(), "");
      }
    });
  }

  private void dispatchError(Exception error) {
    String message = error.getMessage();
    dispatchResult("", message == null || message.isEmpty() ? error.toString() : message);
  }

  private static void deleteQuietly(File file) {
    if (file != null && file.exists()) {
      file.delete();
    }
  }
}
