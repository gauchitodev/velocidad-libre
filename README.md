# Velocidad

App de Android que te dice la velocidad en voz alta mientras andás. Pensada para la moto: el teléfono en el bolsillo, los auriculares puestos, y cada tanto te avisa cómo venís. Sin publicidad, sin cuentas y sin internet.

## Qué hace

- **Cada 5 o 15 minutos** dice la velocidad actual y el promedio del tramo.
- **De 10 en 10 km/h:** cuando cambiás de decena dice "noventa", "cien", "ciento diez". Se apaga con un botón.
- **0 a 100:** tocás el botón, parás, acelerás y te dice en cuántos segundos llegaste a 100.
- **Historial de viajes:** cada viaje queda guardado con km, duración, promedio, máxima y su mejor 0 a 100. Tocás un viaje para ver los avisos y lo mantenés apretado para copiarlo o borrarlo.
- Mientras habla baja la música, y anda con la pantalla apagada.
- La notificación tiene **Decir ahora** y **Parar**, así no tenés que abrir la app.

## Cómo instalarla

Necesitás Android 8 o más nuevo.

1. Desde el teléfono, entrá a la [última versión](https://github.com/gauchitodev/velocidad/releases/latest) y bajá **velocidad.apk**.
2. Abrí el archivo, desde la notificación de la descarga o desde la carpeta Descargas.
3. La primera vez, Android te va a decir que no puede instalar apps de ese lugar. Tocá **Configuración**, activá **Permitir de esta fuente**, volvé atrás y tocá **Instalar**.
4. Si aparece Play Protect diciendo que no conoce la app, tocá **Más detalles** y después **Instalar de todas formas**. Pasa con cualquier app que no viene de Play Store.
5. Abrí **Velocidad**, tocá **Empezar** y aceptá la ubicación ("Mientras se usa la app") y las notificaciones.
6. Cuando te pregunte por la batería, tocá **Dale** y después **Permitir**. Si no lo hacés, muchos teléfonos (Xiaomi, Samsung, Motorola, Huawei) cortan la app con la pantalla apagada.

Si te habla en inglés, entrá a **Ajustes › Sistema › Idiomas › Salida de texto a voz** y elegí español.

## Privacidad

La app no tiene permiso para usar internet. Los viajes se guardan solo en tu teléfono.

## Compilar

Está hecha en Java, sin Gradle ni librerías externas. `compilar.sh` la arma con aapt2, javac, d8 y apksigner. Ajustá las rutas del principio del script a donde tengas esas herramientas.
