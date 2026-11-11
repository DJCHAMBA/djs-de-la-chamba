package com.djchamba.djsdelachamba;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.audio.GainProcessor;
import androidx.media3.common.audio.SonicAudioProcessor;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import androidx.media3.common.util.UnstableApi;

@UnstableApi
public class MainActivity extends Activity {
    private static final int PICK_SONG = 101;
    private static final int PICK_DJ_INTRO = 102;
    private static final int PICK_SECONDARY_INTRO = 103;
    private static final int SAVE_EXPORT = 104;
    private static final int SAVE_ZIP = 105;
    private static final int SAVE_CSV = 106;

    private static final int BG = Color.rgb(9, 10, 13);
    private static final int PANEL = Color.rgb(24, 27, 34);
    private static final int PANEL_DARK = Color.rgb(18, 20, 26);
    private static final int BORDER = Color.rgb(53, 59, 70);
    private static final int TEXT = Color.rgb(245, 247, 250);
    private static final int MUTED = Color.rgb(163, 173, 188);
    private static final int BLUE = Color.rgb(10, 132, 255);
    private static final int CYAN = Color.rgb(91, 193, 255);
    private static final int GREEN = Color.rgb(48, 209, 88);

    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout navigation;
    private TextView statusView;
    private TextView songNameView;
    private TextView djIntroNameView;
    private TextView secondaryIntroNameView;
    private TextView methodInfoView;
    private Spinner splitLengthSpinner;
    private ProgressBar progressBar;

    private Uri songUri;
    private Uri djIntroUri;
    private Uri secondaryIntroUri;
    private String songName = "";
    private String djIntroName = "";
    private String secondaryIntroName = "";
    private String selectedMethod = "main";

    private MediaPlayer previewPlayer;
    private boolean previewPlaying = false;

    private File pendingSaveFile;
    private String pendingSaveMime = "audio/mp4";
    private String pendingSaveName = "DJS-DE-LA-CHAMBA.m4a";
    private File currentExportFile;
    private Transformer currentTransformer;

