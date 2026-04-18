package persistence;

import model.CartItem;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CupcakeMapper {

    public static List<Map<String, Object>> getToppings() throws SQLException {
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
        return toppings;
    }

    public static List<Map<String, Object>> getBottoms() throws SQLException {
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
        return bottoms;
    }

    public static List<Map<String, Object>> getAllOrders() throws SQLException {
        List<Map<String, Object>> orders = new ArrayList<>();
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
            while (rs.next()) {
                orders.add(Map.of(
                        "orderId", rs.getInt("OrderID"),
                        "username", rs.getString("Username"),
                        "orderDate", rs.getString("OrderDate"),
                        "total", rs.getDouble("Total")
                ));
            }
        }
        return orders;
    }

    public static List<Map<String, Object>> getOrderLines(int orderId) throws SQLException {
        List<Map<String, Object>> lines = new ArrayList<>();
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
            while (rs.next()) {
                lines.add(Map.of(
                        "quantity", rs.getInt("Quantity"),
                        "toppingName", rs.getString("ToppingName"),
                        "toppingPrice", rs.getDouble("ToppingPrice"),
                        "bottomName", rs.getString("BottomName"),
                        "bottomPrice", rs.getDouble("BottomPrice")
                ));
            }
        }
        return lines;
    }

    public static List<Map<String, Object>> getAllCustomers() throws SQLException {
        List<Map<String, Object>> customers = new ArrayList<>();
        try (var conn = ConnectionPool.getConnection()) {
            var rs = conn.prepareStatement(
                    "SELECT CustomerID, Username, Balance, Role FROM Customers"
            ).executeQuery();
            while (rs.next()) {
                customers.add(Map.of(
                        "customerId", rs.getInt("CustomerID"),
                        "username", rs.getString("Username"),
                        "balance", rs.getDouble("Balance"),
                        "role", rs.getString("Role")
                ));
            }
        }
        return customers;
    }

    public static double getCustomerBalance(String username) throws SQLException {
        try (var conn = ConnectionPool.getConnection()) {
            var stmt = conn.prepareStatement("SELECT Balance FROM Customers WHERE Username = ?");
            stmt.setString(1, username);
            var rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble("Balance");
        }
        return 0;
    }

    public static void saveCart(int customerId, List<CartItem> items) throws SQLException {
        try (var conn = ConnectionPool.getConnection()) {
            var clearStmt = conn.prepareStatement("DELETE FROM CartItems WHERE CustomerID = ?");
            clearStmt.setInt(1, customerId);
            clearStmt.executeUpdate();

            var stmt = conn.prepareStatement("""
            INSERT INTO CartItems (CustomerID, ToppingID, BottomID, ToppingName, BottomName, ToppingPrice, BottomPrice, Quantity)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """);
            for (var item : items) {
                stmt.setInt(1, customerId);
                stmt.setInt(2, item.toppingId);
                stmt.setInt(3, item.bottomId);
                stmt.setString(4, item.toppingName);
                stmt.setString(5, item.bottomName);
                stmt.setDouble(6, item.toppingPrice);
                stmt.setDouble(7, item.bottomPrice);
                stmt.setInt(8, item.quantity);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    public static List<Map<String, Object>> getCart(int customerId) throws SQLException {
        List<Map<String, Object>> items = new ArrayList<>();
        try (var conn = ConnectionPool.getConnection()) {
            var stmt = conn.prepareStatement(
                    "SELECT * FROM CartItems WHERE CustomerID = ?"
            );
            stmt.setInt(1, customerId);
            var rs = stmt.executeQuery();
            while (rs.next()) {
                items.add(Map.of(
                        "toppingId", rs.getInt("ToppingID"),
                        "bottomId", rs.getInt("BottomID"),
                        "toppingName", rs.getString("ToppingName"),
                        "bottomName", rs.getString("BottomName"),
                        "toppingPrice", rs.getDouble("ToppingPrice"),
                        "bottomPrice", rs.getDouble("BottomPrice"),
                        "quantity", rs.getInt("Quantity"),
                        "lineTotal", (rs.getDouble("ToppingPrice") + rs.getDouble("BottomPrice")) * rs.getInt("Quantity")
                ));
            }
        }
        return items;
    }

    public static void clearCart(int customerId) throws SQLException {
        try (var conn = ConnectionPool.getConnection()) {
            var stmt = conn.prepareStatement("DELETE FROM CartItems WHERE CustomerID = ?");
            stmt.setInt(1, customerId);
            stmt.executeUpdate();
        }
    }

    public static int getCustomerId(String username) throws SQLException {
        try (var conn = ConnectionPool.getConnection()) {
            var stmt = conn.prepareStatement("SELECT CustomerID FROM Customers WHERE Username = ?");
            stmt.setString(1, username);
            var rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("CustomerID");
        }
        return -1;
    }

    public static void deleteOrder(int orderId) throws SQLException {
        try (var conn = ConnectionPool.getConnection()) {
            var stmt = conn.prepareStatement("DELETE FROM Orders WHERE OrderID = ?");
            stmt.setInt(1, orderId);
            stmt.executeUpdate();
        }
    }

    public static void deleteCustomer(int customerId) throws SQLException {
        try (var conn = ConnectionPool.getConnection()) {
            var stmt = conn.prepareStatement("DELETE FROM Customers WHERE CustomerID = ?");
            stmt.setInt(1, customerId);
            stmt.executeUpdate();
        }
    }

    public static void updateBalance(int customerId, double balance) throws SQLException {
        try (var conn = ConnectionPool.getConnection()) {
            var stmt = conn.prepareStatement("UPDATE Customers SET Balance = ? WHERE CustomerID = ?");
            stmt.setDouble(1, balance);
            stmt.setInt(2, customerId);
            stmt.executeUpdate();
        }
    }

    public static void placeOrder(int customerId, double total, List<CartItem> items) throws SQLException {
        try (var conn = ConnectionPool.getConnection()) {
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
            for (var item : items) {
                lineStmt.setInt(1, item.toppingId);
                lineStmt.setInt(2, item.bottomId);
                lineStmt.setInt(3, orderId);
                lineStmt.setInt(4, item.quantity);
                lineStmt.setDouble(5, item.toppingPrice);
                lineStmt.setDouble(6, item.bottomPrice);
                lineStmt.addBatch();
            }
            lineStmt.executeBatch();
        }
    }
}
