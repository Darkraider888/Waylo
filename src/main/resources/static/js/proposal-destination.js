"use strict";

document.addEventListener("DOMContentLoaded", () => {
  const toggle = document.getElementById("addDestinationToggle");
  const fields = document.getElementById("newDestinationFields");
  const existing = document.getElementById("destinationId");
  const name = document.getElementById("suggestedDestinationName");
  const district = document.getElementById("suggestedDistrict");
  const country = document.getElementById("suggestedCountry");
  if (!toggle || !fields || !existing || !name || !district || !country) return;

  function setMode(isNew) {
    fields.hidden = !isNew;
    fields.disabled = !isNew;
    existing.disabled = isNew;
    existing.required = !isNew;
    name.required = isNew;
    district.required = isNew;
    country.required = isNew;
    toggle.setAttribute("aria-expanded", String(isNew));
    toggle.textContent = isNew
      ? "← Choose an existing destination instead"
      : "+ Add a new destination instead";
  }

  setMode(name.value.trim().length > 0);
  toggle.addEventListener("click", () => {
    const open = fields.hidden;
    setMode(open);
    if (open) name.focus(); else existing.focus();
  });
});
