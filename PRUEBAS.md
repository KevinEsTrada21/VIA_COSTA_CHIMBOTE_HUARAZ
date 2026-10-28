# Pruebas sugeridas

1. Primer inicio con PostgreSQL vacío: crea tablas, usuario admin y empleado, y las dos rutas.
2. Segundo inicio: no duplica admin, empleado ni rutas.
3. Ingreso correcto e incorrecto; empleado no ve administración.
4. DNI inválido (7 caracteres): no lo admite. DNI repetido: la restricción UNIQUE lo bloquea.
5. Registrar bus, programar viaje futuro y vender asiento 1.
6. Intentar vender el mismo asiento de nuevo: el botón se deshabilita; la base también bloquea duplicados.
7. Intentar vender pasaje para viaje ya iniciado: no aparece en el selector.
8. Anular una venta como admin: asiento vuelve a estar libre.
9. Intentar cancelar viaje con ventas activas: se rechaza hasta anularlas.
10. Salir y volver a entrar: los datos permanecen en PostgreSQL.

**Pendiente de prueba en tu equipo:** compilar mediante `mvn clean javafx:run`, ventana JavaFX y operaciones PostgreSQL reales. Las dependencias Maven no están disponibles en el entorno de generación.
