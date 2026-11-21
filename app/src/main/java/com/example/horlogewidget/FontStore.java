package com.example.horlogewidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Stockage des fontes importees + des choix HEURE / DATE.
 * - Fichiers copies dans getFilesDir()/fonts : stockage interne prive, zero permission.
 * - Association fichier interne <-> nom affiche, survit au redemarrage (SharedPreferences).
 */
public final class FontStore {

    private static final String PREFS = "clockbob";
    static final String KEY_HOUR = "font_heure";   // nom affiche, "" = police systeme
    static final String KEY_DATE = "font_date";    // nom affiche, "" = police systeme
    static final String KEY_PEND_HOUR = "pend_heure"; // choix en cours, pas encore validés
    static final String KEY_PEND_DATE = "pend_date";
    private static final String KEY_ENTRIES = "font_entries"; // "fichierInterne|nomAffiche" par ligne

    private final Context ctx;

    public FontStore(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    private SharedPreferences prefs() {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public File dir() {
        File d = new File(ctx.getFilesDir(), "fonts");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Liste des fontes : [0] = nom de fichier interne, [1] = nom affiche. */
    public String[][] entries() {
        List<String[]> out = new ArrayList<String[]>();
        String raw = prefs().getString(KEY_ENTRIES, "");
        if (!raw.isEmpty()) {
            for (String line : raw.split("\n")) {
                int p = line.indexOf('|');
                if (p > 0) out.add(new String[]{line.substring(0, p), line.substring(p + 1)});
            }
        }
        return out.toArray(new String[0][]);
    }

    public void add(String internalFile, String displayName) {
        String[][] all = entries();
        StringBuilder sb = new StringBuilder();
        for (String[] e : all) sb.append(e[0]).append('|').append(e[1]).append('\n');
        sb.append(internalFile).append('|').append(displayName);
        prefs().edit().putString(KEY_ENTRIES, sb.toString()).apply();
    }

    /** Supprime l'entree ; le fichier est aussi efface du disque. */
    public void remove(String internalFile) {
        String[][] all = entries();
        StringBuilder sb = new StringBuilder();
        for (String[] e : all) {
            if (!e[0].equals(internalFile)) sb.append(e[0]).append('|').append(e[1]).append('\n');
        }
        if (sb.length() > 0) sb.setLength(sb.length() - 1);
        prefs().edit().putString(KEY_ENTRIES, sb.toString()).apply();
        new File(dir(), internalFile).delete();
    }

    public String getChoice(String key) {
        return prefs().getString(key, "");
    }

    public void setChoice(String key, String displayName) {
        prefs().edit().putString(key, displayName).apply();
    }

    /** Typeface pour un nom affiche ; null ou "" = police systeme (renvoie null). */
    public Typeface typeface(String displayName) {
        if (displayName == null || displayName.isEmpty()) return null;
        for (String[] e : entries()) {
            if (e[1].equals(displayName)) {
                try {
                    return Typeface.createFromFile(new File(dir(), e[0]));
                } catch (Exception ex) {
                    return null; // fonte devenue illisible : repli sur la police systeme
                }
            }
        }
        return null;
    }

    /** Nom de fichier interne libre : font_1.ttf, font_2.ttf... */
    public String nextInternalName(String extension) {
        int n = entries().length + 1;
        while (new File(dir(), "font_" + n + extension).exists()) n++;
        return "font_" + n + extension;
    }
 /** Fichier de stockage interne pour un nom de fichier interne. */
    public File fileOf(String internalName) {
        return new File(dir(), internalName);
    }

     /** Vrai si une fenêtre précédente a laissé des choix non validés. */
    public boolean hasPending() {
        return prefs().contains(KEY_PEND_HOUR);
    }

    /** Efface les choix en cours (après Valider / Annuler / Réinitialiser). */
    public void clearPending() {
        prefs().edit().remove(KEY_PEND_HOUR).remove(KEY_PEND_DATE).apply();
    }
}
