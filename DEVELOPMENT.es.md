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
- **Min SDK:** 24 (Android 7.0)
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
- Soporte de anclaje para mantener archivos importantes al principio.
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
