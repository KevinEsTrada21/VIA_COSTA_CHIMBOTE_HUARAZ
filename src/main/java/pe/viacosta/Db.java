package pe.viacosta;

import org.mindrot.jbcrypt.BCrypt;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Properties;

/** Conexión, migración inicial idempotente y sembrado de datos básicos. */
public final class Db {
    private static final Properties SETTINGS = readSettings();
    private Db() {}

    private static Properties readSettings() {
        Properties p = new Properties();
        Path file = Path.of("config.properties");
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) { p.load(in); }
            catch (Exception e) { System.err.println("No se pudo leer config.properties: " + e.getMessage()); }
        }
        return p;
    }
    public static String config(String key, String fallback) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) return env;
        String val = SETTINGS.getProperty(key);
        return val == null || val.isBlank() ? fallback : val;
    }

    public static Connection open() throws SQLException {
        return DriverManager.getConnection(
                config("DB_URL", "jdbc:postgresql://localhost:5432/viacosta"),
                config("DB_USER", "postgres"),
                config("DB_PASSWORD", "")
        );
    }

    public static void initialize() throws Exception {
        String sql;
        try (InputStream in = Db.class.getResourceAsStream("/db/schema.sql")) {
            if (in == null) throw new IllegalStateException("No existe /db/schema.sql");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // Script propio: sentencias DDL separadas por punto y coma, sin bloques PL/pgSQL.
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                for (String chunk : sql.split(";")) {
                    StringBuilder clean = new StringBuilder();
                    for (String line : chunk.split("\\R")) {
                        if (!line.trim().startsWith("--")) clean.append(line).append('\n');
                    }
                    if (!clean.toString().isBlank()) {
                        try (Statement st = c.createStatement()) { st.execute(clean.toString()); }
                    }
                }
                createInitialUser(c, "admin", "ADMIN", config("INITIAL_ADMIN_PASSWORD", "Admin2026!"));
                createInitialUser(c, "empleado", "EMPLEADO", config("INITIAL_EMPLOYEE_PASSWORD", "Empleado2026!"));
                createRouteIfMissing(c, "Chimbote", "Huaraz");
                createRouteIfMissing(c, "Huaraz", "Chimbote");
                c.commit();
            } catch (Exception ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); }
        }
    }
    private static void createInitialUser(Connection c, String username, String role, String secret) throws SQLException {
        if (secret.length() < 8) throw new IllegalArgumentException("Contraseña inicial muy corta para " + username);
        // La condición + ON CONFLICT evita el error de usuario admin duplicado.
        String q = "INSERT INTO usuarios(usuario,password_hash,rol) VALUES(?,?,?) ON CONFLICT (usuario) DO NOTHING";
        try (PreparedStatement st = c.prepareStatement(q)) {
            st.setString(1, username);
            st.setString(2, BCrypt.hashpw(secret, BCrypt.gensalt(12)));
            st.setString(3, role);
            st.executeUpdate();
        }
    }
    private static void createRouteIfMissing(Connection c, String origen, String destino) throws SQLException {
        String q = "INSERT INTO rutas(origen,destino) SELECT ?,? WHERE NOT EXISTS (SELECT 1 FROM rutas WHERE lower(origen)=lower(?) AND lower(destino)=lower(?))";
        try (PreparedStatement st = c.prepareStatement(q)) {
            st.setString(1, origen); st.setString(2, destino);
            st.setString(3, origen); st.setString(4, destino);
            st.executeUpdate();
        }
    }
    public static int update(String sql, Object... args) throws SQLException {
        try (Connection c = open(); PreparedStatement p = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) p.setObject(i+1, args[i]);
            return p.executeUpdate();
        }
    }
}
