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
        option.text = `${t.name} (${t.price} kr)`;
        toppingSelect.appendChild(option);
    });

    bottoms.forEach(b => {
        const option = document.createElement("option");
        option.value = b.id;
        option.text = `${b.name} (${b.price} kr)`;
        bottomSelect.appendChild(option);
    });
}

async function placeOrder() {
    const toppingId = document.getElementById("toppings").value;
    const bottomId = document.getElementById("bottoms").value;
    const quantity = document.getElementById("amount").value;

    const response = await fetch("/order", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ toppingId: parseInt(toppingId), bottomId: parseInt(bottomId), quantity: parseInt(quantity) })
    });

    if (response.ok) {
        alert("Order placed!");
    } else {
        alert("Something went wrong");
    }
}

// Load options when page loads
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