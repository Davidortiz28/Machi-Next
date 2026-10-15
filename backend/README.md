# Backend API

El backend usa Java 21 como versión objetivo, Spring Boot 3.5 y Maven. Spring Boot administra versiones compatibles de Spring MVC, Spring Security, Spring Data JPA, Hibernate y el controlador MySQL.

## Requisitos locales

- JDK 21 o superior compatible con Spring Boot 3.5.
- Maven 3.6.3 o superior.
- MySQL levantado con Docker Compose desde la raíz del repositorio.

El entorno actual tiene JDK 25 y Maven 3.9.16. Maven compila el código para Java 21 mediante `java.version`; Spring Boot 3.5 es compatible con JDK 25.

## Conexión a MySQL

La aplicación lee `DB_HOST`, `DB_PORT`, `MYSQL_DATABASE`, `MYSQL_USER` y `MYSQL_PASSWORD` del entorno. Por defecto usa `localhost:3307`, porque en el equipo el puerto 3306 no está disponible. Si se cambia el puerto publicado en el `.env` raíz, configura `DB_PORT` con el mismo valor antes de iniciar el backend.

`ddl-auto: validate` hace que Hibernate compruebe el mapeo contra el esquema existente. El esquema se administra con los scripts SQL de `database/`; Hibernate no debe crearlo ni modificarlo.

## Arquitectura

El paquete base solo contiene el punto de entrada de Spring Boot. Los módulos de negocio se añadirán por contexto (`identity`, `businesses`, `catalog`, `rentals`, `payments` y `reports`), separando dominio y aplicación de los adaptadores web y de persistencia. Así las reglas de negocio no dependerán de Spring MVC ni de JPA.
