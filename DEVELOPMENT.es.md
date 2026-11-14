# Guía de Desarrollo

Este documento contiene información técnica para desarrolladores que trabajan en FileManager.

## Contenidos

- [Stack tecnológico](#stack-tecnológico)
- [Estructura del proyecto](#estructura-del-proyecto)
- [Comportamiento de la aplicación](#comportamiento-de-la-aplicación)
- [Configuración](#configuración)
- [Versiones y changelog](#versiones-y-changelog)
- [Testing recomendado](#testing-recomendado)
- [Problemas comunes](#problemas-comunes)

---

## Stack tecnológico

- **Lenguaje:** Java
- **Plataforma:** Android
- **Build:** Gradle (Android Application plugin)
- **UI:** AppCompat, RecyclerView, SwipeRefreshLayout
- **Min SDK:** 30 (Android 11)
- **Target SDK:** 34
- **Namespace/ApplicationId:** com.fraugz.filemanager

---

## Estructura del proyecto

```text
app/
  src/main/
    java/com/fraugz/filemanager/
      MainActivity.java
      SettingsActivity.java
      TrashActivity.java
      FileAdapter.java
      RecentAdapter.java
      ThemeManager.java
      LocaleManager.java
      TrashManager.java
      RecentManager.java
      DefaultAppsManager.java
      ...
    res/
      layout/
      drawable/
      values/       # Inglés (base)
      values-es/    # Español
```

---

## Comportamiento de la aplicación

### Desplazamiento rápido

- Almacenamiento, Recientes y Papelera comparten `FastScrollRecyclerView`.
- Cuando la lista necesita scroll, aparece una barra fina con forma de píldora en el borde derecho: 6 x 48 dp (8 dp de ancho y color de acento mientras se arrastra), con zona táctil de 48 dp de ancho. Aumenta con la escala grande de interfaz.
- Mientras se arrastra, una burbuja con forma de lágrima apunta hacia ella desde la izquierda, separada del borde para que el dedo no tape el texto. Indica la fila por la que pasa, usando el criterio por el que está ordenada la lista: la inicial (orden por nombre), la fecha (orden por fecha, y siempre en Recientes), el tamaño del fichero (orden por tamaño) o la fecha de borrado (Papelera). Los adaptadores aportan el texto mediante `FastScrollRecyclerView.LabelProvider`.
- En orden por nombre, la inicial no sirve cuando muchos ficheros empiezan igual (`IMG_…`, `Screenshot_…`, `20261114-…`). Cuando más de 30 filas seguidas comparten el comienzo, `NameLabels` lo salta y las etiqueta con lo que cambia después: la fecha escrita en el nombre (`aaaaMMdd` o `aaaa-MM-dd`, mostrada como en Recientes) o, si no la hay, los primeros caracteres distintos. Una etiqueta de texto empieza siempre por el comienzo real del nombre, para que nunca parezca otra inicial: el comienzo entero si es corto (`La ciudad` → `LA C`) y, si no, la inicial, unos puntos suspensivos y lo que cambia (`Factura_Enero` → `F…E`, `informe_137` → `I…13`). Los grupos más cortos conservan la inicial.
- Arrastrarla permite saltar por la lista sin recorrer los elementos intermedios. Se oculta en listas vacías o que caben en pantalla.
- El arrastre bloquea el gesto de refrescar y el cambio horizontal de pestaña; fuera de la uña se mantienen los gestos normales y la selección.

### Apps predeterminadas

- Al abrir un archivo, la app intenta guardar el handler externo resuelto.
- Las actividades chooser/resolver del sistema se ignoran.
- Si no hay app predeterminada explícita pero existe un único handler válido, se guarda como app efectiva.
- La lista en Ajustes > Apps por defecto se ordena por detección más reciente.
- Las reglas se guardan por extensión de archivo (por ejemplo: `.txt` → paquete de app).
- El flujo de Añadir extensión y app ofrece primero extensiones comunes pendientes, más una opción de extensión personalizada.
- Los diálogos de selección de app incluyen buscador e iconos de aplicaciones.
- El mismo selector buscable con iconos también está disponible desde Archivos/Recientes para definir la app predeterminada al momento.
- En menús de selección/fichero, la acción se muestra como **Abrir**; filtra apps de usuario por tipo de fichero, guarda la elegida como predeterminada para esa extensión y abre el fichero al instante con esa app.
- La visibilidad de apps en Android 11+ usa queries del manifiesto para handlers launcher y VIEW.

**Acciones disponibles por entrada:**
- Cambiar app
- Borrar entrada
- Limpiar todas (con confirmación)

### Papelera

- Los elementos eliminados se mueven a la papelera interna de la app (desde Almacenamiento) o a la papelera del sistema (archivos media en Android 11+).
- Cada entrada guarda metadatos: ruta original, nombre original y fecha de borrado.
- Restaurar intenta devolver al origen; si hay conflicto genera un nombre único.
- Vaciar papelera elimina definitivamente todos los elementos.
- Existe una política de retención que limpia elementos antiguos automáticamente.
- Si el movimiento directo falla, se aplica fallback de copia/borrado cuando es posible.

### Importación desde compartir

- FileManager aparece como destino en la hoja de compartir de Android para envío simple y múltiple.
- Los elementos compartidos quedan en cola dentro de la app y se muestran en la barra superior como **Guardar aquí**.
- Puedes navegar a cualquier carpeta y guardar allí todos los archivos compartidos pendientes.

### Comportamiento de Recientes

- Los archivos recientes se ordenan por último acceso.
- Cuando un archivo se mueve a una nueva ruta, la entrada anterior se elimina de Recientes.
- Gestos de swipe horizontal para cambiar entre Recientes y Archivos.
- Arrastre hacia abajo en la vista de Archivos para refrescar el directorio actual.
- Acciones rápidas en Recientes:
  - Limpiar todos los recientes con confirmación
  - Pulsación larga en un reciente para **Definir app por defecto** o **Quitar de recientes**
- Los elementos fijados quedan siempre arriba, en su propia sección «Fijados». No cuentan para el límite de 50 elementos recientes, así que debajo siguen apareciendo los recientes por muchos elementos que se fijen.
- Separadores de día para mejor organización.
- Limpiar recientes conserva los elementos anclados.
- Las carpetas visitadas también aparecen en Recientes, ordenadas por último acceso.

### Operaciones de archivo

- **Renombrar** con control de extensión (nombre completo o solo nombre), sin preselección forzada en el campo.
- **Acciones de multiselección:** copiar, mover, compartir y enviar a la papelera.
- La barra de acciones de selección incluye la acción directa **Abrir** cuando exactamente un fichero está seleccionado.
- **Previsualizaciones enriquecidas** con caché: miniaturas de imagen, fotogramas de vídeo, portadas de audio embebidas, iconos de APK y fallback al icono de la app configurada para tipos sin preview.
- Los nombres de archivo muy largos se muestran en tantas líneas como sean necesarias.

### Selección múltiple — acciones disponibles

Las acciones visibles en la barra inferior varían según la pestaña activa y lo que esté seleccionado.

**Almacenamiento**

| Selección | Enviar | Abrir | Reproducir | Fijar/Desfijar | Mover | Copiar | Eliminar | Renombrar | Info |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 fichero | ✓ | ✓ | — | — | ✓ | ✓ | ✓ | ✓ | ✓ |
| 1 carpeta | — | — | — | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| N ficheros (todos media) | ✓ | — | ✓ | — | ✓ | ✓ | ✓ | — | — |
| N ficheros | ✓ | — | — | — | ✓ | ✓ | ✓ | — | — |
| N carpetas | — | — | — | ✓ | ✓ | ✓ | ✓ | — | — |
| Mix ficheros y carpetas | ✓ | — | — | — | ✓ | ✓ | ✓ | — | — |

**Recientes**

| Selección | Enviar | Reproducir | Localizar | Mover | Copiar | Eliminar | Renombrar | Fijar/Desfijar |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 fichero | ✓ | — | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 1 carpeta | — | — | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| N ficheros (todos media) | ✓ | ✓ | — | ✓ | ✓ | ✓ | — | ✓ |
| N ficheros | ✓ | — | — | ✓ | ✓ | ✓ | — | ✓ |
| N carpetas | — | — | — | ✓ | ✓ | ✓ | — | ✓ |

**Papelera**

| Selección | Restaurar | Info | Eliminar definitivamente |
|---|:---:|:---:|:---:|
| 1 elemento | ✓ | ✓ | ✓ |
| N elementos | ✓ | — | ✓ |

---

## Configuración

### Idioma

**Ruta:** Ajustes > Idioma

**Opciones:**
- Español
- English

**Comportamiento inicial:**
- Si no hay preferencia guardada, se usa el idioma del dispositivo.
- Si el dispositivo no está en español, el fallback es inglés.

### Tema y escala

**Ruta:** Ajustes

**Opciones:**
- **Tema:** Dark / Light
- **Escala UI:** Small, Normal, Large, Extra large

---

## Versiones y changelog

**Tags publicados:**

- **v1.0.0:** Baseline inicial.
- **v1.1.0:** Cambio de branding y paquete a `com.fraugz.filemanager`.
- **v1.2.0:** Localización bilingüe (EN/ES) y selector de idioma.
- **v1.2.1:** Ajustes de estabilidad y mejoras menores.
- **v1.2.2:** Correcciones en flujos de gestión de archivos.
- **v1.2.3:** Mejoras incrementales de UX y robustez.
- **v1.2.4:** Actualizaciones de gestión de papelera/errores recientes y mantenimiento.
- **v1.2.5:** Refinamientos de UX en Recientes/selección, acción directa de app por defecto desde Archivos/Recientes, flujo de app por extensión con selector buscable, previsualizaciones más ricas (audio/vídeo/APK + fallback al icono de app), aviso de seguridad para instalar APK, actualización de icono de idioma y correcciones de visibilidad de apps en Android 11+.
- **v1.2.6:** Actualización de robustez de reproducción (playlist M3U temporal con compatibilidad específica para VLC/AIMP/Total Commander), seleccionar todo inline y limpieza de estado de selección, comportamiento de pegado de un solo uso con etiqueta dinámica Mover/Pegar, diálogos de progreso mejorados para flujos de mover/borrar/papelera, cancelación real en operaciones largas de papelera, opción de eliminar definitivamente con confirmación adicional de aviso e icono de alerta, UI de progreso al borrar en papelera y optimización de refresco incremental tras renombrar para reducir bloqueos en carpetas grandes.
- **v1.2.7:** Ajustes finos de UI/UX: el botón de ajustes abre Settings directamente, los textos de la barra inferior de selección se limitan a una línea, la acción App por defecto se renombra a Abrir y el selector filtra por tipo de fichero manteniendo solo apps de usuario, al elegir una app se guarda como predeterminada y abre el fichero al instante, más actualización del icono de seleccionar todo (cuadrado vacío/marcado), consistencia de color con el icono de nueva carpeta y ajuste vertical a nivel de píxel.
- **v1.2.8:** Nombres de playlist temporales más descriptivos para reproducción multiarchivo, FileManager añadido como destino de compartir de Android con flujo de importación Guardar aquí, y los archivos movidos se eliminan de Recientes en su ruta anterior.
- **v1.2.9:** Nombre de app localizado por idioma del sistema (File Manager / Gestor Archivos), selector Abrir ampliado a un segundo paso con botón adicional Aplicaciones del sistema, acción Info en la barra de selección (elemento único, incluidas carpetas), Renombrar oculto en multiselección, nuevos enlaces a Guía Rápida (EN/ES) y GitHub del proyecto en Ajustes.
- **v1.3.0:** Recientes rediseñado para igualar la UX de lista/selección de Almacenamiento, luego refinado a acciones Localizar/Fijar/Info (selección única) y Fijar/Quitar de recientes en multiselección (Info oculto con 2+ elementos), con soporte directo para quitar de recientes uno o varios elementos, con Localizar saltando a Almacenamiento y seleccionando el fichero objetivo; incluye insignia de anclaje visible, orden por fecha de acceso con separadores de día, limpieza de recientes conservando anclados, y protección para que el autoescaneo no repueble archivos antiguos tras limpiar; el orden de confirmación de borrado mejoró (Cancelar, Eliminar definitivamente, Mover a papelera) con énfasis en la acción destructiva; Más apps sigue la misma estrategia de filtrado base que seleccionar extensión; los controles del encabezado de Almacenamiento se compactaron junto al título con acceso rápido de Inicio; el breadcrumb usa el ancho completo con márgenes laterales y salta de línea solo cuando es necesario; Enviar desde Almacenamiento ahora también admite selección de carpetas y abre apps compatibles desde el selector; además el guardado de texto compartido pide nombre/tipo personalizado antes de crear el fichero, el swipe horizontal entre Recientes y Almacenamiento sigue el dedo de forma fluida, y la etiqueta del selector Abrir se simplificó a Más apps.
- **v1.3.1:** Revisión completa de UX de la pantalla de papelera: previsualizaciones con miniatura para elementos de imagen/vídeo/audio/APK; al pulsar abre el fichero con la app preferida (respetando DefaultAppsManager, igual que el navegador principal); la pulsación larga muestra una barra de acciones inferior (Restaurar / Info / Eliminar) con iconos que coinciden con la barra principal de selección; ambas secciones de papelera (app y sistema) siempre visibles con marcador de estado vacío cuando cada una está vacía; los encabezados de sección llevan un distintivo de info ℹ (adaptado al tema); Vaciar papelera cubre ambas secciones con un diálogo de confirmación con recuento combinado; el recuento de elementos de carpeta ahora excluye archivos ocultos y marca los elementos en papelera por separado; corrección en apertura de imágenes por URI de MediaStore para que las apps de galería puedan deslizarse entre hermanos; cuenta atrás de auto-eliminación mostrada en las filas de elementos y en el diálogo de Info; el diálogo de Info también muestra la ruta original del archivo.
- **v1.3.2:** Corrección crítica: los archivos ahora se mueven correctamente a la papelera de la app en lugar de eliminarse directamente. Corrección en multiselección que también eliminaba permanentemente en lugar de mover a la papelera. Selección múltiple en la pantalla de papelera (pulsación larga para seleccionar, toque para alternar con selección activa, acciones en lote Restaurar/Eliminar). Las miniaturas de la papelera ahora usan el nombre original del archivo para detectar el tipo de media. Las carpetas visitadas aparecen ahora en Recientes, ordenadas por último acceso igual que los ficheros.
- **v1.3.3:** La papelera del sistema se detecta ahora mediante consulta a MediaStore (mismo enfoque que Google Photos), solucionando elementos que no aparecían en la pantalla de Papelera. El recuento de elementos de carpeta incluye tanto la papelera del sistema como la papelera de la app. En Recientes solo aparecen carpetas ancladas automáticamente; las no ancladas ya no se añaden al escanear. El icono de papelera es visible en las pestañas Almacenamiento y Recientes. Nuevo diálogo de comprobador de actualizaciones (una vez al día).
- **v1.3.4:** App registrada como manejador de ACTION_VIEW — aparece en "Abrir con…" desde cualquier app; el fichero abierto se añade a Recientes y el explorador navega a su carpeta. App registrada como manejador de ACTION_GET_CONTENT — aparece como selector de archivos cuando otras apps solicitan adjuntar o subir un fichero (mensajería, correo, almacenamiento en la nube, etc.); al seleccionar un archivo se devuelve su URI al llamador mediante setResult y se cierra la app. Autodescubrimiento de Recientes y escaneo de MediaStore en segundo plano eliminados (deprecados); solo aparecen ficheros abiertos manualmente o anclados. Volver a abrir un fichero ya presente en Recientes actualiza su marca de tiempo de acceso sin cambiar su posición (nuevo RecentManager.touch()).
- **v1.3.5:** Autodescubrimiento de Recientes restaurado mediante una consulta única a `MediaStore.Files` — sin rutas hardcodeadas; cubre imágenes, vídeo, audio, documentos (MEDIA_TYPE_DOCUMENT) y ficheros MEDIA_TYPE_NONE con una whitelist de extensiones conocidas (zip, apk, pdf, docx, epub, etc.). Se usa `DATE_ADDED` de MediaStore como timestamp de llegada en lugar de `File.lastModified()`, corrigiendo fotos de WhatsApp/Telegram con fechas históricas. Gesto pull-to-refresh en Recientes fuerza un escaneo inmediato (omite el throttle de 5 minutos). Volver a Recientes tras ≥30 segundos también lanza un escaneo forzado. Corregido el tamaño del `LruCache` (era 20 KB fijo — menor que una miniatura; ahora 1/8 de la heap). Corregido el bug de bucle de descubrimiento (el scan se relanzaba en cada `loadRecentFiles`, provocando ciclos de `notifyDataSetChanged` y múltiples taps para abrir). Corregido el gesto pull-to-refresh en carpetas vacías y resultados de búsqueda vacíos (`SwipeRefreshLayout` quedaba en `GONE`).
- **v1.3.10:** Recientes: el orden de la lista lo da solo `RecentManager` (fijados primero y después lo más reciente; `MainActivity` ya no reordena por fecha de acceso), las filas fijadas comparten una única cabecera "Fijados" y `RecentManager.trim()` conserva todos los fijados más los 50 no fijados más nuevos, en lugar de 50 entradas en total. `NameLabels`: una etiqueta de texto empieza siempre por el comienzo del nombre (`LA C` si el comienzo común es corto, `F…E` si es largo) en lugar de un carácter suelto de mitad del nombre. `versionCode` pasa a 1310; las versiones posteriores deben usar un número mayor (por ejemplo 1400 para la 1.4.0).
- **v1.3.9:** Desplazamiento rápido: `FastScrollRecyclerView` (Almacenamiento, Recientes, Papelera) dibuja una barra fina arrastrable y, mientras se arrastra, una burbuja que aporta el adaptador mediante `LabelProvider`; `NameLabels` salta el comienzo común de las series largas de nombres parecidos y las etiqueta con la fecha del nombre o los primeros caracteres distintos. `FileOperations.rename()` se niega a sustituir otro elemento existente. "Guardar como" ya no recorta a 80 caracteres los nombres confirmados, y el portapapeles solo se consume al confirmar el pegado. `onNewIntent()` atiende los accesos directos de carpeta. `UpdateChecker`: receptor de descargas registrado como `RECEIVER_EXPORTED` en el contexto de la aplicación (el aviso de descarga completada lo envía el proveedor de descargas del sistema, así que un receptor no exportado nunca lo recibe), las comprobaciones fallidas se avisan en la comprobación manual, las versiones descartadas se vuelven a ofrecer en la comprobación manual y no se muestra el diálogo sobre una actividad cerrada. Las copias canceladas o fallidas eliminan su destino parcial. Eliminar o enviar a la papelera un único elemento usa el camino en segundo plano. Cachés de miniaturas de `TrashActivity` y `RecentAdapter` dimensionadas a 1/8 de la memoria. Pruebas instrumentadas añadidas en `app/src/androidTest`.
- **v1.3.8:** Corrección: el `BroadcastReceiver` del `DownloadManager` se registraba sin la flag `RECEIVER_EXPORTED`, lo que hacía que la auto-instalación fallase silenciosamente en Android 13+ (API 33+, `targetSdkVersion >= 33`). Corregido pasando `Context.RECEIVER_EXPORTED` cuando `Build.VERSION.SDK_INT >= 33`, y flag `0` en versiones anteriores.
- **v1.3.7:** Al guardar un único fichero compartido o al pegar un único fichero del portapapeles (copiar o mover), aparece un diálogo "Guardar como" para confirmar o editar el nombre de destino antes de ejecutar la operación. El nombre base (antes de la extensión) queda preseleccionado para teclear uno nuevo de inmediato. El diálogo se muestra mediante el nuevo método auxiliar `showRenameBeforeSaveDialog`; la ruta de importación de elementos compartidos se refactorizó en `doImportSharedItems()` y la ruta de pegado del portapapeles en `doClipboardPaste()`. Las operaciones con varios ficheros no se ven afectadas.
- **v1.3.6:** App eliminada del selector del sistema “Abrir con…” para tipos de fichero arbitrarios; solo quedan registradas las intents ACTION_SEND, ACTION_SEND_MULTIPLE y ACTION_GET_CONTENT. Etiqueta de recuento de selección (`tv_selection_count`) añadida encima de la barra de acciones, mostrando el número de elementos seleccionados con el mismo color de fondo que la barra para evitar una línea visible. Botones “Seleccionar todo” (`btn_select_all_recent`) y “Seleccionar por día” (`btn_select_day_recent`) añadidos al encabezado de la pestaña Recientes; seleccionar todo ahora alterna — un segundo toque llama a `exitSelectionMode()` y el botón muestra un icono de verificación cuando todos los elementos están seleccionados (igual que la pestaña Almacenamiento). El menú contextual de pulsación larga en carpetas incluye una nueva opción “Añadir acceso directo al escritorio” mediante `ShortcutManagerCompat.requestPinShortcut`. Diálogo de actualización rediseñado con tres botones: Descargar APK (positivo), Recordar más tarde (neutro), Descartar actualización (negativo — guarda la versión descartada en SharedPreferences). La descarga de actualización reemplazada con `DownloadManager` para descarga directa en la app; un `BroadcastReceiver` de un solo uso escucha `ACTION_DOWNLOAD_COMPLETE` y lanza el instalador del sistema automáticamente.

---

## Testing recomendado

**Checklist mínimo antes de publicar:**

- ✅ Navegación de carpetas y breadcrumb
- ✅ Búsqueda (inicio, cancelación y resultados)
- ✅ Gesto de swipe horizontal entre las pestañas/vistas Recientes y Archivos
- ✅ Arrastre hacia abajo en la vista Archivos para refrescar el directorio actual
- ✅ Renombrado con y sin cambios de extensión
- ✅ Ordenación de Recientes por tiempo de acceso
- ✅ Comportamiento de pulsación larga en Recientes (aproximadamente medio segundo): Definir app por defecto y Quitar de recientes
- ✅ Acción Limpiar recientes y diálogo de confirmación
- ✅ Barra de acciones de selección:
  - Aparece al seleccionar ficheros/carpetas
  - Definir app por defecto solo aparece para selección de un único fichero
- ✅ Comportamiento de previsualizaciones enriquecidas:
  - Previsualizaciones de imagen/vídeo/audio/APK cuando están disponibles
  - Icono de fallback de la app configurada cuando no hay preview enriquecida
- ✅ Los nombres de archivo muy largos se parten en múltiples líneas sin truncado forzado
- ✅ Apertura de archivos y registro de app predeterminada
- ✅ Acciones de papelera: mover, restaurar y vaciar
- ✅ Selección múltiple en papelera: pulsación larga, alternado por toque, restaurar en lote, eliminar en lote
- ✅ Cambio de idioma y persistencia tras reinicio
- ✅ Cambio de tema y escala de UI

---

## Problemas comunes

### Permisos en Android 11+

Verificar acceso completo a archivos en ajustes del sistema si la app no puede acceder a ciertos directorios.

### Tipos de archivo que no abren

Algunos tipos de archivo pueden no abrirse dependiendo de qué apps externas estén instaladas en el dispositivo.

### Advertencias LF/CRLF en Git

En Windows, estas advertencias suelen ser inocuas si el build no se ve afectado. Configura los finales de línea de Git si es necesario:

```bash
git config core.autocrlf true
```
