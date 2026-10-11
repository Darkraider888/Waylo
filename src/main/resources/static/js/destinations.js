document.addEventListener("DOMContentLoaded", () => {
    const picker = document.getElementById("destination-picker");
    if (!picker) return;
    const search = document.getElementById("destinationSearch");
    const select = document.getElementById("destinationId");
    const preview = document.getElementById("mapPreview");
    const link = document.getElementById("openGoogleMaps");
    const csrf = document.querySelector('meta[name="_csrf"]')?.content;
    const form = document.getElementById("inlineDestination");
    const toggle = document.getElementById("showNewDestination");
    const save = document.getElementById("addDestinationButton");
    const error = document.getElementById("destinationError");
    if (!search || !select || !preview || !link) return;

    const destinations = Array.from(select.options).filter(o => !!o.value).map(o => ({
        id: o.value, label: o.textContent.trim(), name: o.dataset.name || "",
        district: o.dataset.district || "", country: o.dataset.country || "",
        latitude: o.dataset.lat || "", longitude: o.dataset.lng || ""
    }));

    function updateMap() {
        const destination = destinations.find(d => d.id === select.value);
        preview.replaceChildren();
        if (!destination) {
            preview.textContent = "Choose a destination to view its location.";
            link.href = "https://www.google.com/maps";
            return;
        }
        const query = destination.latitude && destination.longitude
            ? destination.latitude + "," + destination.longitude
            : [destination.name, destination.district, destination.country].filter(Boolean).join(", ");
        link.href = "https://www.google.com/maps/search/?api=1&query=" + encodeURIComponent(query);
        const key = picker.dataset.mapsKey || "";
        if (!key) {
            preview.textContent = "Selected location: " + query + ". Open in Google Maps for details.";
            return;
        }
        const iframe = document.createElement("iframe");
        iframe.title = "Map of " + destination.name;
        iframe.loading = "lazy";
        iframe.referrerPolicy = "strict-origin-when-cross-origin";
        iframe.src = "https://www.google.com/maps/embed/v1/place?key=" + encodeURIComponent(key)
            + "&q=" + encodeURIComponent(query);
        preview.append(iframe);
    }

    function filter(term, selectedId = select.value) {
        const query = term.trim().toLocaleLowerCase();
        const matches = destinations.filter(d =>
            [d.label, d.name, d.district, d.country].join(" ").toLocaleLowerCase().includes(query));
        select.replaceChildren(new Option("Choose a destination", ""));
        matches.forEach(d => {
            const option = new Option(d.label, d.id);
            option.dataset.name = d.name;
            option.dataset.district = d.district;
            option.dataset.country = d.country;
            option.dataset.lat = d.latitude;
            option.dataset.lng = d.longitude;
            select.append(option);
        });
        if (matches.some(d => d.id === selectedId)) select.value = selectedId;
        else if (matches.length === 1) select.value = matches[0].id;
        else select.value = "";
        updateMap();
    }
    search.addEventListener("input", () => filter(search.value));
    select.addEventListener("change", updateMap);
    updateMap();

    toggle?.addEventListener("click", () => {
        form.hidden = !form.hidden;
        toggle.setAttribute("aria-expanded", String(!form.hidden));
        if (!form.hidden) document.getElementById("newPlaceName")?.focus();
    });

    save?.addEventListener("click", async () => {
        const value = id => document.getElementById(id)?.value.trim() || "";
        const latitude = value("newLatitude");
        const longitude = value("newLongitude");
        const request = {
            name: value("newPlaceName"), district: value("newDistrict"),
            country: value("newCountry"), description: value("newDescription"),
            latitude: latitude ? Number(latitude) : null,
            longitude: longitude ? Number(longitude) : null
        };
        error.textContent = "";
        if (!request.name || !request.district || !request.country || Boolean(latitude) !== Boolean(longitude)) {
            error.textContent = "Enter a name, district and country; provide both coordinates or neither.";
            return;
        }
        save.disabled = true;
        save.textContent = "Adding destination...";
        try {
            const response = await fetch("/organizer/destinations/inline", {
                method: "POST", credentials: "same-origin",
                headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrf || "" },
                body: JSON.stringify(request)
            });
            const json = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(json.error || "Unable to add destination. Please check the fields.");
            const added = {
                id: String(json.id), name: json.name,
                district: json.district || "", country: json.country || "",
                latitude: json.latitude == null ? "" : String(json.latitude),
                longitude: json.longitude == null ? "" : String(json.longitude),
                label: [json.name, json.district, json.country].filter(Boolean).join(" — ")
            };
            destinations.push(added);
            search.value = "";
            filter("", added.id);
            form.hidden = true;
            toggle.setAttribute("aria-expanded", "false");
            select.focus();
        } catch (e) {
            error.textContent = e.message;
        } finally {
            save.disabled = false;
            save.textContent = "Add and select destination";
        }
    });
});
