package codes.wetter.graffitiar;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.ar.sceneform.ux.ArFragment;

public class MainActivity extends AppCompatActivity {

  public static final String TAG = "GRAFFITIAR";
  private static final double MIN_OPENGL_VERSION = 3.0;

  private ArFragment arFragment;

  @Override
  @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (!checkIsSupportedDeviceOrFinish(this)) {
      return;
    }
    setContentView(R.layout.activity_main);

    arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
    // TODO..
  }

  public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
    // Android version
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
      String error = "Sceneform SDK requires Android N or later";
      Log.e(TAG, error);
      Toast.makeText(activity, error, Toast.LENGTH_LONG).show();
      activity.finish();
      return false;
    }

    // OpenGL version
    ActivityManager activityManager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
    String openGlVersionString = activityManager.getDeviceConfigurationInfo().getGlEsVersion();
    if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
      String error = "Sceneform SDK requires OpenGL ES 3.0 or later";
      Log.e(TAG, error);
      Toast.makeText(activity, error, Toast.LENGTH_LONG)
              .show();
      activity.finish();
      return false;
    }

    return true;
  }
}
