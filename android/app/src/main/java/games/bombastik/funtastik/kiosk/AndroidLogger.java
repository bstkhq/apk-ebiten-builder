package @@APP_ID@@;

import android.util.Log;
import @@JAVA_PKG@@.@@GO_PKG@@.Logger;

public class AndroidLogger implements Logger {
  @Override
  public void info(String msg) {
    Log.i("BSTK", msg);
  }

  @Override
  public void error(String msg) {
    Log.e("BSTK", msg);
  }
}