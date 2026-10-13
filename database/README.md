# Esquema y datos de demostración

Scripts dirigidos a MySQL 8.4 LTS. Ejecutarlos en este orden desde la raíz del repositorio:

```powershell
Get-Content -Raw -Encoding UTF8 database/001_schema.sql | mysql --default-character-set=utf8mb4 -u root -p
Get-Content -Raw -Encoding UTF8 database/002_seed_demo.sql | mysql --default-character-set=utf8mb4 -u root -p
```

`001_schema.sql` crea `machi_next` y las tablas si no existen; no borra tablas ni datos. `002_seed_demo.sql` se ejecuta una vez sobre el esquema vacío y usa identificadores explícitos. Si el esquema ya contiene datos, no se debe ejecutar este seed sin revisar primero posibles choques de claves.

El conjunto ficticio contiene 20 negocios, 41 usuarios inactivos (20 proveedores, 20 clientes y un administrador), 20 asignaciones, 20 modelos, 20 unidades, 20 tarifas, 22 reservas y líneas, 20 alquileres, 71 operaciones de pago y 20 facturas. Incluye cinco ejemplos opcionales de daños con evidencia de demostración (dos retenciones totales y tres parciales), dos cancelaciones con reembolsos de ejemplo (100% y 90%) y dos transferencias pendientes. Las claves de evidencia `demo-placeholder/...` son referencias ficticias; no existen archivos de imagen adjuntos.

Los usuarios usan `password_hash = 'NO_LOGIN_DEMO_SEED'` y estado `INACTIVE`: no pueden iniciar sesión. Al implementar autenticación, las cuentas deberán crearse mediante el flujo normal para almacenar un hash BCrypt real.

El script SQL todavía no se ha ejecutado contra un servidor MySQL. Antes de usar los datos como evidencia de funcionamiento, hay que correr ambos comandos en el entorno Docker y revisar el resultado.
