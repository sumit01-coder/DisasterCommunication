package com.example.disastercomm;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.disastercomm.fragments.MapFragment;

public class MapActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new MapFragment())
                    .commit();
        }
    }
}
