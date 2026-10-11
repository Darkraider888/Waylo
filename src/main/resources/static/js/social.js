// Enhancements only; all form actions still work without JavaScript.
document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll("[data-photo-input]").forEach(input => {
        const preview = input.parentElement.querySelector("[data-photo-preview]");
        const img = preview?.querySelector("[data-photo-preview-image]");
        let previous = null;
        input.addEventListener("change", () => {
            if (previous) URL.revokeObjectURL(previous);
            previous = null;
            const file = input.files?.[0];
            if (!file) { if (preview) preview.hidden = true; return; }
            if (!["image/jpeg", "image/png"].includes(file.type) || file.size > 6 * 1024 * 1024) {
                input.value = "";
                if (preview) preview.hidden = true;
                alert("Choose a JPEG or PNG photo smaller than 6 MB.");
                return;
            }
            if (img && preview) {
                previous = URL.createObjectURL(file);
                img.src = previous;
                preview.hidden = false;
            }
        });
    });
    document.querySelectorAll("[data-share-url]").forEach(button => {
        button.addEventListener("click", async () => {
            const path = button.dataset.shareUrl;
            if (!path) return;
            const url = new URL(path, window.location.origin).href;
            try {
                if (navigator.share) {
                    await navigator.share({title: "Waylo travel story", url});
                } else if (navigator.clipboard) {
                    await navigator.clipboard.writeText(url);
                    button.setAttribute("aria-label", "Link copied");
                    button.textContent = "✓ Copied";
                } else {
                    window.prompt("Copy post link:", url);
                }
            } catch (error) {
                if (error?.name !== "AbortError") window.prompt("Copy post link:", url);
            }
        });
    });
});