    private final List<String[]> markers = new ArrayList<>();
    private final List<File> splitFiles = new ArrayList<>();
    private long splitDurationMs = 0;
    private long splitPartLengthMs = 7 * 60 * 1000L;
    private int splitPartIndex = 0;
    private int splitPartCount = 0;
    private String splitBaseName = "Audio";
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setStatusBarContrastEnforced(false);
        }
        buildShell();
        showEditor();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(int color, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        ScrollView page = new ScrollView(this);
        page.setFillViewport(false);
        page.setClipToPadding(false);

        LinearLayout pageColumn = new LinearLayout(this);
        pageColumn.setOrientation(LinearLayout.VERTICAL);
        pageColumn.setPadding(dp(18), dp(22), dp(18), dp(28));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(2), dp(4), dp(2), dp(18));

        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(com.djchamba.djsdelachamba.R.drawable.ic_launcher_foreground);
        logo.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        header.addView(logo, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout headerText = new LinearLayout(this);
        headerText.setOrientation(LinearLayout.VERTICAL);
        headerText.setPadding(dp(12), 0, 0, 0);
        TextView title = text("DJS DE LA CHAMBA", 21, TEXT, true);
        TextView subtitle = text("ESTUDIO DE AUDIO · ANDROID NATIVO", 10, MUTED, true);
        subtitle.setLetterSpacing(.11f);
        headerText.addView(title);
        headerText.addView(subtitle);
        header.addView(headerText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView version = text("NATIVE\n2.0", 10, CYAN, true);
        version.setGravity(Gravity.CENTER);
        header.addView(version, new LinearLayout.LayoutParams(dp(54), ViewGroup.LayoutParams.WRAP_CONTENT));
        pageColumn.addView(header);

        TextView hero = text("Tu estudio de audio,\ndirecto en el teléfono.", 25, TEXT, true);
        hero.setLineSpacing(dp(1), 1f);
        pageColumn.addView(hero);
        TextView description = text("Procesa pistas en el dispositivo. Tus archivos permanecen en tu teléfono.", 13, MUTED, false);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.setMargins(0, dp(7), 0, dp(18));
        pageColumn.addView(description, descParams);

        HorizontalScrollView navScroll = new HorizontalScrollView(this);
        navScroll.setHorizontalScrollBarEnabled(false);
        navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setPadding(0, 0, 0, dp(12));
        navScroll.addView(navigation);
        pageColumn.addView(navScroll);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        pageColumn.addView(content);

        page.addView(pageColumn);
        root.addView(page, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView footer = text("DJS DE LA CHAMBA  ·  PROCESAMIENTO LOCAL", 9, Color.rgb(108, 119, 136), true);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(8), dp(7), dp(8), dp(10));
        root.addView(footer);

        setContentView(root);
        buildNavigation();
    }

    private void buildNavigation() {
        navigation.removeAllViews();
        addNav("Editor", "editor");
        addNav("Dividir", "split");
        addNav("Detector", "detector");
        addNav("Roblox", "roblox");
    }

    private void addNav(String label, String id) {
        Button button = button(label, false, () -> {
            stopPreview();
            switch (id) {
                case "split": showSplitter(); break;
                case "detector": showDetector(); break;
                case "roblox": showRoblox(); break;
                default: showEditor();
            }
            buildNavigation();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        params.setMargins(0, 0, dp(7), 0);
        navigation.addView(button, params);
    }

    private void showEditor() {
        content.removeAllViews();

        LinearLayout intros = card("01  ·  Intros antes de la canción", "Opcionales · Se conservan originales, sin aplicar tempo, tono ni volumen.");
        djIntroNameView = text(djIntroUri == null ? "Ninguna seleccionada" : djIntroName, 12, MUTED, false);
        secondaryIntroNameView = text(secondaryIntroUri == null ? "Ninguna seleccionada" : secondaryIntroName, 12, MUTED, false);

        addFileChoice(intros, "🎙  Intro de DJ", djIntroNameView, "Elegir intro DJ", () -> openPicker(PICK_DJ_INTRO));
        addFileChoice(intros, "♫  Intro secundaria", secondaryIntroNameView, "Elegir intro secundaria", () -> openPicker(PICK_SECONDARY_INTRO));
        TextView order = text("ORDEN DE SALIDA   Intro DJ  →  Intro secundaria  →  canción procesada", 10, CYAN, true);
        order.setPadding(0, dp(10), 0, 0);
        intros.addView(order);
        content.addView(intros);

        LinearLayout song = card("02  ·  Canción principal", "Selecciona la canción que quieres procesar.");
        songNameView = text(songUri == null ? "No has elegido una canción" : songName, 13, MUTED, false);
        addFileChoice(song, "🎵  Archivo principal", songNameView, "Seleccionar canción", () -> openPicker(PICK_SONG));
        Button preview = button(previewPlaying ? "■  Detener vista previa" : "▶  Escuchar canción", false, this::togglePreview);
        song.addView(preview, matchParams(dp(46)));
        content.addView(song);

        LinearLayout methods = card("03  ·  Método de procesamiento", "Elige cómo quieres preparar la canción principal.");
        addMethodChoice(methods, "Método principal", "Tempo/tono ×2.5 · Volumen −3 dB · Roblox PlaybackSpeed 0.4", "main");
        addMethodChoice(methods, "Método Audacity", "Tempo +8% · Tono −3 semitonos · Roblox PlaybackSpeed 1.0", "audacity");
        methodInfoView = text(selectedMethod.equals("main") ? "Seleccionado: método principal" : "Seleccionado: método Audacity", 12, CYAN, true);
        methodInfoView.setPadding(0, dp(10), 0, 0);
        methods.addView(methodInfoView);
        content.addView(methods);

        LinearLayout output = card("04  ·  Exportar", "La app procesa el audio en el teléfono. La salida nativa es M4A / AAC.");
        Button process = button(busy ? "Procesando…" : "✦  Procesar y guardar audio", true, this::processSong);
        process.setEnabled(!busy && songUri != null);
        output.addView(process, matchParams(dp(50)));
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(busy);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
        barParams.setMargins(0, dp(13), 0, dp(3));
        output.addView(bar, barParams);
        statusView = text("Listo para trabajar sin abrir el navegador.", 12, MUTED, false);
        output.addView(statusView);
        content.addView(output);

        LinearLayout info = card("Hecho para tu teléfono", "Interfaz Android nativa · selector del sistema · reproducción y procesamiento local · exportación a Descargas.");
        content.addView(info);
    }

    private void showSplitter() {
        content.removeAllViews();
        LinearLayout split = card("Dividir audio", "Recorta la pista en partes consecutivas. No aplica tempo, tono ni efectos.");
        TextView source = text(songUri == null ? "Primero selecciona un audio" : songName, 13, MUTED, false);
        addFileChoice(split, "🎵  Audio que vas a dividir", source, "Seleccionar audio", () -> openPicker(PICK_SONG));

        TextView label = text("DURACIÓN MÁXIMA DE CADA PARTE", 11, MUTED, true);
        label.setPadding(0, dp(12), 0, dp(7));
        split.addView(label);

        splitLengthSpinner = new Spinner(this);
        String[] lengths = {"1 minuto", "2 minutos", "3 minutos", "4 minutos", "5 minutos", "6 minutos", "7 minutos"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, lengths);
        splitLengthSpinner.setAdapter(adapter);
        splitLengthSpinner.setSelection(6);
        split.addView(splitLengthSpinner, matchParams(dp(48)));

        Button run = button("✂  Dividir y guardar ZIP", true, this::startSplit);
        LinearLayout.LayoutParams p = matchParams(dp(50));
        p.setMargins(0, dp(14), 0, dp(0));
        split.addView(run, p);

        TextView note = text("Cada segmento se exporta como M4A/AAC y después se agrupa en un ZIP. El tiempo exacto de corte se calcula desde la duración detectada del audio.", 12, MUTED, false);
        note.setPadding(0, dp(12), 0, 0);
        split.addView(note);
        content.addView(split);
    }

    private void showDetector() {
        content.removeAllViews();
        LinearLayout detector = card("Detector · Marcadores manuales", "Escucha el concierto y guarda marcas de tiempo. Este modo no inventa títulos ni reconoce canciones automáticamente.");
        TextView selected = text(songUri == null ? "Selecciona una grabación en Editor" : songName, 13, MUTED, false);
        addFileChoice(detector, "🎧  Audio de referencia", selected, "Seleccionar audio", () -> openPicker(PICK_SONG));

        Button play = button("▶  Reproducir / pausar grabación", false, this::togglePreview);
        detector.addView(play, matchParams(dp(46)));

        Button songMark = button("♫  Marcar inicio de canción", true, () -> addMarker("Inicio de canción"));
        detector.addView(songMark, matchParams(dp(46)));
        Button voiceMark = button("🎙  Marcar entrada de voz", false, () -> addMarker("Entrada de voz"));
        detector.addView(voiceMark, matchParams(dp(46)));

        Button csv = button("⇩  Exportar marcadores CSV", false, this::exportMarkers);
        detector.addView(csv, matchParams(dp(46)));

        TextView markerInfo = text(markers.isEmpty() ? "Todavía no hay marcadores." : markers.size() + " marcadores guardados en esta sesión.", 12, MUTED, false);
        markerInfo.setPadding(0, dp(12), 0, 0);
        detector.addView(markerInfo);
        for (String[] marker : markers) {
            TextView row = text(marker[0] + "   ·   " + marker[1], 12, CYAN, false);
            row.setPadding(0, dp(7), 0, 0);
            detector.addView(row);
        }
        content.addView(detector);

        LinearLayout limitation = card("Reconocimiento automático", "Para identificar títulos por huella musical se necesita un servicio de reconocimiento y una clave propia. Esta versión nativa ofrece el registro manual de tiempos.");
        content.addView(limitation);
    }

    private void showRoblox() {
        content.removeAllViews();
        LinearLayout roblox = card("Herramientas Roblox", "Tu app Android es independiente de la página web. Estas acciones abren enlaces externos en el navegador del teléfono, si los necesitas.");
        roblox.addView(button("Abrir Creator Dashboard", true, () -> openExternal("https://create.roblox.com/")), matchParams(dp(48)));
        roblox.addView(button("Abrir administración DJS de la Chamba", false,
                () -> openExternal("https://djchamba.github.io/djs-de-la-chamba/")), matchParams(dp(48)));
        TextView note = text("El procesamiento y la división de audio funcionan dentro de esta app. Las operaciones de cuenta, permisos y publicación se gestionan con los servicios oficiales de Roblox.", 12, MUTED, false);
        note.setPadding(0, dp(12), 0, 0);
        roblox.addView(note);
        content.addView(roblox);
    }

    private LinearLayout card(String title, String subtitle) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(PANEL, BORDER, 20));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(13));
        card.setLayoutParams(params);
        TextView heading = text(title, 16, TEXT, true);
        card.addView(heading);
        TextView sub = text(subtitle, 12, MUTED, false);
        sub.setPadding(0, dp(6), 0, dp(12));
        card.addView(sub);
        return card;
    }

    private void addFileChoice(LinearLayout parent, String label, TextView selected, String action, Runnable onClick) {
        TextView heading = text(label, 13, TEXT, true);
        heading.setPadding(0, dp(7), 0, dp(5));
        parent.addView(heading);
        selected.setPadding(dp(12), dp(10), dp(12), dp(10));
        selected.setBackground(rounded(PANEL_DARK, BORDER, 11));
        parent.addView(selected, matchParams(ViewGroup.LayoutParams.WRAP_CONTENT));
        Button choose = button("+  " + action, false, onClick);
        LinearLayout.LayoutParams p = matchParams(dp(43));
        p.setMargins(0, dp(7), 0, dp(7));
        parent.addView(choose, p);
    }

    private void addMethodChoice(LinearLayout parent, String title, String details, String id) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(11), dp(12), dp(11));
        row.setBackground(rounded(selectedMethod.equals(id) ? Color.rgb(19, 42, 70) : PANEL_DARK,
                selectedMethod.equals(id) ? BLUE : BORDER, 13));
        TextView name = text((selectedMethod.equals(id) ? "●  " : "○  ") + title, 14, TEXT, true);
        TextView description = text(details, 11, MUTED, false);
        description.setPadding(dp(22), dp(4), 0, 0);
        row.addView(name);
        row.addView(description);
        row.setOnClickListener(v -> {
            selectedMethod = id;
            showEditor();
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(5), 0, dp(7));
        parent.addView(row, p);
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(true);
        return view;
    }

    private Button button(String label, boolean primary, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        button.setTextColor(primary ? Color.WHITE : TEXT);
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        button.setBackground(rounded(primary ? BLUE : PANEL_DARK, primary ? BLUE : BORDER, 13));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private LinearLayout.LayoutParams matchParams(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                height == ViewGroup.LayoutParams.WRAP_CONTENT ? height : dp(height));
    }

    private void openPicker(int requestCode) {
        if (busy) {
            toast("Espera a que termine el procesamiento.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, requestCode);
        } catch (Exception error) {
            toast("No se pudo abrir el selector de archivos.");
        }
    }

    private String queryDisplayName(Uri uri) {
        String name = "Audio seleccionado";
        android.database.Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return name == null ? "Audio seleccionado" : name;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            if (requestCode == SAVE_EXPORT || requestCode == SAVE_ZIP || requestCode == SAVE_CSV) {
                if (pendingSaveFile != null) pendingSaveFile.delete();
                pendingSaveFile = null;
            }
            return;
        }

        if (requestCode == PICK_SONG || requestCode == PICK_DJ_INTRO || requestCode == PICK_SECONDARY_INTRO) {
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            String name = queryDisplayName(uri);
            if (requestCode == PICK_SONG) {
                stopPreview();
                songUri = uri;
                songName = name;
            } else if (requestCode == PICK_DJ_INTRO) {
                djIntroUri = uri;
                djIntroName = name;
            } else {
                secondaryIntroUri = uri;
                secondaryIntroName = name;
            }
            if (content != null) showEditor();
            toast("Audio seleccionado.");
            return;
        }

        if (requestCode == SAVE_EXPORT || requestCode == SAVE_ZIP || requestCode == SAVE_CSV) {
            Uri destination = data.getData();
            File source = pendingSaveFile;
            pendingSaveFile = null;
            if (destination != null && source != null) copyToUri(source, destination);
        }
    }

    private void copyToUri(File source, Uri destination) {
        new Thread(() -> {
            try (InputStream input = new BufferedInputStream(new FileInputStream(source));
                 OutputStream output = new BufferedOutputStream(getContentResolver().openOutputStream(destination, "w"))) {
                if (output == null) throw new IllegalStateException("No se pudo crear el archivo de destino.");
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (count > 0) output.write(buffer, 0, count);
                }
                output.flush();
                source.delete();
                runOnUiThread(() -> toast("Guardado correctamente en el teléfono."));
            } catch (Exception error) {
                runOnUiThread(() -> toast("No se pudo guardar: " + error.getMessage()));
            }
        }).start();
    }

    private void requestSave(File file, String mime, String suggestedName, int requestCode) {
        pendingSaveFile = file;
        pendingSaveMime = mime;
        pendingSaveName = suggestedName;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, suggestedName);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, requestCode);
    }

    private void setBusy(boolean value, String message) {
        busy = value;
        if (statusView != null) statusView.setText(message);
        if (content != null && content.getChildCount() > 0 && songUri != null && message.startsWith("Listo")) {
            showEditor();
        }
    }

    private void processSong() {
        if (songUri == null) {
            toast("Selecciona la canción principal.");
            return;
        }
        if (busy) return;

        try {
            List<EditedMediaItem> items = new ArrayList<>();
            if (djIntroUri != null) {
                items.add(new EditedMediaItem.Builder(MediaItem.fromUri(djIntroUri)).build());
            }
            if (secondaryIntroUri != null) {
                items.add(new EditedMediaItem.Builder(MediaItem.fromUri(secondaryIntroUri)).build());
            }

            SonicAudioProcessor sonic = new SonicAudioProcessor();
            List<androidx.media3.common.audio.AudioProcessor> processors = new ArrayList<>();
            if ("audacity".equals(selectedMethod)) {
                sonic.setSpeed(1.08f);
                sonic.setPitch(0.8408964f);
            } else {
                sonic.setSpeed(2.5f);
                sonic.setPitch(2.5f);
                processors.add(new GainProcessor(new GainProcessor.GainProvider() {
                    @Override
                    public float getGainFactorAtSamplePosition(long samplePosition, int sampleRate) {
                        return 0.70794578f;
                    }
                    @Override
                    public long isUnityUntil(long samplePosition, int sampleRate) {
                        return C.TIME_UNSET;
                    }
                }));
            }
            processors.add(sonic);

            Effects effects = new Effects(processors, Collections.emptyList());
            items.add(new EditedMediaItem.Builder(MediaItem.fromUri(songUri)).setEffects(effects).build());

            EditedMediaItemSequence sequence = EditedMediaItemSequence.withAudioFrom(items);
            Composition composition = new Composition.Builder(sequence).build();

            String base = cleanName(songName.replaceFirst("\\.[^.]+$", ""));
            File output = new File(getCacheDir(), "DJS_" + base + "_" + System.currentTimeMillis() + ".m4a");
            currentExportFile = output;
            setBusy(true, "Procesando canción + " + (items.size() - 1) + " intro(s)…");

            Transformer.Listener listener = new Transformer.Listener() {
                @Override
                public void onCompleted(Composition composition, ExportResult result) {
                    currentTransformer = null;
                    setBusy(false, "Listo. Tu audio nativo ya está procesado.");
                    toast("Procesamiento terminado. Elige dónde guardar el M4A.");
                    requestSave(output, "audio/mp4", "DJS_" + base + ".m4a", SAVE_EXPORT);
                    showEditor();
                }

                @Override
                public void onError(Composition composition, ExportResult result, ExportException exception) {
                    currentTransformer = null;
                    setBusy(false, "No se pudo procesar este audio.");
                    output.delete();
                    toast("Error de audio: " + exception.getMessage());
                    showEditor();
                }
            };

            currentTransformer = new Transformer.Builder(this)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(listener)
                    .build();
            currentTransformer.start(composition, output.getAbsolutePath());
            showEditor();
            if (statusView != null) statusView.setText("Procesando… las intros se conservan originales.");
        } catch (Exception error) {
            setBusy(false, "No se pudo iniciar el procesamiento.");
            toast("No se pudo iniciar: " + error.getMessage());
        }
    }

    private String cleanName(String value) {
        if (value == null || value.trim().isEmpty()) return "Audio";
        return value.replaceAll("[^a-zA-Z0-9_-]+", "_").replaceAll("_+", "_");
    }

    private void togglePreview() {
        if (songUri == null) {
            toast("Selecciona una canción primero.");
            return;
        }
        if (previewPlayer != null && previewPlaying) {
            stopPreview();
            showEditor();
            return;
        }
        stopPreview();
        try {
            previewPlayer = new MediaPlayer();
            previewPlayer.setDataSource(this, songUri);
            previewPlayer.setOnPreparedListener(player -> {
                try {
                    if (Build.VERSION.SDK_INT >= 23) {
                        PlaybackParams params = new PlaybackParams();
                        if ("audacity".equals(selectedMethod)) {
                            params.setSpeed(1.08f).setPitch(0.8408964f);
                        } else {
                            params.setSpeed(2.5f).setPitch(2.5f);
                        }
                        player.setPlaybackParams(params);
                    }
                    player.start();
                    previewPlaying = true;
                    showEditor();
                } catch (Exception error) {
                    toast("No se pudo iniciar la vista previa.");
                    stopPreview();
                }
            });
            previewPlayer.setOnCompletionListener(player -> {
                stopPreview();
                showEditor();
            });
            previewPlayer.prepareAsync();
            toast("Preparando vista previa…");
        } catch (Exception error) {
            stopPreview();
            toast("No se pudo reproducir este archivo.");
        }
    }

    private void stopPreview() {
        previewPlaying = false;
        if (previewPlayer != null) {
            try { previewPlayer.stop(); } catch (Exception ignored) { }
            try { previewPlayer.release(); } catch (Exception ignored) { }
            previewPlayer = null;
        }
    }

    private void startSplit() {
        if (songUri == null) {
            toast("Selecciona el audio que quieres dividir.");
            return;
        }
        if (busy) return;
        int minutes = splitLengthSpinner == null ? 7 : splitLengthSpinner.getSelectedItemPosition() + 1;
        splitPartLengthMs = minutes * 60_000L;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, songUri);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            splitDurationMs = Long.parseLong(duration);
        } catch (Exception error) {
            toast("No pude leer la duración de este archivo.");
            return;
        } finally {
            try { retriever.release(); } catch (Exception ignored) { }
        }

        splitPartCount = (int) Math.ceil((double) splitDurationMs / splitPartLengthMs);
        if (splitPartCount < 1 || splitPartCount > 250) {
            toast("La división generaría demasiadas partes. Elige segmentos más largos.");
            return;
        }
        splitFiles.clear();
        splitPartIndex = 0;
        splitBaseName = cleanName(songName.replaceFirst("\\.[^.]+$", ""));
        setBusy(true, "Preparando " + splitPartCount + " partes…");
        showSplitter();
        processNextSplitPart();
    }

    private void processNextSplitPart() {
        if (splitPartIndex >= splitPartCount) {
            finishSplitZip();
            return;
        }
        final int current = splitPartIndex + 1;
        long startMs = splitPartIndex * splitPartLengthMs;
        long endMs = Math.min(splitDurationMs, startMs + splitPartLengthMs);
        MediaItem item = new MediaItem.Builder()
                .setUri(songUri)
                .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build())
                .build();

        EditedMediaItem edited = new EditedMediaItem.Builder(item).build();
        File output = new File(getCacheDir(), "DJS_PART_" + String.format(Locale.US, "%03d", current) + ".m4a");
        if (output.exists()) output.delete();

        Transformer.Listener listener = new Transformer.Listener() {
            @Override
            public void onCompleted(Composition composition, ExportResult result) {
                splitFiles.add(output);
                splitPartIndex++;
                setBusy(true, "Parte " + current + " de " + splitPartCount + " completada.");
                processNextSplitPart();
            }

            @Override
            public void onError(Composition composition, ExportResult result, ExportException exception) {
                setBusy(false, "Falló la parte " + current + ".");
                output.delete();
                toast("Error al dividir: " + exception.getMessage());
                showSplitter();
            }
        };

        currentTransformer = new Transformer.Builder(this)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(listener)
                .build();
        try {
            currentTransformer.start(edited, output.getAbsolutePath());
        } catch (Exception error) {
            setBusy(false, "No se pudo dividir el audio.");
            toast(error.getMessage());
            showSplitter();
        }
    }

    private void finishSplitZip() {
        setBusy(true, "Empaquetando partes en ZIP…");
        File zip = new File(getCacheDir(), "DJS_" + splitBaseName + "_Partes.zip");
        if (zip.exists()) zip.delete();
        new Thread(() -> {
            try (ZipOutputStream output = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)))) {
                byte[] buffer = new byte[64 * 1024];
                for (int i = 0; i < splitFiles.size(); i++) {
                    File part = splitFiles.get(i);
                    String entryName = splitBaseName + "_Parte_" + String.format(Locale.US, "%02d", i + 1) + ".m4a";
                    output.putNextEntry(new ZipEntry(entryName));
                    try (InputStream input = new BufferedInputStream(new FileInputStream(part))) {
                        int count;
                        while ((count = input.read(buffer)) >= 0) {
                            if (count > 0) output.write(buffer, 0, count);
                        }
                    }
                    output.closeEntry();
                    part.delete();
                }
                output.finish();
                runOnUiThread(() -> {
                    setBusy(false, "Listo. " + splitPartCount + " partes en ZIP.");
                    toast("División terminada. Guarda tu ZIP.");
                    requestSave(zip, "application/zip", "DJS_" + splitBaseName + "_Partes.zip", SAVE_ZIP);
                    showSplitter();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    setBusy(false, "No se pudo crear el ZIP.");
                    toast("Error creando ZIP: " + error.getMessage());
                    showSplitter();
                });
            }
        }).start();
    }

    private void addMarker(String type) {
        if (previewPlayer == null) {
            toast("Primero inicia la reproducción para marcar el tiempo.");
            return;
        }
        long time = Math.max(0, previewPlayer.getCurrentPosition());
        markers.add(new String[]{formatTime(time), type});
        toast(type + " marcada en " + formatTime(time));
        showDetector();
    }

    private String formatTime(long ms) {
        long total = Math.max(0, ms / 1000);
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;
        if (hours > 0) return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    private void exportMarkers() {
        if (markers.isEmpty()) {
            toast("No hay marcadores para exportar.");
            return;
        }
        File csv = new File(getCacheDir(), "DJS_Marcadores.csv");
        try (FileOutputStream output = new FileOutputStream(csv)) {
            StringBuilder content = new StringBuilder("Tiempo,Marca\n");
            for (String[] marker : markers) {
                content.append(marker[0]).append(",\"").append(marker[1].replace("\"", "\"\"")).append("\"\n");
            }
            output.write(content.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            requestSave(csv, "text/csv", "DJS_Marcadores.csv", SAVE_CSV);
        } catch (Exception error) {
            toast("No se pudo crear el CSV.");
        }
    }

    private void openExternal(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(intent);
        } catch (Exception error) {
            toast("No se pudo abrir el enlace.");
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onBackPressed() {
        if (previewPlayer != null) {
            stopPreview();
            showEditor();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        stopPreview();
        if (currentTransformer != null) {
            try { currentTransformer.cancel(); } catch (Exception ignored) { }
            currentTransformer = null;
        }
        super.onDestroy();
    }
}
