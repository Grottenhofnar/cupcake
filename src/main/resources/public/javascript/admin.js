async function loadOrders() {
    const res = await fetch("/admin/orders");
    if (!res.ok) { alert("Access denied"); return; }

    const orders = await res.json();
    const tbody = document.getElementById("orders-body");
    tbody.innerHTML = "";

    orders.forEach(order => {
        const row = document.createElement("tr");
        row.style.cursor = "pointer";
        row.innerHTML = `
            <td>${order.orderId}</td>
            <td>${order.username}</td>
            <td>${order.orderDate}</td>
            <td>${order.total.toFixed(2)} kr</td>
            <td><button onclick="deleteOrder(${order.orderId}, event)">🗑 Delete</button></td>
        `;
        row.onclick = () => openOrderModal(order.orderId);
        tbody.appendChild(row);
    });
}

async function openOrderModal(orderId) {
    const res = await fetch(`/admin/orders/${orderId}`);
    if (!res.ok) { alert("Could not load order details"); return; }

    const lines = await res.json();

    const modal = document.getElementById("order-modal");
    const modalBody = document.getElementById("modal-body");
    const modalTitle = document.getElementById("modal-title");

    modalTitle.textContent = `Order #${orderId}`;
    modalBody.innerHTML = "";

    let total = 0;
    lines.forEach(line => {
        const lineTotal = (line.toppingPrice + line.bottomPrice) * line.quantity;
        total += lineTotal;
        const div = document.createElement("div");
        div.classList.add("modal-line");
        div.innerHTML = `
            <span>${line.quantity}x <strong>${line.bottomName}</strong> bottom + <strong>${line.toppingName}</strong> topping</span>
            <span>${lineTotal.toFixed(2)} kr</span>
        `;
        modalBody.appendChild(div);
    });

    const totalDiv = document.createElement("div");
    totalDiv.classList.add("modal-total");
    totalDiv.innerHTML = `<strong>Total: ${total.toFixed(2)} kr</strong>`;
    modalBody.appendChild(totalDiv);

    modal.style.display = "flex";
}

function closeModal() {
    document.getElementById("order-modal").style.display = "none";
}

async function deleteOrder(orderId, event) {
    event.stopPropagation();
    if (!confirm(`Are you sure you want to delete order #${orderId}?`)) return;

    const res = await fetch(`/admin/orders/${orderId}`, { method: "DELETE" });
    if (res.ok) {
        alert(`Order #${orderId} deleted`);
        loadOrders();
    } else {
        alert("Failed to delete order");
    }
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
        <td>
            <button onclick="openBalanceModal(${customer.customerId}, '${customer.username}', ${customer.balance})">Edit Balance</button>
            <button onclick="deleteCustomer(${customer.customerId}, '${customer.username}')">🗑 Delete</button>
        </td>
    `;
        tbody.appendChild(row);
    });
}

async function deleteCustomer(customerId, username) {
    if (!confirm(`Are you sure you want to delete customer "${username}"? This will also delete all their orders.`)) return;

    const res = await fetch(`/admin/customers/${customerId}`, { method: "DELETE" });

    if (res.ok) {
        alert(`Customer "${username}" deleted`);
        loadCustomers();
    } else {
        alert("Failed to delete customer");
    }
}
function openBalanceModal(customerId, username, currentBalance) {
    document.getElementById("balance-modal-title").textContent = `Edit balance for ${username}`;
    document.getElementById("balance-input").value = currentBalance;
    document.getElementById("balance-modal").dataset.customerId = customerId;
    document.getElementById("balance-modal").style.display = "flex";
}

function closeBalanceModal() {
    document.getElementById("balance-modal").style.display = "none";
}

async function saveBalance() {
    const modal = document.getElementById("balance-modal");
    const customerId = modal.dataset.customerId;
    const newBalance = parseFloat(document.getElementById("balance-input").value);

    if (isNaN(newBalance) || newBalance < 0) {
        alert("Please enter a valid balance");
        return;
    }

    const res = await fetch(`/admin/customers/${customerId}/balance`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ balance: newBalance })
    });

    if (res.ok) {
        closeBalanceModal();
        loadCustomers();
    } else {
        alert("Failed to update balance");
    }
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

async function loadUser() {
    const res = await fetch("/me");

    if (res.ok) {
        const data = await res.json();
        const el = document.getElementById("nav-username");
        if (el) el.innerText = data.username;
    } else {
        window.location = "/login";
    }
}

loadUser();
loadOrders();