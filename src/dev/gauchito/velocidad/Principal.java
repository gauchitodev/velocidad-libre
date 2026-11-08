package dev.gauchito.velocidad;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Una sola pantalla: velocidad en vivo, cada cuánto avisa, empezar/parar y el historial de viajes. */
public class Principal extends Activity {
    private static final int FONDO = 0xFF121412, SUPERFICIE = 0xFF1F241F, TEXTO = 0xFFE8E6E1,
            TENUE = 0xFF8C918B, VERDE = 0xFF7BC86C, ROJO = 0xFFE8603C, OSCURO = 0xFF101310;

    private TextView vel, estado;
    private Button boton, decir, b5, b15, b10, bAcel;
    private LinearLayout lista;
    private final Set<Integer> abiertos = new HashSet<>();
    private boolean primeraCarga = true, activoAntes;
    private long tamanoLeido = -2;
    private String accionPendiente;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable refresco = new Runnable() {
        @Override public void run() { refrescar(); h.postDelayed(this, 1000); }
    };

    /** Un viaje: de un "inicio" hasta su "fin" (o hasta el último aviso, si se cortó sin fin). */
    private static final class Viaje {
        int desde, hasta;
        final List<String[]> filas = new ArrayList<>();
    }

    // Toda la pantalla lee los textos en el idioma elegido en la app.
    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(Idioma.envolver(base)); }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private TextView texto(String t, float sp, int color) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        return v;
    }

    private Button boton(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setStateListAnimator(null);
        b.setMinHeight(dp(56));
        return b;
    }

    private GradientDrawable redondo(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(14));
        return g;
    }

    private void pintar(Button b, int fondo, int letra) {
        b.setBackground(redondo(fondo));
        b.setTextColor(letra);
    }

    private LinearLayout.LayoutParams ancho(int arriba) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(arriba);
        return p;
    }

    private LinearLayout fila(Button a, Button b) {
        LinearLayout f = new LinearLayout(this);
        LinearLayout.LayoutParams izq = new LinearLayout.LayoutParams(0, -2, 1);
        LinearLayout.LayoutParams der = new LinearLayout.LayoutParams(0, -2, 1);
        der.leftMargin = dp(10);
        f.addView(a, izq);
        f.addView(b, der);
        return f;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(FONDO);
        getWindow().setNavigationBarColor(FONDO);

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundColor(FONDO);
        raiz.setPadding(dp(20), dp(12), dp(20), 0);

        // Idioma, arriba a la derecha.
        LinearLayout arriba = new LinearLayout(this);
        arriba.setGravity(Gravity.END);
        TextView idioma = texto(Idioma.NOMBRES[Idioma.indice(this)], 14, TENUE);
        GradientDrawable borde = new GradientDrawable();
        borde.setCornerRadius(dp(20));
        borde.setStroke(dp(1), 0xFF3A403A);
        idioma.setBackground(borde);
        idioma.setPadding(dp(14), dp(7), dp(14), dp(7));
        idioma.setContentDescription(getString(R.string.idioma));
        idioma.setOnClickListener(v -> elegirIdioma(false));
        arriba.addView(idioma);
        raiz.addView(arriba, ancho(0));

        vel = texto("—", 104, TEXTO);
        vel.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        vel.setGravity(Gravity.CENTER);
        vel.setIncludeFontPadding(false);
        raiz.addView(vel, ancho(4));
        TextView unidad = texto("km/h", 18, TENUE);
        unidad.setGravity(Gravity.CENTER);
        raiz.addView(unidad, ancho(0));
        estado = texto(getString(R.string.apagado), 15, TENUE);
        estado.setGravity(Gravity.CENTER);
        estado.setOnClickListener(v -> {
            if (Servicio.activo && Servicio.sinVoz) instalarVoz();
        });
        raiz.addView(estado, ancho(10));

        b5 = boton(getString(R.string.cada_min, 5));
        b15 = boton(getString(R.string.cada_min, 15));
        raiz.addView(fila(b5, b15), ancho(24));
        b10 = boton(getString(R.string.cada_10));
        bAcel = boton(getString(R.string.cero_cien));
        raiz.addView(fila(b10, bAcel), ancho(10));
        b5.setOnClickListener(v -> elegir(5));
        b15.setOnClickListener(v -> elegir(15));
        b10.setOnClickListener(v -> {
            getSharedPreferences("p", 0).edit().putBoolean("diez", !Servicio.porDecena(this)).apply();
            pintarIntervalo();
        });
        bAcel.setOnClickListener(v -> {
            if (Servicio.acel != Servicio.ACEL_NO) mandar(Servicio.ACEL_CANCELAR);
            else if (Servicio.activo) mandar(Servicio.ACEL);
            else empezar(Servicio.ACEL);
        });

        boton = boton(getString(R.string.empezar));
        boton.setTextSize(20);
        boton.setMinHeight(dp(68));
        boton.setTypeface(Typeface.DEFAULT_BOLD);
        raiz.addView(boton, ancho(12));
        boton.setOnClickListener(v -> {
            if (Servicio.activo) mandar(Servicio.PARAR);
            else empezar(null);
        });

        decir = boton(getString(R.string.decir_ahora));
        pintar(decir, SUPERFICIE, TEXTO);
        raiz.addView(decir, ancho(10));
        decir.setOnClickListener(v -> mandar(Servicio.DECIR));

        TextView titulo = texto(getString(R.string.historial), 17, TEXTO);
        titulo.setTypeface(Typeface.DEFAULT_BOLD);
        raiz.addView(titulo, ancho(24));
        raiz.addView(texto(getString(R.string.ayuda_historial), 13, TENUE), ancho(2));

        lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        lista.setPadding(0, 0, 0, dp(24));
        raiz.addView(lista, ancho(0));

        // Toda la pantalla se desliza junta: el historial no queda en una ventanita abajo.
        ScrollView pagina = new ScrollView(this);
        pagina.setBackgroundColor(FONDO);
        pagina.setFillViewport(true);
        pagina.addView(raiz);
        setContentView(pagina);
        pintarIntervalo();
        if (!Idioma.elegido(this)) elegirIdioma(true);
    }

    @Override protected void onResume() { super.onResume(); h.post(refresco); }

    @Override protected void onPause() { super.onPause(); h.removeCallbacks(refresco); }

    /** La primera vez no se puede saltear; después se abre desde el botón de arriba. */
    private void elegirIdioma(boolean primera) {
        new AlertDialog.Builder(this)
                .setTitle(primera ? "Idioma · Language" : getString(R.string.idioma))
                .setSingleChoiceItems(Idioma.NOMBRES, Idioma.indice(this), (d, w) -> {
                    d.dismiss();
                    Idioma.elegir(this, Idioma.CODIGOS[w]);
                    if (Servicio.activo) startService(new Intent(this, Servicio.class).setAction(Servicio.IDIOMA));
                    recreate();
                })
                .setCancelable(!primera)
                .show();
    }

    private void instalarVoz() {
        try {
            startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.falta_voz, Toast.LENGTH_LONG).show();
        }
    }

    private void elegir(int min) {
        getSharedPreferences("p", 0).edit().putInt("min", min).apply();
        pintarIntervalo();
        if (Servicio.activo) mandar(Servicio.REPROGRAMAR);
    }

    private void pintarIntervalo() {
        boolean cinco = Servicio.minutos(this) == 5;
        pintar(b5, cinco ? VERDE : SUPERFICIE, cinco ? OSCURO : TEXTO);
        pintar(b15, cinco ? SUPERFICIE : VERDE, cinco ? TEXTO : OSCURO);
        boolean diez = Servicio.porDecena(this);
        pintar(b10, diez ? VERDE : SUPERFICIE, diez ? OSCURO : TEXTO);
    }

    private void mandar(String accion) {
        startService(new Intent(this, Servicio.class).setAction(accion));
        h.postDelayed(this::refrescar, 300);
    }

    private void empezar(String accion) {
        accionPendiente = accion;
        List<String> faltan = new ArrayList<>();
        List<String> pedir = new ArrayList<>();
        pedir.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33) pedir.add(Manifest.permission.POST_NOTIFICATIONS);
        for (String p : pedir) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) faltan.add(p);
        }
        if (faltan.isEmpty()) lanzar();
        else requestPermissions(faltan.toArray(new String[0]), 1);
    }

    @Override public void onRequestPermissionsResult(int c, String[] permisos, int[] r) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) lanzar();
        else Toast.makeText(this, R.string.sin_permiso, Toast.LENGTH_LONG).show();
    }

    private void lanzar() {
        startForegroundService(new Intent(this, Servicio.class).setAction(accionPendiente));
        accionPendiente = null;
        h.postDelayed(this::refrescar, 300);
        pedirBateria();
    }

    /** Una sola vez: sin esto, muchos teléfonos cortan la app con la pantalla apagada. */
    private void pedirBateria() {
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm.isIgnoringBatteryOptimizations(getPackageName())) return;
        if (getSharedPreferences("p", 0).getBoolean("pidioBateria", false)) return;
        getSharedPreferences("p", 0).edit().putBoolean("pidioBateria", true).apply();
        new AlertDialog.Builder(this)
                .setMessage(R.string.bateria_msg)
                .setPositiveButton(R.string.dale, (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:" + getPackageName())));
                    } catch (RuntimeException e) {
                        Toast.makeText(this, R.string.bateria_ajustes, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(R.string.ahora_no, null).show();
    }

    private void refrescar() {
        boolean a = Servicio.activo;
        boton.setText(a ? R.string.parar : R.string.empezar);
        pintar(boton, a ? ROJO : VERDE, OSCURO);
        decir.setEnabled(a);
        decir.setAlpha(a ? 1f : 0.4f);
        long ahora = SystemClock.elapsedRealtime();
        boolean senal = Servicio.ultimoFix > 0 && ahora - Servicio.ultimoFix < 10000;
        String e;
        vel.setTextColor(a && senal ? TEXTO : 0xFF3A403A);
        if (!a) {
            vel.setText("—");
            e = getString(R.string.apagado);
        } else {
            vel.setText(senal ? String.valueOf(Math.round(Servicio.kmh)) : "—");
            e = getString(senal ? R.string.gps_bien : R.string.buscando_gps);
            if (Servicio.proximoAviso > 0) {
                long s = Math.max(0, (Servicio.proximoAviso - ahora) / 1000);
                e += " · " + getString(R.string.proximo_en, String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60));
            }
        }
        int ac = Servicio.acel;
        if (ac == Servicio.ACEL_ESPERA_PARADO) e = getString(R.string.acel_parar);
        else if (ac == Servicio.ACEL_LISTO) e = getString(R.string.acel_listo);
        else if (ac == Servicio.ACEL_MIDIENDO) e = getString(R.string.acel_midiendo);
        else if (Servicio.ultimoCeroCien != null && a) e = getString(R.string.acel_resultado, Servicio.ultimoCeroCien) + " · " + e;
        boolean faltaVoz = a && Servicio.sinVoz;
        if (faltaVoz) e = getString(R.string.falta_voz);
        estado.setText(e);
        estado.setTextColor(faltaVoz ? ROJO : TENUE);
        bAcel.setText(ac == Servicio.ACEL_NO ? R.string.cero_cien : R.string.cancelar_cero_cien);
        pintar(bAcel, ac == Servicio.ACEL_NO ? SUPERFICIE : ROJO, ac == Servicio.ACEL_NO ? TEXTO : OSCURO);
        File f = Servicio.archivo(this);
        long tam = f.exists() ? f.length() : -1;
        if (tam != tamanoLeido || a != activoAntes) {
            tamanoLeido = tam;
            activoAntes = a;
            mostrarHistorial();
        }
    }

    // ---------- historial ----------

    private List<String> lineas() {
        List<String> ls = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(Servicio.archivo(this)))) {
            String l;
            while ((l = r.readLine()) != null) ls.add(l);
        } catch (IOException ignorado) {
            // Sin archivo todavía.
        }
        return ls;
    }

    /** registro.csv: fecha,hora,tipo,actual,promedio,maxima,km_tramo,km_viaje. */
    private static List<Viaje> viajes(List<String> ls) {
        List<Viaje> vs = new ArrayList<>();
        Viaje v = null;
        for (int i = 0; i < ls.size(); i++) {
            String[] c = ls.get(i).split(",", -1);
            if (c.length < 8) continue;
            if (v == null || c[2].equals("inicio")) {
                v = new Viaje();
                v.desde = i;
                vs.add(v);
            }
            v.filas.add(c);
            v.hasta = i;
            if (c[2].equals("fin")) v = null;
        }
        return vs;
    }

    private static double num(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Date cuando(String[] c) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).parse(c[0] + " " + c[1]);
        } catch (ParseException e) {
            return null;
        }
    }

    private String uno(double v) { return String.format(Idioma.locale(this), "%.1f", v); }

    private String titulo(Viaje v, boolean enCurso) {
        String[] a = v.filas.get(0), z = v.filas.get(v.filas.size() - 1);
        Date d = cuando(a);
        Locale l = Idioma.locale(this);
        String dia = d == null ? a[0] : new SimpleDateFormat(DateFormat.getBestDateTimePattern(l, "EEEdMM"), l).format(d);
        return dia + " · " + a[1] + (enCurso ? " · " + getString(R.string.en_curso) : "–" + z[1]);
    }

    private String resumen(Viaje v) {
        Date a = cuando(v.filas.get(0)), z = cuando(v.filas.get(v.filas.size() - 1));
        long min = a == null || z == null ? 0 : (z.getTime() - a.getTime()) / 60000;
        double kms = 0, mejor = 0;
        long max = 0, prom = -1;
        for (String[] c : v.filas) {
            kms = Math.max(kms, num(c[7]));
            if (c[2].equals("acel")) {
                double s = num(c[3]);
                if (s > 0 && (mejor == 0 || s < mejor)) mejor = s;
                continue;
            }
            if (!c[2].equals("inicio")) max = Math.max(max, Math.round(num(c[5])));
            if (c[2].equals("fin")) prom = Math.round(num(c[4]));
        }
        if (prom < 0) prom = min > 0 ? Math.round(kms / (min / 60.0)) : 0;
        String dur = min < 60 ? min + " min" : (min / 60) + " h " + (min % 60) + " min";
        return uno(kms) + " km · " + dur + " · " + getString(R.string.abrev_prom) + " " + prom
                + " · " + getString(R.string.abrev_max) + " " + max
                + (mejor > 0 ? " · " + getString(R.string.acel_resultado, uno(mejor)) : "");
    }

    private String detalle(Viaje v) {
        String prom = getString(R.string.abrev_prom), max = getString(R.string.abrev_max);
        StringBuilder sb = new StringBuilder();
        for (String[] c : v.filas) {
            if (c[2].equals("acel")) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(c[1]).append("  ").append(getString(R.string.acel_resultado, uno(num(c[3]))));
                continue;
            }
            if (!c[2].equals("aviso")) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(c[1]).append("  ");
            if (c[3].equals("sin GPS")) sb.append(getString(R.string.sin_gps));
            else sb.append(String.format(Locale.ROOT, "%3s km/h", c[3]));
            sb.append("  ").append(prom).append(' ').append(c[4]).append("  ").append(max).append(' ').append(c[5])
                    .append("  ").append(uno(num(c[6]))).append(" km");
        }
        return sb.length() == 0 ? getString(R.string.sin_avisos) : sb.toString();
    }

    private void mostrarHistorial() {
        List<Viaje> vs = viajes(lineas());
        lista.removeAllViews();
        if (vs.isEmpty()) {
            lista.addView(texto(getString(R.string.sin_viajes), 14, TENUE), ancho(14));
            return;
        }
        if (primeraCarga) {
            abiertos.add(vs.get(vs.size() - 1).desde);
            primeraCarga = false;
        }
        for (int i = vs.size() - 1; i >= 0; i--) {
            Viaje v = vs.get(i);
            boolean enCurso = Servicio.activo && i == vs.size() - 1;
            lista.addView(tarjeta(v, enCurso), ancho(10));
        }
    }

    private View tarjeta(Viaje v, boolean enCurso) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setBackground(redondo(SUPERFICIE));
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        TextView tit = texto(titulo(v, enCurso), 15, TEXTO);
        tit.setTypeface(Typeface.DEFAULT_BOLD);
        t.addView(tit);
        t.addView(texto(resumen(v), 14, VERDE), ancho(4));
        TextView det = texto(detalle(v), 13, TEXTO);
        det.setTypeface(Typeface.MONOSPACE);
        det.setLineSpacing(dp(3), 1);
        det.setVisibility(abiertos.contains(v.desde) ? View.VISIBLE : View.GONE);
        t.addView(det, ancho(10));
        t.setOnClickListener(x -> {
            boolean abrir = det.getVisibility() != View.VISIBLE;
            det.setVisibility(abrir ? View.VISIBLE : View.GONE);
            if (abrir) abiertos.add(v.desde);
            else abiertos.remove(v.desde);
        });
        t.setOnLongClickListener(x -> {
            new AlertDialog.Builder(this)
                    .setTitle(titulo(v, enCurso))
                    .setItems(new String[]{getString(R.string.copiar), getString(R.string.borrar_viaje)}, (d, w) -> {
                        if (w == 0) copiar(v, enCurso);
                        else if (enCurso) Toast.makeText(this, R.string.parar_antes, Toast.LENGTH_SHORT).show();
                        else confirmarBorrar(v);
                    }).show();
            return true;
        });
        return t;
    }

    private void copiar(Viaje v, boolean enCurso) {
        String txt = titulo(v, enCurso) + "\n" + resumen(v) + "\n\n" + detalle(v);
        getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Velocidad Libre", txt));
        Toast.makeText(this, R.string.viaje_copiado, Toast.LENGTH_SHORT).show();
    }

    private void confirmarBorrar(Viaje v) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.confirmar_borrar, titulo(v, false)))
                .setPositiveButton(R.string.borrar, (d, w) -> borrar(v))
                .setNegativeButton(R.string.cancelar, null).show();
    }

    private void borrar(Viaje v) {
        List<String> ls = lineas();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ls.size(); i++) {
            if (i < v.desde || i > v.hasta) sb.append(ls.get(i)).append('\n');
        }
        try (FileWriter w = new FileWriter(Servicio.archivo(this), false)) {
            w.write(sb.toString());
        } catch (IOException e) {
            Toast.makeText(this, R.string.no_pude_borrar, Toast.LENGTH_SHORT).show();
        }
        abiertos.remove(v.desde);
        tamanoLeido = -2;
        refrescar();
    }
}
