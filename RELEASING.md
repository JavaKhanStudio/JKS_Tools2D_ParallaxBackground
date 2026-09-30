# Branches, versions and releases

## Branches

| Branch        | What it holds                                                                       |
|---------------|--------------------------------------------------------------------------------------|
| `develop`     | Day-to-day work. Always on a `-SNAPSHOT` version. Every push publishes that snapshot. |
| `release/X.Y` | Stabilising a version: only fixes, no new features. Created from `develop`.           |
| `master`      | What has been released. Every release is tagged here (`vX.Y.Z`).                      |

Feature work goes on `feature/...` branches merged into `develop` (directly or through a pull request). CI builds
every push and pull request on JDK 17, 21 and 25.

## Test versions (snapshots)

Every push to `develop` publishes the library, and the jMonkeyEngine reader (`parallax-background-jme`), as a snapshot,
under the `version` in `develop`'s `gradle.properties` (`X.Y.Z-SNAPSHOT`). A game can use it with:

```groovy
repositories {
    mavenCentral()
    maven { url "https://central.sonatype.com/repository/maven-snapshots/" }
}
dependencies {
    implementation "io.github.javakhanstudio:parallax-background:X.Y.Z-SNAPSHOT"
}
```

This is how a fix reaches a game: push it to `develop`, and the game takes the snapshot. A release is for a version
that should stay on Maven Central for good, not for each fix.

`X.Y.Z-SNAPSHOT` moves with every push, so a game built twice can get two different libraries. To keep a game's build
reproducible, pin one snapshot build instead, by its timestamped version:

```groovy
implementation "io.github.javakhanstudio:parallax-background:2.5.0-20260926.092334-1"
```

The builds of a snapshot are listed in
`https://central.sonatype.com/repository/maven-snapshots/io/github/javakhanstudio/parallax-background/X.Y.Z-SNAPSHOT/maven-metadata.xml`
(the `<value>` entries), and the Publish snapshot run of the push that made one is in the Actions tab. Central deletes
snapshot builds after 90 days: a game still on one then needs a newer snapshot or a release.

`./gradlew -p tools/snapshot-probe resolve` resolves the current snapshot from Central the way a game does
(`-Pparallax=<version>` for one pinned build).

