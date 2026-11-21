package com.example.horlogewidget;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Fenetre PARAMETRES, thème système (aucune couleur codée en dur).
 * Disposition d'après la maquette : titre encadré, avertissement, menus HEURE/DATE,
 * champ d'ajout avec bouton ..., zone MES POLICES, boutons bas.
 */
public class ParamsActivity extends Activity {

    private static final int REQ_PICK_FONT = 1;
    private static final String DEFAULT_LABEL = "Par défaut";

    private FontStore store;
    private String selHeure, selDate;      // "" = police systeme
    private ArrayAdapter<String> adHeure, adDate;
    private LinearLayout listFonts;
    private EditText txtPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_params);

        store = new FontStore(this);
        selHeure = store.getChoice(FontStore.KEY_HOUR);
        selDate = store.getChoice(FontStore.KEY_DATE);

        adHeure = makeSpinnerAdapter();
        adDate = makeSpinnerAdapter();
        setupSpinner(R.id.spin_heure, adHeure, true);
        setupSpinner(R.id.spin_date, adDate, false);

        txtPath = (EditText) findViewById(R.id.txt_path);
        txtPath.setFocusable(false); // rempli uniquement par le selecteur
        listFonts = (LinearLayout) findViewById(R.id.list_fonts);

        findViewById(R.id.btn_browse).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openPicker(); }
        });
        findViewById(R.id.btn_reset).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                store.setChoice(FontStore.KEY_HOUR, "");
                store.setChoice(FontStore.KEY_DATE, "");
                ClockWidgetProvider.refreshAll(ParamsActivity.this);
                setResult(RESULT_OK);
                finish();
            }
        });
        findViewById(R.id.btn_cancel).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setResult(RESULT_CANCELED); finish(); }
        });
        findViewById(R.id.btn_validate).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                store.setChoice(FontStore.KEY_HOUR, selHeure);
                store.setChoice(FontStore.KEY_DATE, selDate);
                ClockWidgetProvider.refreshAll(ParamsActivity.this);
                setResult(RESULT_OK);
                finish();
            }
        });

        refreshSpinners();
        refreshFontList();
    }

    private ArrayAdapter<String> makeSpinnerAdapter() {
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return ad;
    }

    private void setupSpinner(int id, ArrayAdapter<String> ad, final boolean isHeure) {
        Spinner sp = (Spinner) findViewById(id);
        sp.setAdapter(ad);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String name = pos == 0 ? "" : (String) parent.getItemAtPosition(pos);
                if (isHeure) selHeure = name; else selDate = name;
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    /** Alimente les menus HEURE/DATE et leur selection courante. */
    private void refreshSpinners() {
        adHeure.clear();
        adDate.clear();
        adHeure.add(DEFAULT_LABEL);
        adDate.add(DEFAULT_LABEL);
        for (String[] e : store.entries()) {
            adHeure.add(e[1]);
            adDate.add(e[1]);
        }
        select(adHeure, (Spinner) findViewById(R.id.spin_heure), selHeure);
        select(adDate, (Spinner) findViewById(R.id.spin_date), selDate);
    }

    private void select(ArrayAdapter<String> ad, Spinner sp, String name) {
        int pos = (name == null || name.isEmpty()) ? 0 : ad.getPosition(name);
        sp.setSelection(pos < 0 ? 0 : pos, false);
    }

    /** Zone MES POLICES : une ligne par fonte avec son bouton Supprimer. */
    private void refreshFontList() {
        listFonts.removeAllViews();
        for (final String[] e : store.entries()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView name = new TextView(this);
            name.setText(e[1]);
            name.setTextSize(16);
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(name, lp);

            Button del = new Button(this);
            del.setText(R.string.delete);
            del.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { removeFont(e[0], e[1]); }
            });
            row.addView(del);

            listFonts.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void removeFont(String internalFile, String displayName) {
        store.remove(internalFile);
        // Fonte en cours d'utilisation ? Retour a Par défaut dans la fenetre
        if (displayName.equals(selHeure)) selHeure = "";
        if (displayName.equals(selDate)) selDate = "";
        refreshSpinners();
        refreshFontList();
    }

    private void openPicker() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*"); // MIUI grise les .ttf si on filtre par type MIME ici
        startActivityForResult(Intent.createChooser(i, getString(R.string.pick_font)), REQ_PICK_FONT);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK_FONT || res != RESULT_OK || data == null
                || data.getData() == null) return;
        importFont(data.getData());
    }

    /** Copie + verification, puis alimentation des listes (maquette : aucun bouton d'ajout). */
    private void importFont(Uri uri) {
        try {
            // Nom affiche = nom du fichier sans extension
            String fileName = queryName(uri);
            int dot = fileName.lastIndexOf('.');
            String ext = dot < 0 ? "" : fileName.substring(dot).toLowerCase();
                 if (!ext.equals(".ttf") && !ext.equals(".otf") && !ext.equals(".ttc")) {
                Toast.makeText(this, R.string.font_rejected, Toast.LENGTH_LONG).show();
                return;
            }
            String visibleName = dot < 0 ? fileName : fileName.substring(0, dot);

            // Verification sur une copie provisoire en cache
            File probe = new File(getCacheDir(), "probe" + ext);
            copy(uri, probe);
            Typeface tf = Typeface.createFromFile(probe);
            probe.delete();
            if (tf == null) throw new IllegalStateException();

            // Ajout effectif : copie dans le stockage interne
            String internal = store.nextInternalName(ext);
            copy(uri, store.fileOf(internal));
            store.add(internal, visibleName);

            txtPath.setText(fileName);
            refreshSpinners();
            refreshFontList();
        } catch (Exception ex) {
            Toast.makeText(this, R.string.font_rejected, Toast.LENGTH_LONG).show();
            new File(getCacheDir(), "probe").delete();
        }
    }

    private void copy(Uri uri, File dest) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new IllegalStateException("flux indisponible");
        OutputStream out = new FileOutputStream(dest);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.close();
        in.close();
    }

    private String queryName(Uri uri) {
        String name = "police.ttf";
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return name == null || name.isEmpty() ? "police.ttf" : name;
    }
}
