# Fase 1 — Diseño inicial del dominio y la arquitectura

**Estado:** diseño inicial para revisión académica; sin código implementado.

## 1. Alcance y supuestos confirmados

- La plataforma admite varios negocios y varios proveedores.
- Un proveedor trabaja para un solo negocio a la vez. Puede ser transferido a otro negocio; el acceso al negocio anterior debe terminar al hacerse efectiva la transferencia.
- El sistema confirma automáticamente una reserva si el equipo está disponible para todo el período solicitado.
- La renta se calcula por día y contempla un depósito.
- Al registrar la devolución, el depósito se devuelve si no hay daños. Si se registran daños parciales o completos, puede retenerse la parte correspondiente.
- Los pagos y comprobantes son académicos/simulados; no existe integración con una pasarela financiera.
- Se usará Docker para el entorno.

## 2. Lenguaje ubicuo

| Término | Significado dentro del sistema |
| --- | --- |
| Negocio | Organización que publica equipos y tiene sus propios usuarios proveedores y datos operativos. |
| Proveedor | Usuario autorizado para administrar equipos y operaciones de un único negocio vigente. |
| Cliente | Usuario que consulta el catálogo y solicita reservas. |
| Equipo | Unidad física individual que se puede reservar; no es solo el tipo/modelo de herramienta. |
| Reserva | Compromiso de disponibilidad para un equipo y período, previo a la entrega. |
| Alquiler | Ciclo operativo del equipo desde la entrega al cliente hasta el registro de devolución. |
| Depósito | Importe asociado al alquiler que se devuelve al cierre, sujeto a daños registrados. |
| Daño | Hallazgo de devolución que documenta condición, evidencia y monto retenido, si se determina. |

## 3. Límites de contexto y módulos

Se propone un monolito modular con una base de datos relacional compartida. Cada módulo es dueño de sus reglas y expone operaciones de aplicación; no se propone microservicios para esta primera versión.

1. **Identidad y acceso**: credenciales, roles, autenticación y permisos.
2. **Negocios y proveedores**: negocios, asignación vigente de proveedores y transferencias.
3. **Catálogo e inventario**: tipos de herramienta, unidades físicas, tarifas, condiciones y estado operativo.
4. **Reservas y alquileres**: disponibilidad, reservas, entrega, devolución e informes de daño.
5. **Pagos y comprobantes**: registros simulados, depósitos, devoluciones/retenciones y comprobantes académicos.
6. **Reportes**: consultas autorizadas sobre actividad, ingresos, disponibilidad y uso.

Identidad responde quién es el usuario; Negocios/Proveedores responde a qué negocio pertenece actualmente y qué recursos puede administrar. Los reportes consultan datos de los módulos dueños, sin convertirse en otra fuente de verdad.

## 4. Agregados y consistencia propuesta

Los agregados son límites de consistencia transaccional: las invariantes de un agregado se validan al modificar su raíz.

- **Negocio**: raíz `Negocio`; administra estado y datos de la organización. Las asignaciones de proveedor se registran con vigencia para preservar historial de transferencias.
- **Equipo**: raíz `Equipo`; contiene identidad de unidad, negocio propietario, estado y tarifa vigente. Las unidades físicas se identifican individualmente para evitar doble reserva.
- **Reserva**: raíz `Reserva`; contiene cliente, equipo, período, importe calculado y estado. El importe guarda la tarifa aplicada al confirmar para que cambios posteriores de precio no alteren una reserva ya aceptada.
- **Alquiler**: raíz `Alquiler`; referencia la reserva y registra entrega, devolución, condición y daños. En esta primera propuesta no se duplica el ciclo de vida de la reserva: al entregar una reserva aceptada comienza el alquiler.
- **Pago/depósito**: raíz `Pago`; registra operaciones y estado, con identificador de idempotencia para evitar duplicar una misma operación simulada. La devolución o retención del depósito queda registrada como operación asociada, no como borrado/edición del pago original.

El monto retenido por daños no se calcula automáticamente todavía: hace falta definir quién lo determina, qué evidencia se exige y si requiere aceptación del cliente. El diseño debe conservar el daño y el ajuste monetario como registros auditables.

## 5. Estados y transiciones iniciales

### Reserva

`CONFIRMADA` al crearla si la disponibilidad se mantiene; `CANCELADA` si se cancela antes de la entrega; `EN_ALQUILER` al registrar la entrega; `FINALIZADA` al registrar la devolución. Una solicitud que no pasa validación no se confirma y devuelve un conflicto de disponibilidad.

### Equipo

`DISPONIBLE`, `RESERVADO/NO_DISPONIBLE` según período, `EN_ALQUILER`, `EN_MANTENIMIENTO` y `RETIRADO`. La disponibilidad para fechas se deriva de reservas/alquileres solapados y del estado operativo; no se debe confiar en un único booleano editable.

### Depósito

`PENDIENTE`, `COBRADO`, `DEVUELTO` o `RETENIDO_PARCIALMENTE`/`RETENIDO_TOTALMENTE`. Los estados finales requieren una devolución registrada. Las transiciones y los movimientos deben ser idempotentes.

