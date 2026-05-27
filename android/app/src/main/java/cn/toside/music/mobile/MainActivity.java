package cn.toside.music.mobile;

import android.os.Build;
import android.os.Bundle;

import com.reactnativenavigation.NavigationActivity;

public class MainActivity extends NavigationActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getWindow().getDecorView().setDefaultFocusHighlightEnabled(true);
        }
    }
}
