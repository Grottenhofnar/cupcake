package routes;

import config.SessionConfig;
import io.javalin.Javalin;
import io.javalin.http.Context;
import controller.CupcakeController;
import persistence.CupcakeMapper;
import model.BalanceRequest;
import model.CheckoutRequest;

import java.util.Map;

public class Routes {
    public static void register(Javalin app) {

        app.before(ctx -> SessionConfig.checkSession(ctx));

        app.get("/login", ctx -> ctx.render("templates/login.html"));
        app.get("/signup", ctx -> ctx.render("templates/signup.html"));
        app.post("/login", ctx -> CupcakeController.handleLogin(ctx));
        app.post("/signup", ctx -> CupcakeController.handleSignup(ctx));
        app.get("/logout", ctx -> CupcakeController.handleLogout(ctx));

        app.get("/index", ctx -> {
            String username = ctx.sessionAttribute("user");
            ctx.render("templates/index.html", Map.of("username", username));
        });

        app.get("/me", ctx -> {
            String username = ctx.sessionAttribute("user");
            if (username == null) { ctx.status(401).result("Not logged in"); return; }
            double balance = CupcakeMapper.getCustomerBalance(username);
            ctx.json(Map.of("username", username, "balance", balance));
        });

        app.get("/toppings", ctx -> ctx.json(CupcakeMapper.getToppings()));
        app.get("/bottoms", ctx -> ctx.json(CupcakeMapper.getBottoms()));

        app.post("/checkout", ctx -> {
            String username = ctx.sessionAttribute("user");
            if (username == null) { ctx.status(401).result("Not logged in"); return; }
            var body = ctx.bodyAsClass(CheckoutRequest.class);
            int customerId = CupcakeMapper.getCustomerId(username);
            double balance = CupcakeMapper.getCustomerBalance(username);
            double total = body.items.stream()
                    .mapToDouble(i -> (i.toppingPrice + i.bottomPrice) * i.quantity)
                    .sum();
            if (balance < total) {
                ctx.status(400).result("Insufficient balance. You have " + balance + " kr, but need " + total + " kr.");
                return;
            }
            CupcakeMapper.placeOrder(customerId, total, body.items);
            ctx.result("Order placed");
        });


        app.post("/cart", ctx -> {
            String username = ctx.sessionAttribute("user");
            if (username == null) { ctx.status(401).result("Not logged in"); return; }
            var body = ctx.bodyAsClass(CheckoutRequest.class);
            int customerId = CupcakeMapper.getCustomerId(username);
            CupcakeMapper.saveCart(customerId, body.items);
            ctx.result("Cart saved");
        });


        app.get("/cart", ctx -> {
            String username = ctx.sessionAttribute("user");
            if (username == null) { ctx.status(401).result("Not logged in"); return; }
            int customerId = CupcakeMapper.getCustomerId(username);
            ctx.json(CupcakeMapper.getCart(customerId));
        });


        app.delete("/cart", ctx -> {
            String username = ctx.sessionAttribute("user");
            if (username == null) { ctx.status(401).result("Not logged in"); return; }
            int customerId = CupcakeMapper.getCustomerId(username);
            CupcakeMapper.clearCart(customerId);
            ctx.result("Cart cleared");
        });

        app.get("/admin", ctx -> {
            String role = ctx.sessionAttribute("role");
            if (role == null || !role.equals("admin")) { ctx.redirect("/login"); return; }
            ctx.render("templates/admin.html", Map.of("username", ctx.sessionAttribute("user")));
        });

        app.get("/admin/orders", ctx -> {
            if (!isAdmin(ctx)) return;
            ctx.json(CupcakeMapper.getAllOrders());
        });

        app.get("/admin/orders/{orderId}", ctx -> {
            if (!isAdmin(ctx)) return;
            ctx.json(CupcakeMapper.getOrderLines(Integer.parseInt(ctx.pathParam("orderId"))));
        });

        app.delete("/admin/orders/{orderId}", ctx -> {
            if (!isAdmin(ctx)) return;
            CupcakeMapper.deleteOrder(Integer.parseInt(ctx.pathParam("orderId")));
            ctx.result("Order deleted");
        });

        app.get("/admin/customers", ctx -> {
            if (!isAdmin(ctx)) return;
            ctx.json(CupcakeMapper.getAllCustomers());
        });

        app.put("/admin/customers/{customerId}/balance", ctx -> {
            if (!isAdmin(ctx)) return;
            var body = ctx.bodyAsClass(BalanceRequest.class);
            CupcakeMapper.updateBalance(Integer.parseInt(ctx.pathParam("customerId")), body.balance);
            ctx.result("Balance updated");
        });

        app.delete("/admin/customers/{customerId}", ctx -> {
            if (!isAdmin(ctx)) return;
            CupcakeMapper.deleteCustomer(Integer.parseInt(ctx.pathParam("customerId")));
            ctx.result("Customer deleted");
        });
    }

    private static boolean isAdmin(Context ctx) {
        String role = ctx.sessionAttribute("role");
        if (role == null || !role.equals("admin")) {
            ctx.status(403).result("Forbidden");
            return false;
        }
        return true;
    }
}

