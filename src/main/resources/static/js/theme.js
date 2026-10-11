(function () {
    const KEY = "waylo-theme";

    function savedTheme() {
        try {
            return localStorage.getItem(KEY);
        } catch (e) {
            return null;
        }
    }

    function preferredTheme() {
        const saved = savedTheme();

        if (saved === "dark" || saved === "light") {
            return saved;
        }

        return window.matchMedia &&
        window.matchMedia("(prefers-color-scheme: dark)").matches
            ? "dark"
            : "light";
    }

    function applyTheme(theme) {
        document.documentElement.dataset.theme = theme;
        document.documentElement.style.colorScheme = theme;

        document.querySelectorAll("[data-theme-toggle]")
            .forEach(button => {
                const dark = theme === "dark";

                button.setAttribute(
                    "aria-label",
                    dark ? "Switch to light mode" : "Switch to dark mode"
                );

                button.setAttribute("aria-pressed", String(dark));

                const label = button.querySelector("[data-theme-label]");
                const icon = button.querySelector("[data-theme-icon]");

                if (label) {
                    label.textContent = dark ? "Light mode" : "Dark mode";
                }

                if (icon) {
                    icon.textContent = dark ? "☀" : "☾";
                }
            });
    }

    applyTheme(preferredTheme());

    document.addEventListener("DOMContentLoaded", () => {
        applyTheme(preferredTheme());

        document.querySelectorAll("[data-theme-toggle]")
            .forEach(button => {
                button.addEventListener("click", () => {
                    const next =
                        document.documentElement.dataset.theme === "dark"
                            ? "light"
                            : "dark";

                    try {
                        localStorage.setItem(KEY, next);
                    } catch (e) {
                        // Page theme still changes when storage is unavailable.
                    }

                    applyTheme(next);
                });
            });
    });

    if (window.matchMedia) {
        const media = window.matchMedia(
            "(prefers-color-scheme: dark)"
        );

        if (media.addEventListener) {
            media.addEventListener("change", () => {
                if (!savedTheme()) {
                    applyTheme(preferredTheme());
                }
            });
        }
    }
})();