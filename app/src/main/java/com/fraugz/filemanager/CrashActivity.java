package com.fraugz.filemanager;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;

public class CrashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String error = getIntent().getStringExtra("error");
        if (error == null) error = "Error desconocido";

        // Build UI programmatically (no layout dependency)
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1C1C1E);
        root.setPadding(32, 64, 32, 32);

        TextView title = new TextView(this);
        title.setText("La app encontro un error");
        title.setTextColor(0xFFFF453A);
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, 16);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Copia el error y reportalo para ayudar a solucionarlo:");
        subtitle.setTextColor(0xFF999999);
        subtitle.setTextSize(14);
        subtitle.setPadding(0, 0, 0, 16);
        root.addView(subtitle);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        scroll.setBackgroundColor(0xFF000000);

        TextView errorText = new TextView(this);
        errorText.setText(error);
        errorText.setTextColor(0xFFFFFFFF);
        errorText.setTextSize(11);
        errorText.setTypeface(android.graphics.Typeface.MONOSPACE);
        errorText.setPadding(24, 24, 24, 24);
        scroll.addView(errorText);
        root.addView(scroll);

        // Buttons row
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, 24, 0, 0);

        final String finalError = error;
        Button btnCopy = new Button(this);
        btnCopy.setText("Copiar error");
        btnCopy.setTextColor(0xFFFFFFFF);
        btnCopy.setBackgroundColor(0xFF2C2C2E);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.setMarginEnd(12);
        btnCopy.setLayoutParams(lp);
        btnCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("error", finalError));
            Toast.makeText(this, "Error copiado al portapapeles", Toast.LENGTH_SHORT).show();
        });
        btns.addView(btnCopy);

        Button btnRestart = new Button(this);
        btnRestart.setText("Reiniciar");
        btnRestart.setTextColor(0xFFFFFFFF);
        btnRestart.setBackgroundColor(0xFF1E88E5);
        btnRestart.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        btnRestart.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });
        btns.addView(btnRestart);

        root.addView(btns);
        setContentView(root);
    }
}

