1. **Install the latest version(or preview):** https://github.com/Suwayomi/Suwayomi-Server/releases/latest
2. Find an extension store, there is now no default extensions, and you have to use Google to find a Tachiyomi extension repo.
   - Note: the store URL must point to an **index file** of the repo, not the repository page itself — e.g. `https://raw.githubusercontent.com/user/reponame/branch/index.min.json` (JSON index) or `index.pb` (protobuf index). A bare repo URL like `https://www.github.com/user/reponame` returns HTML and cannot be parsed.
3. Configure it using one of the following:
   - Suwayomi-Server option 1: With the **new launcher**, go to the `Extensions` tab and add the extensions repo.
   - Suwayomi-Server option 2: Use the **server settings** in **WebUI** to add the extensions repo.
   - Suwayomi-Server option 3: Go to the `server.conf` file in the data files and add the extensions repo.
   - Suwayomi docker container: Edit the `EXTENSION_STORES` environment variable and add the extension repo in the format listed in the container README.

## Extension signatures and trust

Extension packages downloaded from a store are verified before installation:

- **APK extensions must be signed.** An unsigned APK, or one whose signature cannot be verified, is refused.
- **`signing_key` in a store index** declares the fingerprint of the key the store operator signs extensions with. When a store provides it, packages from that store must be signed by that key — a different signature is rejected. This is a trust anchor configured by whoever added the store, so only add stores you trust.
- **Trust on first use (TOFU).** Extensions installed before signature tracking existed (or installed from a source without a recorded signature) carry no recorded fingerprint; the first update that is signed records it. From then on the same signer is required.
- **Signature continuity.** An update whose signature differs from the recorded one — or an unsigned package replacing a previously signed extension — is refused. If a package legitimately changed signer, uninstall the extension first (uninstalling clears the recorded signature) and reinstall it.
- **Unsigned `.jar` packages** cannot carry an Android signature and are not signature-checked. They are gated only by the extension-install permission (`INSTALL_EXTERNAL_EXTENSIONS` for manual uploads). Only install jars you trust.
