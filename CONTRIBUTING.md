# Contributing
## Where should I start?
Checkout [This Kanban Board](https://github.com/Suwayomi/Suwayomi-Server/projects/1) to see the rough development roadmap.

### Important notes
- Notify the developers on [Suwayomi discord](https://discord.gg/DDZdqZWaHA) (#tachidesk-server and #tachidesk-webui channels) or open a WIP pull request before starting if you decide to take on working on anything from/not from the roadmap in order to avoid parallel efforts on the same issue/feature.
- Your pull request will be squashed into a single commit.
- We hate big pull requests, make them as small as possible, change one meaningful thing. Spam pull requests, we don't mind.

### Project goals and vision
- Porting Tachiyomi and covering its features
- Syncing with Tachiyomi, [main issue](https://github.com/Suwayomi/Suwayomi-Server/issues/159)
- Generally rejecting features that Tachiyomi(main app) doesn't have,
    - Unless it's something that makes sense for desktop sizes or desktop form factor (keyboard + mouse)
    - Additional/crazy features can go in forks and alternative clients
- [Suwayomi-WebUI](https://github.com/Suwayomi/Suwayomi-WebUI) should
    - be responsive
    - support both desktop and mobile form factors well
     
## How does Suwayomi-Server work?
This project has two components: 
1. **Server:** contains the implementation of [tachiyomi's extensions library](https://github.com/tachiyomiorg/extensions-lib) and uses an Android compatibility library to run jar libraries converted from apk extensions. All this concludes to serving a GraphQL API.
2. **WebUI:** A React SPA(`create-react-app`) project that works with the server to do the presentation located at https://github.com/Suwayomi/Suwayomi-WebUI

### API
#### GraphQL
*Only available in the preview at the moment*

The GraphQL API can be queried with a POST request to `/api/graphql`. There is also the GraphiQL IDE accessible by the browser at `/api/graphql` to perform ad-hoc queries and explore the API.

#### REST
> [!WARNING]
>
> Soon to be deprecated

The REST API can be queried at `/api/v1`. An interactive Swagger API explorer is available at `/api/swagger-ui`.

### Tracker client authorization
#### OAuth
Since the url of a Suwayomi-Server is not known, it is not possible to redirect directly to the client.<br/>
Thus, to provide tracker support via oauth, the tracker clients redirect to the [suwayomi website](https://suwayomi.org/)
and there the actual redirection to the client takes place.

When implementing the login process in your client you have to make sure to follow some preconditions:

To be able to redirect to the client you have to attach a `state` object to the query of the auth url
- this `state` object has to have a `redirectUrl` which points to the client route at which you want to handle the auth result
- besides the `redirectUrl` you can pass any information you require to handle the result (e.g. the server `id` of the tracker client)
- example URL for AniList: `https://anilist.co/api/v2/oauth/authorize?client_id=ID&response_type=token&state={ redirectUrl: "http://localhost:4567/handle/oauth/result", trackerId: 1, anyOtherInfo: "your client requires" }`

Once the permission has been granted, you will get redirected to the client at the provided route (`redirectUrl`).<br/>
- Example URL (decoded) for AniList: `http://localhost:4567/handle/oauth/result?access_token=TOKEN&token_type=Bearer&expires_in=31622400&state={ redirectUrl: "http://localhost:4567/handle/oauth/result", trackerId: 1, anyOtherInfo: "your client requires" }`).<br/>

Finally, to finish the login process, you just have to pass this URL to the server as the `callbackUrl`.

## Why a web app?
This structure is chosen to
- Achieve the maximum multi-platform-ness
- Gives the ability to access Suwayomi-Server from a remote client e.g., your phone, tablet or smart TV
- Ease development of user interfaces for Suwayomi

## Building from source
### Prerequisites
You need these software packages installed in order to build the project

- Java Development Kit and Java Runtime Environment version 8 or newer(both Oracle JDK and OpenJDK works)

### building the full-blown jar (Suwayomi-Server + Suwayomi-WebUI bundle)
Run `./gradlew server:downloadWebUI server:shadowJar`, the resulting built jar file will be `server/build/Suwayomi-Server-vX.Y.Z-rxxx.jar`.

### building without `webUI` bundled (server only)
Delete `server/src/main/resources/WebUI.zip` if exists from previous runs, then run `./gradlew server:shadowJar`, the resulting built jar file will be `server/build/Suwayomi-Server-vX.Y.Z-rxxx.jar`.

### building the Windows package
First Build the jar, then cd into the `scripts` directory and run `./windows-bundler.sh win32` or `./windows-bundler.sh win64` depending on the target architecture, the resulting built zip package file will be `server/build/Suwayomi-Server-vX.Y.Z-rxxx-winXX.zip`.

## Running in development mode
run `./gradlew :server:run --stacktrace` to run the server

## Running tests
run `./gradlew :server:test` to execute all tests
to test a specific class run `./gradlew :server:test --tests <package.with.classname>`

## Building the android-jar maven repository
Run `AndroidCompat/getAndroid.sh`(macOS/Linux) or `AndroidCompat/getAndroid.ps1`(Windows)
from project's root directory to download and rebuild the jar file from Google's repository,
then use `AndroidCompat/lib/android.jar` to manually create a maven repository inside the `android-jar` git branch.
Update the dependency declaration afterwards.
