// Progressive enhancement only — every page works without JavaScript.
(function () {
  'use strict';

  // "Select all" for the batch-approve table.
  document.querySelectorAll('[data-select-all]').forEach(function (master) {
    master.addEventListener('change', function () {
      var name = master.getAttribute('data-select-all');
      document.querySelectorAll('input[type=checkbox][name="' + name + '"]').forEach(function (cb) {
        cb.checked = master.checked;
      });
    });
  });

  // Confirm destructive or bulk actions.
  document.querySelectorAll('form[data-confirm]').forEach(function (form) {
    form.addEventListener('submit', function (e) {
      if (!window.confirm(form.getAttribute('data-confirm'))) {
        e.preventDefault();
      }
    });
  });

  // Hovering a field row highlights its bounding box on the label image.
  document.querySelectorAll('[data-field-item]').forEach(function (row) {
    var id = row.getAttribute('data-field-item');
    var rect = document.getElementById('bbox-' + id);
    if (!rect) return;
    row.addEventListener('mouseenter', function () { rect.classList.add('highlight'); });
    row.addEventListener('mouseleave', function () { rect.classList.remove('highlight'); });
  });

  // Disable submit buttons while the (synchronous) AI analysis runs.
  document.querySelectorAll('form[data-busy]').forEach(function (form) {
    form.addEventListener('submit', function () {
      var btn = form.querySelector('button[type=submit]');
      if (btn) {
        btn.disabled = true;
        btn.textContent = form.getAttribute('data-busy');
      }
    });
  });

  // Pre-fill: read the chosen label images and fill empty form fields.
  var form = document.getElementById('submission-form');
  if (form && form.getAttribute('data-prefill-url')) {
    var input = document.getElementById('images');
    var button = document.getElementById('prefill-button');
    var status = document.getElementById('prefill-status');
    var notice = document.getElementById('prefill-notice');

    var readLabel = function () {
      if (!input.files || input.files.length === 0) {
        return;
      }
      var data = new FormData();
      Array.prototype.forEach.call(input.files, function (f) { data.append('images', f); });
      var csrf = form.querySelector('input[name="_csrf"]');
      var headers = { 'Accept': 'application/json' };
      if (csrf) {
        data.append('_csrf', csrf.value);
        headers['X-CSRF-TOKEN'] = csrf.value;
      }
      status.textContent = 'Reading label…';
      button.disabled = true;
      fetch(form.getAttribute('data-prefill-url'), { method: 'POST', body: data, headers: headers, credentials: 'same-origin' })
        .then(function (r) { return r.json().then(function (body) { return { ok: r.ok, body: body }; }); })
        .then(function (res) {
          if (!res.ok) {
            status.textContent = res.body.error || 'The label could not be read. Enter the values manually.';
            return;
          }
          var filled = applyPrefill(res.body);
          status.textContent = filled > 0
            ? 'Filled ' + filled + ' field(s) from the label in ' + res.body.processingTimeMs + ' ms. Review them below.'
            : 'No text could be recognised on this image. Enter the values manually.';
          if (filled > 0 && notice) { notice.hidden = false; }
        })
        .catch(function () { status.textContent = 'The label could not be read. Enter the values manually.'; })
        .then(function () { button.disabled = false; });
    };

    var setIfEmpty = function (name, value) {
      if (value === null || value === undefined || value === '') { return 0; }
      var el = form.querySelector('[name="' + name + '"]');
      if (!el || (el.type !== 'checkbox' && el.value && el.value.trim() !== '')) { return 0; }
      if (el.type === 'checkbox') {
        if (el.checked) { return 0; }
        el.checked = !!value;
      } else {
        el.value = value;
      }
      el.classList.add('prefilled');
      el.addEventListener('input', function () { el.classList.remove('prefilled'); }, { once: true });
      return 1;
    };

    var applyPrefill = function (p) {
      var n = 0;
      n += setIfEmpty('beverageType', p.beverageType);
      n += setIfEmpty('containerSizeMl', p.containerSizeMl);
      n += setIfEmpty('sulfiteDeclaration', p.sulfiteDeclaration);
      Object.keys(p.fields || {}).forEach(function (k) { n += setIfEmpty(k, p.fields[k]); });
      return n;
    };

    input.addEventListener('change', readLabel);
    button.addEventListener('click', readLabel);
    input.addEventListener('change', function () { button.disabled = !input.files || input.files.length === 0; });
  }

  // Demo mode on the login page: the email field is a combobox listing demo accounts.
  // Choosing one fills the email and a masked placeholder password and routes "Sign in" to the
  // demo endpoint. The server only holds password hashes, so no real password reaches the
  // browser. Editing either field afterwards switches back to a normal password check.
  var loginForm = document.getElementById('login-form');
  var panel = document.getElementById('demo-panel');
  if (loginForm && panel && loginForm.getAttribute('data-demo-action')) {
    var normalAction = loginForm.getAttribute('action');
    var demoAction = loginForm.getAttribute('data-demo-action');
    var emailInput = document.getElementById('email');
    var passwordInput = document.getElementById('password');
    var signIn = document.getElementById('sign-in');
    var options = Array.prototype.slice.call(panel.querySelectorAll('[role=option]'));
    var active = -1;
    var filling = false;

    var visible = function () { return options.filter(function (o) { return !o.hidden; }); };
    var setActive = function (opt) {
      options.forEach(function (o) { o.classList.remove('active'); o.setAttribute('aria-selected', 'false'); });
      if (opt) {
        opt.classList.add('active');
        opt.setAttribute('aria-selected', 'true');
        emailInput.setAttribute('aria-activedescendant', opt.id);
        opt.scrollIntoView({ block: 'nearest' });
      } else {
        emailInput.removeAttribute('aria-activedescendant');
      }
    };
    var open = function () {
      if (visible().length === 0) { close(); return; }
      panel.hidden = false;
      emailInput.setAttribute('aria-expanded', 'true');
    };
    var close = function () {
      panel.hidden = true;
      emailInput.setAttribute('aria-expanded', 'false');
      active = -1;
      setActive(null);
    };
    var filter = function () {
      var q = emailInput.value.trim().toLowerCase();
      options.forEach(function (o) {
        var hay = (o.getAttribute('data-name') + ' ' + o.getAttribute('data-email')).toLowerCase();
        o.hidden = q !== '' && hay.indexOf(q) === -1;
      });
      active = -1;
      setActive(null);
    };
    var placeholder = function () {
      var bytes = new Uint8Array(12);
      window.crypto.getRandomValues(bytes);
      return 'demo-' + Array.prototype.map.call(bytes, function (b) { return ('0' + b.toString(16)).slice(-2); }).join('');
    };
    var useNormalLogin = function () {
      loginForm.setAttribute('action', normalAction);
      emailInput.classList.remove('prefilled');
      passwordInput.classList.remove('prefilled');
    };
    var choose = function (opt) {
      filling = true;
      emailInput.value = opt.getAttribute('data-email');
      passwordInput.value = placeholder();
      filling = false;
      emailInput.classList.add('prefilled');
      passwordInput.classList.add('prefilled');
      loginForm.setAttribute('action', demoAction);
      close();
      // Both fields are filled: focus Sign in so pressing Enter signs in.
      signIn.classList.add('ready');
      signIn.focus();
    };
    signIn.addEventListener('blur', function () { signIn.classList.remove('ready'); });

    options.forEach(function (o) {
      // mousedown (not click) so the email field's blur doesn't close the panel first
      o.addEventListener('mousedown', function (e) { e.preventDefault(); choose(o); });
    });
    emailInput.addEventListener('focus', function () { filter(); open(); });
    emailInput.addEventListener('click', function () { filter(); open(); });
    emailInput.addEventListener('blur', close);
    emailInput.addEventListener('input', function () {
      if (!filling && loginForm.getAttribute('action') === demoAction) {
        passwordInput.value = '';
        useNormalLogin();
      }
      filter();
      open();
    });
    passwordInput.addEventListener('input', function () {
      if (!filling && loginForm.getAttribute('action') === demoAction) { useNormalLogin(); }
    });
    emailInput.addEventListener('keydown', function (e) {
      var list = visible();
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault();
        if (panel.hidden) { filter(); open(); list = visible(); }
        if (list.length === 0) { return; }
        active = e.key === 'ArrowDown' ? (active + 1) % list.length : (active - 1 + list.length) % list.length;
        setActive(list[active]);
      } else if (e.key === 'Enter' && !panel.hidden && active >= 0 && list[active]) {
        e.preventDefault();
        choose(list[active]);
      } else if (e.key === 'Escape' && !panel.hidden) {
        e.preventDefault();
        close();
      } else if (e.key === 'Enter' && panel.hidden) {
        // With the list closed, Enter moves on to the password instead of submitting.
        e.preventDefault();
        passwordInput.focus();
      }
    });
  }

  // Login: show a spinner while signing in and prevent double submission.
  var loginFormForBusy = document.getElementById('login-form');
  var loginSubmit = document.getElementById('sign-in');
  if (loginFormForBusy && loginSubmit) {
    loginFormForBusy.addEventListener('submit', function () {
      loginSubmit.classList.remove('ready');
      loginSubmit.classList.add('busy');
      loginSubmit.setAttribute('aria-busy', 'true');
      var label = loginSubmit.querySelector('.login-submit-label');
      if (label) { label.textContent = 'Signing in…'; }
      window.setTimeout(function () { loginSubmit.disabled = true; }, 0);
    });
  }
})();
