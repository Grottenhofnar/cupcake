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

        app.before(ctx -> checkSession(ctx));

        app.get("/login", ctx -> ctx.render("templates/login.html"));
        app.get("/signup", ctx -> ctx.render("templates/signup.html"));

        app.get("/index", ctx -> {
            String username = ctx.sessionAttribute("user");
            ctx.render("templates/index.html", Map.of("username", username));
        });

        app.post("/login", ctx -> handleLogin(ctx));
        app.post("/signup", ctx -> handleSignup(ctx));
        app.get("/logout", ctx -> handleLogout(ctx));


        app.get("/me", ctx -> {
            String username = ctx.sessionAttribute("user");

            if (username == null) {
                ctx.status(401).result("Not logged in");
                return;
            }

            try (var conn = ConnectionPool.getConnection()) {
                var stmt = conn.prepareStatement("SELECT Balance FROM Customers WHERE Username = ?");
                stmt.setString(1, username);
                var rs = stmt.executeQuery();
                if (rs.next()) {
                    double balance = rs.getDouble("Balance");
                    ctx.json(Map.of("username", username, "balance", balance));
                }
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).result("Server error");
            }
        });

        app.get("/admin", ctx -> {
            String role = ctx.sessionAttribute("role");
            if (role == null || !role.equals("admin")) {
                ctx.redirect("/login");
                return;
            }
            String username = ctx.sessionAttribute("user");
            ctx.render("templates/admin.html", Map.of("username", username));
        });

        app.put("/admin/customers/{customerId}/balance", ctx -> {
            String role = ctx.sessionAttribute("role");
            if (role == null || !role.equals("admin")) {
                ctx.status(403).result("Forbidden");
                return;
            }

            int customerId = Integer.parseInt(ctx.pathParam("customerId"));
            var body = ctx.bodyAsClass(BalanceRequest.class);

            try (var conn = ConnectionPool.getConnection()) {
                var stmt = conn.prepareStatement(
                        "UPDATE Customers SET Balance = ? WHERE CustomerID = ?"
                );
                stmt.setDouble(1, body.balance);
                stmt.setInt(2, customerId);
                stmt.executeUpdate();
                ctx.result("Balance updated");
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).result("Server error");
            }
        });

        app.delete("/admin/customers/{customerId}", ctx -> {
            String role = ctx.sessionAttribute("role");
            if (role == null || !role.equals("admin")) {
                ctx.status(403).result("Forbidden");
                return;
            }

            int customerId = Integer.parseInt(ctx.pathParam("customerId"));

            try (var conn = ConnectionPool.getConnection()) {
                var stmt = conn.prepareStatement("DELETE FROM Customers WHERE CustomerID = ?");
                stmt.setInt(1, customerId);
                stmt.executeUpdate();
                ctx.result("Customer deleted");
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).result("Server error");
            }
        });

        app.get("/admin/orders", ctx -> {
            String role = ctx.sessionAttribute("role");
            if (role == null || !role.equals("admin")) {
                ctx.status(403).result("Forbidden");
                return;
            }

            try (var conn = ConnectionPool.getConnection()) {
                var rs = conn.prepareStatement("""
            SELECT o.OrderID, c.Username, o.OrderDate,
                   SUM((ol.ToppingPrice + ol.BottomPrice) * ol.Quantity) AS Total
            FROM Orders o
            JOIN Customers c ON o.CustomerID = c.CustomerID
            JOIN Orderlines ol ON o.OrderID = ol.OrderID
            GROUP BY o.OrderID, c.Username, o.OrderDate
            ORDER BY o.OrderDate DESC
        """).executeQuery();

                List<Map<String, Object>> orders = new ArrayList<>();
                while (rs.next()) {
                    orders.add(Map.of(
                            "orderId", rs.getInt("OrderID"),
                            "username", rs.getString("Username"),
                            "orderDate", rs.getString("OrderDate"),
                            "total", rs.getDouble("Total")
                    ));
                }
                ctx.json(orders);
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).result("Server error");
            }
        });

        app.get("/admin/customers", ctx -> {
            String role = ctx.sessionAttribute("role");
            if (role == null || !role.equals("admin")) {
                ctx.status(403).result("Forbidden");
                return;
            }

            try (var conn = ConnectionPool.getConnection()) {
                var rs = conn.prepareStatement(
                        "SELECT CustomerID, Username, Balance, Role FROM Customers"
                ).executeQuery();

                List<Map<String, Object>> customers = new ArrayList<>();
                while (rs.next()) {
                    customers.add(Map.of(
                            "customerId", rs.getInt("CustomerID"),
                            "username", rs.getString("Username"),
                            "balance", rs.getDouble("Balance"),
                            "role", rs.getString("Role")
                    ));
                }
                ctx.json(customers);
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).result("Server error");
            }
        });

        app.get("/admin/orders/{orderId}", ctx -> {
            String role = ctx.sessionAttribute("role");
            if (role == null || !role.equals("admin")) {
                ctx.status(403).result("Forbidden");
                return;
            }

            int orderId = Integer.parseInt(ctx.pathParam("orderId"));

            try (var conn = ConnectionPool.getConnection()) {
                var stmt = conn.prepareStatement("""
            SELECT ol.Quantity,
                   t.ToppingName, ol.ToppingPrice,
                   b.BottomName, ol.BottomPrice
            FROM Orderlines ol
            JOIN Toppings t ON ol.ToppingID = t.ToppingID
            JOIN Bottoms b ON ol.BottomID = b.BottomID
            WHERE ol.OrderID = ?
        """);
                stmt.setInt(1, orderId);
                var rs = stmt.executeQuery();

                List<Map<String, Object>> lines = new ArrayList<>();
                while (rs.next()) {
                    lines.add(Map.of(
                            "quantity", rs.getInt("Quantity"),
                            "toppingName", rs.getString("ToppingName"),
                            "toppingPrice", rs.getDouble("ToppingPrice"),
                            "bottomName", rs.getString("BottomName"),
                            "bottomPrice", rs.getDouble("BottomPrice")
                    ));
                }
                ctx.json(lines);
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).result("Server error");
            }
        });

        app.delete("/admin/orders/{orderId}", ctx -> {
            String role = ctx.sessionAttribute("role");
            if (role == null || !role.equals("admin")) {
                ctx.status(403).result("Forbidden");
                return;
            }

            int orderId = Integer.parseInt(ctx.pathParam("orderId"));

            try (var conn = ConnectionPool.getConnection()) {
                var stmt = conn.prepareStatement("DELETE FROM Orders WHERE OrderID = ?");
                stmt.setInt(1, orderId);
                stmt.executeUpdate();
                ctx.result("Order deleted");
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).result("Server error");
            }
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

        app.post("/checkout", ctx -> {
            String username = ctx.sessionAttribute("user");
            if (username == null) { ctx.status(401).result("Not logged in"); return; }

            var body = ctx.bodyAsClass(CheckoutRequest.class);

            try (var conn = ConnectionPool.getConnection()) {

                var custStmt = conn.prepareStatement(
                        "SELECT CustomerID, Balance FROM Customers WHERE Username = ?"
                );
                custStmt.setString(1, username);
                var custRs = custStmt.executeQuery();
                if (!custRs.next()) { ctx.status(404).result("Customer not found"); return; }

                int customerId = custRs.getInt("CustomerID");
                double balance = custRs.getDouble("Balance");


                double total = body.items.stream()
                        .mapToDouble(i -> (i.toppingPrice + i.bottomPrice) * i.quantity)
                        .sum();

                if (balance < total) {
                    ctx.status(400).result("Insufficient balance. You have " + balance + " kr, but need " + total + " kr.");
                    return;
                }

                var deductStmt = conn.prepareStatement(
                        "UPDATE Customers SET Balance = Balance - ? WHERE CustomerID = ?"
                );
                deductStmt.setDouble(1, total);
                deductStmt.setInt(2, customerId);
                deductStmt.executeUpdate();

                var orderStmt = conn.prepareStatement(
                        "INSERT INTO Orders (CustomerID) VALUES (?) RETURNING OrderID"
                );
                orderStmt.setInt(1, customerId);
                var orderRs = orderStmt.executeQuery();
                orderRs.next();
                int orderId = orderRs.getInt("OrderID");

                var lineStmt = conn.prepareStatement(
                        "INSERT INTO Orderlines (ToppingID, BottomID, OrderID, Quantity, ToppingPrice, BottomPrice) VALUES (?, ?, ?, ?, ?, ?)"
                );
                for (var item : body.items) {
                    lineStmt.setInt(1, item.toppingId);
                    lineStmt.setInt(2, item.bottomId);
                    lineStmt.setInt(3, orderId);
                    lineStmt.setInt(4, item.quantity);
                    lineStmt.setDouble(5, item.toppingPrice);
                    lineStmt.setDouble(6, item.bottomPrice);
                    lineStmt.addBatch();
                }
                lineStmt.executeBatch();

                ctx.result("Order placed");
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).result("Server error");
            }
        });

    }
    private static void checkSession(io.javalin.http.Context ctx) {
        String user = ctx.sessionAttribute("user");
        String path = ctx.path();

        if (path.equals("/login.html") || path.equals("/signup.html") ||
                path.equals("/login") || path.equals("/signup") ||
                path.equals("/me") ||
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
                        "SELECT password, role FROM Customers WHERE username = ?"
                );
                stmt.setString(1, loginUser.username);
                var rs = stmt.executeQuery();

                if (rs.next()) {
                    String hashedPassword = rs.getString("password");
                    String role = rs.getString("role");

                    if (BCrypt.checkpw(loginUser.password, hashedPassword)) {
                        ctx.sessionAttribute("user", loginUser.username);
                        ctx.sessionAttribute("role", role);
                        ctx.json(Map.of("role", role));
                    } else {
                        ctx.status(401).result("Forkert login");
                    }
                } else {
                    ctx.status(401).result("Forkert login");
                }
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
    public static class CheckoutRequest {
        public List<CartItem> items;
    }

    public static class CartItem {
        public int toppingId;
        public int bottomId;
        public String toppingName;
        public String bottomName;
        public double toppingPrice;
        public double bottomPrice;
        public int quantity;
        public double lineTotal;
    }
}


