# User Accounts

How accounts are created and managed. No passwords are stored in the repository.

## Bootstrap accounts

On an **empty** database the app creates one specialist and one applicant (`APP_SEED_*` variables; see [deploy-railway.md](deploy-railway.md#5-first-sign-in)). They are created once. To recover a lost password, use `APP_SEED_RESET_PASSWORD` ([deploy-railway.md](deploy-railway.md#lost-or-unknown-bootstrap-password)).

## Additional accounts: `APP_USERS`

Declare any number of specialists and applicants in one variable holding a JSON array:

```json
[
  {"role": "SPECIALIST", "email": "reviewer@agency.example.gov", "name": "A. Reviewer",
   "passwordHash": "{bcrypt}$2a$10$…"},
  {"role": "APPLICANT", "email": "owner@winery.example.com", "name": "B. Owner",
   "company": "Example Winery", "passwordHash": "{bcrypt}$2a$10$…"}
]
```

| Field | Required | Notes |
|---|---|---|
| `role` | yes | `SPECIALIST` or `APPLICANT` (case-insensitive) |
| `email` | yes | Sign-in name; must be unique |
| `name` | no | Display name; defaults to the email |
| `company` | applicants | The applicant's company. It is created if missing and matched case-insensitively, so several users can share one company |
| `passwordHash` | one of these | **Preferred.** A Spring Security encoded hash including its id, e.g. `{bcrypt}$2a$10$…`. The variable then holds no usable password. |
| `password` | one of these | Plain text, at least 12 characters, hashed at startup. Anyone who can read the variable can read it. |

### Behavior on every startup

- A missing account is **created**.
- An existing account keeps its data. Its **password is updated** if the declared one differs, so edit the variable and restart to rotate a password.
- The role of an existing account is **not** changed; a warning is logged.
- Invalid entries (bad email, unknown role, password under 12 characters, hash without `{id}`) are **skipped** with a log message. Other entries still apply.
- A summary is logged, e.g. `APP_USERS: 6 created, 0 password(s) updated, 0 skipped, 6 declared.` Passwords and hashes are never logged, and invalid JSON is reported by line and column only.
- Removing an entry does **not** delete the account.

### Generating a bcrypt hash

With Java 21 and the project's dependencies downloaded (`./mvnw package` once):

```bash
CP=$(find ~/.m2/repository/org/springframework/security/spring-security-crypto -name "*.jar" | tail -1):$(find ~/.m2/repository/org/springframework/spring-jcl -name "*.jar" | tail -1)
```

```bash
printf 'public class H { public static void main(String[] a){ System.out.println(org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(a[0])); } }' > /tmp/H.java && java -cp "$CP" /tmp/H.java 'the-new-password'
```

Paste the output, which starts with `{bcrypt}`, into `passwordHash`.

### On Railway

In the app service's **Variables**, add `APP_USERS` with the JSON array as the value (one line is fine), then **Deploy**. Check the log for the `APP_USERS:` summary line.

## Demo mode: pick an account on the login page

For demonstrations, the login page's **Email** field can open a list of demo accounts, each with initials, name, email and a Specialist or Applicant badge.

> [!IMPORTANT]
> **How to sign in with demo mode:**
> 1. Click the **Email** field. The **Demo accounts** list opens.
> 2. **Select a user.**
> 3. The **email and password load automatically**. There's nothing to type.
> 4. **Sign in** is highlighted and focused, so press Enter or click it.

| Variable | Default | Effect |
|---|---|---|
| `APP_DEMO_LOGIN` | `false` | `true` turns the Email field into a demo-account picker |
| `APP_DEMO_LOGIN_ACCOUNTS` | *(all accounts)* | Comma-separated emails to offer, e.g. `specialist2@example.gov,applicant2@example.com` |

- **Anyone who can reach the site can sign in as any listed account while this is on.** Use it only with fictional data, and remove the variable for real use.
- The app stores only password hashes, so real passwords are never put in the page. The password box gets a masked placeholder, and **Sign in** uses the demo sign-in endpoint.
- Typing a different email or password afterwards switches back to a normal password check, so regular sign-in keeps working.
- The list opens on focus or click, filters as you type, and supports ↑/↓, Enter and Esc. Screen readers announce it as a combobox.
- Every startup logs `DEMO LOGIN IS ENABLED …` as a WARN. Demo sign-in is CSRF-protected and gets a fresh session id.
- Without JavaScript, a simple fallback form appears instead.
