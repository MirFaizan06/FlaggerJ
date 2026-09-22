# Publishing FlaggerJ (JitPack, free)

This is the exact, minimal path to make FlaggerJ installable by anyone, worldwide, at zero cost,
via [JitPack](https://jitpack.io). JitPack builds straight from a public GitHub repository — there
is no account to create on JitPack itself, no artifact signing, and no namespace approval process.
You only need a (free) GitHub account.

## 1. Create the GitHub repository

1. Go to https://github.com/new while signed in as **MirFaizan06**.
2. Repository name: **FlaggerJ** (must match, since the JitPack coordinate embeds it).
3. Visibility: **Public** — required for JitPack to be able to build it for free.
4. Do **not** initialize with a README/`.gitignore`/license — this project already has all three
   locally, and initializing on GitHub would conflict with the push below.
5. Click **Create repository** and leave the resulting "quick setup" page open; you'll need the
   remote URL it shows you (`https://github.com/MirFaizan06/FlaggerJ.git`).

## 2. Push the local repository

From this project's root (`C:\Users\mirfa\OneDrive\Desktop\FlaggerJ`):

```powershell
git remote add origin https://github.com/MirFaizan06/FlaggerJ.git
git branch -M main
git push -u origin main
```

Git will prompt you to authenticate the first time (browser sign-in, or a Personal Access Token if
you've disabled browser auth). This is your GitHub identity, not something anyone else — including
an AI assistant — should do on your behalf.

## 3. Cut a release (this is what JitPack actually builds)

JitPack builds a specific **tag**, not just your default branch. Tag and push a release:

```powershell
git tag v1.0.0
git push origin v1.0.0
```

(Optional but nice: also turn that tag into a GitHub Release under the repo's "Releases" page —
purely cosmetic for JitPack, but gives users release notes.)

## 4. Trigger the first build

JitPack builds lazily, on first request. Open this in a browser to trigger and watch the build:

```
https://jitpack.io/#MirFaizan06/FlaggerJ/v1.0.0
```

Since this is a multi-module Maven project, JitPack will build both `flaggerj-core` and
`flaggerj-processor` and expose them individually. Expect the first build to take a minute or two;
it's cached indefinitely afterward.

## 5. What your users write

Once the build above shows green, anyone in the world can add:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.MirFaizan06.FlaggerJ</groupId>
        <artifactId>flaggerj-core</artifactId>
        <version>v1.0.0</version>
    </dependency>
</dependencies>
```

and wire `flaggerj-processor` in as an `annotationProcessorPath` the same way, using the same
`groupId`/`version` with `artifactId` `flaggerj-processor` — see the
[README's Installation section](README.md#installation) for the full snippet, including the
Gradle equivalent.

## Releasing a new version later

Bump `<version>` in the root `pom.xml` (and it cascades to both modules via the parent), commit,
then repeat steps 3–4 with the new tag (e.g. `v1.1.0`). Each tag is an independent, immutable
JitPack build — old tags keep working forever, so this is safe to do as often as you like.

## Later: moving to Maven Central

If FlaggerJ gains real adoption, Maven Central (via https://central.sonatype.com) is the natural
next step, since it needs no `<repositories>` block from consumers — it's the default repository
every build tool already trusts. It's still free but requires a verified namespace (either domain
ownership, or a GitHub-verified `io.github.mirfaizan06` groupId), a GPG signing key, and full POM
metadata (already added to this repo's `pom.xml`). Ask when you're ready and this can be scoped out
step by step.
