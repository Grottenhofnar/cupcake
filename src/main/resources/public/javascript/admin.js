async function loadOrders() {
    const res = await fetch("/admin/orders");
    if (!res.ok) { alert("Access denied"); return; }

    const orders = await res.json();
    const tbody = document.getElementById("orders-body");
    tbody.innerHTML = "";

    orders.forEach(order => {
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${order.orderId}</td>
            <td>${order.username}</td>
            <td>${order.orderDate}</td>
            <td>${order.total.toFixed(2)} kr</td>
        `;
        tbody.appendChild(row);
    });
}

async function loadCustomers() {
    const res = await fetch("/admin/customers");
    if (!res.ok) { alert("Access denied"); return; }

    const customers = await res.json();
    const tbody = document.getElementById("customers-body");
    tbody.innerHTML = "";

    customers.forEach(customer => {
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${customer.customerId}</td>
            <td>${customer.username}</td>
            <td>${customer.balance.toFixed(2)} kr</td>
            <td>${customer.role}</td>
        `;
        tbody.appendChild(row);
    });
}

function showSection(section) {
    document.getElementById("orders-section").style.display = section === "orders" ? "block" : "none";
    document.getElementById("customers-section").style.display = section === "customers" ? "block" : "none";

    if (section === "orders") loadOrders();
    if (section === "customers") loadCustomers();
}

async function logout() {
    await fetch("/logout");
    window.location = "/login";
}

loadOrders();