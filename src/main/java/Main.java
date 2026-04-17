import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.mindrot.jbcrypt.BCrypt;
import persistence.ConnectionPool;
import io.javalin.rendering.template.JavalinThymeleaf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) {

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
            });

            config.fileRenderer(new JavalinThymeleaf());
        }).start(7000);

        app.get("/login", ctx -> ctx.render("templates/login.html"));
        app.get("/signup", ctx -> ctx.render("templates/signup.html"));
        app.get("/index", ctx -> ctx.render("templates/index.html"));
        app.post("/login", ctx -> handleLogin(ctx));
        app.post("/signup", ctx -> handleSignup(ctx));
        app.get("/logout", ctx -> handleLogout(ctx));


        app.get("/me", ctx -> {
            String username = ctx.sessionAttribute("user");

            if (username == null) {
                ctx.status(401).result("Not logged in");
                return;
            }

            ctx.json(new UserResponse(username));
        });

        app.get("/toppings", ctx -> {
            List<Map<String, Object>> toppings = new ArrayList<>();
            try (var conn = ConnectionPool.getConnection()) {
                var rs = conn.prepareStatement("SELECT ToppingID, ToppingName, Price FROM Toppings").executeQuery();
                while (rs.next()) {
                    toppings.add(Map.of(
                            "id", rs.getInt("ToppingID"),
                            "name", rs.getString("ToppingName"),
                            "price", rs.getDouble("Price")
                    ));
                }
            }
            ctx.json(toppings);
        });

        app.get("/bottoms", ctx -> {
            List<Map<String, Object>> bottoms = new ArrayList<>();
            try (var conn = ConnectionPool.getConnection()) {
                var rs = conn.prepareStatement("SELECT BottomID, BottomName, Price FROM Bottoms").executeQuery();
                while (rs.next()) {
                    bottoms.add(Map.of(
                            "id", rs.getInt("BottomID"),
                            "name", rs.getString("BottomName"),
                            "price", rs.getDouble("Price")
                    ));
                }
            }
            ctx.json(bottoms);
        });

        app.post("/order", ctx -> {
            String username = ctx.sessionAttribute("user");
            if (username == null) { ctx.status(401).result("Not logged in"); return; }

            var body = ctx.bodyAsClass(OrderRequest.class);

            try (var conn = ConnectionPool.getConnection()) {
                // Get CustomerID
                var stmt = conn.prepareStatement("SELECT CustomerID FROM Customers WHERE Username = ?");
                stmt.setString(1, username);
                var rs = stmt.executeQuery();
                if (!rs.next()) { ctx.status(404).result("Customer not found"); return; }
                int customerId = rs.getInt("CustomerID");

                // Create Order
                var orderStmt = conn.prepareStatement(
                        "INSERT INTO Orders (CustomerID) VALUES (?) RETURNING OrderID"
                );
                orderStmt.setInt(1, customerId);
                var orderRs = orderStmt.executeQuery();
                orderRs.next();
                int orderId = orderRs.getInt("OrderID");

                // Get prices
                var toppingStmt = conn.prepareStatement("SELECT Price FROM Toppings WHERE ToppingID = ?");
                toppingStmt.setInt(1, body.toppingId);
                var toppingRs = toppingStmt.executeQuery();
                toppingRs.next();
                double toppingPrice = toppingRs.getDouble("Price");

                var bottomStmt = conn.prepareStatement("SELECT Price FROM Bottoms WHERE BottomID = ?");
                bottomStmt.setInt(1, body.bottomId);
                var bottomRs = bottomStmt.executeQuery();
                bottomRs.next();
                double bottomPrice = bottomRs.getDouble("Price");

                // Insert Orderline
                var lineStmt = conn.prepareStatement(
                        "INSERT INTO Orderlines (ToppingID, BottomID, OrderID, Quantity, ToppingPrice, BottomPrice) VALUES (?, ?, ?, ?, ?, ?)"
                );
                lineStmt.setInt(1, body.toppingId);
                lineStmt.setInt(2, body.bottomId);
                lineStmt.setInt(3, orderId);
                lineStmt.setInt(4, body.quantity);
                lineStmt.setDouble(5, toppingPrice);
                lineStmt.setDouble(6, bottomPrice);
                lineStmt.executeUpdate();

                ctx.result("Order placed");
            }
        });

    }
    private static void checkSession(io.javalin.http.Context ctx) {
        String user = ctx.sessionAttribute("user");
        String path = ctx.path();

        if (path.equals("/login.html") || path.equals("/signup.html") ||
                path.equals("/login") || path.equals("/signup") ||
                path.equals("/me") ||
                path.startsWith("/notes") ||
                path.endsWith(".js") || path.endsWith(".css") ||
                path.endsWith(".png") || path.endsWith(".jpg")) {
            return;
        }

        if (user == null) {
            ctx.redirect("/login");
        }
    }

    private static void handleLogin(io.javalin.http.Context ctx) {

        try {
            User loginUser = ctx.bodyAsClass(User.class);

            try (var conn = ConnectionPool.getConnection()) {

                var stmt = conn.prepareStatement(
                        "SELECT password FROM customers WHERE username = ?"
                );

                stmt.setString(1, loginUser.username);

                var rs = stmt.executeQuery();

                if (rs.next()) {
                    String hashedPassword = rs.getString("password");

                    if (org.mindrot.jbcrypt.BCrypt.checkpw(loginUser.password, hashedPassword)) {
                        ctx.sessionAttribute("user", loginUser.username);
                        ctx.result("Login success");
                    } else {
                        ctx.status(401).result("Forkert login");
                    }
                } else {
                    ctx.status(401).result("Forkert login");
                }

            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).result("Server error");
            }

        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(400).result("Invalid request");
        }
    }



    private static void handleSignup(io.javalin.http.Context ctx) {

        try {
            User newUser = ctx.bodyAsClass(User.class);

            if (newUser.username == null || newUser.username.isEmpty() ||
                    newUser.password == null || newUser.password.isEmpty()) {

                ctx.status(400).result("Ugyldigt input");
                return;
            }

            try (var conn = ConnectionPool.getConnection()) {

                var checkStmt = conn.prepareStatement(
                        "SELECT 1 FROM customers WHERE username = ?"
                );
                checkStmt.setString(1, newUser.username);

                var rs = checkStmt.executeQuery();

                if (rs.next()) {
                    ctx.status(400).result("Bruger findes allerede");
                    return;
                }

                String hashedPassword = BCrypt.hashpw(newUser.password, BCrypt.gensalt());


                var insertStmt = conn.prepareStatement(
                        "INSERT INTO customers (username, password) VALUES (?, ?)"
                );

                insertStmt.setString(1, newUser.username);
                insertStmt.setString(2, hashedPassword);

                insertStmt.executeUpdate();

                System.out.println("Gemte bruger: " + newUser.username);
                ctx.result("Bruger oprettet");

            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).result("Database error");
            }

        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(400).result("Invalid request");
        }
    }

    private static void handleLogout(io.javalin.http.Context ctx) {
        ctx.req().getSession().invalidate();
        ctx.redirect("/login");
    }
}


