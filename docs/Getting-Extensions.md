1. **Install the latest version(or preview):** https://github.com/Suwayomi/Suwayomi-Server/releases/latest
2. Find an extensions repo, there is now no default extensions and you have to use google to find a Tachiyomi extension repo.
   - Note: The repo should look like `https://raw.githubusercontent.com/user/reponame` or `https://www.github.com/user/reponame`
3. Configure it using one of the following:
   - Suwayomi-Server option 1: With the **new launcher**, go to the `Extensions` tab and add the extensions repo.
   - Suwayomi-Server option 2: Use the **server settings** in **WebUI** to add the extensions repo.
   - Suwayomi-Server option 3: Go to the `server.conf` file in the data files and add the extensions repo.
   - Suwayomi docker container: Edit the `EXTENSION_REPOS` environment variable and add the extension repo in the format listed in the container README.