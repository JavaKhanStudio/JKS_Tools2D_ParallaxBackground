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

Every push to `develop` publishes the library as a snapshot. A game can use it with:

```groovy
repositories {
    mavenCentral()
    maven { url "https://central.sonatype.com/repository/maven-snapshots/" }
}
dependencies {
    implementation "io.github.javakhanstudio:parallax-background:2.1.0-SNAPSHOT"
}
```

To try a change without publishing anything, `./gradlew :core:publishToMavenLocal` and add `mavenLocal()` to the
game's repositories.

## Releasing X.Y.Z

```bash
git checkout develop && git pull
git checkout -b release/2.1                      # stabilise, only fixes from here

# set the final version
sed -i 's/^version=.*/version=2.1.0/' gradle.properties
# and the version in README.md's "Get it" snippets (Groovy, Kotlin, Maven): it is the published front page
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

The release workflow refuses to run if the tag and `gradle.properties` disagree, so the version in the repository
always matches what was published. A tag with a suffix (`v2.1.0-rc1`) is published as a GitHub pre-release.

Each release publishes:

- the library to Maven Central: `io.github.javakhanstudio:parallax-background`;
- a GitHub release with the two downloads, the library jars (+ sources, + javadoc) and
  `ParallaxEditor-X.Y.Z.zip` (the editor with the library and the sample projects).

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
./gradlew :core:publishToMavenCentral --no-configuration-cache
```
