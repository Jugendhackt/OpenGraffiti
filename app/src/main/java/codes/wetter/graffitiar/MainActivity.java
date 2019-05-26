package codes.wetter.graffitiar;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Trackable;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.pes.androidmaterialcolorpickerdialog.ColorPicker;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;

public class MainActivity extends AppCompatActivity {

  public static final String TAG = "GRAFFITIAR-LOG";

  private final int MIN_ANDROID_VERSION = Build.VERSION_CODES.N;
  private final double MIN_OPENGL_VERSION = 3.0;

  private ArFragment arFragment;

  private FloatingActionButton colorFab;
  private ColorPicker colorPicker;
  private CompletableFuture<ViewRenderable> image;

  private final int QUEUE_CAPACITY = 16;
  private ArrayBlockingQueue<MotionEvent> queuedScrollPresses = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

  @SuppressLint("ClickableViewAccessibility")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (!isSupportedDevice(this)) {
      return;
    }
    setContentView(R.layout.activity_main);

    arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
    if (arFragment == null) {
      return;
    }
    arFragment.getArSceneView().getPlaneRenderer().setShadowReceiver(false);

    int defaultColor = R.color.black;
    colorPicker = new ColorPicker(this, Color.red(defaultColor), Color.green(defaultColor), Color.blue(defaultColor));
    colorPicker.enableAutoClose();
    colorPicker.setCallback(this::updateSprayColor);

    colorFab = findViewById(R.id.colorFab);
    colorFab.setOnClickListener(view -> colorPicker.show());

    updateSprayColor(defaultColor);

    GestureDetector.OnGestureListener gestureListener = new GestureDetector.OnGestureListener() {
      @Override
      public boolean onDown(MotionEvent motionEvent) {
        return false;
      }

      @Override
      public void onShowPress(MotionEvent motionEvent) {
      }

      @Override
      public boolean onSingleTapUp(MotionEvent motionEvent) {
        return false;
      }

      @Override
      public boolean onScroll(MotionEvent startMotionEvent, MotionEvent motionEvent, float v, float v1) {
        queuedScrollPresses.offer(motionEvent);
        return false;
      }

      @Override
      public void onLongPress(MotionEvent motionEvent) {
      }

      @Override
      public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
        return false;
      }
    };
    arFragment.getArSceneView().setOnTouchListener((view, motionEvent) -> new GestureDetector(getBaseContext(), gestureListener).onTouchEvent(motionEvent));

    arFragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
      MotionEvent motionEvent = queuedScrollPresses.poll();
      if (motionEvent == null) {
        return;
      }

      Frame frame = arFragment.getArSceneView().getArFrame();
      if (frame == null) {
        return;
      }

      for (HitResult hitResult : frame.hitTest(motionEvent)) {
        Trackable trackable = hitResult.getTrackable();
        if (trackable instanceof Plane) {
          Plane plane = (Plane) trackable;
          if (plane.isPoseInPolygon(hitResult.getHitPose())) {
            Anchor anchor = hitResult.createAnchor();
            AnchorNode anchorNode = new AnchorNode(anchor);
            anchorNode.setParent(arFragment.getArSceneView().getScene());

            Node node = new Node();
            node.setParent(anchorNode);

            float[] planeRotation = plane.getCenterPose().getRotationQuaternion();
            Vector3 rotationVector = qToVector3(new Quaternion(-planeRotation[0], planeRotation[1], planeRotation[2], planeRotation[3]));
            node.setLookDirection(rotationVector);

            image
                    .thenAccept(
                            (renderable) -> node.setRenderable(renderable))
                    .exceptionally(
                            throwable -> {
                              Toast.makeText(this, "Unable to load the renderable", Toast.LENGTH_LONG).show();
                              return null;
                            });
          }
        }
      }
    });
  }

  @SuppressLint("InflateParams")
  private void updateSprayColor(int color) {
    LayoutInflater layoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    ConstraintLayout sprayLayout = (ConstraintLayout) layoutInflater.inflate(R.layout.spray_image, null);
    ImageView sprayView = sprayLayout.findViewById(R.id.sprayView);
    sprayView.getDrawable().mutate().setColorFilter(color, PorterDuff.Mode.SRC_IN);
    image = ViewRenderable.builder().setView(this, sprayLayout).build();

    colorFab.setBackgroundTintList(ColorStateList.valueOf(color));
  }

  private Vector3 qToVector3(Quaternion q) {
    // roll (x-axis rotation)
    double sinr_cosp = +2.0 * (q.w * q.x + q.y * q.z);
    double cosr_cosp = +1.0 - 2.0 * (q.x * q.x + q.y * q.y);
    double roll = Math.atan2(sinr_cosp, cosr_cosp);

    // pitch (y-axis rotation)
    double sinp = +2.0 * (q.w * q.y - q.z * q.x);
    double pitch;
    if (Math.abs(sinp) >= 1) {
      pitch = Math.copySign(Math.PI / 2, sinp); // use 90 degrees if out of range
    } else {
      pitch = Math.asin(sinp);
    }

    // yaw (z-axis rotation)
    double siny_cosp = +2.0 * (q.w * q.z + q.x * q.y);
    double cosy_cosp = +1.0 - 2.0 * (q.y * q.y + q.z * q.z);
    double yaw = Math.atan2(siny_cosp, cosy_cosp);
    return new Vector3((float) roll, (float) pitch, (float) yaw);
  }

  @SuppressLint("ObsoleteSdkInt")
  private boolean isSupportedDevice(final Activity activity) {
    // Android version
    if (Build.VERSION.SDK_INT < MIN_ANDROID_VERSION) {
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
