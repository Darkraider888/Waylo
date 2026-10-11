/* Waylo reservation calculator. Preview only; server is the source of truth. */
document.addEventListener('DOMContentLoaded', () => {
    'use strict';

    const form = document.querySelector('.wl-reservation-form');
    if (!form) return;

    const seatsInput = form.querySelector('#seats');
    const advanceInput = form.querySelector('#advancePercent');
    const submitButton = form.querySelector('#continueBooking');
    const warning = form.querySelector('#paymentLimitWarning');
    const maxSeats = Math.max(1, Math.min(6, Number(form.dataset.maxSeats || 1)));
    const gatewayReady = form.dataset.checkoutReady === 'true';

    const nodes = {
        perSeatAmount: form.querySelector('#perSeatAmount'),
        seatsSummary: form.querySelector('#seatsSummary'),
        tripTotal: form.querySelector('#tripTotal'),
        advanceAmount: form.querySelector('#advanceAmount'),
        platformFee: form.querySelector('#platformFee'),
        paymentDue: form.querySelector('#paymentDue'),
        remainingBalance: form.querySelector('#remainingBalance'),
        advanceRateLabel: form.querySelector('#advanceRateLabel'),
        advancePercentValue: form.querySelector('#advancePercentValue')
    };

    // Work in integer poisha, with HALF_UP rounding to match Java BigDecimal.
    function parseAmountToPoisha(value) {
        const parts = String(value).trim().split('.');
        if (parts.length > 2 || !/^\d+$/.test(parts[0]) ||
            (parts.length === 2 && !/^\d{1,2}$/.test(parts[1]))) {
            throw new Error('Invalid trip price.');
        }
        return BigInt(parts[0]) * 100n + BigInt((parts[1] || '').padEnd(2, '0'));
    }

    const price = parseAmountToPoisha(form.dataset.price || '0');
    const currency = amount => '\u09F3' +
        (amount / 100n).toLocaleString('en-BD') + '.' +
        (amount % 100n).toString().padStart(2, '0');

    function selectedSeats() {
        const value = Number.parseInt(seatsInput.value, 10);
        return Math.min(maxSeats, Math.max(1, Number.isFinite(value) ? value : 1));
    }

    function update() {
        const seats = selectedSeats();
        const percent = Math.min(100, Math.max(10, Number(advanceInput.value) || 10));
        const fullTrip = price * BigInt(seats);
        const advance = (fullTrip * BigInt(percent) + 50n) / 100n;
        const fee = (advance + 50n) / 100n; // 1% of THIS payment only.
        const payNow = advance + fee;
        const remaining = fullTrip - advance;

        seatsInput.value = String(seats);
        nodes.perSeatAmount.textContent = currency(price);
        nodes.seatsSummary.textContent = String(seats);
        nodes.tripTotal.textContent = currency(fullTrip);
        nodes.advanceAmount.textContent = currency(advance);
        nodes.platformFee.textContent = currency(fee);
        nodes.paymentDue.textContent = currency(payNow);
        nodes.remainingBalance.textContent = currency(remaining);
        nodes.advanceRateLabel.textContent = '(' + percent + '%)';
        nodes.advancePercentValue.textContent = percent + '%';
        advanceInput.setAttribute('aria-valuetext', percent + ' percent advance');

        const outOfRange = payNow < 1000n || payNow > 50000000n;
        warning.hidden = !outOfRange;
        warning.textContent = outOfRange
            ? 'This sandbox supports a payment between \u09F310.00 and \u09F3500,000.00. Change your seats or advance amount.'
            : '';
        submitButton.disabled = !gatewayReady || outOfRange;
        form.querySelectorAll('[data-seat-step]').forEach(button => {
            const direction = Number(button.dataset.seatStep);
            button.disabled = direction < 0 ? seats <= 1 : seats >= maxSeats;
        });
    }

    form.querySelectorAll('[data-seat-step]').forEach(button => {
        button.addEventListener('click', () => {
            seatsInput.value = String(Math.min(maxSeats,
                Math.max(1, selectedSeats() + Number(button.dataset.seatStep))));
            update();
        });
    });

    seatsInput.addEventListener('change', update);
    advanceInput.addEventListener('input', update);
    update();
});
