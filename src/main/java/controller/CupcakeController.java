package controller;
import model.User;
import io.javalin.http.Context;

import org.mindrot.jbcrypt.BCrypt;
import persistence.ConnectionPool;

import java.util.Map;

public class CupcakeController {

    public static void handleLogin(Context ctx) {
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

    public static void handleSignup(Context ctx) {
        try {
            User newUser = ctx.bodyAsClass(User.class);
            if (newUser.username == null || newUser.username.isEmpty() ||
                    newUser.password == null || newUser.password.isEmpty()) {
                ctx.status(400).result("Ugyldigt input");
                return;
            }
            try (var conn = ConnectionPool.getConnection()) {
                var checkStmt = conn.prepareStatement("SELECT 1 FROM customers WHERE username = ?");
                checkStmt.setString(1, newUser.username);
                if (checkStmt.executeQuery().next()) {
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
                ctx.result("Bruger oprettet");
            }
        } catch (Exception e) {
            e.printStackTrace();
            ctx.status(400).result("Invalid request");
        }
    }

    public static void handleLogout(Context ctx) {
        ctx.req().getSession().invalidate();
        ctx.redirect("/login");
    }
}

