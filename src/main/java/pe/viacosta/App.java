package pe.viacosta;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.mindrot.jbcrypt.BCrypt;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Aplicación de escritorio VÍA COSTA. Sin RENIEC: datos de pasajeros ingresados manualmente. */
public class App extends Application {
    private Stage stage;
    private BorderPane shell;
    private long userId;
    private String username = "";
    private String role = "";
    private Ticket lastTicket;
    private final DateTimeFormatter dateTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private record Choice(long id, String text) {
        @Override public String toString() { return text; }
    }
    private record Ticket(long id, String pasajero, String dni, String ruta, String salida, int asiento, BigDecimal monto) {}

    @Override public void start(Stage mainStage) {
        stage = mainStage;
        stage.setTitle("VÍA COSTA  |  Transporte Chimbote - Huaraz");
        stage.setMinWidth(1000);
        stage.setMinHeight(680);
        showLogin(null);
        stage.show();
        // No iniciar con fallo silencioso: mostrar la configuración requerida.
        try { Db.initialize(); }
        catch (Exception ex) {
            showLogin("No se pudo conectar o preparar PostgreSQL. Revisa la base 'viacosta' y el archivo config.properties.\n" + readable(ex));
        }
    }
    private Scene scene(Node root, int width, int height) {
        Scene sc = new Scene((javafx.scene.Parent) root, width, height);
        var css = getClass().getResource("/css/app.css");
        if (css != null) sc.getStylesheets().add(css.toExternalForm());
        return sc;
    }
    private Label label(String text, String styleClass) {
        Label l = new Label(text);
        if (styleClass != null) l.getStyleClass().add(styleClass);
        return l;
    }
    private Button button(String text, boolean primary, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add(primary ? "primary-button" : "secondary-button");
        b.setOnAction(e -> action.run());
        return b;
    }
    private TextField input(String prompt) {
        TextField tf = new TextField(); tf.setPromptText(prompt); tf.setPrefWidth(170); return tf;
    }
    private void showLogin(String errorText) {
        Label mark = label("VÍA COSTA", "brand-big");
        Label tagline = label("TRANSPORTE INTERPROVINCIAL", "brand-caption");
        Label route1 = label("CHIMBOTE  →  HUARAZ", "route-highlight");
        Label route2 = label("HUARAZ  →  CHIMBOTE", "route-highlight");
        VBox brand = new VBox(18, label("●  SISTEMA DE PASAJES", "overline"), mark,
            tagline, new Region(), label("CONECTANDO DESTINOS", "overline"), route1, route2,
            label("Gestión de pasajeros, viajes y venta de boletos", "brand-foot"));
        brand.getStyleClass().add("login-brand");
        brand.setPrefWidth(450);
        VBox.setVgrow(brand.getChildren().get(3), Priority.ALWAYS);

        TextField usernameField = input("Usuario"); usernameField.setMaxWidth(Double.MAX_VALUE);
        PasswordField pwd = new PasswordField(); pwd.setPromptText("Contraseña");
        Label message = label(errorText == null ? "" : errorText, "error-message");
        message.setWrapText(true);
        Button loginBtn = button("Ingresar al sistema", true, () -> {
            if (usernameField.getText().isBlank() || pwd.getText().isBlank()) {
                message.setText("Ingresa tu usuario y contraseña."); return;
            }
            try (Connection c = Db.open(); PreparedStatement ps = c.prepareStatement(
                "SELECT id, password_hash, rol FROM usuarios WHERE usuario=? AND activo=TRUE")) {
                ps.setString(1, usernameField.getText().trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && BCrypt.checkpw(pwd.getText(), rs.getString("password_hash"))) {
                        userId = rs.getLong("id");
                        role = rs.getString("rol");
                        username = usernameField.getText().trim();
                        showWorkspace();
                    } else message.setText("Usuario o contraseña incorrectos.");
                }
            } catch (Exception ex) { message.setText("Error de acceso: " + readable(ex)); }
        });
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        pwd.setOnAction(e -> loginBtn.fire());
        VBox form = new VBox(15,
            label("Bienvenido", "login-title"), label("Inicia sesión para administrar tus operaciones.", "muted"),
            label("Usuario", "field-label"), usernameField,
            label("Contraseña", "field-label"), pwd, loginBtn, message,
            label("Accesos iniciales: admin / empleado", "muted"));
        form.setMaxWidth(380);
        VBox loginArea = new VBox(form); loginArea.setAlignment(Pos.CENTER);
        loginArea.setPadding(new Insets(48)); HBox.setHgrow(loginArea, Priority.ALWAYS);
        HBox root = new HBox(brand, loginArea);
        root.getStyleClass().add("login-root");
        stage.setScene(scene(root, 1100, 720));
    }
    private void showWorkspace() {
        shell = new BorderPane(); shell.getStyleClass().add("workspace");
        VBox nav = new VBox(9); nav.getStyleClass().add("sidebar"); nav.setPrefWidth(230);
        Label logo = label("VÍA COSTA", "sidebar-logo");
        Label sub = label("CHIMBOTE  ↔  HUARAZ", "sidebar-tagline");
        VBox heading = new VBox(4, logo, sub); heading.setPadding(new Insets(8, 8, 24, 8));
        nav.getChildren().add(heading);
        addNav(nav, "◫   Panel principal", this::dashboard);
        addNav(nav, "♙   Pasajeros", this::passengers);
        addNav(nav, "▤   Viajes y asientos", this::tripList);
        addNav(nav, "◈   Vender pasaje", this::sales);
        addNav(nav, "≡   Historial de ventas", this::history);
        if (isAdmin()) {
            nav.getChildren().add(label("ADMINISTRACIÓN", "menu-label"));
            addNav(nav, "▣   Buses", this::buses);
            addNav(nav, "↔   Rutas", this::routes);
            addNav(nav, "◷   Programar viajes", this::schedule);
            addNav(nav, "♙   Usuarios", this::users);
        }
        Region space = new Region(); VBox.setVgrow(space, Priority.ALWAYS); nav.getChildren().add(space);
        Button logout = button("Cerrar sesión", false, () -> { lastTicket = null; showLogin(null); });
        logout.getStyleClass().add("logout"); logout.setMaxWidth(Double.MAX_VALUE);
        nav.getChildren().addAll(label(username + " · " + role, "sidebar-user"), logout);
        shell.setLeft(nav);
        dashboard();
        stage.setScene(scene(shell, 1250, 790));
    }
    private void addNav(VBox nav, String text, Runnable action) {
        Button b = new Button(text); b.getStyleClass().add("nav-button");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> action.run()); nav.getChildren().add(b);
    }
    private boolean isAdmin() { return "ADMIN".equals(role); }
    private VBox page(String title, String desc, Node... content) {
        VBox area = new VBox(17);
        area.getStyleClass().add("content-pane");
        Label heading = label(title, "page-title");
        VBox pageHeading = new VBox(4, heading, label(desc, "muted"));
        area.getChildren().add(pageHeading);
        area.getChildren().addAll(content);
        for (Node n : content) if (n instanceof TableView<?> t) VBox.setVgrow(t, Priority.ALWAYS);
        shell.setCenter(area); return area;
    }
    private TableView<Map<String,String>> table(String... cols) {
        TableView<Map<String,String>> t = new TableView<>();
        t.getStyleClass().add("data-table");
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        t.setPlaceholder(label("No hay registros para mostrar", "muted"));
        for (String s : cols) {
            TableColumn<Map<String,String>,String> col = new TableColumn<>(s);
            col.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getOrDefault(s, "")));
            t.getColumns().add(col);
        }
        t.setPrefHeight(450);
        return t;
    }
    private void load(TableView<Map<String,String>> table, String sql, Object... args) {
        try (Connection c = Db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i=0; i<args.length; i++) ps.setObject(i+1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                var items = FXCollections.<Map<String,String>>observableArrayList();
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    Map<String,String> row = new LinkedHashMap<>();
                    for (int k=1; k<=md.getColumnCount(); k++) row.put(md.getColumnLabel(k), Objects.toString(rs.getObject(k), ""));
                    items.add(row);
                }
                table.setItems(items);
            }
        } catch (Exception ex) { failure(ex); }
    }
    private long scalar(String sql) throws SQLException {
        try (Connection c = Db.open(); PreparedStatement ps = c.prepareStatement(sql); ResultSet r = ps.executeQuery()) {
            r.next(); return r.getLong(1);
        }
    }
    private String money(BigDecimal d) { return "S/ " + (d == null ? "0.00" : d.setScale(2, java.math.RoundingMode.HALF_UP)); }
    private VBox kpi(String value, String desc) {
        VBox box = new VBox(8, label(value, "kpi-number"), label(desc, "muted"));
        box.getStyleClass().add("kpi-card"); HBox.setHgrow(box, Priority.ALWAYS);
        box.setMaxWidth(Double.MAX_VALUE); return box;
    }
    private void dashboard() {
        try {
            long totalPassengers = scalar("SELECT count(*) FROM pasajeros");
            long busCount = scalar("SELECT count(*) FROM buses");
            long upcoming = scalar("SELECT count(*) FROM viajes WHERE estado='PROGRAMADO' AND salida>=now()");
            long sold = scalar("SELECT count(*) FROM ventas WHERE NOT anulada");
            HBox stats = new HBox(12, kpi(Long.toString(totalPassengers), "Pasajeros registrados"),
                kpi(Long.toString(busCount), "Buses"), kpi(Long.toString(upcoming), "Próximos viajes"),
                kpi(Long.toString(sold), "Pasajes vendidos"));
            TableView<Map<String,String>> recent = table("ID", "Ruta", "Bus", "Salida", "Precio S/", "Estado", "Libres");
            load(recent, tripsSql() + " WHERE v.estado='PROGRAMADO' AND v.salida>=now() ORDER BY v.salida LIMIT 25");
            Label legend = label("Viajes programados. Los datos de horarios y precios son definidos por el administrador.", "muted");
            page("Panel principal", "Empresa de transporte terrestre · Chimbote ↔ Huaraz", stats,
                label("PRÓXIMAS SALIDAS", "section-title"), legend, recent);
        } catch (Exception ex) { failure(ex); }
    }
    private void passengers() {
        TableView<Map<String,String>> t = table("ID", "DNI", "Nombres", "Apellidos", "Teléfono");
        Runnable refresh = () -> load(t, "SELECT id AS \"ID\", dni AS \"DNI\", nombres AS \"Nombres\", apellidos AS \"Apellidos\", COALESCE(telefono,'') AS \"Teléfono\" FROM pasajeros ORDER BY id DESC");
        TextField dni = input("DNI: 8 números"), first = input("Nombres"), last = input("Apellidos"), phone = input("Teléfono");
        Button save = button("Registrar pasajero", true, () -> {
            try {
                if (!dni.getText().matches("\\d{8}")) throw new IllegalArgumentException("El DNI debe tener 8 dígitos.");
                if (first.getText().isBlank() || last.getText().isBlank()) throw new IllegalArgumentException("Completa nombres y apellidos.");
                Db.update("INSERT INTO pasajeros(dni,nombres,apellidos,telefono) VALUES(?,?,?,?)",
                    dni.getText(), first.getText().trim(), last.getText().trim(), phone.getText().trim());
                dni.clear(); first.clear(); last.clear(); phone.clear(); refresh.run(); success("Pasajero registrado.");
            } catch (Exception ex) { failure(ex); }
        });
        Button edit = button("Editar seleccionado", false, () -> {
            Map<String,String> row = t.getSelectionModel().getSelectedItem();
            if (row == null) { warn("Selecciona un pasajero de la tabla."); return; }
            try {
                if (first.getText().isBlank() || last.getText().isBlank()) throw new IllegalArgumentException("Escribe los nombres y apellidos nuevos en el formulario.");
                Db.update("UPDATE pasajeros SET nombres=?, apellidos=?, telefono=? WHERE id=?",
                    first.getText().trim(), last.getText().trim(), phone.getText().trim(), Long.parseLong(row.get("ID")));
                refresh.run(); success("Pasajero actualizado.");
            } catch (Exception ex) { failure(ex); }
        });
        t.getSelectionModel().selectedItemProperty().addListener((obs, old, r) -> {
            if (r != null) { dni.setText(r.get("DNI")); first.setText(r.get("Nombres")); last.setText(r.get("Apellidos")); phone.setText(r.get("Teléfono")); }
        });
        FlowPane inputs = formRow(dni, first, last, phone, save, edit, button("Actualizar", false, refresh));
        refresh.run(); page("Pasajeros", "Registro manual de DNI, nombres y datos de contacto · Sin API RENIEC", inputs, t);
    }
    private void buses() {
        TableView<Map<String,String>> t = table("ID", "Placa", "Capacidad", "Modelo");
        Runnable refresh = () -> load(t, "SELECT id AS \"ID\", placa AS \"Placa\", capacidad AS \"Capacidad\", COALESCE(modelo,'') AS \"Modelo\" FROM buses ORDER BY id DESC");
        TextField plate = input("Placa del bus"), cap = input("N.º de asientos"), model = input("Modelo o descripción");
        Button add = button("Registrar bus", true, () -> {
            try {
                int seats = Integer.parseInt(cap.getText().trim());
                if (seats < 1 || seats > 100) throw new IllegalArgumentException("Indica una capacidad entre 1 y 100.");
                if (plate.getText().isBlank()) throw new IllegalArgumentException("La placa es obligatoria.");
                Db.update("INSERT INTO buses(placa,capacidad,modelo) VALUES(?,?,?)", plate.getText().trim().toUpperCase(), seats, model.getText().trim());
                plate.clear(); cap.clear(); model.clear(); refresh.run(); success("Bus registrado.");
            } catch (Exception ex) { failure(ex); }
        });
        refresh.run(); page("Buses", "Administra las unidades de transporte y sus capacidades", formRow(plate, cap, model, add, button("Actualizar", false, refresh)), t);
    }
    private void routes() {
        TableView<Map<String,String>> t = table("ID", "Origen", "Destino", "Duración (h)");
        Runnable refresh = () -> load(t, "SELECT id AS \"ID\", origen AS \"Origen\", destino AS \"Destino\", COALESCE(duracion_horas::text,'Por definir') AS \"Duración (h)\" FROM rutas ORDER BY id");
        TextField from = input("Origen"), to = input("Destino"), hours = input("Duración horas (opcional)");
        Button add = button("Agregar ruta", true, () -> {
            try {
                if (from.getText().isBlank() || to.getText().isBlank()) throw new IllegalArgumentException("Origen y destino son obligatorios.");
                if (from.getText().trim().equalsIgnoreCase(to.getText().trim())) throw new IllegalArgumentException("Origen y destino deben ser distintos.");
                BigDecimal length = hours.getText().isBlank() ? null : new BigDecimal(hours.getText().trim());
                if (length != null && length.signum() <= 0) throw new IllegalArgumentException("La duración debe ser positiva.");
                Db.update("INSERT INTO rutas(origen,destino,duracion_horas) VALUES(?,?,?)",
                    from.getText().trim(), to.getText().trim(), length);
                from.clear(); to.clear(); hours.clear(); refresh.run(); success("Ruta registrada.");
            } catch (Exception ex) { failure(ex); }
        });
        refresh.run(); page("Rutas", "Chimbote → Huaraz y Huaraz → Chimbote precargadas", formRow(from, to, hours, add, button("Actualizar", false, refresh)), t);
    }
    private List<Choice> choices(String sql) throws SQLException {
        ArrayList<Choice> items = new ArrayList<>();
        try (Connection c = Db.open(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) items.add(new Choice(rs.getLong(1), rs.getString(2)));
        }
        return items;
    }
    private ComboBox<Choice> combo(List<Choice> options, String hint) {
        ComboBox<Choice> box = new ComboBox<>(FXCollections.observableArrayList(options));
        box.setPromptText(hint); box.setPrefWidth(270);
        return box;
    }
    private String tripsSql() {
        return "SELECT v.id AS \"ID\", r.origen||' → '||r.destino AS \"Ruta\", " +
            "b.placa AS \"Bus\", to_char(v.salida,'DD/MM/YYYY HH24:MI') AS \"Salida\", " +
            "v.precio AS \"Precio S/\", v.estado AS \"Estado\", " +
            "(b.capacidad-(SELECT count(*) FROM ventas s WHERE s.viaje_id=v.id AND NOT s.anulada)) AS \"Libres\" " +
            "FROM viajes v JOIN rutas r ON r.id=v.ruta_id JOIN buses b ON b.id=v.bus_id";
    }
    private void schedule() {
        try {
            TableView<Map<String,String>> t = table("ID", "Ruta", "Bus", "Salida", "Precio S/", "Estado", "Libres");
            Runnable refresh = () -> load(t, tripsSql() + " ORDER BY v.salida DESC LIMIT 300");
            ComboBox<Choice> route = combo(choices("SELECT id, origen||' → '||destino FROM rutas ORDER BY id"), "Seleccionar ruta");
            ComboBox<Choice> bus = combo(choices("SELECT id, placa||' · '||capacidad||' asientos' FROM buses ORDER BY placa"), "Seleccionar bus");
            DatePicker day = new DatePicker(LocalDate.now().plusDays(1)); day.setPrefWidth(160);
            TextField time = input("Hora HH:mm (ej. 08:30)"), price = input("Precio S/");
            Button add = button("Programar salida", true, () -> {
                try {
                    if (route.getValue() == null || bus.getValue() == null || day.getValue() == null) throw new IllegalArgumentException("Selecciona ruta, bus y fecha.");
                    LocalDateTime depart = LocalDateTime.of(day.getValue(), LocalTime.parse(time.getText().trim()));
                    if (!depart.isAfter(LocalDateTime.now())) throw new IllegalArgumentException("La fecha y hora deben ser futuras.");
                    BigDecimal amount = new BigDecimal(price.getText().trim());
                    if (amount.signum() < 0) throw new IllegalArgumentException("El precio debe ser mayor o igual a cero.");
                    Db.update("INSERT INTO viajes(ruta_id,bus_id,salida,precio) VALUES(?,?,?,?)",
                        route.getValue().id(), bus.getValue().id(), Timestamp.valueOf(depart), amount);
                    refresh.run(); success("Salida programada.");
                } catch (Exception ex) { failure(ex); }
            });
            Button cancel = button("Cancelar viaje seleccionado", false, () -> {
                Map<String,String> r = t.getSelectionModel().getSelectedItem();
                if (r == null) { warn("Selecciona un viaje."); return; }
                try {
                    long id = Long.parseLong(r.get("ID"));
                    long active = countSales(id);
                    if (active > 0) { warn("Este viaje tiene " + active + " pasajes activos. Anúlalos antes de cancelar el viaje."); return; }
                    if (!confirm("¿Cancelar el viaje N.º " + id + "?")) return;
                    Db.update("UPDATE viajes SET estado='CANCELADO' WHERE id=? AND estado='PROGRAMADO'", id);
                    refresh.run();
                } catch (Exception ex) { failure(ex); }
            });
            refresh.run();
            page("Programar viajes", "El administrador define salidas, unidades y precios reales de VÍA COSTA",
                formRow(route, bus, day, time, price, add), formRow(cancel, button("Actualizar", false, refresh)), t);
        } catch (Exception ex) { failure(ex); }
    }
    private long countSales(long tripId) throws SQLException {
        try (Connection c=Db.open(); PreparedStatement p=c.prepareStatement("SELECT count(*) FROM ventas WHERE viaje_id=? AND NOT anulada")) {
            p.setLong(1, tripId); try(ResultSet r=p.executeQuery()) {r.next();return r.getLong(1);}
        }
    }
    private void tripList() {
        TableView<Map<String,String>> t = table("ID", "Ruta", "Bus", "Salida", "Precio S/", "Estado", "Libres");
        Runnable refresh = () -> load(t, tripsSql() + " ORDER BY v.salida DESC LIMIT 300");
        FlowPane seats = new FlowPane(); seats.setHgap(8); seats.setVgap(8);
        ScrollPane scroll = new ScrollPane(seats); scroll.setFitToWidth(true); scroll.setPrefViewportHeight(160);
        t.getSelectionModel().selectedItemProperty().addListener((obs, prev, row) -> {
            seats.getChildren().clear();
            if (row == null) return;
            try {
                long tripId = Long.parseLong(row.get("ID"));
                try (Connection c = Db.open(); PreparedStatement ps=c.prepareStatement(
                    "SELECT b.capacidad FROM viajes v JOIN buses b ON b.id=v.bus_id WHERE v.id=?")) {
                    ps.setLong(1, tripId);
                    try (ResultSet rs=ps.executeQuery()) {
                        if (!rs.next()) return;
                        int total = rs.getInt(1);
                        Set<Integer> taken = occupied(tripId);
                        for (int i=1; i<=total; i++) {
                            Label seat = label(String.format("%02d",i), taken.contains(i) ? "seat-taken" : "seat-free");
                            seats.getChildren().add(seat);
                        }
                    }
                }
            } catch (Exception ex) { failure(ex); }
        });
        refresh.run();
        VBox box = page("Viajes y asientos", "Selecciona un viaje para visualizar los asientos libres y ocupados",
            formRow(button("Actualizar viajes", false, refresh)), t, label("DISTRIBUCIÓN DE ASIENTOS  ·  Azul = libre   /   Gris = ocupado", "section-title"), scroll);
        VBox.setVgrow(t, Priority.ALWAYS);
    }
    private Set<Integer> occupied(long tripId) throws SQLException {
        Set<Integer> taken = new HashSet<>();
        try (Connection c=Db.open();PreparedStatement p=c.prepareStatement("SELECT asiento FROM ventas WHERE viaje_id=? AND NOT anulada")) {
            p.setLong(1,tripId);
            try (ResultSet r=p.executeQuery()) {while(r.next()) taken.add(r.getInt(1));}
        }
        return taken;
    }
    private void sales() {
        try {
            ComboBox<Choice> trip = combo(choices("SELECT v.id, r.origen||' → '||r.destino||' · '||to_char(v.salida,'DD/MM HH24:MI')||' · '||b.placa FROM viajes v JOIN rutas r ON r.id=v.ruta_id JOIN buses b ON b.id=v.bus_id WHERE v.estado='PROGRAMADO' AND v.salida>now() ORDER BY v.salida LIMIT 150"), "Selecciona la salida");
            ComboBox<Choice> passenger = combo(choices("SELECT id, dni||' · '||nombres||' '||apellidos FROM pasajeros ORDER BY apellidos,nombres"), "Selecciona el pasajero");
            Label status = label("Selecciona un viaje para mostrar sus asientos.", "muted");
            FlowPane seats = new FlowPane(8,8);
            ScrollPane seatScroll = new ScrollPane(seats); seatScroll.setFitToWidth(true); seatScroll.setPrefViewportHeight(145);
            final int[] selectedSeat = {0};
            Runnable updateSeats = () -> {
                seats.getChildren().clear(); selectedSeat[0] = 0;
                Choice chosen = trip.getValue();
                if (chosen == null) return;
                try (Connection c=Db.open();PreparedStatement p=c.prepareStatement("SELECT b.capacidad, v.precio FROM viajes v JOIN buses b ON b.id=v.bus_id WHERE v.id=?")) {
                    p.setLong(1,chosen.id());
                    try (ResultSet r=p.executeQuery()) {
                        if (!r.next()) return;
                        int cap = r.getInt(1); BigDecimal price = r.getBigDecimal(2);
                        Set<Integer> taken = occupied(chosen.id());
                        status.setText("Precio " + money(price) + "  ·  " + (cap-taken.size()) + " de " + cap + " asientos disponibles");
                        for (int n=1;n<=cap;n++) {
                            final int seatNum=n;
                            Button b = new Button(String.format("%02d",n));
                            b.getStyleClass().add(taken.contains(n) ? "seat-disabled" : "seat-choice");
                            b.setDisable(taken.contains(n));
                            b.setOnAction(ev -> {
                                selectedSeat[0]=seatNum;
                                for(Node node: seats.getChildren()) node.getStyleClass().remove("seat-selected");
                                b.getStyleClass().add("seat-selected");
                            });
                            seats.getChildren().add(b);
                        }
                    }
                } catch (Exception ex) { failure(ex); }
            };
            trip.setOnAction(e -> updateSeats.run());
            Button issue = button("Confirmar venta", true, () -> {
                if (trip.getValue()==null || passenger.getValue()==null || selectedSeat[0]<1) {
                    warn("Selecciona viaje, pasajero y asiento disponible."); return;
                }
                try {
                    Ticket ticket = book(trip.getValue().id(), passenger.getValue().id(), selectedSeat[0]);
                    lastTicket = ticket;
                    success("Pasaje N.º " + ticket.id() + " vendido. " + money(ticket.monto()));
                    updateSeats.run();
                } catch (Exception ex) { failure(ex); updateSeats.run(); }
            });
            Button receipt = button("Guardar último boleto (.txt)", false, () -> exportReceipt());
            Button refresh = button("Actualizar listas", false, this::sales);
            VBox content = page("Venta de pasajes", "1. Registra al pasajero  ·  2. Selecciona viaje  ·  3. Elige asiento  ·  4. Confirma la venta",
                formRow(trip, passenger, refresh), status, label("ASIENTOS DISPONIBLES", "section-title"), seatScroll,
                formRow(issue, receipt), label("Cada asiento se vende una sola vez. Las ventas se guardan en PostgreSQL.", "muted"));
        } catch (Exception ex) { failure(ex); }
    }
    private Ticket book(long tripId, long passengerId, int seat) throws SQLException {
        try (Connection c=Db.open()) {
            c.setAutoCommit(false);
            try {
                // FOR UPDATE serializa ventas de un mismo viaje; el índice parcial es la defensa final.
                String q = "SELECT v.precio,b.capacidad,r.origen,r.destino,v.salida FROM viajes v " +
                           "JOIN buses b ON b.id=v.bus_id JOIN rutas r ON r.id=v.ruta_id " +
                           "WHERE v.id=? AND v.estado='PROGRAMADO' AND v.salida>now() FOR UPDATE OF v";
                BigDecimal price; int capacity; String route;String depart;
                try (PreparedStatement p=c.prepareStatement(q)) {
                    p.setLong(1,tripId);
                    try(ResultSet r=p.executeQuery()) {
                        if(!r.next()) throw new IllegalArgumentException("Viaje inexistente o no disponible.");
                        price=r.getBigDecimal(1); capacity=r.getInt(2);
                        route=r.getString(3)+" → "+r.getString(4);
                        depart=r.getTimestamp(5).toLocalDateTime().format(dateTime);
                    }
                }
                if (seat < 1 || seat > capacity) throw new IllegalArgumentException("El asiento no existe en este bus.");
                String passengerName, dni;
                try (PreparedStatement p=c.prepareStatement("SELECT dni, nombres||' '||apellidos FROM pasajeros WHERE id=?")) {
                    p.setLong(1,passengerId);
                    try(ResultSet r=p.executeQuery()) {
                        if(!r.next()) throw new IllegalArgumentException("Pasajero inexistente.");
                        dni=r.getString(1); passengerName=r.getString(2);
                    }
                }
                long saleId;
                try (PreparedStatement p=c.prepareStatement(
                    "INSERT INTO ventas(viaje_id,pasajero_id,empleado_id,asiento,monto) VALUES(?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    p.setLong(1,tripId);p.setLong(2,passengerId);p.setLong(3,userId);p.setInt(4,seat);p.setBigDecimal(5,price);
                    p.executeUpdate();
                    try(ResultSet keys=p.getGeneratedKeys()) {keys.next();saleId=keys.getLong(1);}
                }
                c.commit(); return new Ticket(saleId,passengerName,dni,route,depart,seat,price);
            } catch (Exception e) {
                c.rollback();
                if(e instanceof SQLException s) throw s;
                if(e instanceof RuntimeException r) throw r;
                throw new SQLException(e);
            } finally {c.setAutoCommit(true);}
        }
    }
    private void exportReceipt() {
        if (lastTicket == null) { warn("Primero vende un pasaje para generar su comprobante."); return; }
        try {
            FileChooser file = new FileChooser();file.setTitle("Guardar comprobante de pasaje");
            file.setInitialFileName("VIA_COSTA_boleto_"+lastTicket.id()+".txt");
            file.getExtensionFilters().add(new FileChooser.ExtensionFilter("Texto UTF-8", "*.txt"));
            java.io.File selected = file.showSaveDialog(stage);
            if (selected == null) return;
            Ticket t=lastTicket;
            String receipt = "VÍA COSTA - TRANSPORTE TERRESTRE\n" +
                "================================\n" + "BOLETO N.º " + t.id() + "\n" +
                "Pasajero: " + t.pasajero() + "\nDNI: " + t.dni() + "\n" +
                "Ruta: " + t.ruta() + "\nSalida: " + t.salida() + "\n" +
                "Asiento: " + t.asiento() + "\nMonto: " + money(t.monto()) + "\n" +
                "================================\nComprobante referencial (no tributario).\n";
            Files.writeString(selected.toPath(), receipt, StandardCharsets.UTF_8);
            success("Comprobante guardado en " + selected.getAbsolutePath());
        } catch (Exception ex) { failure(ex); }
    }
    private void history() {
        TableView<Map<String,String>> t=table("Venta", "Fecha", "DNI", "Pasajero", "Ruta", "Salida", "Asiento", "Monto S/", "Empleado", "Estado");
        Runnable refresh=()->load(t, "SELECT s.id AS \"Venta\",to_char(s.creado_en,'DD/MM/YYYY HH24:MI') AS \"Fecha\", " +
            "p.dni AS \"DNI\",p.nombres||' '||p.apellidos AS \"Pasajero\", r.origen||' → '||r.destino AS \"Ruta\", " +
            "to_char(v.salida,'DD/MM/YYYY HH24:MI') AS \"Salida\",s.asiento AS \"Asiento\",s.monto AS \"Monto S/\", " +
            "u.usuario AS \"Empleado\",CASE WHEN s.anulada THEN 'ANULADA' ELSE 'ACTIVA' END AS \"Estado\" " +
            "FROM ventas s JOIN pasajeros p ON p.id=s.pasajero_id JOIN viajes v ON v.id=s.viaje_id " +
            "JOIN rutas r ON r.id=v.ruta_id JOIN usuarios u ON u.id=s.empleado_id ORDER BY s.id DESC LIMIT 500");
        Button cancel = button("Anular venta seleccionada", false, () -> {
            if (!isAdmin()) { warn("Solo un administrador puede anular ventas."); return; }
            Map<String,String> row=t.getSelectionModel().getSelectedItem();
            if (row==null) { warn("Selecciona una venta.");return; }
            if ("ANULADA".equals(row.get("Estado"))) { warn("Esta venta ya está anulada.");return; }
            if (!confirm("¿Anular la venta N.º " + row.get("Venta") + "? El asiento volverá a estar disponible.")) return;
            try {
                Db.update("UPDATE ventas SET anulada=TRUE WHERE id=? AND NOT anulada",Long.parseLong(row.get("Venta")));
                refresh.run();success("Venta anulada.");
            } catch(Exception ex){failure(ex);}
        });
        FlowPane controls = isAdmin() ? formRow(button("Actualizar", false, refresh), cancel) : formRow(button("Actualizar", false, refresh));
        refresh.run();page("Historial de ventas", "Registro de boletos vendidos y anulados (últimos 500)", controls, t);
    }
    private void users() {
        TableView<Map<String,String>> t=table("ID", "Usuario", "Rol", "Activo");
        Runnable refresh = () -> load(t,"SELECT id AS \"ID\",usuario AS \"Usuario\",rol AS \"Rol\",CASE WHEN activo THEN 'SÍ' ELSE 'NO' END AS \"Activo\" FROM usuarios ORDER BY id");
        TextField name=input("Nuevo usuario"); PasswordField password=new PasswordField();password.setPromptText("Contraseña (mín. 8)");password.setPrefWidth(180);
        ComboBox<String> roles=new ComboBox<>(FXCollections.observableArrayList("EMPLEADO","ADMIN"));roles.setValue("EMPLEADO");
        Button create=button("Crear usuario",true,()->{
            try {
                if(!name.getText().trim().matches("[A-Za-z0-9._-]{3,50}")) throw new IllegalArgumentException("Nombre de usuario: 3-50 letras, números, punto, guion o guion bajo.");
                if(password.getText().length()<8) throw new IllegalArgumentException("La contraseña debe tener 8 caracteres como mínimo.");
                Db.update("INSERT INTO usuarios(usuario,password_hash,rol) VALUES(?,?,?)",
                    name.getText().trim(), BCrypt.hashpw(password.getText(),BCrypt.gensalt(12)),roles.getValue());
                password.clear();name.clear();refresh.run();success("Usuario creado.");
            } catch(Exception ex){failure(ex);}
        });
        Button toggle=button("Activar / desactivar seleccionado",false,()->{
            Map<String,String> row=t.getSelectionModel().getSelectedItem();
            if(row==null){warn("Selecciona un usuario.");return;}
            if(Long.parseLong(row.get("ID"))==userId){warn("No puedes desactivar tu propia cuenta.");return;}
            try {Db.update("UPDATE usuarios SET activo=NOT activo WHERE id=?",Long.parseLong(row.get("ID")));refresh.run();}
            catch(Exception ex){failure(ex);}
        });
        refresh.run();page("Usuarios del sistema", "Administradores y empleados; las contraseñas se almacenan con BCrypt",
            formRow(name,password,roles,create,toggle),t);
    }
    private FlowPane formRow(Node... nodes) {
        FlowPane f=new FlowPane(10,10);f.setAlignment(Pos.CENTER_LEFT);
        f.getStyleClass().add("form-row"); f.getChildren().addAll(nodes);return f;
    }
    private boolean confirm(String msg) {
        return new Alert(Alert.AlertType.CONFIRMATION,msg,ButtonType.YES,ButtonType.NO).showAndWait().orElse(ButtonType.NO)==ButtonType.YES;
    }
    private void success(String msg) {new Alert(Alert.AlertType.INFORMATION,msg,ButtonType.OK).showAndWait();}
    private void warn(String msg) {new Alert(Alert.AlertType.WARNING,msg,ButtonType.OK).showAndWait();}
    private String readable(Exception e) {
        if (e instanceof SQLException s) {
            if ("23505".equals(s.getSQLState())) return "Registro duplicado. Ya existe un dato con ese valor (DNI, placa, asiento o usuario).";
            if ("23503".equals(s.getSQLState())) return "No se puede completar la operación porque existen registros relacionados.";
            if ("28P01".equals(s.getSQLState())) return "La contraseña de PostgreSQL es incorrecta. Revisa DB_PASSWORD en config.properties.";
            if ("3D000".equals(s.getSQLState())) return "La base 'viacosta' no existe. Créala en pgAdmin.";
        }
        return Objects.toString(e.getMessage(),e.getClass().getSimpleName());
    }
    private void failure(Exception ex) {new Alert(Alert.AlertType.ERROR,readable(ex),ButtonType.OK).showAndWait();}
    public static void main(String[] args) {launch(args);}
}
