# VÍA COSTA — Sistema de transporte terrestre (Chimbote ↔ Huaraz)

Aplicación **de escritorio para Windows** con **Java 21 + JavaFX + PostgreSQL**. Proyecto Maven para IntelliJ IDEA. No necesita API RENIEC: los pasajeros se registran manualmente.

## Funciones incluidas

- Acceso de **administrador** y **empleado** (BCrypt para las contraseñas).
- Panel de estadísticas: pasajeros, buses, viajes programados y boletos vendidos.
- Alta y edición básica de pasajeros (DNI, nombres, apellidos, teléfono).
- Alta de buses y capacidad de asientos (administrador).
- Rutas precargadas **Chimbote → Huaraz** y **Huaraz → Chimbote**, con opción de agregar más (administrador).
- Programación de viajes con bus, ruta, fecha, hora y precio (administrador).
- Consulta de viajes, capacidad y asientos ocupados.
- Venta de pasajes con selección visual de asiento; protección ante doble venta mediante una transacción y un índice único parcial en PostgreSQL.
- Historial de ventas; anulación por administrador.
- Guardado del último comprobante como **TXT referencial** (no comprobante tributario).
- Gestión de usuarios (solo administrador).

> No se precargan horarios, placas ni tarifas reales de VÍA COSTA. Los registra el administrador. No es un sistema de facturación electrónica ni de emisión de comprobantes SUNAT.

## 1. Requisitos

- Windows 10/11 o sistema compatible con JavaFX.
- **JDK 21** configurado en IntelliJ IDEA.
- **PostgreSQL** ejecutándose localmente (habitualmente puerto 5432).
- **IntelliJ IDEA** con soporte Maven, o Maven instalado.
- Internet **solo la primera vez** para descargar las dependencias Maven.

## 2. Crear base en pgAdmin 4

1. Abre **pgAdmin 4 → Servers → PostgreSQL → Databases**.
2. Haz clic derecho en la base de datos **postgres** (NO en «viacosta») → **Query Tool**.
3. Escribe y ejecuta:

   ```sql
   CREATE DATABASE viacosta;
   ```

   Si ya existe, no vuelvas a crearla.
4. **No es necesario ejecutar SQL para las tablas**: al abrir por primera vez el programa, el archivo `src/main/resources/db/schema.sql` las crea automáticamente sin borrar datos existentes. Si prefieres hacerlo manualmente: abre un Query Tool de `viacosta` y ejecuta `sql/01_esquema.sql`.

**IMPORTANTE:** si PostgreSQL da `CREATE DATABASE cannot run inside a transaction block`, ejecuta el `CREATE DATABASE` por separado en un Query Tool nuevo, sin `BEGIN`.

## 3. Configurar conexión a PostgreSQL

1. Copia el archivo `config.properties.example` y renombra la copia como **`config.properties`** en la **raíz del proyecto**, al lado de `pom.xml`.
2. Abre `config.properties` y cambia `DB_PASSWORD` por la contraseña de tu usuario `postgres` en PostgreSQL (por ejemplo, la que ya usas en pgAdmin).
3. Revisa estos valores:

   ```properties
   DB_URL=jdbc:postgresql://localhost:5432/viacosta
   DB_USER=postgres
   DB_PASSWORD=TU_CONTRASENA_POSTGRESQL
   ```

4. **No publiques `config.properties` ni compartas esa clave**. El archivo está excluido en `.gitignore`.

También se aceptan variables de entorno `DB_URL`, `DB_USER`, `DB_PASSWORD`, que tienen prioridad sobre `config.properties`.

## 4. Abrir en IntelliJ IDEA

1. Descomprime el ZIP en una carpeta (por ejemplo, `D:\VIA_COSTA_CHIMBOTE_HUARAZ`).
2. En IntelliJ IDEA: **File → Open**, selecciona la carpeta que contiene el archivo `pom.xml`.
3. Selecciona **JDK 21** para el proyecto y permite importar dependencias Maven.
4. Crea/edita `config.properties` como se indicó antes.
5. En el panel derecho **Maven → Plugins → javafx → javafx:run**, ejecuta con doble clic.

Desde la terminal situada en la carpeta del proyecto también puedes ejecutar:

```bash
mvn clean javafx:run
```

