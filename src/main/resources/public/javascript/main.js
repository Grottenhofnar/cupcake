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