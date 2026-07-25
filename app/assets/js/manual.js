// Manual-entry behaviour: the form drafts one entry at a time; "Add" appends
// it to the pending table, "Save" posts the whole table to the backend, which
// groups entries by date into one hledger transaction per day.
(function () {
  'use strict';

  var DefaultCurrency = 'EUR';

  var form = document.getElementById('entry-form');
  var feedbackHost = document.getElementById('feedback');
  var pendingBody = document.getElementById('pending-body');
  var saveBtn = document.getElementById('save-btn');
  if (!form || !pendingBody || !saveBtn) return;

  var fields = {
    date: form.elements.date,
    from: form.elements.from,
    to: form.elements.to,
    amount: form.elements.amount,
    currency: form.elements.currency,
    description: form.elements.description,
    comment: form.elements.comment
  };

  var pending = []; // drafted entries not yet saved
  var attempted = false; // has Add been pressed since the last successful add?

  function normalizeAmount(raw) {
    return raw.trim().replace(',', '.');
  }

  // Mandatory fields only — description and comment are optional.
  function invalidKeys() {
    var invalid = [];
    if (fields.date.value === '') invalid.push('date');
    if (fields.from.value.trim() === '') invalid.push('from');
    if (fields.to.value.trim() === '') invalid.push('to');
    if (fields.currency.value.trim() === '') invalid.push('currency');
    var amount = Number(normalizeAmount(fields.amount.value));
    if (fields.amount.value.trim() === '' || !isFinite(amount) || amount === 0) {
      invalid.push('amount');
    }
    return invalid;
  }

  // Invalid fields light up with a red border — but only after the first Add
  // attempt, and the border clears as soon as the user fixes the value.
  function refreshValidity() {
    var invalid = attempted ? invalidKeys() : [];
    ['date', 'from', 'to', 'amount', 'currency'].forEach(function (key) {
      fields[key].classList.toggle('is-invalid', invalid.indexOf(key) >= 0);
    });
  }

  Object.keys(fields).forEach(function (key) {
    fields[key].addEventListener('input', refreshValidity);
  });

  function setFeedback(isError, msg) {
    feedbackHost.textContent = '';
    if (msg === null) return;
    var div = document.createElement('div');
    div.className = 'alert ' + (isError ? 'alert-danger' : 'alert-success') + ' py-2';
    div.setAttribute('role', 'alert');
    div.textContent = msg;
    feedbackHost.appendChild(div);
  }

  function plural(n) {
    return n === 1 ? 'y' : 'ies';
  }

  function td(text, className) {
    var cell = document.createElement('td');
    if (className) cell.className = className;
    cell.textContent = text;
    return cell;
  }

  function renderPending() {
    pendingBody.textContent = '';

    if (pending.length === 0) {
      var row = document.createElement('tr');
      row.appendChild(td('No entries yet.', 'text-center fst-italic text-muted'));
      row.firstChild.colSpan = 8;
      pendingBody.appendChild(row);
    }

    pending.forEach(function (tx, i) {
      var row = document.createElement('tr');
      row.appendChild(td(tx.date));
      row.appendChild(td(tx.from));
      row.appendChild(td(tx.to));
      row.appendChild(td(Number(tx.amount).toFixed(2), 'text-end amount-num'));
      row.appendChild(td(tx.currency));
      row.appendChild(td(tx.description));
      row.appendChild(td(tx.comment));

      var actions = td('', 'text-end');
      var remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'btn btn-sm btn-outline-danger';
      remove.textContent = 'Remove';
      remove.addEventListener('click', function () {
        pending.splice(i, 1);
        renderPending();
      });
      actions.appendChild(remove);
      row.appendChild(actions);

      pendingBody.appendChild(row);
    });

    saveBtn.disabled = pending.length === 0;
    saveBtn.textContent = 'Save ' + pending.length + ' entr' + plural(pending.length);
  }

  form.addEventListener('submit', function (ev) {
    ev.preventDefault();
    if (invalidKeys().length > 0) {
      // Don't show a message — just light up the offending field borders.
      attempted = true;
      refreshValidity();
      return;
    }

    pending.push({
      date: fields.date.value,
      from: fields.from.value.trim(),
      to: fields.to.value.trim(),
      amount: normalizeAmount(fields.amount.value),
      currency: fields.currency.value.trim().toUpperCase(),
      description: fields.description.value.trim().toUpperCase(),
      comment: fields.comment.value.trim().toUpperCase()
    });

    // Keep date and currency so a run of similar entries is quick to add.
    var keptDate = fields.date.value;
    var keptCurrency = fields.currency.value;
    form.reset();
    fields.date.value = keptDate;
    fields.currency.value = keptCurrency || DefaultCurrency;

    attempted = false;
    refreshValidity();
    setFeedback(false, null);
    renderPending();
  });

  saveBtn.addEventListener('click', function () {
    if (pending.length === 0) return;
    var txns = pending.slice();

    // The amount is sent as a bare (unquoted-by-the-server) decimal string so
    // its scale survives — "42.50" must reach the id: dedup tag as "42.50",
    // which JSON.stringify of a JS number would collapse to "42.5".
    fetch('/api/transactions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(txns)
    }).then(function (response) {
      return response.text().then(function (body) {
        if (response.ok) {
          pending = [];
          renderPending();
          setFeedback(false, 'Saved ' + txns.length + ' entr' + plural(txns.length) + ' to the journal.');
        } else {
          setFeedback(true, 'Save failed: request failed: HTTP ' + response.status + ' / body=' + body);
        }
      });
    }).catch(function (err) {
      setFeedback(true, 'Save failed: ' + err);
    });
  });

  renderPending();
})();