Si el IDE no detecta Maven, haz clic derecho sobre `pom.xml` → **Add as Maven Project** / **Load Maven Changes**.

## 5. Usuarios iniciales

| Rol | Usuario | Contraseña inicial |
|---|---|---|
| Administrador | `admin` | `Admin2026!` |
| Empleado | `empleado` | `Empleado2026!` |

Se crean **solo si no existen**. Si tenías una versión anterior con estos usuarios, se conservan sus contraseñas y se evita el error `usuarios_usuario_key` de registro duplicado.

Si vas a utilizar datos reales, **cambia las contraseñas iniciales** y limita el acceso a PostgreSQL. Puedes modificar las contraseñas para cuentas recién creadas mediante `INITIAL_ADMIN_PASSWORD` y `INITIAL_EMPLOYEE_PASSWORD` antes de la primera ejecución.

## 6. Flujo recomendado para probar la aplicación

1. Inicia sesión como `admin`.
2. En **Buses**, registra un bus con su placa y capacidad real.
3. En **Programar viajes**, selecciona Chimbote → Huaraz o Huaraz → Chimbote, un bus, una fecha futura, una hora y un precio de prueba.
4. En **Pasajeros**, registra un pasajero (con datos de prueba si solo estás demostrando el sistema).
5. En **Venta de pasajes**, selecciona viaje, pasajero y un asiento azul; confirma la venta.
6. En **Viajes y asientos** puedes comprobar que el asiento pasó a ocupado.
7. En **Historial de ventas** comprueba la transacción.

## 7. Estructura del proyecto

```text
VIA_COSTA_CHIMBOTE_HUARAZ/
├── pom.xml                        # Dependencias JavaFX, PostgreSQL, BCrypt
├── config.properties.example      # Plantilla (NO contiene clave real)
├── .gitignore                     # Excluye credenciales locales
├── iniciar_VIA_COSTA.bat          # Lanzador Windows (requiere Maven)
├── sql/
│   ├── 00_crear_base.sql
│   └── 01_esquema.sql
└── src/main/
    ├── java/pe/viacosta/
    │   ├── App.java                # Ventanas y operaciones
    │   └── Db.java                 # Configuración y acceso PostgreSQL
    └── resources/
        ├── css/app.css             # Diseño de interfaz
        └── db/schema.sql           # Tablas e índices
```

## 8. Tablas principales

| Tabla | Datos |
|---|---|
| `usuarios` | usuario, password_hash, rol, activo |
| `pasajeros` | dni, nombres, apellidos, teléfono |
| `buses` | placa, capacidad, modelo |
| `rutas` | origen, destino, duración (opcional) |
| `viajes` | ruta, bus, fecha y hora, precio, estado |
| `ventas` | viaje, pasajero, empleado, asiento, monto, anulada |

## 9. Solución de problemas

- **`FATAL: password authentication failed for user postgres`** → contraseña incorrecta en `config.properties` (o variable `DB_PASSWORD` del sistema).
- **`database viacosta does not exist`** → crea la base `viacosta` desde pgAdmin antes de ejecutar.
- **`usuarios_usuario_key`** → el programa actualizado usa `ON CONFLICT (usuario) DO NOTHING`; revisa que abriste esta carpeta nueva y no la versión anterior.
- **`Connection refused`** → inicia el servicio PostgreSQL, verifica puerto y dirección.
- **No aparece Maven** → abre la carpeta que tiene `pom.xml` y configura JDK 21.
- **No hay viajes al vender** → primero crea un bus y programa una salida en fecha futura.
- **Usuario admin ya existía** → su contraseña es la que ya tenía; los datos anteriores no se borran.

## Notas de seguridad y alcance

Este es un sistema demostrativo/operativo inicial para un solo servidor PostgreSQL. Para producción faltan, entre otros: respaldo automático, control de permisos a nivel de servicio, auditoría, pruebas integrales, cancelaciones con política de devolución, políticas de protección de datos de pasajeros, comprobantes SUNAT y empaquetado instalable. No almacena ni consulta información biométrica ni integra RENIEC.

> Verificación de entrega: los archivos y la estructura ZIP se comprueban en el entorno de creación. La ejecución gráfica y la conexión a una base PostgreSQL real deben probarse en el equipo donde se instalará.
