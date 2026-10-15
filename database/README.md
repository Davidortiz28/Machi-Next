# MySQL local con Docker

El servicio usa MySQL 8.4 LTS. Desde la raíz del repositorio, crea la configuración local y arranca el contenedor:

En Windows, Docker Desktop debe estar iniciado y tener disponible un motor Linux (normalmente mediante WSL 2). Comprueba `wsl --status` y `docker info` si Compose informa que no puede conectar con Docker.

```powershell
Copy-Item .env.example .env
# Edita .env y reemplaza las dos contraseñas de ejemplo.
docker compose up -d db
docker compose ps
docker compose logs -f db
```

Al inicializar un volumen vacío, la imagen ejecuta `001_schema.sql` y después `002_seed_demo.sql` en orden. La base queda disponible en `127.0.0.1:${MYSQL_PORT}`, con base `machi_next` y usuario de aplicación `machi_app`. En esta máquina `.env` publica MySQL en el puerto `3307`, ya que Windows no permitió reservar `3306`; si cambias el valor de `MYSQL_PORT`, usa el mismo puerto al conectar desde aplicaciones ejecutadas en el host. Dentro de Docker, el servicio sigue escuchando en el puerto `3306`. Las credenciales locales están en `.env`, que Git ignora.

Los scripts se ejecutan automáticamente solo cuando MySQL inicializa un volumen vacío. Si el contenedor ya tiene un volumen con datos, no se vuelven a aplicar. `001_schema.sql` no elimina tablas ni datos; `002_seed_demo.sql` usa identificadores explícitos y está pensado para ejecutarse una vez sobre un esquema vacío. No repetir el seed sin revisar posibles conflictos.

El conjunto ficticio contiene 20 negocios, 41 usuarios inactivos (20 proveedores, 20 clientes y un administrador), 20 asignaciones, 20 modelos, 20 unidades, 20 tarifas, 22 reservas y líneas, 20 alquileres, 71 operaciones de pago y 20 facturas. Incluye cinco ejemplos opcionales de daños con evidencia de demostración (dos retenciones totales y tres parciales), dos cancelaciones con reembolsos de ejemplo (100% y 90%) y dos transferencias pendientes. Las claves de evidencia `demo-placeholder/...` son referencias ficticias; no existen archivos de imagen adjuntos.

Los usuarios usan `password_hash = 'NO_LOGIN_DEMO_SEED'` y estado `INACTIVE`: no pueden iniciar sesión. Al implementar autenticación, las cuentas deberán crearse mediante el flujo normal para almacenar un hash BCrypt real.

Los scripts se ejecutaron al crear el volumen Docker local. Los registros de MySQL confirmaron que ambos archivos terminaron sin errores. Si necesitas reiniciar la base desde cero, recuerda que los scripts de inicialización solo se ejecutan automáticamente cuando el volumen está vacío.
