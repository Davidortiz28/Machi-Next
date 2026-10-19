# Backend API

El backend usa Java 21 como versión objetivo, Spring Boot 3.5 y Maven. Spring Boot administra versiones compatibles de Spring MVC, Spring Security, Spring Data JPA, Hibernate y el controlador MySQL.

## Requisitos locales

- JDK 21 o superior compatible con Spring Boot 3.5.
- Maven 3.6.3 o superior.
- MySQL levantado con Docker Compose desde la raíz del repositorio.

El entorno actual tiene JDK 25 y Maven 3.9.16. Maven compila el código para Java 21 mediante `java.version`; Spring Boot 3.5 es compatible con JDK 25.

## Conexión a MySQL

La aplicación lee `DB_HOST`, `DB_PORT`, `MYSQL_DATABASE`, `MYSQL_USER` y `MYSQL_PASSWORD` del entorno. Por defecto usa `localhost:3307`, porque en el equipo el puerto 3306 no está disponible. Si se cambia el puerto publicado en el `.env` raíz, configura `DB_PORT` con el mismo valor antes de iniciar el backend. JWT usa `JWT_SECRET` (mínimo 32 bytes) y `JWT_TTL_MINUTES`; el valor predeterminado de `application.yml` es solo para desarrollo local.

`ddl-auto: validate` hace que Hibernate compruebe el mapeo contra el esquema existente. El esquema se administra con los scripts SQL de `database/`; Hibernate no debe crearlo ni modificarlo.

## Comprobar la API y la base de datos

Con MySQL activo, carga las variables del `.env` raíz en PowerShell y ejecuta `mvn spring-boot:run`. Luego abre `http://localhost:8080/actuator/health` o consulta la URL con `Invoke-RestMethod`. Una respuesta `{"status":"UP"}` confirma que la aplicación está activa y que el indicador de salud de MySQL puede conectarse. Si el puerto 8080 está ocupado, inicia la aplicación con `$env:SERVER_PORT = "8081"` y usa esa dirección.

El endpoint de salud es una herramienta operativa de Spring Actuator, no una función de negocio. Está permitido sin autenticación para facilitar la comprobación local.

## Cuentas y autenticación

- `POST /api/v1/auth/register` crea una cuenta activa con rol `CLIENTE` y devuelve un JWT.
- `POST /api/v1/auth/login` valida correo y contraseña y devuelve un JWT.
- Las contraseñas se guardan con BCrypt. Los tokens son Bearer, firmados con HS256 y duran 60 minutos por defecto.
- Catálogo y salud son públicos. Las demás rutas requieren `Authorization: Bearer <accessToken>`.
- El registro normal nunca permite elegir rol. Las cuentas de demostración inactivas no pueden iniciar sesión.

Ejemplo de registro:

```json
{"email":"cliente@example.com","password":"una-clave-segura","displayName":"Cliente de prueba"}
```

## Interfaz web de demostración

Con el backend y MySQL activos, abre `http://localhost:8080/`. La página consume la API en el mismo origen y permite consultar disponibilidad por fechas, crear una cuenta cliente, iniciar sesión, revisar el precio y depósito por separado, confirmar una reserva y ver las reservas de la sesión. El JWT se conserva en `sessionStorage` y se elimina al cerrar la pestaña o salir.

Las cuentas `PROVEEDOR` pueden abrir `/provider.html` después de iniciar sesión en el catálogo. El panel muestra los equipos del negocio que tienen asignado y permite registrar equipos con tarifa y depósito, además de cambiar su estado entre `AVAILABLE`, `MAINTENANCE` y `RETIRED`. Un proveedor sin asignación vigente no puede consultar ni modificar inventario.

La API de inventario requiere un JWT con rol `PROVEEDOR` y obtiene el negocio exclusivamente de la asignación vigente del usuario:

- `GET /api/v1/provider/equipment` lista los equipos y su última tarifa registrada.
- `POST /api/v1/provider/equipment` registra `categoryName`, `manufacturer`, `modelName`, `serialNumber`, `dailyAmount`, `depositAmount` y `validFrom`; acepta también `description`, `currency` (por defecto `COP`) y `validTo` opcional.
- `PATCH /api/v1/provider/equipment/{equipmentId}/status` actualiza el estado con `{"status":"MAINTENANCE"}` (también acepta `AVAILABLE` o `RETIRED`).

El número de serie debe ser único dentro del negocio. La misma categoría y modelo se reutilizan al registrar unidades adicionales. Las tarifas y el inventario se guardan en las tablas existentes, y las nuevas unidades comienzan como `AVAILABLE`.

### Configurar el primer administrador

