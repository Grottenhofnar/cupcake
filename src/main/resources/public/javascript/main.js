const burger = document.getElementById("burg-logo");
const menu = document.getElementById("burg-menu");

burger.addEventListener("click", () => {
    console.log("clicked");
    menu.classList.toggle("active");
});