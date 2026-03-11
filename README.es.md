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
- Gestos de deslizamiento horizontal para cambiar entre las vistas Recientes y Archivos.
- Gesto de arrastrar hacia abajo en la vista Archivos para refrescar el directorio actual.
- Acciones rapidas en Recientes:
  - limpiar todos los recientes con confirmacion
  - pulsacion prolongada en un fichero reciente para Abrir con... o Quitar de recientes
- Renombrado con control de extension (nombre completo o solo nombre).
- Acciones en multiseleccion: copiar, mover, compartir y enviar a papelera.
- La barra de acciones de seleccion incluye Abrir con... cuando hay exactamente un fichero seleccionado.
- Papelera con restauracion y borrado permanente.
- Miniaturas de imagen asincronas con cache.
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
- v1.2.5: refinamientos en UX de recientes/seleccion, flujo de apps por extension con selector buscable, aviso de seguridad para instalar APK, nuevo icono de idioma y correcciones de visibilidad de apps en Android 11+.

Siguiente version sugerida:

- v1.3.0: consolidar mejoras de calidad y pruebas automatizadas.

## Testing recomendado

Checklist minimo antes de publicar:

- Navegacion de carpetas y breadcrumb.
- Busqueda (inicio, cancelacion y resultados).
- Cambio entre las pestanas Recientes y Archivos con gesto horizontal (swipe).
- Arrastre hacia abajo en vista Archivos para refrescar el directorio actual.
- Renombrado con y sin cambio de extension.
- Vista de recientes y orden por acceso.
- Pulsacion prolongada en Recientes (aprox. medio segundo): Abrir con... y Quitar de recientes.
- Accion Limpiar recientes y dialogo de confirmacion.
- Barra de acciones de seleccion:
  - aparece al seleccionar ficheros/carpetas
  - Abrir con... solo aparece para seleccion de un unico fichero
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
