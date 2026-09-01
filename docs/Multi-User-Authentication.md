# Multi-User Authentication

Suwayomi supports a multi-user mode in which each person on the server has their own account, their own library, their own downloads, and their own per-user settings. This page explains how to enable it, how accounts work, and what the security posture is.

Multi-user mode is gated by the `ui_login` authentication mode. In all other modes (`none`, `basic_auth`, `simple_login`) there is effectively a single admin account and the per-user features are inert.

## Enabling multi-user mode

Set the authentication mode to `ui_login` in `server.conf`:

```
server.authMode = "ui_login"
```

Restart the server. With `ui_login` enabled:

- The UI (e.g. WebUI) shows a login page. Login is handled by the `login` GraphQL mutation, which returns a JWT access token and a refresh token.
- Every request authenticates via the `Authorization: Bearer <token>` header (or the `suwayomi-server-token` cookie / `?token=` query param as fallbacks).
- Data is scoped per account: each user sees only their own library, chapters, categories, downloads, and settings.

The `jwt*` settings tune session duration:

```
server.jwtAudience = "suwayomi-server-api"   # embedded in every token; changing it logs out all users
server.jwtTokenExpiry = "5m"                 # access token lifetime
server.jwtRefreshExpiry = "60d"              # refresh token lifetime
```

## Default admin account

On first start, migration `M0063` seeds the built-in admin account (user id `1`):

| Field | Value |
|---|---|
| Username | `admin` |
| Password | `password` |

**This is a security-sensitive default.** If you run the server on a network other people can reach, change the admin password immediately (via the UI or the `setPassword` mutation, which requires the current password). The built-in admin (user `1`) cannot be modified or demoted through the API — it is fixed as the `ADMIN` role.

## Creating accounts

There are three ways to create an account. All require the `MANAGE_USERS` permission (i.e. an admin) except where noted.

### 1. Admin registers a user directly

Use the `register` mutation (requires `MANAGE_USERS`). The new account gets the default permissions and the `USER` role.

### 2. Registration codes (controlled self-signup)

An admin issues a one-time registration code with `createRegistrationCode` (requires `MANAGE_USERS`). The code is returned in plaintext exactly once and is handed to the user out-of-band.

- A registration code is valid for **7 days**.
- Anyone (even a logged-out visitor) can redeem it with the `redeemRegistrationCode` mutation by providing the code, a chosen username, and a chosen password. This creates the account (default permissions + `USER` role) and returns a JWT so the user is logged in immediately.
- Registration codes may stack (an admin can issue several). An admin can list outstanding codes (`userCodes`) and revoke any of them (`revokeUserCode`).

### 3. Recovery codes (password recovery)

A user who forgets their password has no way back in on their own (`setPassword` requires the old password and there is no email infrastructure on a self-hosted server). An admin issues a one-time recovery code with `createRecoveryCode` (requires `MANAGE_USERS`), bound to the target user.

- A recovery code is valid for **24 hours**.
- Only **one active recovery code per user** — issuing a new one consumes the outstanding one.
- Anyone (even a logged-out visitor) can redeem it with the `redeemRecoveryCode` mutation by providing the code and a new password. This sets the new password, invalidates all of the account's sessions (see below), and returns a JWT so the user is logged in immediately. The admin never learns the new password.
- User `1` (the built-in admin) is a valid recovery target. If user `1` is the only admin and is locked out, there is no API path back in — an out-of-band database edit is required.

All redemption failures (not found, expired, already consumed, revoked) return the same uniform error ("Invalid or expired code") so the API never leaks which state a code is in.

## Permissions

Accounts are granted fine-grained permissions. The `ADMIN` role (user `1`) bypasses all permission checks. For other accounts, each permission gates a set of operations:

