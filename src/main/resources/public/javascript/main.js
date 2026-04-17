async function login(){

    const username = document.getElementById("username").value;
    const password = document.getElementById("password").value;

    const response = await fetch("/login",{
        method:"POST",
        headers:{
            "Content-Type":"application/json"
        },
        body:JSON.stringify({username,password})
    });

    if(response.ok){
        window.location = "/index";
    }
    else{
        alert("Forkert login");
    }

}

async function createUser() {

    const username = document.getElementById("username").value;
    const password = document.getElementById("password").value;

    const response = await fetch("/signup", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({username, password})
    });

    if (response.ok) {
        alert("Bruger oprettet");
        window.location = "/login";
    } else {
        alert("Bruger findes allerede");
    }
}

let cart = [];

async function loadOptions() {
    const [toppingRes, bottomRes] = await Promise.all([
        fetch("/toppings"),
        fetch("/bottoms")
    ]);

    const toppings = await toppingRes.json();
    const bottoms = await bottomRes.json();

    const toppingSelect = document.getElementById("toppings");
    const bottomSelect = document.getElementById("bottoms");

    toppings.forEach(t => {
        const option = document.createElement("option");
        option.value = t.id;
        option.dataset.name = t.name;
        option.dataset.price = t.price;
        option.text = `${t.name} (${t.price} kr)`;
        toppingSelect.appendChild(option);
    });

    bottoms.forEach(b => {
        const option = document.createElement("option");
        option.value = b.id;
        option.dataset.name = b.name;
        option.dataset.price = b.price;
        option.text = `${b.name} (${b.price} kr)`;
        bottomSelect.appendChild(option);
    });
}

function addToCart() {
    const toppingSelect = document.getElementById("toppings");
    const bottomSelect = document.getElementById("bottoms");
    const quantity = parseInt(document.getElementById("amount").value);

    if (!quantity || quantity < 1) {
        alert("Please enter a valid amount");
        return;
    }

    const toppingId = parseInt(toppingSelect.value);
    const bottomId = parseInt(bottomSelect.value);
    const toppingName = toppingSelect.selectedOptions[0].dataset.name;
    const bottomName = bottomSelect.selectedOptions[0].dataset.name;
    const toppingPrice = parseFloat(toppingSelect.selectedOptions[0].dataset.price);
    const bottomPrice = parseFloat(bottomSelect.selectedOptions[0].dataset.price);
    const lineTotal = (toppingPrice + bottomPrice) * quantity;

    cart.push({ toppingId, bottomId, toppingName, bottomName, toppingPrice, bottomPrice, quantity, lineTotal });

    renderCart();
    updateCartIcon();
}

function removeFromCart(index) {
    cart.splice(index, 1);
    renderCart();
    updateCartIcon();
}

function updateCartIcon() {
    const totalItems = cart.reduce((sum, item) => sum + item.quantity, 0);
    document.getElementById("cart-count").textContent = totalItems;
}

function renderCart() {
    const cartItems = document.getElementById("cart-items");
    const cartTotal = document.getElementById("cart-total");
    cartItems.innerHTML = "";

    if (cart.length === 0) {
        cartItems.innerHTML = "<p>Your cart is empty.</p>";
        cartTotal.textContent = "0.00";
        return;
    }

    let total = 0;
    cart.forEach((item, index) => {
        total += item.lineTotal;
        const div = document.createElement("div");
        div.classList.add("cart-item");
        div.innerHTML = `
            <span>${item.quantity}x ${item.bottomName} bottom + ${item.toppingName} topping</span>
            <span>${item.lineTotal.toFixed(2)} kr</span>
            <button onclick="removeFromCart(${index})">✕</button>
        `;
        cartItems.appendChild(div);
    });

    cartTotal.textContent = total.toFixed(2);
}

function toggleCart() {
    const sidebar = document.getElementById("cart-sidebar");
    sidebar.classList.toggle("open");
}

async function checkout() {
    if (cart.length === 0) {
        alert("Your cart is empty!");
        return;
    }

    const response = await fetch("/checkout", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ items: cart })
    });

    if (response.ok) {
        alert("Order placed successfully!");
        cart = [];
        renderCart();
        updateCartIcon();
    } else {
        const msg = await response.text();
        alert(msg);
    }
}

loadOptions();

async function loadUser() {
    const res = await fetch("/me");

    if (res.ok) {
        const data = await res.json();
        document.getElementById("login-name").innerText = data.username;
    }
}
function goToCreate(){
    window.location = "/signup";
}

function goToLogin(){
    window.location = "/login";
}
async function logout() {
    await fetch("/logout");
    window.location = "/login";
}