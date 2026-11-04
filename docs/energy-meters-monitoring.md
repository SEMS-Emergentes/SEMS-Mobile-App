# Medidores, monitoreo y resumen

## Modelo y pantallas terminadas

- **Dispositivos** administra únicamente equipos del local. `meter` ya no se ofrece al crear un equipo; se conserva como valor heredado al editar datos antiguos.
- **Monitoreo** contiene los medidores IoT del local activo, filtros, lectura más reciente, historial por medidor, vinculación por QR o serial manual, cambio de zona y desvinculación autorizadas por el backend.
- Un medidor pertenece al local y su zona es opcional. Nunca se asigna directamente a un equipo.
- El QR acepta un serial plano o JSON con `serial`/`meter_serial` y `model` opcional. Se valida antes de llamar al servidor y se bloquean envíos repetidos.
- Monitoreo muestra el agregado oficial por zona —incluido **Sin zona**—, períodos de 7/14/30 días y valores por equipo solo si el backend los atribuye explícitamente.
- **Resumen** muestra el agregado oficial de 30 días, cantidad de medidores y costo solo cuando el backend lo devuelve. La demanda se marca como no disponible porque no se verificó un endpoint que la provea.

Los modelos `EnergyMeter`, `EnergyReading`, `SiteZoneConsumption` y `DeviceConsumption`, y `EnergyRepository`, quedan reutilizables por Alertas, Analítica y Reportes.

## Endpoints utilizados

Contratos contrastados con el archivo `C:\Users\grssc\Downloads\api-docs.json` entregado por el equipo (copia de OpenAPI). El acceso al servidor en ejecución puede variar:

- `GET /api/v1/energy-meters/site/{siteId}`
- `POST /api/v1/energy-meters`
- `PATCH /api/v1/energy-meters/{meterId}/zone`
- `DELETE /api/v1/energy-meters/{meterId}`
- `GET /api/v1/energy-readings/meter/{meterId}/latest`
- `GET /api/v1/energy-readings/range?userId={userId}&from={isoDateTime}&to={isoDateTime}`; la respuesta se filtra por `meter_id` para el historial del medidor del local activo.
- `GET /api/v1/energy/devices/{deviceId}/consumption/current`
- `GET /api/v1/energy/devices/{deviceId}/consumption/history`
- `GET /api/v1/sites/{siteId}/zone-consumption?days={days}`
- `GET /api/v1/device-consumptions/user/{userId}`

La app no suma medidores que podrían solaparse ni calcula una factura multiplicando kWh por una tarifa.

## Ajustes tras revisar OpenAPI (4 de noviembre de 2026)

- El historial de cada medidor ya usa lecturas reales del endpoint de rango, con períodos de 7, 14 y 30 días, estado vacío y reintento ante error.
- Al dejar un medidor **sin zona**, se envía `{"zone_id":null}` de forma explícita. La configuración global del serializador omite los nulos de objetos Kotlin, por lo que se añadió una prueba para evitar regresiones.
- Si la consulta de zonas falla, Monitoreo muestra el error; no presenta una zona desconocida como «Sin zona» ni permite reasignar hasta poder cargar las zonas.
- La vinculación queda limitada al local seleccionado para que el medidor aparezca al volver a Monitoreo. OpenAPI no define restricciones de rol para esta operación: la app deja que el backend autorice y muestra su rechazo si corresponde.
- Los campos numéricos esenciales de consumo y desglose ya no toman `0` cuando el servidor omite el dato: la respuesta inválida se trata como error, para no mostrar una cifra inventada.

## Cómo probar

1. Abre el repositorio en VS Code y arranca un emulador desde Android Studio Device Manager.
2. En la terminal PowerShell del proyecto ejecuta:

   ```powershell
   $env:ANDROID_HOME='C:\Users\grssc\AppData\Local\Android\Sdk'
   .\gradlew.bat testDebugUnitTest
   .\gradlew.bat installDebug
   ```

3. Inicia sesión con una cuenta existente, selecciona un local y entra a **Monitoreo**.
4. Verifica: local sin zonas, entrada manual, serial inválido, QR válido, permiso de cámara denegado, doble toque en Vincular, ausencia de lecturas, reintento tras error de red, filtros y detalle.

No se deben crear cuentas, medidores ni lecturas de prueba en el backend compartido.

Verificación de esta revisión: `testDebugUnitTest` y `assembleDebug` terminaron correctamente (181 pruebas, 0 fallos); `installDebug` instaló la APK en `emulator-5554` y la actividad principal arrancó sin un fallo inmediato. No se probó la vinculación real porque requiere un serial y permisos de una cuenta autorizada.

## Limitaciones verificadas

- La prueba real de alta/desvinculación requiere credenciales autorizadas y un serial real; no se mutó el backend durante esta entrega.
- La cámara requiere dispositivo físico o emulador configurado con cámara.
- El endpoint de historial devuelve lecturas por usuario, no un historial filtrado por medidor en servidor; la app verifica el medidor del local activo y filtra por `meter_id`. Falta comprobar con una cuenta autorizada el volumen y el formato de las respuestas reales.
- El historial del medidor muestra las lecturas en una lista con período; falta una gráfica para esas lecturas y validación visual con datos reales.
- El documento OpenAPI no declara las reglas de autorización por rol ni garantiza que una cuenta concreta pueda vincular, cambiar de zona o desvincular; esa verificación exige pruebas con perfiles autorizados.
- Alertas, Analítica, Reportes, Suscripción y Configuración permanecen como estados no implementados en la app. El OpenAPI entregado sí enumera operaciones para varios de estos módulos, pero aún falta mapear cada contrato, verificar los permisos y construir sus pantallas. No se simulan pagos, recomendaciones, PDF ni alertas.
- “Mis locales” conserva zonas y datos del local, pero aún no integra un indicador específico de cobertura por medidor.
- El backend alojado en Render puede iniciar lentamente o agotar el tiempo; esos fallos se muestran como error/reintento y no como una lista vacía.