To try a change without publishing anything, `./gradlew :core:publishToMavenLocal` (and `:jme:publishToMavenLocal`
for the jME reader) and add `mavenLocal()` to the game's repositories. `./gradlew -p tools/jme-probe run` is such a
game: a headless jME app that takes `parallax-background-jme` from `mavenLocal()` (or Central) and loads and
scrolls a demo page with it (no pixels: jME's null renderer), then prints OK.

## Releasing X.Y.Z

A library is refactored before it is released (Simon, atelier d40): the last refacto day must be at most 2 days old.
Check it first:

```bash
atelier --project parallax upkeep release        # exit 0: go on; exit 1 prints why (never run, or ran N days ago)
```

On a refusal, run a refacto day (`atelier upkeep run refacto`), and release once its tickets are closed.

```bash
git checkout develop && git pull
git checkout -b release/2.1                      # stabilise, only fixes from here

# set the final version
sed -i 's/^version=.*/version=2.1.0/' gradle.properties
# and the version in README.md's "Get it" snippets (Groovy, Kotlin, Maven) and "In jMonkeyEngine 3" snippet:
# it is the published front page
git commit -am "Prepare 2.1.0"
git push -u origin release/2.1                   # CI builds it

git checkout master && git merge --no-ff release/2.1
git tag -a v2.1.0 -m "2.1.0"
git push origin master --follow-tags             # the tag starts the Release workflow

# back to develop for the next version
git checkout develop
git merge --no-ff master
sed -i 's/^version=.*/version=2.2.0-SNAPSHOT/' gradle.properties
git commit -am "Start 2.2.0"
git push
```

Once the release is out, update the Godot Asset Library listing to it (below, "Godot Asset Library").

The release workflow refuses to run if the tag and `gradle.properties` disagree, so the version in the repository
always matches what was published. A tag with a suffix (`v2.1.0-rc1`) is published as a GitHub pre-release.

Each release publishes:

- the library to Maven Central: `io.github.javakhanstudio:parallax-background`;
- the jMonkeyEngine reader (`engines/jme/src`) to Maven Central, under the same version:
  `io.github.javakhanstudio:parallax-background-jme`. Its classes are public API: keep them compatible;
- a GitHub release with the downloads: the library jars (+ sources, + javadoc), the jME reader's jars,
  `ParallaxEditor-X.Y.Z.zip` (the editor with the library and the sample projects) and `jks-parallax-godot-X.Y.Z.zip`
  (the Godot reader, `addons/jks_parallax/` at its root, made by `tools/godot-addon-zip.sh`).

## Godot Asset Library

The Godot reader (`engines/godot/addons/jks_parallax`) is published on the Godot Asset Library
(https://godotengine.org/asset-library), versioned with the Maven release: listing version X.Y.Z is the
`jks-parallax-godot-X.Y.Z.zip` of GitHub release `vX.Y.Z` (atelier d11). The listing downloads that zip rather than
the repository, because the repository's addon sits under `engines/godot/` next to the Java code, and a game would get
all of it.

**Each release**, after the Release workflow has attached the zip: sign in, open the asset → *Edit*, and set

| Field | Value |
|-------|-------|
| Version string | `X.Y.Z` |
| Godot version | the lowest 4.x the reader was checked with (`GODOT_VERSION` in `ci.yml`'s `godot-frames` job) |
| Download commit | `https://github.com/JavaKhanStudio/JKS_Tools2D_ParallaxBackground/releases/download/vX.Y.Z/jks-parallax-godot-X.Y.Z.zip` |

With the *Custom* repository host, the *Download commit* field is the download URL itself: the whole zip URL, ending
in `.zip`, not a hash or a tag (the Asset Library's `Utils::getComputedDownloadUrl` returns it as is, and suggests
Custom for GitHub Releases downloads).

The edit waits for a moderator before the new version shows. Skip a pre-release (`v2.1.0-rc1`): the listing only takes
releases.

**Once, to create the listing** (the Godot account is Simon's): *Submit Assets* with

- Title `JKS Parallax`, category *2D Tools*, license *Apache-2.0*;
- Repository host *Custom*, browse URL `https://github.com/JavaKhanStudio/JKS_Tools2D_ParallaxBackground`, issues URL
  its `/issues`, and the fields of the table above;
- Icon URL `https://raw.githubusercontent.com/JavaKhanStudio/JKS_Tools2D_ParallaxBackground/master/editor/assets/skins/uis/parallaxIcon.png`;
- the description: the README's *In Godot 4* section, and screenshots of a page in Godot.

`tools/godot-addon-zip-check.sh` installs the zip into a blank Godot project, loads a page from `res://` and saves a
frame (`build/godot-addon/check.png`): run it before a first submission, or after the addon's files change.

## One-time setup: Maven Central credentials

Until these secrets exist (`SIGNING_KEY_PASSWORD` may be empty), a tag push makes the release workflow **fail** before
building anything, so a release can never look green without being on Maven Central. To release the GitHub downloads
only, run the Release workflow by hand (Actions → Release → Run workflow), pick the tag, and tick `without_central`:
the run summary and the release notes then say it is not on Maven Central. Snapshot builds on `develop` do not fail;
they skip publishing with a warning in the run summary.

The release publishes with `publishAndReleaseToMavenCentral`, which releases the deployment.
`publishToMavenCentral` alone (as in "Publishing by hand" below) only uploads it: it then waits for a click on
**Publish** under Deployments in the Central Portal, and nothing reaches Maven Central until then.

1. **Create a Central Portal account** at https://central.sonatype.com (sign in with GitHub).
2. **Claim the namespace** `io.github.javakhanstudio`: Namespaces → Add Namespace → `io.github.javakhanstudio`.
   Signing in with the GitHub account verifies it automatically; otherwise the portal asks you to create a public
   repository whose name is a verification code.
3. **Generate a user token**: Account → Generate User Token. It gives a username and a password.
4. **Create a signing key** (Maven Central requires signed artifacts):

   ```bash
   gpg --quick-generate-key "Simon Bedard <JavaKhanStudio@Gmail.com>" rsa4096 sign 2y
   gpg --list-secret-keys --keyid-format=long              # note the key id
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>   # publish it, Central checks
   gpg --armor --export-secret-keys <KEY_ID> > private-key.asc # keep this file safe, it is a secret
   ```

5. **Add the secrets** to the repository (Settings → Secrets and variables → Actions), or from the terminal:

   ```bash
   gh secret set MAVEN_CENTRAL_USERNAME    # the token username from step 3
   gh secret set MAVEN_CENTRAL_PASSWORD    # the token password from step 3
   gh secret set SIGNING_KEY < private-key.asc
   gh secret set SIGNING_KEY_PASSWORD      # the passphrase of the key, empty if none
   rm private-key.asc
   ```

The first publication to a new namespace can take a few minutes to appear on https://central.sonatype.com, and up to
an hour before `mavenCentral()` serves it.

## Publishing by hand

Rarely needed, the workflows do it, but with the same four values in the environment:

```bash
ORG_GRADLE_PROJECT_mavenCentralUsername=... \
ORG_GRADLE_PROJECT_mavenCentralPassword=... \
ORG_GRADLE_PROJECT_signingInMemoryKey="$(cat private-key.asc)" \
ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=... \
./gradlew :core:publishToMavenCentral :jme:publishToMavenCentral --no-configuration-cache
```
