package dev.gauchito.velocidad;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Mide con el GPS todo el tiempo y:
 *  - cada N minutos dice la velocidad actual y el promedio del tramo (queda en registro.csv);
 *  - si está prendido, avisa cada vez que cambiás de decena (noventa, cien, ciento diez…);
 *  - en modo 0 a 100, mide cuánto tardás desde parado hasta 100 km/h.
 */
public class Servicio extends Service implements LocationListener, TextToSpeech.OnInitListener {
    static final String PARAR = "dev.gauchito.velocidad.PARAR";
    static final String DECIR = "dev.gauchito.velocidad.DECIR";
    static final String REPROGRAMAR = "dev.gauchito.velocidad.REPROGRAMAR";
    static final String ACEL = "dev.gauchito.velocidad.ACEL";
    static final String ACEL_CANCELAR = "dev.gauchito.velocidad.ACEL_CANCELAR";
    static final String IDIOMA = "dev.gauchito.velocidad.IDIOMA";
    private static final String CANAL = "velocidad";
    private static final String TAG = "Velocidad";

    // Modo 0 a 100.
    static final int ACEL_NO = 0, ACEL_ESPERA_PARADO = 1, ACEL_LISTO = 2, ACEL_MIDIENDO = 3;
    private static final float ARRANQUE = 2f;  // km/h: de acá para arriba ya se movió

    // Estado que lee la pantalla (mismo proceso).
    static volatile boolean activo;
    static volatile float kmh = -1;
    static volatile long ultimoFix;     // elapsedRealtime del último dato del GPS
    static volatile long proximoAviso;  // elapsedRealtime del próximo aviso
    static volatile int acel = ACEL_NO;
    static volatile String ultimoCeroCien;
    static volatile boolean sinVoz;   // el teléfono no tiene voz para el idioma elegido

    private final Handler h = new Handler(Looper.getMainLooper());
    private LocationManager lm;
    private TextToSpeech tts;
    private boolean ttsListo;
    private String pendiente;
    private Locale vozPuesta;
    private AudioManager am;
    private AudioFocusRequest foco;
    private PowerManager.WakeLock wl;
    private NotificationManager nm;
    private long ultimaNotif;

    private Location anterior;
    private double mTramo, mViaje;
    private float maxTramo, maxViaje;
    private long iniTramo, iniViaje;

    private int decena = -1;     // última decena dicha
    private long ultimoDicho;    // elapsedRealtime de la última vez que habló por decena

    private float acelV;         // velocidad y tiempo (ns) del dato anterior, para interpolar
    private long acelT, acelInicio;

    private final Runnable tick = new Runnable() {
        @Override public void run() { pedir(true); programar(); }
    };

    // Con la pantalla apagada el teléfono junta las posiciones y las entrega de a tandas:
    // al avisar se le pide que las largue ya y, si igual no hay una reciente, se espera hasta 20 s.
    private boolean esperando, esperandoRegistrar;
    private final Runnable sinSenal = new Runnable() {
        @Override public void run() { if (esperando) responder(); }
    };

    static int minutos(Context c) { return c.getSharedPreferences("p", 0).getInt("min", 15); }

    static boolean porDecena(Context c) { return c.getSharedPreferences("p", 0).getBoolean("diez", true); }

    static File archivo(Context c) { return new File(c.getFilesDir(), "registro.csv"); }

