# Velocidad Libre

[English](README.md) · **Español**

App de Android que te dice la velocidad en voz alta mientras te movés, en lo que sea que te muevas. Con el teléfono en el bolsillo o en el soporte, cada tanto te avisa cómo venís, sin que tengas que mirar la pantalla. Sin publicidad, sin cuentas y sin internet.

**Idiomas:** español, inglés, portugués, francés, italiano y alemán. Elegís uno la primera vez que abrís la app y lo cambiás cuando quieras con el botón de arriba a la derecha. Cambian la pantalla, la notificación y la voz.

## Qué hace

- **Cada 5 o 15 minutos** dice la velocidad actual y el promedio del tramo.
- **De 10 en 10 km/h:** cuando cambiás de decena dice "noventa", "cien", "ciento diez". Se apaga con un botón.
- **0 a 100:** tocás el botón, parás, acelerás y te dice en cuántos segundos llegaste a 100.
- **Historial de viajes:** cada viaje queda guardado con km, duración, promedio, máxima y su mejor 0 a 100. Tocás un viaje para ver los avisos y lo mantenés apretado para copiarlo o borrarlo.
- Mientras habla baja la música, y anda con la pantalla apagada.
- La notificación tiene **Decir ahora** y **Parar**, así no tenés que abrir la app.

## Cómo instalarla

Necesitás Android 8 o más nuevo.

1. Desde el teléfono, entrá a la [última versión](https://github.com/gauchitodev/velocidad-libre/releases/latest) y bajá **velocidad-libre.apk**.
2. Abrí el archivo, desde la notificación de la descarga o desde la carpeta Descargas.
3. La primera vez, Android te va a decir que no puede instalar apps de ese lugar. Tocá **Configuración**, activá **Permitir de esta fuente**, volvé atrás y tocá **Instalar**.
4. Si aparece Play Protect diciendo que no conoce la app, tocá **Más detalles** y después **Instalar de todas formas**. Pasa con cualquier app que no viene de Play Store.
5. Abrí **Velocidad Libre**, elegí el idioma, tocá **Empezar** y aceptá la ubicación ("Mientras se usa la app") y las notificaciones.
6. Cuando te pregunte por la batería, tocá **Dale** y después **Permitir**. Si no lo hacés, muchos teléfonos (Xiaomi, Samsung, Motorola, Huawei) cortan la app con la pantalla apagada.

La voz es la del texto a voz del teléfono. Si el teléfono no tiene la voz del idioma que elegiste, la app muestra un aviso en rojo: tocalo para instalarla.

## Privacidad

La app no tiene permiso para usar internet. Los viajes se guardan solo en tu teléfono.

## Compilar

Está hecha en Java, sin Gradle ni librerías externas. `compilar.sh` la arma con aapt2, javac, d8 y apksigner. Ajustá las rutas del principio del script a donde tengas esas herramientas.

## Sumar un idioma

1. Copiá `res/values/strings.xml` (español) a `res/values-XX/strings.xml`, donde `XX` es el código del idioma, y traducí los textos. Las partes `%1$s` y `%1$d` dejalas como están.
2. Agregá el idioma a las tres listas de `src/dev/gauchito/velocidad/Idioma.java`: el código, el nombre en su propio idioma y la voz del texto a voz (por ejemplo `nl-NL`).
3. Mandá un pull request.

## Licencia

[MIT](LICENSE)
