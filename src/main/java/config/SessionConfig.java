package config;

public class SessionConfig {
    public static void checkSession(io.javalin.http.Context ctx) {
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
}
