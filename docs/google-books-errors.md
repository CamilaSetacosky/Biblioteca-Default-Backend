# Google Books: fallos del proveedor (GDS2026-111)

La vista previa sigue usando `GET /api/v1/books/external/isbn/{isbn}` para
lectores y bibliotecarios. No guarda libros, autores ni categorías. El alta
revisada continúa usando `POST /api/v1/books`.

## Contrato de errores

Las respuestas usan `application/problem+json` con `status`, `title`, `detail`,
`instance`, `code`, `errorId` y `timestamp`. `errorId` permite relacionar el mensaje
con un registro seguro del backend. No se incluyen claves, URLs externas,
cuerpos del proveedor ni excepciones de transporte como causas.

| Condición | HTTP | code |
|---|---|---|
| Google 429 o 403 con motivo de cuota reconocido | 503 | GOOGLE_BOOKS_QUOTA_EXCEEDED |
| Google 401/403 de acceso, o 400 con clave inválida reconocida | 503 | GOOGLE_BOOKS_CONFIGURATION_ERROR |
| Google 5xx o error de conexión | 503 | GOOGLE_BOOKS_UNAVAILABLE |
| Tiempo máximo excedido | 503 | GOOGLE_BOOKS_TIMEOUT |
| Respuesta vacía, ilegible o incompatible | 502 | GOOGLE_BOOKS_INVALID_RESPONSE |
| Otro rechazo HTTP del proveedor | 502 | GOOGLE_BOOKS_REQUEST_REJECTED |

Un rechazo de la clave del servidor no se traduce a un 401 del lector.
Google 404 o una respuesta válida sin resultados mantienen el 404 local.
Los permisos y errores de autenticación propios conservan 401/403.

Se transmite `Retry-After` en errores 503 únicamente si Google aporta segundos
no negativos válidos o una fecha HTTP válida. No se inventa una espera.
No hay reintentos HTTP automáticos, incluidos los reintentos de transporte de
Reactor Netty. Cada suscripción hace como máximo un intento.

El límite de tiempo por consulta es 10 segundos y se configura mediante
`GOOGLE_BOOKS_TIMEOUT` (por ejemplo `10s`). Nunca colocar la clave en este documento,
en Angular o en Git. La clave local continúa en `.env`, excluido de Git.

El adaptador es compartido por vista previa, importación y sincronización;
sus errores están documentados en Swagger para los tres recorridos.

## Diagnóstico real previo al cambio

La comprobación con el ISBN `9780134494166` devolvió HTTP 503 de Google,
motivo `backendFailed` y mensaje `Service temporarily unavailable.` sin
`Retry-After`. Los fallos anteriores incluían HTTP 429. Estos dos diagnósticos
no deben confundirse: cambiar la respuesta local no recupera el servicio externo.
No hay evidencia en esa respuesta 503 para pedir otra clave, pagar o aumentar cuota.

## Verificación y alcance

Las pruebas del adaptador usan exclusivamente MockWebServer en localhost. Las
pruebas HTTP usan un proveedor simulado y H2 en memoria aislada, con comprobación
de que no cambia la cantidad de libros, autores ni categorías ante un fallo.
No se conectan a PostgreSQL ni a Google. La prueba real por Swagger y luego
Angular continúa pendiente en GDS2026-112 y requiere acordar cada intento.
