package com.example.user.myapplication;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ToggleButton;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final ToggleButton tb = (ToggleButton)findViewById(R.id.btn_toggle);

        tb.setOnClickListener(toggleClickListener);
    }

    ToggleButton.OnClickListener toggleClickListener = new View.OnClickListener() {
        public void onClick(View v) {
            ToggleButton t = (ToggleButton) v;
            if (t.isChecked()) {
                Toast.makeText(getApplicationContext(), "Toast", Toast.LENGTH_SHORT).show();
            }
        }
    };
}
