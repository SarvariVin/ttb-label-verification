# User Accounts

This page covers where accounts come from, how to add more, and how the demo sign-in works. The repository never contains a password.

There are three sources of accounts:

| Source | When it runs | Typical use |
|--------|--------------|-------------|
| Bootstrap seeding (`APP_SEED*`) | Once, on an empty database | A first specialist and two test applicants, so you can sign in straight away |
| `APP_USERS` | Every startup, idempotently | The real list of reviewers and applicants, kept in configuration |
| Demo picker (`APP_DEMO_LOGIN`) | Every page load, while switched on | Passwordless sign-in for demonstrations with fictional data |

## Bootstrap accounts

When the app starts against an **empty** database it creates three accounts, all sharing the password in `APP_SEED_PASSWORD`. If that variable isn't set, a random password is generated and printed once in the startup log.

| Account | Role | Company | Email (default) | Override with |
|---------|------|---------|-----------------|---------------|
| Labeling Specialist | Specialist | — | `specialist@example.gov` | `APP_SEED_SPECIALIST_EMAIL` |
| Test Applicant One | Applicant | Sample Distilling Co. | `applicant@example.com` | `APP_SEED_APPLICANT_EMAIL`, `APP_SEED_APPLICANT_COMPANY` |
| Test Applicant Two | Applicant | Sample Winery LLC | `applicant.two@example.com` | `APP_SEED_APPLICANT2_EMAIL`, `APP_SEED_APPLICANT2_COMPANY` |

The two applicants belong to **different companies**. That lets you check that each one sees only its own submissions and gets a 404 for the other's.

- The accounts are created once. Changing the variables later has no effect on existing accounts.
- To recover a lost password, use `APP_SEED_RESET_PASSWORD`, which resets all three ([deploy-railway.md](deploy-railway.md#lost-or-unknown-bootstrap-password)).
- In production, set `APP_SEED=false` so an empty database is never seeded with a known password.

## More accounts: `APP_USERS`

Put any number of specialists and applicants into a single variable holding a JSON array:

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
| `role` | yes | `SPECIALIST` or `APPLICANT`, in any case |
| `email` | yes | The sign-in name. It must be unique. |
| `name` | no | The display name. Defaults to the email. |
| `company` | applicants | The applicant's company. It is matched ignoring case and created if it doesn't exist, so several users can share one company. |
| `passwordHash` | one of the two | **Preferred.** A Spring Security encoded hash with its id prefix, such as `{bcrypt}$2a$10$…`. The variable then holds nothing anyone could sign in with. |
| `password` | one of the two | Plain text of at least 12 characters, hashed at startup. Anyone who can read the variable can read the password. |

The value may also be Base64-encoded JSON, which is useful when a dashboard mangles `$` characters.

### What happens at each startup

- An account that doesn't exist yet is **created**.
- An account that already exists keeps its data. Its **password is updated** only if the declared one is different, so you rotate a password by editing the variable and restarting.
- An existing account's **role is never changed**. A warning is logged instead.
- A bad entry (invalid email, unknown role, password under 12 characters, hash without an `{id}` prefix) is **skipped** and logged. The other entries still apply.
- A summary line is logged, for example `APP_USERS: 6 created, 0 password(s) updated, 0 skipped, 6 declared.` Passwords and hashes never appear in logs, and malformed JSON is reported only by line and column.
- Taking an entry out of the variable does **not** delete the account.

### Making a bcrypt hash

You need Java 21 and the project's dependencies downloaded (run `./mvnw package` once):

```bash
CP=$(find ~/.m2/repository/org/springframework/security/spring-security-crypto -name "*.jar" | tail -1):$(find ~/.m2/repository/org/springframework/spring-jcl -name "*.jar" | tail -1)
```

```bash
printf 'public class H { public static void main(String[] a){ System.out.println(org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(a[0])); } }' > /tmp/H.java && java -cp "$CP" /tmp/H.java 'the-new-password'
```

Copy the output, which begins with `{bcrypt}`, into `passwordHash`.

### Setting it on Railway

Open the app service's **Variables**, add `APP_USERS` with the JSON array as its value (a single line is fine), and **Deploy**. The `APP_USERS:` summary line in the deploy log confirms what was applied.

## Demo mode: choosing an account on the login page

For demonstrations, the **Email** field on the login page can turn into a picker. Each entry shows initials, name, email and a Specialist or Applicant badge.

> [!IMPORTANT]
> **Signing in with demo mode**
> 1. Click the **Email** field, and the **Demo accounts** list opens.
> 2. **Pick a user.**
> 3. The **email and password fill in by themselves**. There's nothing to type.
> 4. **Sign in** is highlighted and focused, so press Enter or click it.

| Variable | Default | Effect |
|---|---|---|
| `APP_DEMO_LOGIN` | `false` | `true` turns the Email field into the picker |
| `APP_DEMO_LOGIN_ACCOUNTS` | *(every account)* | A comma-separated list of the emails to offer, for example `specialist@example.gov,applicant@example.com` |

- **While this is on, anyone who can reach the site can sign in as any listed account.** Use it only with fictional data, and remove the variable for real use.
- Only password hashes are stored, so no real password ever reaches the page. The password box shows a masked placeholder, and **Sign in** goes to the demo sign-in endpoint.
- If you then type a different email or password, the form switches back to a normal password check, so regular sign-in still works.
- The list opens on focus or click, filters as you type, and supports ↑/↓, Enter and Esc. Screen readers announce it as a combobox.
- Each startup logs a `DEMO LOGIN IS ENABLED …` warning. Demo sign-in is CSRF-protected and issues a fresh session id.
- Without JavaScript, a plain fallback form appears instead.
