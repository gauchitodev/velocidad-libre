package dev.gauchito.velocidad;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * Idioma elegido en la app (independiente del idioma del teléfono).
 * Para sumar uno: agregarlo acá en los tres arreglos y crear res/values-XX/strings.xml.
 */
final class Idioma {
    static final String[] CODIGOS = {"es", "en", "pt", "fr", "it", "de"};
    static final String[] NOMBRES = {"Español", "English", "Português", "Français", "Italiano", "Deutsch"};
    // Voz para el texto a voz: español latino, portugués de Brasil.
    private static final String[] VOCES = {"es-US", "en-US", "pt-BR", "fr-FR", "it-IT", "de-DE"};

    private Idioma() { }

    private static SharedPreferences p(Context c) { return c.getSharedPreferences("p", 0); }

    static boolean elegido(Context c) { return p(c).contains("idioma"); }

    static void elegir(Context c, String codigo) { p(c).edit().putString("idioma", codigo).apply(); }

    /** El elegido o, si todavía no eligió, el del teléfono (inglés si no lo tenemos). */
    static String codigo(Context c) {
        String k = p(c).getString("idioma", null);
        if (k != null) return k;
        String tel = Locale.getDefault().getLanguage();
        for (String x : CODIGOS) if (x.equals(tel)) return x;
        return "en";
    }

    static int indice(Context c) {
        String k = codigo(c);
        for (int i = 0; i < CODIGOS.length; i++) if (CODIGOS[i].equals(k)) return i;
        return 0;
    }

    static Locale locale(Context c) { return Locale.forLanguageTag(codigo(c)); }

    static Locale voz(Context c) { return Locale.forLanguageTag(VOCES[indice(c)]); }

    /** Un contexto que lee los textos en el idioma elegido. */
    static Context envolver(Context c) {
        Configuration conf = new Configuration(c.getResources().getConfiguration());
        conf.setLocale(locale(c));
        return c.createConfigurationContext(conf);
    }
}