| Permission | Gates |
|---|---|
| `INSTALL_EXTENSIONS` | Installing extensions from the store |
| `INSTALL_EXTERNAL_EXTENSIONS` | Installing untrusted extensions (`installExternalExtension`) |
| `UNINSTALL_EXTENSIONS` | Uninstalling extensions |
| `DOWNLOAD_CHAPTERS` | Enqueueing chapter downloads |
| `ACCESS_NSFW` | Viewing/fetching NSFW sources and extensions |
| `MANAGE_SETTINGS` | Reading/writing global server settings |
| `MANAGE_USERS` | Registering users, listing users, granting/revoking permissions, setting roles, issuing/redeeming user codes |
| `MANAGE_EXTENSION_STORES` | Adding/removing extension stores |
| `MANAGE_SOURCE_PREFERENCES` | Modifying global ConfigurableSource preferences |
| `MANAGE_CACHE` | Clearing cached images and webview cookies/cache |

### Default permissions for new accounts

**Important security posture:** a newly created account (via `register` or a registration code) is granted, by default:

```
INSTALL_EXTENSIONS
UNINSTALL_EXTENSIONS
DOWNLOAD_CHAPTERS
ACCESS_NSFW
```

This means a self-registered user can install **untrusted** `.jar` extensions and can view **NSFW** content. This is intentional (it matches the reader's expected capabilities) but it is a deliberate security choice: in a multi-user deployment, treat all accounts as trusted with respect to extension installation and NSFW access. An admin can trim or expand an account's grants with the `updateUser` mutation.

An admin can change an account's permissions and role with `updateUser` (requires `MANAGE_USERS`):

- `permissions` (non-null) replaces the account's full permission set.
- `role` (non-null) replaces the account's single role (`USER` or `VISITOR`). The `ADMIN` role cannot be granted, and user `1` cannot be modified.

Note: permission and role changes take effect at the target account's next access-token refresh (up to `jwtTokenExpiry`), because the claims are embedded in the JWT.

## Session invalidation

Accounts use stateless JWTs (HMAC, no token store). To let the server revoke a session (e.g. after a password change), each account has a `SESSION_VERSION` counter that is embedded in every access and refresh token as the `token_version` claim.

- Changing a password — either the account's own `setPassword` or redeeming a recovery code — bumps the `SESSION_VERSION`.
- Once bumped, **all previously issued access and refresh tokens for that account are rejected** (the `token_version` no longer matches). The account is logged out everywhere.
- Freshly issued tokens (after the change) verify normally.

This means a password change is a full logout: the account must log in again with the new password.

## Per-user vs global settings

Most behavioral settings are **per-user** (each account can override them); a smaller set remains **global** (server-level policy).

### Per-user settings

Accounts read and write their own settings through the `userSettings` query and `setUserSettings` / `resetUserSettings` mutations. An account's override is independent of every other account; `resetUserSettings` clears an account's overrides back to the defaults.

## Downloads (reference-counted, shared)

Downloads are shared on disk but tracked per user:

- Each user has their own download **intent**.
- The actual file state is **global**: a chapter's file is kept as long as **at least one** user has intent for it.
- A user only sees their own intent in the UI. Deleting a download clears that user's intent; the file is only removed when no user has intent anymore.

## Automated backups (one file per user)

Automated backups produce **one backup file per user**, named `org.suwayomi.tachidesk.auto.user<id>_<date>.tachibk`, in the shared automated-backup directory. Each file contains only that user's library and per-user settings. A failure for one user does not stop the others.

## Known limitations

The following are **not** handled by the multi-user work (documented so they are not mistaken for bugs):

- **NSFW checks on content-fetch operations** (`fetchManga` / `fetchMangaAndChapters`, `fetchChapters` / `fetchChapterPages`) are not enforced. NSFW is enforced on the source/extension surface (lists and singular lookups) and on `fetchSourceManga`.
- **page-image and CBZ endpoints** serve content without an ownership/NSFW check; a non-NSFW user could read page content of NSFW manga another user added.
- **Shared download queue control** (`start`/`stop`/`clear`/`reorder`/`dequeue`) operates on the shared queue and is not per-user gated.
- **`updateStop`** cancels all users' library-update jobs (a per-user reset would be invasive); known limitation.
