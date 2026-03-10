package com.fraugz.filemanager;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

public class App extends Application {

    private static final String TAG = "GestorApp";

    @Override
    public void onCreate() {
        super.onCreate();

        // Global crash handler — muestra un diálogo en vez de cerrar silenciosamente
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "CRASH en hilo " + thread.getName(), throwable);
            try {
                Intent intent = new Intent(this, CrashActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intent.putExtra("error", throwable.toString() + "\n\n" +
                        Log.getStackTraceString(throwable));
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Error al mostrar CrashActivity", e);
            }
            android.os.Process.killProcess(android.os.Process.myPid());
        });
    }
}

