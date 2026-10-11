document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll("[data-password-toggle]")
        .forEach(button => {

            const input = document.getElementById(
                button.dataset.passwordToggle
            );

            if (!input) return;

            button.addEventListener("click", () => {
                const isHidden = input.type === "password";

                input.type = isHidden ? "text" : "password";
                button.textContent = isHidden ? "Hide" : "Show";

                button.setAttribute(
                    "aria-pressed",
                    String(isHidden)
                );
            });
        });
});