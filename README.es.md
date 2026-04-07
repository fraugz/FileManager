# FileManager

Aplicacion Android de gestion de archivos con interfaz moderna, papelera, vista de recientes, miniaturas ligeras y localizacion bilingue (espanol/ingles).

## Idioma

| Idioma | Documento |
| --- | --- |
| Ingles | [README.md](README.md) |
| Espanol | [README.es.md](README.es.md) |

## Contents

- [Descripcion general](#descripcion-general)
- [Funciones principales](#funciones-principales)
- [Stack tecnico](#stack-tecnico)
- [Requisitos](#requisitos)
- [Compilacion y ejecucion](#compilacion-y-ejecucion)
- [Configuracion](#configuracion)
- [Comportamiento de la aplicacion](#comportamiento-de-la-aplicacion)
- [Estructura del proyecto](#estructura-del-proyecto)
- [Versiones y changelog](#versiones-y-changelog)
- [Testing recomendado](#testing-recomendado)
- [Problemas comunes](#problemas-comunes)
- [Roadmap](#roadmap)
- [Contribuir](#contribuir)

## Descripcion general

FileManager permite navegar almacenamiento interno, buscar archivos, gestionar papelera y abrir archivos con apps externas recordando preferencias de apertura.

## Funciones principales

- Navegacion por carpetas con breadcrumb.
- Busqueda con carga incremental y cancelacion.
- Vista de recientes ordenada por ultimo acceso.
- Cuando un fichero se mueve a otra ruta, la entrada de la ruta anterior se elimina de Recientes.
- Gestos de deslizamiento horizontal para cambiar entre las vistas Recientes y Archivos.
- Gesto de arrastrar hacia abajo en la vista Archivos para refrescar el directorio actual.
- Acciones rapidas en Recientes:
  - limpiar todos los recientes con confirmacion
  - pulsacion prolongada en un fichero reciente para definir App por defecto o Quitar de recientes
- Renombrado con control de extension (nombre completo o solo nombre), sin preseleccion forzada en el campo.
- Acciones en multiseleccion: copiar, mover, compartir y enviar a papelera.
- Destino de compartir entrante: FileManager aparece en la hoja de compartir de Android y permite navegar hasta la carpeta destino antes de guardar archivos compartidos.
- La barra de acciones de seleccion incluye accion directa de App por defecto cuando hay exactamente un fichero seleccionado.
- Papelera con restauracion y borrado permanente.
- Previsualizaciones enriquecidas con cache: miniaturas de imagen, frame de video, cover embebido en audio, icono de APK y fallback al icono de la app configurada cuando no hay preview.
- Los nombres largos de fichero se muestran en tantas lineas como haga falta en Archivos y Recientes.
- Ajustes de apariencia: tema, escala UI e idioma.
- Gestion de apps predeterminadas para apertura de archivos.

## Stack tecnico

- Lenguaje: Java
- Plataforma: Android
- Build: Gradle (Android Application plugin)
- UI: AppCompat, RecyclerView, SwipeRefreshLayout
- Min SDK: 24
- Target SDK: 34
- Namespace/ApplicationId: com.fraugz.filemanager

## Requisitos

- Android Studio actualizado
- JDK compatible con Android Gradle Plugin
- Android SDK 34 instalado
- Dispositivo Android o emulador (API 24+)

## Compilacion y ejecucion

### Opcion A: Android Studio (recomendada)

1. Abrir el proyecto en Android Studio.
2. Esperar a que termine la sincronizacion de Gradle.
3. Compilar con Build > Make Project.
4. Ejecutar con Run en un emulador o dispositivo fisico.

### Opcion B: linea de comandos

Nota: el repositorio no incluye gradlew/gradlew.bat. Si quieres compilar por CLI, usa una instalacion local de Gradle.

```bash
gradle :app:assembleDebug
```

APK de debug esperado:

- app/build/outputs/apk/debug/app-debug.apk

## Configuracion

### Idioma

Ruta: Settings > Language

- Spanish
- English

Comportamiento inicial:

- Si no hay preferencia guardada, se usa el idioma del dispositivo.
- Si el dispositivo no esta en espanol, el fallback es ingles.

### Tema y escala

Ruta: Settings

- Tema: Dark / Light
- Escala UI: Small, Normal, Large, Extra large

## Comportamiento de la aplicacion

### Apps predeterminadas

- Al abrir un archivo, la app intenta guardar el handler externo resuelto.
- Actividades de chooser/resolver del sistema se ignoran.
- Si no hay default explicito pero existe un unico handler valido, se guarda como app efectiva.
- La lista en Settings > Default apps se ordena por deteccion mas reciente.
- Las reglas de app predeterminada se guardan por extension (por ejemplo: .txt -> paquete de app).
- El flujo de Anadir extension y app ahora prioriza extensiones comunes pendientes y ofrece opcion de extension personalizada.
- Los dialogos de seleccion de app incluyen buscador e iconos de aplicaciones.
- El mismo selector con buscador e iconos tambien se usa desde Archivos/Recientes para definir la app predeterminada al momento.
- En menus de seleccion/fichero, la accion se muestra como Abrir; filtra apps de usuario por tipo de fichero, guarda la app elegida como predeterminada para esa extension y abre el fichero al instante con esa app.
- En Android 11+, la visibilidad de apps se apoya en queries del manifiesto (launcher y VIEW).

Acciones disponibles por entrada:

- Cambiar app
- Borrar entrada
- Limpiar todas (con confirmacion)

### Papelera

- Los elementos eliminados se mueven a una papelera interna de la app.
- Cada entrada guarda metadatos: ruta original, nombre original y fecha de borrado.
- Restaurar intenta devolver al origen y, si existe conflicto, genera un nombre unico.
- Vaciar papelera elimina definitivamente todos los elementos.
- Existe politica de limpieza por retencion de elementos antiguos.
- Si el move directo falla, se aplica fallback de copy/delete cuando es posible.

### Importacion desde compartir

- FileManager aparece como destino en la hoja de compartir de Android para envio simple y multiple.
- Los elementos compartidos quedan en cola dentro de la app y se muestran en la barra superior como Guardar aqui.
- Puedes navegar a cualquier carpeta y guardar ahi todos los archivos compartidos pendientes.

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
      values/       # English (base)
      values-es/    # Spanish
```

## Versiones y changelog

Tags publicados:

- v1.0.0: baseline inicial.
- v1.1.0: cambio de branding y paquete a com.fraugz.filemanager.
- v1.2.0: localizacion bilingue (EN/ES) y selector de idioma.
- v1.2.1: ajustes de estabilidad y mejoras menores.
- v1.2.2: correcciones sobre flujos de gestion de archivos.
- v1.2.3: mejoras incrementales de UX y robustez.
- v1.2.4: ajustes recientes de papelera/errores y mantenimiento.
- v1.2.5: refinamientos en UX de recientes/seleccion, accion directa de app predeterminada desde Archivos/Recientes, flujo de apps por extension con selector buscable, previsualizaciones enriquecidas (audio/video/APK + fallback por icono de app), aviso de seguridad para instalar APK, nuevo icono de idioma y correcciones de visibilidad de apps en Android 11+.
- v1.2.6: mejora de robustez en reproduccion multiple (playlist M3U temporal con compatibilidad especifica para VLC/AIMP/Total Commander), select-all inline y limpieza del estado visual de seleccion, pegado de un solo uso con etiqueta dinamica Mover/Pegar, mejoras de barra de progreso en mover/borrar/papelera, cancelacion real en operaciones largas de papelera, opcion de eliminar definitivamente con doble confirmacion e icono de advertencia, progreso al borrar en papelera y optimizacion del refresco tras renombrar para reducir bloqueos en carpetas grandes.
- v1.2.7: ajustes finos de UI/UX: el boton de ajustes superior abre Settings directamente (sin menus intermedios), los textos de la barra inferior de seleccion se fuerzan en una sola linea para evitar saltos en pantallas pequenas, la accion App por defecto pasa a mostrarse como Abrir y el selector de apps filtra por tipo de fichero manteniendo solo apps de usuario, al elegir app se guarda como predeterminada y ademas se abre el fichero al instante, y se refinan iconos/alineacion de seleccionar todo (cuadrado vacio/cuadrado marcado), igualando color con nueva carpeta y ajuste de posicion en pixeles.
- v1.2.8: nombres temporales mas descriptivos para playlists M3U de reproduccion multiple, FileManager agregado como destino en la hoja de compartir con flujo Guardar aqui, y eliminacion automatica en Recientes de la ruta anterior al mover ficheros.
- v1.2.9: nombre de app localizado por idioma del sistema (File Manager / Gestor Archivos), selector de app en Abrir ampliado a una segunda ventana con boton adicional Aplicaciones del sistema, accion Info en la barra de seleccion (un solo elemento, incluyendo carpetas), ocultar Renombrar en multiseleccion, y nuevos enlaces en Ajustes a guia rapida (EN/ES) y GitHub del proyecto.

- v1.3.0: Recientes se rediseño para igualar la UX de lista/seleccion de Almacenamiento y luego se ajusto a acciones Localizar/Fijar/Info (seleccion unica) y Fijar/Quitar de recientes en multiseleccion (Info oculto con 2+ elementos), con soporte directo para quitar de recientes uno o varios elementos seleccionados, con Localizar abriendo Almacenamiento y seleccionando el fichero objetivo; incluye chincheta visible en fijados, orden por fecha de acceso con separadores por dia, limpieza de recientes conservando fijados, y proteccion para que el autoescaneo no repueble archivos antiguos tras limpiar; la confirmacion de borrado se reordeno (Cancelar, Eliminar definitivamente, Mover a papelera) resaltando la accion destructiva; Mas aplicaciones usa la misma base de filtrado que anadir extension/app; se compactaron controles del encabezado junto al titulo de Almacenamiento con acceso rapido de Casa; el breadcrumb ocupa todo el ancho util con margenes laterales y salta de linea solo cuando hace falta; Enviar desde Almacenamiento ahora tambien admite seleccionar carpetas y abre apps compatibles desde el selector; ademas el flujo de guardar texto compartido pide nombre/tipo personalizado antes de crear el fichero, el swipe horizontal entre Recientes y Almacenamiento sigue el dedo de forma fluida, y la etiqueta del selector Abrir se simplifico a MAS APPs.

## Testing recomendado

Checklist minimo antes de publicar:

- Navegacion de carpetas y breadcrumb.
- Busqueda (inicio, cancelacion y resultados).
- Cambio entre las pestanas Recientes y Archivos con gesto horizontal (swipe).
- Arrastre hacia abajo en vista Archivos para refrescar el directorio actual.
- Renombrado con y sin cambio de extension.
- Vista de recientes y orden por acceso.
- Pulsacion prolongada en Recientes (aprox. medio segundo): App por defecto y Quitar de recientes.
- Accion Limpiar recientes y dialogo de confirmacion.
- Barra de acciones de seleccion:
  - aparece al seleccionar ficheros/carpetas
  - App por defecto solo aparece para seleccion de un unico fichero
- Comprobar previsualizaciones enriquecidas:
  - imagen/video/audio/APK cuando exista contenido de preview
  - fallback al icono de la app configurada si no hay preview
- Comprobar que nombres extremadamente largos se parten en varias lineas.
- Apertura de archivos y registro de apps predeterminadas.
- Papelera: mover, restaurar y vaciar.
- Cambio de idioma y persistencia tras reinicio.
- Cambio de tema y escala UI.

## Problemas comunes

- Permisos en Android 11+: validar acceso completo a archivos en ajustes del sistema.
- Algunos tipos de archivo no abren: depende de apps externas instaladas.
- Avisos LF/CRLF en Git: en Windows suelen ser inocuos si no rompen build.

## Roadmap

- Migrar toda la capa de listado/operaciones a Storage Access Framework para mejorar compatibilidad Android 11+.
- Anadir tests UI instrumentados para flujos criticos (papelera, renombrado, multiseleccion).
- Incorporar modo de vista dual (lista/cuadricula) con orden y filtros persistentes.
- Mejorar accesibilidad: content descriptions completas, contraste y navegacion por teclado/lector.
- Anadir previsualizacion enriquecida para PDF/video/texto sin salir de la app.
- Implementar favoritos y colecciones inteligentes (Recientes, Descargas, Imagenes, Documentos).
- Generar release notes automaticas por tag desde el historial de cambios.

## Contribuir

- Abre una issue con contexto y pasos de reproduccion.
- Crea una rama por feature o fix.
- Haz commits pequenos y enfocados.
- Incluye capturas para cambios visuales.
- Describe como validaste los cambios antes de abrir PR.

