# Contributing

Thanks for helping! Bug reports and pull requests are welcome.

## Reporting a bug
Use the **Bug report** issue template. Please include your Minecraft version, Fabric Loader and Fabric API versions, the mod jar you used and the log (`logs/latest.log`).

## Code
- One folder per Minecraft version: the root folder is 1.21.4, the others are `mc-<version>`. 26.x folders use Mojang names, the older ones use Yarn.
- Keep the code the same across versions where possible. Version-specific changes should be small.
- Every push builds all versions with GitHub Actions. A pull request must keep all builds green.
- The mod must never attack players, break blocks, place blocks or send commands. Pull requests that add anything meant to hide the mod from servers or anti-cheat will be closed.

## Build locally
Open a version folder and run `gradle build` (Java 17 for 1.20.4, Java 21 for 1.20.6-1.21.11, Java 25 for 26.x).