El primer administrador se crea una sola vez con `POST /api/v1/auth/bootstrap-admin`. El endpoint exige `ADMIN_BOOTSTRAP_TOKEN`, un secreto aleatorio de al menos 32 caracteres. Si ya hay un administrador activo, devuelve conflicto; si el secreto no está configurado o no coincide, rechaza la solicitud. El script de datos demo crea un administrador inactivo, por lo que no bloquea esta inicialización.

Guarda un secreto propio en el `.env` local (no lo publiques) y cárgalo en la misma ventana de PowerShell donde ejecutarás la API:

```powershell
$env:ADMIN_BOOTSTRAP_TOKEN = "pega-aqui-un-secreto-aleatorio-de-32-caracteres-o-mas"
mvn spring-boot:run
```

En otra ventana, usa ese mismo secreto para inicializar la cuenta. La respuesta contiene un JWT de administrador; el secreto no se almacena en la base de datos ni se devuelve:

```powershell
$body = @{
  email = "admin@ejemplo.com"
  password = "una-clave-segura-de-12-o-mas"
  displayName = "Administrador"
  bootstrapToken = "pega-aqui-un-secreto-aleatorio-de-32-caracteres-o-mas"
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/auth/bootstrap-admin -ContentType "application/json" -Body $body
```

### Negocios y proveedores

Con el JWT retornado, consulta `GET /api/v1/admin/businesses` y crea un proveedor asignado a un negocio activo con `POST /api/v1/admin/providers`:

```powershell
$token = "JWT-devuelto-por-bootstrap"
Invoke-RestMethod -Uri http://localhost:8080/api/v1/admin/businesses -Headers @{ Authorization = "Bearer $token" }
$provider = @{
  email = "proveedor@ejemplo.com"
  password = "una-clave-segura-de-12-o-mas"
  displayName = "Proveedor de prueba"
  businessId = 1
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/admin/providers -Headers @{ Authorization = "Bearer $token" } -ContentType "application/json" -Body $provider
```

La cuenta queda activa con el rol `PROVEEDOR`. Para iniciar una solicitud usa `POST /api/v1/admin/provider-transfers` con `providerUserId` y `toBusinessId`. El sistema determina el negocio actual a partir de la asignación vigente. Revisa las solicitudes con `GET /api/v1/admin/provider-transfers`.

Un administrador global crea las cuentas que representan a los negocios con `POST /api/v1/admin/business-managers`, enviando `email`, `password`, `displayName` y `businessId`. Puede retirar el permiso con `DELETE /api/v1/admin/business-managers/{userId}/businesses/{businessId}`. Estos usuarios inician sesión en `/api/v1/auth/login`; sus credenciales solo autorizan decisiones para los negocios que tienen asignados.

El administrador crea un representante por negocio (repite la solicitud para el negocio de destino):

```powershell
$manager = @{ email = "encargado-origen@example.com"; password = "clave-segura-de-12-o-mas"; displayName = "Encargado origen"; businessId = 1 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/admin/business-managers -Headers @{ Authorization = "Bearer $token" } -ContentType "application/json" -Body $manager
```

Los representantes de negocio consultan solo sus transferencias con `GET /api/v1/business/provider-transfers` y responden en `POST /api/v1/business/provider-transfers/{transferId}/source-decision` o `/destination-decision`, usando `{"decision":"APPROVED"}` o `{"decision":"REJECTED"}`. El servidor verifica que el usuario esté asignado al negocio correspondiente; un representante no puede aprobar por el otro lado a menos que tenga permisos explícitos para ese negocio también. Un administrador global puede iniciar y consultar solicitudes, pero no sustituir las decisiones de los representantes.

Cuando ambos aprueban, la solicitud pasa a `WAITING` y empieza un plazo fijo de 3 días. `effectiveAt` muestra cuándo podrá hacerse efectiva. Durante la espera el proveedor conserva su asignación actual. El backend revisa transferencias vencidas cada minuto y cierra la asignación anterior y crea la nueva en una transacción. Si el backend estuvo apagado, las procesa al volver a iniciar. El endpoint de administrador `POST /api/v1/admin/provider-transfers/{transferId}/complete` permite ejecutar o reintentar la misma operación después del plazo; antes de la fecha responde HTTP 409. `completionMethod` y `completedByUserId` distinguen la ejecución automática de la manual. Un rechazo resuelve la solicitud como `REJECTED`. La decisión se guarda una sola vez por lado.

## Primer caso de uso: consultar el catálogo

`GET /api/v1/catalog/equipment` devuelve equipos cuyo estado operativo es `AVAILABLE`, cuyo negocio está activo y que tienen una tarifa vigente hoy. La tarifa incluye el precio diario, el depósito y la moneda.

