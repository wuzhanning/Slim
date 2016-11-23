package panes.slim.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;

import panes.slim.Slim;
import panes.slim.SlimBundle;
import panes.slim.SlimConfig;
import panes.slim.SlimListener;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "Slim";
    TextView tv;
    ImageView iv;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv = (TextView) findViewById(R.id.tv);
        iv = (ImageView) findViewById(R.id.iv);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
        } else {
            dynamicLoad();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission Granted
                Log.i(TAG, "start slim");
                dynamicLoad();
            } else {
                tv.setText("未开启读取SD卡权限.");
            }
        }
    }

    private void dynamicLoad() {
        SlimBundle slimBundle = new SlimBundle("panes.slim.bundle", Environment.getExternalStorageDirectory() + File.separator +  "slim.bundle.apk", SlimBundle.TYPE_RESOURCES);
        SlimConfig.addResourcesBundle(slimBundle);
        Slim.init(getApplicationContext(), new SlimListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "initResource onSuccess.");
                tv.setText("show resource in bundle.");
                iv.setImageDrawable(Slim.getDrawable("logo"));
            }

            @Override
            public void onError(String msg) {
                tv.setText(msg);
            }
        });
    }
}
