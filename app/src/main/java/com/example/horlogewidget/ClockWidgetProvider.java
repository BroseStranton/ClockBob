package com.example.horlogewidget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.widget.RemoteViews;

import java.util.Calendar;

/**
 * Widget heure + date, avec fontes personnalisables (ClockBob).
 * RemoteViews ne permet pas d'appliquer un Typeface à un TextClock : l'heure et
 * la date sont donc rendues dans un bitmap affiché par un ImageView.
 * Le rendu est refait chaque minute (AlarmManager) et à chaque événement
 * date/heure/fuseau/redimensionnement.
 */
public class ClockWidgetProvider extends AppWidgetProvider {

    private static final String CLOCK_PACKAGE = "com.android.deskclock";
    private static final String ACTION_TICK = "com.example.horlogewidget.ACTION_TICK";

    // Calendar.DAY_OF_WEEK : lundi doit donner l'index 0, dimanche l'index 6
    private static final String[] JOURS = {"LUN", "MAR", "MER", "JEU", "VEN", "SAM", "DIM"};
    private static final String[] MOIS = {
            "JAN", "FÉV", "MAR", "AVR", "MAI", "JUIN",
            "JUIL", "AOÛ", "SEP", "OCT", "NOV", "DÉC"};

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            manager.updateAppWidget(id, buildViews(context, manager, id));
        }
        scheduleMinute(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (ACTION_TICK.equals(action)
                || Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            refreshAll(context);
            scheduleMinute(context);
        }
    }

    @Override
    public void onEnabled(Context context) {
        scheduleMinute(context);
    }

    @Override
    public void onDisabled(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(tickIntent(context));
        }
    }

    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName cn = new ComponentName(context, ClockWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(cn);
        for (int id : ids) {
            manager.updateAppWidget(id, buildViews(context, manager, id));
        }
    }

    /** Appelé aussi par ParamsActivity après « Valider » / « Réinitialiser ». */
    private static RemoteViews buildViews(Context context, AppWidgetManager manager, int id) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_clock);
        views.setImageViewBitmap(R.id.widget_canvas, render(context, manager, id));
        views.setOnClickPendingIntent(R.id.widget_root, clockAppIntent(context));
        return views;
    }

    /** Ouvre l'application Horloge (Xiaomi / AOSP : com.android.deskclock). */
    private static PendingIntent clockAppIntent(Context context) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(CLOCK_PACKAGE);
        if (intent == null) {
            // Repli : n'importe quelle application horloge installee
            intent = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Ex : "ven 6 nov" (jour sans zero devant). */
    static String formatDate(Calendar c) {
        // DAY_OF_WEEK : DIM=1, LUN=2, ..., SAM=7  ->  index : LUN=0, ..., DIM=6
        int index = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        return JOURS[index] + " " + c.get(Calendar.DAY_OF_MONTH)
                + " " + MOIS[c.get(Calendar.MONTH)];
    }

    private static PendingIntent tickIntent(Context context) {
        Intent intent = new Intent(context, ClockWidgetProvider.class);
        intent.setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Programme un rafraichissement a la minute suivante. */
    private static void scheduleMinute(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Calendar next = Calendar.getInstance();
        next.add(Calendar.MINUTE, 1);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        am.setExact(AlarmManager.RTC, next.getTimeInMillis(), tickIntent(context));
    }

    /** Dessine heure et date dans un bitmap net, avec les fontes choisies. */
    private static Bitmap render(Context context, AppWidgetManager manager, int id) {
        FontStore store = new FontStore(context);
        Typeface tfHeure = store.typeface(store.getChoice(FontStore.KEY_HOUR));
        Typeface tfDate = store.typeface(store.getChoice(FontStore.KEY_DATE));
        if (tfHeure == null) tfHeure = Typeface.create("sans-serif", Typeface.NORMAL);
        if (tfDate == null) tfDate = Typeface.create("sans-serif", Typeface.NORMAL);

        // Taille du rendu : basee sur la taille du widget, sur-echantillonnee pour rester nette
        Bundle o = manager.getAppWidgetOptions(id);
        float density = context.getResources().getDisplayMetrics().density;
        int w = Math.max(600, (int) (o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110) * density * 1.5f));
        int h = Math.max(300, (int) (o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40) * density * 3f));

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Calendar now = Calendar.getInstance();

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColor(Color.WHITE);
        p.setShadowLayer(6, 4, 4, 0x66000000);

        // Repartition comme l'ancien layout : heure weight 5, date weight 2
        int hTime = h * 5 / 7;
        drawFit(canvas, String.format("%02d:%02d",
                        now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)),
                tfHeure, w, hTime, 0, p);
        drawFit(canvas, formatDate(now), tfDate, w, h - hTime, hTime, p);
        return bmp;
    }

    /** Dessine un texte centre dans sa bande, a la plus grande taille qui tient. */
    private static void drawFit(Canvas canvas, String text, Typeface tf,
                                int w, int bandH, int yOffset, Paint p) {
        p.setTypeface(tf);
        float size = 200f;
        p.setTextSize(size);
        float tw = p.measureText(text);
        Paint.FontMetrics fm = p.getFontMetrics();
        float th = fm.descent - fm.ascent;
        float scale = Math.min(w * 0.92f / tw, bandH * 0.92f / th);
        size *= scale;
        if (size < 1f) size = 1f;
        p.setTextSize(size);
        tw = p.measureText(text);
        fm = p.getFontMetrics();
        float baseline = yOffset + bandH / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(text, (w - tw) / 2f, baseline, p);
    }
}