## 6. Reglas e invariantes de negocio

1. Un proveedor tiene como máximo una asignación vigente a un negocio.
2. Una transferencia cierra la asignación anterior y abre la nueva en una operación consistente; las acciones posteriores usan la pertenencia vigente. Los registros históricos conservan el negocio al que correspondían cuando ocurrieron.
3. Solo se reserva un equipo físico si está operativo y no existe una reserva confirmada/alquiler que se cruce con el intervalo solicitado.
4. La comprobación y confirmación deben ser atómicas para que solicitudes concurrentes no confirmen períodos incompatibles. La estrategia concreta de bloqueo/aislamiento se seleccionará al diseñar persistencia.
5. El importe de renta se calcula según días facturables definidos y tarifa acordada; reserva confirmada conserva una instantánea de esa tarifa.
6. La devolución registra fecha y condición del equipo. Sin daño reportado se devuelve el depósito completo; con daño se registra la decisión y el monto devuelto/ retenido.
7. Un proveedor solo accede a recursos del negocio al que está asignado actualmente, con reglas explícitas para transferencias e historial.
8. Un identificador de idempotencia evita registrar dos veces el mismo pago o movimiento de depósito.

## 7. Casos de uso principales

- Registrar cliente e iniciar sesión.
- Crear negocio y asignar proveedor.
- Transferir proveedor a otro negocio, con cierre de asignación previa.
- Publicar/editar/retirar equipo y definir tarifa.
- Consultar catálogo y disponibilidad para un intervalo.
- Reservar equipo: validar fechas, estado, permisos, precio y depósito; confirmar en una operación atómica.
- Registrar pago simulado y asociarlo a reserva.
- Registrar entrega e iniciar alquiler.
- Registrar devolución, inspección, daños y devolución/retención del depósito.
- Consultar historial y reportes con filtro por negocio y rol.

## 8. Puertos y adaptadores

### Puertos de entrada (casos de uso)

`CrearReserva`, `ConsultarDisponibilidad`, `RegistrarEntrega`, `RegistrarDevolucion`, `TransferirProveedor`, `GestionarEquipo`, `RegistrarPagoSimulado` y `ConsultarReportes`.

Los controladores REST traducirán HTTP/JSON a estos casos de uso; no contendrán reglas de negocio.

### Puertos de salida

- Repositorios de dominio: equipos, reservas, negocios/asignaciones, alquileres y pagos.
- Puerto de transacción/unidad de trabajo para confirmar reserva y registrar movimientos relacionados.
- Reloj inyectable para reglas de fecha y pruebas deterministas.
- Puerto de generación de comprobantes (si el formato se implementa) y almacenamiento de evidencia de daños, si se acuerda guardar archivos.
- Puerto de pago con adaptador simulado; una pasarela real podría implementarse luego sin cambiar las reglas del dominio.

### Adaptadores

- Entrada: API REST Spring Boot, validación de DTO y documentación OpenAPI.
- Persistencia: adaptadores Spring Data JPA/MySQL que convierten entre filas JPA y modelo de dominio.
- Seguridad: adaptador Spring Security/JWT que establece identidad y roles; los casos de uso verifican además el acceso al negocio/recurso.
- Pago: simulador con registros idempotentes.
- Presentación: frontend HTML/CSS/JavaScript que consume la API.

El dominio no importará clases de Spring, JPA, HTTP ni SDK de pagos.

## 9. Estructura conceptual

```text
identidad/
negocios/
inventario/
reservas/
pagos/
reportes/
  Cada módulo contiene, según necesidad:
  domain/       reglas, entidades, objetos de valor y puertos
  application/  casos de uso y coordinación
  adapter/in/   REST y entrada web
  adapter/out/  persistencia e integraciones
```

La estructura final de paquetes se decidirá al preparar el proyecto. La organización no será suficiente por sí sola: las dependencias deben apuntar hacia dominio y aplicación, y los adaptadores implementan los puertos.

## 10. Decisiones pendientes antes de diseñar la base de datos

- ¿El proveedor recibe acceso al nuevo negocio de inmediato al transferirse, o la transferencia requiere aceptación del nuevo negocio?
- ¿Cómo se calcula el día facturable: días calendario, bloques de 24 horas o regla de hora de corte? ¿Se permiten períodos parciales?
- ¿Cuándo se cobra el depósito y qué ocurre si el cliente cancela?
- ¿Quién determina y acepta el monto por daño? ¿Se necesita evidencia fotográfica?
- ¿Qué se considera “daño parcial” y “completo”, y puede la retención superar el depósito?
- ¿Qué políticas de cancelación y anticipación se requieren?

## 11. Progreso

- **Fase actual:** Fase 1, diseño del dominio y arquitectura.
- **Completado hoy:** conceptos, módulos, agregados iniciales, estados, invariantes, casos de uso y puertos/adaptadores propuestos.
- **Pendiente:** resolver políticas de negocio, validar agregados y definir el modelo relacional/normalización en Fase 2.
- **Código y pruebas:** aún no iniciados.