    /** Texto en el idioma elegido en la app (se lee cada vez: se puede cambiar en marcha). */
    private String t(int id, Object... args) { return Idioma.envolver(this).getString(id, args); }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int flags, int id) {
        String a = i == null ? null : i.getAction();
        if (PARAR.equals(a)) { stopSelf(); return START_NOT_STICKY; }
        if (!activo) {
            if (!arrancar()) { stopSelf(); return START_NOT_STICKY; }
            if (ACEL.equals(a)) armarAcel();
        } else if (DECIR.equals(a)) {
            pedir(false);
        } else if (REPROGRAMAR.equals(a)) {
            programar();
            notificar(true);
            hablar(t(R.string.voz_intervalo, minutos(this)));
        } else if (ACEL.equals(a)) {
            armarAcel();
        } else if (ACEL_CANCELAR.equals(a)) {
            terminarAcel();
            hablar(t(R.string.voz_acel_cancelado));
        } else if (IDIOMA.equals(a)) {
            canal();
            notificar(true);
        }
        return START_STICKY;
    }

    private boolean arrancar() {
        nm = getSystemService(NotificationManager.class);
        canal();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(1, notificacion(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(1, notificacion());
            }
        } catch (Exception e) {
            Log.w(TAG, "startForeground", e);
            return false;
        }
        lm = getSystemService(LocationManager.class);
        if (!pedirGps(1000)) return false;
        am = getSystemService(AudioManager.class);
        wl = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "velocidad:avisos");
        wl.acquire();
        pendiente = t(R.string.voz_inicio, minutos(this));
        tts = new TextToSpeech(this, this);
        iniTramo = iniViaje = SystemClock.elapsedRealtime();
        kmh = -1;
        ultimoFix = 0;
        decena = -1;
        acel = ACEL_NO;
        activo = true;
        registrar("inicio", "", "", "", "", "");
        programar();
        notificar(true);
        return true;
    }

    private void canal() {
        nm.createNotificationChannel(new NotificationChannel(CANAL, t(R.string.canal), NotificationManager.IMPORTANCE_LOW));
    }

    private boolean pedirGps(long cadaMs) {
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, cadaMs, 0, this, Looper.getMainLooper());
            return true;
        } catch (SecurityException | IllegalArgumentException e) {
            Log.w(TAG, "requestLocationUpdates", e);
            return false;
        }
    }

    private void programar() {
        h.removeCallbacks(tick);
        long ms = minutos(this) * 60000L;
        proximoAviso = SystemClock.elapsedRealtime() + ms;
        h.postDelayed(tick, ms);
    }

    @Override public void onLocationChanged(Location l) {
        float v = l.hasSpeed() ? l.getSpeed() * 3.6f : 0f;
        kmh = v;
        // La hora del dato en sí, no la de entrega (las tandas llegan atrasadas).
        ultimoFix = Math.min(SystemClock.elapsedRealtime(), l.getElapsedRealtimeNanos() / 1000000);
        boolean preciso = l.getAccuracy() < 40;
        // Parado, el GPS "camina" solo: la distancia se suma únicamente en movimiento.
        if (anterior != null && preciso && v > 3) {
            float d = anterior.distanceTo(l);
            mTramo += d;
            mViaje += d;
        }
        anterior = l;
        if (preciso) {
            maxTramo = Math.max(maxTramo, v);
            maxViaje = Math.max(maxViaje, v);
        }
        if (acel != ACEL_NO) ceroCien(v, l.getElapsedRealtimeNanos());
        else if (preciso) decena(v);
        notificar(false);
        if (esperando && conSenal()) responder();
    }

    // Android 8–10 todavía llama a estos tres; sin ellos la app se cerraría.
    @Override public void onStatusChanged(String p, int estado, Bundle extras) { }
    @Override public void onProviderEnabled(String p) { }
    @Override public void onProviderDisabled(String p) { }

    /** Dice la decena nueva cuando la velocidad cambió 10 km/h desde la última que dijo. */
    private void decena(float v) {
        if (!porDecena(this)) { decena = -1; return; }
        if (decena < 0) { decena = Math.round(v / 10) * 10; return; }  // la primera vez, callado
        int nueva;
        if (v >= decena + 10) nueva = (int) (v / 10) * 10;
        else if (v <= decena - 10) nueva = (int) Math.ceil(v / 10) * 10;
        else return;
        long ahora = SystemClock.elapsedRealtime();
        if (tts == null || !ttsListo || tts.isSpeaking() || ahora - ultimoDicho < 3000) return;
        decena = nueva;
        ultimoDicho = ahora;
        if (nueva > 0) hablar(String.valueOf(nueva));
    }

    private void armarAcel() {
        pedirGps(0);  // lo más seguido que dé el GPS
        acel = ACEL_ESPERA_PARADO;
        ultimoCeroCien = null;
        if (conSenal() && kmh < ARRANQUE) {
            acel = ACEL_LISTO;
            acelV = kmh;
            acelT = SystemClock.elapsedRealtimeNanos();
            hablar(t(R.string.voz_acel_listo_inicio));
        } else {
            hablar(t(R.string.voz_acel_parar));
        }
        notificar(true);
    }

    private void terminarAcel() {
        acel = ACEL_NO;
        decena = -1;
        if (lm != null) pedirGps(1000);
        notificar(true);
    }

    /** t en nanosegundos del propio dato del GPS: aunque lleguen de a tandas, el tiempo da bien. */
    private void ceroCien(float v, long t) {
        switch (acel) {
            case ACEL_ESPERA_PARADO:
                if (v < ARRANQUE) {
                    acel = ACEL_LISTO;
                    hablar(t(R.string.voz_acel_listo));
                    notificar(true);
                }
                break;
            case ACEL_LISTO:
                if (v >= ARRANQUE) {
                    acelInicio = cruce(acelV, acelT, v, t, ARRANQUE);
                    acel = ACEL_MIDIENDO;
                    notificar(true);
                }
                break;
            case ACEL_MIDIENDO:
                if (v >= 100) {
                    double s = (cruce(acelV, acelT, v, t, 100) - acelInicio) / 1e9;
                    ultimoCeroCien = String.format(Idioma.locale(this), "%.1f", s);
                    hablar(t(R.string.voz_acel_resultado, ultimoCeroCien));
                    registrar("acel", String.format(Locale.ROOT, "%.1f", s), "", "", "", km(mViaje));
                    terminarAcel();
                } else if (v < ARRANQUE) {
                    acel = ACEL_LISTO;
                    hablar(t(R.string.voz_acel_cortado));
                    notificar(true);
                } else if (t - acelInicio > 60e9) {
                    hablar(t(R.string.voz_acel_tiempo));
                    terminarAcel();
                }
                break;
            default:
                break;
        }
        acelV = v;
        acelT = t;
    }

    /** Momento (ns) en que la velocidad pasó por `meta`, interpolando entre dos datos del GPS. */
    private static long cruce(float v1, long t1, float v2, long t2, float meta) {
        if (v2 <= v1 || t2 <= t1) return t2;
        return t1 + (long) ((meta - v1) / (v2 - v1) * (t2 - t1));
    }

    private void pedir(boolean registrar) {
        Log.i(TAG, "aviso pedido; último dato hace " + (SystemClock.elapsedRealtime() - ultimoFix) + " ms");
        esperandoRegistrar |= registrar;
        esperando = true;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                lm.requestFlush(LocationManager.GPS_PROVIDER, this, 1);
            } catch (RuntimeException e) {
                Log.w(TAG, "requestFlush", e);
            }
        }
        if (conSenal()) { responder(); return; }
        h.removeCallbacks(sinSenal);
        h.postDelayed(sinSenal, 20000);
    }

    private void responder() {
        esperando = false;
        h.removeCallbacks(sinSenal);
        if (esperandoRegistrar) {
            esperandoRegistrar = false;
            aviso();
        } else {
            hablar(texto());
        }
    }

    private static boolean conSenal() {
        return ultimoFix > 0 && SystemClock.elapsedRealtime() - ultimoFix < 10000;
    }

    private static long prom(double metros, long desde) {
        double s = (SystemClock.elapsedRealtime() - desde) / 1000.0;
        return s < 1 ? 0 : Math.round(metros / s * 3.6);
    }

    private static String km(double metros) { return String.format(Locale.ROOT, "%.1f", metros / 1000); }

    private String texto() {
        if (!conSenal()) return t(R.string.voz_sin_gps);
        return t(R.string.voz_velocidad, (int) Math.round(kmh), (int) prom(mTramo, iniTramo));
    }

    private void aviso() {
        // Durante el 0 a 100 no se interrumpe: el aviso se anota igual, sin hablar.
        if (acel != ACEL_MIDIENDO) hablar(texto());
        if (conSenal()) decena = Math.round(kmh / 10) * 10;
        String actual = conSenal() ? String.valueOf(Math.round(kmh)) : "sin GPS";
        registrar("aviso", actual, String.valueOf(prom(mTramo, iniTramo)), String.valueOf(Math.round(maxTramo)),
                km(mTramo), km(mViaje));
        mTramo = 0;
        maxTramo = 0;
        iniTramo = SystemClock.elapsedRealtime();
    }

    private void registrar(String tipo, String actual, String promedio, String maxima, String kmTramo, String kmViaje) {
        String linea = new SimpleDateFormat("yyyy-MM-dd,HH:mm", Locale.ROOT).format(new Date())
                + "," + tipo + "," + actual + "," + promedio + "," + maxima + "," + kmTramo + "," + kmViaje + "\n";
        try (FileWriter w = new FileWriter(archivo(this), true)) {
            w.write(linea);
        } catch (IOException ignorado) {
            // Sin registro no se corta el aviso por audio.
        }
    }

    private AudioAttributes atributos() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    }

    private void hablar(String t) {
        if (!ttsListo) {
            pendiente = pendiente == null ? t : pendiente + " " + t;
            return;
        }
        if (foco == null) {
            foco = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(atributos()).build();
        }
        ponerVoz();
        am.requestAudioFocus(foco);  // baja la música mientras habla
        tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "v" + SystemClock.elapsedRealtime());
    }

    /** Pone la voz del idioma elegido; si el teléfono no la tiene, prueba sin país y avisa a la pantalla. */
    private void ponerVoz() {
        Locale v = Idioma.voz(this);
        if (v.equals(vozPuesta)) return;
        vozPuesta = v;
        int r = tts.setLanguage(v);
        if (r < 0) r = tts.setLanguage(new Locale(v.getLanguage()));
        sinVoz = r < 0;
        Log.i(TAG, "voz " + v + " -> " + r);
    }

    @Override public void onInit(int estado) {
        if (estado != TextToSpeech.SUCCESS || tts == null) return;
        tts.setAudioAttributes(atributos());
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { soltarFoco(); }
            @Override public void onError(String id) { soltarFoco(); }
        });
        ttsListo = true;
        if (pendiente != null) { String p = pendiente; pendiente = null; hablar(p); }
    }

    private void soltarFoco() { if (am != null && foco != null) am.abandonAudioFocusRequest(foco); }

    private Notification notificacion() {
        String txt = conSenal() ? Math.round(kmh) + " km/h" : t(R.string.buscando_gps);
        if (acel == ACEL_ESPERA_PARADO) txt += " · " + t(R.string.acel_parar);
        else if (acel == ACEL_LISTO) txt += " · " + t(R.string.acel_listo);
        else if (acel == ACEL_MIDIENDO) txt += " · " + t(R.string.acel_midiendo);
        else if (proximoAviso > 0) {
            long cuando = System.currentTimeMillis() + (proximoAviso - SystemClock.elapsedRealtime());
            txt += " · " + t(R.string.notif_proximo, new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date(cuando)));
        }
        int f = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent abrir = PendingIntent.getActivity(this, 0, new Intent(this, Principal.class), f);
        PendingIntent decir = PendingIntent.getService(this, 1, new Intent(this, Servicio.class).setAction(DECIR), f);
        PendingIntent parar = PendingIntent.getService(this, 2, new Intent(this, Servicio.class).setAction(PARAR), f);
        Icon ic = Icon.createWithResource(this, R.drawable.notif);
        return new Notification.Builder(this, CANAL)
                .setSmallIcon(R.drawable.notif)
                .setContentTitle(t(R.string.nombre) + " · " + t(R.string.cada_min, minutos(this)))
                .setContentText(txt)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(abrir)
                .addAction(new Notification.Action.Builder(ic, t(R.string.decir_ahora), decir).build())
                .addAction(new Notification.Action.Builder(ic, t(R.string.parar), parar).build())
                .build();
    }

    private void notificar(boolean ya) {
        long ahora = SystemClock.elapsedRealtime();
        if (!activo || (!ya && ahora - ultimaNotif < 5000)) return;
        ultimaNotif = ahora;
        nm.notify(1, notificacion());
    }

    @Override public void onDestroy() {
        h.removeCallbacks(tick);
        h.removeCallbacks(sinSenal);
        if (activo) {
            registrar("fin", "", String.valueOf(prom(mViaje, iniViaje)), String.valueOf(Math.round(maxViaje)),
                    "", km(mViaje));
        }
        activo = false;
        kmh = -1;
        proximoAviso = 0;
        acel = ACEL_NO;
        sinVoz = false;
        if (lm != null) lm.removeUpdates(this);
        if (tts != null) tts.shutdown();
        soltarFoco();
        if (wl != null && wl.isHeld()) wl.release();
        super.onDestroy();
    }
}