`GET /api/v1/catalog/equipment/availability?startDate=YYYY-MM-DD&returnDate=YYYY-MM-DD` filtra el catálogo para un período. Las dos fechas son inclusivas: una reserva existente se cruza con la búsqueda cuando `existing.start_date <= returnDate` y `existing.return_date >= startDate`. Se ignoran reservas canceladas/completadas; una reserva confirmada o un alquiler que se cruza bloquea el equipo. Un alquiler sin devolución registrada sigue bloqueándolo aunque haya pasado su fecha prevista de retorno. La tarifa debe cubrir todo el período solicitado. La respuesta incluye una cotización que multiplica la tarifa diaria por los días calendario y mantiene el depósito separado. Si la fecha de devolución es anterior al inicio, la API responde HTTP 400.

Ambas rutas están separadas en un adaptador web, casos de uso, un puerto de salida y un adaptador de persistencia. La consulta usa Spring Data JPA con SQL nativo porque el catálogo combina cuatro tablas; la proyección evita cargar entidades completas que la respuesta no necesita. `RentalPeriod` aplica la regla de contar inicio y devolución, por lo que una renta del mismo día equivale a un día calendario.

## Reservas

Las rutas siguientes requieren un JWT de una cuenta con rol `CLIENTE`:

- `POST /api/v1/reservations` crea una reserva con `equipmentId`, `startDate` y `returnDate` (`YYYY-MM-DD`). El servidor vuelve a verificar la tarifa y disponibilidad y conserva el precio diario, el depósito y el total como valores históricos de la reserva. El inicio y el día de devolución cuentan dentro del período.
- `GET /api/v1/reservations` lista las reservas del cliente autenticado.
- `POST /api/v1/reservations/{reservationId}/cancel` cancela una reserva confirmada propia. Acepta un cuerpo opcional `{"reason":"..."}`. Devuelve 100% si faltan 24 horas o más para el inicio y 90% si falta menos; calcula el porcentaje sobre renta más depósito y lo registra en la reserva.

Ejemplo de solicitud:

```json
{"equipmentId":1,"startDate":"2026-10-20","returnDate":"2026-10-22"}
```

En este corte `CONFIRMED` representa una reserva de demostración. Los cargos y reembolsos se guardan como operaciones simuladas en `payment_operation`; no se mueve dinero ni se integra una pasarela. La verificación y creación se ejecutan en una transacción con bloqueo del equipo para evitar que dos solicitudes simultáneas lo reserven en fechas que se cruzan.

## Entrega y devolución

Estas rutas requieren un JWT con rol `PROVEEDOR` y el proveedor debe tener una asignación vigente al negocio de la reserva:

- `POST /api/v1/rentals/{reservationId}/handover` registra quién entrega el equipo y cambia la reserva a `IN_RENTAL`.
- `POST /api/v1/rentals/{reservationId}/return` recibe `{"condition":"GOOD"}`, `{"condition":"PARTIAL_DAMAGE"}` o `{"condition":"TOTAL_DAMAGE"}`. Registra quién recibe el equipo, cambia la reserva a `COMPLETED` y crea un reporte de daño cuando corresponde.

Una devolución `GOOD` registra un reembolso simulado por el total del depósito. Si hay daño, el proveedor que hizo la entrega debe adjuntar evidencia con `POST /api/v1/rentals/{reservationId}/damage-evidence` usando `multipart/form-data` con el campo `file`. Se aceptan JPG, PNG, MP4 y PDF de hasta 10 MB. Los archivos quedan en `./uploads/evidence` (configurable con `EVIDENCE_STORAGE_DIRECTORY`) y ese directorio está excluido de Git.

Un administrador revisa los casos en `GET /api/v1/admin/damage-reports`, consulta evidencias en `GET /api/v1/admin/damage-reports/{reportId}/evidence` y las descarga o visualiza en `GET /api/v1/admin/damage-reports/{reportId}/evidence/{evidenceId}/content`. Para resolver un reporte usa `POST /api/v1/admin/damage-reports/{reportId}/review` con `{"decision":"APPROVE","assessedAmount":40000,"retainedAmount":30000,"decisionNote":"Daño parcial verificado"}`. La retención no puede exceder el daño evaluado ni el depósito; el saldo se registra como reembolso simulado. Con `REJECT`, el depósito completo se registra para devolución. Se requiere al menos una evidencia antes de la revisión.

## Arquitectura

El paquete base solo contiene el punto de entrada de Spring Boot. Los módulos de negocio se añadirán por contexto (`identity`, `businesses`, `catalog`, `rentals`, `payments` y `reports`), separando dominio y aplicación de los adaptadores web y de persistencia. Así las reglas de negocio no dependerán de Spring MVC ni de JPA.
