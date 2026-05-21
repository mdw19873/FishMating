# Releasing

This project uses a **pom-driven**, tag-triggered release process. The version in
`pom.xml` is the single source of truth; `plugin.yml` derives its version from it via
Maven resource filtering (`version: '${project.version}'`).

Releases follow [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`, with
optional pre-release suffixes (e.g. `1.2.0-rc.1`).

## Cutting a release

1. **Update the changelog.** In `CHANGELOG.md`, move the items under `## [Unreleased]`
   into a new `## [X.Y.Z] - YYYY-MM-DD` section, and add the compare/release links at
   the bottom.
2. **Bump the version** in `pom.xml` (`<version>X.Y.Z</version>`).
3. **Commit** both changes:
   ```bash
   git commit -am "Release X.Y.Z"
   ```
4. **Tag and push.** The tag must be the version prefixed with `v`:
   ```bash
   git tag -a vX.Y.Z -m "FishMating X.Y.Z"
   git push origin main vX.Y.Z
   ```

Pushing the tag triggers the [`Release`](.github/workflows/release.yml) workflow, which:

- verifies the tag matches the `pom.xml` version (fails fast on mismatch);
- runs `mvn clean verify` (a release never ships untested code);
- builds `fishmating-X.Y.Z.jar` and generates a `.sha256` checksum;
- produces a [build-provenance attestation](https://docs.github.com/actions/security-guides/using-artifact-attestations) for the jar;
- extracts the matching `CHANGELOG.md` section as the release notes;
- publishes a **GitHub Release** with the jar and checksum attached.

Tags with a pre-release suffix (any `-` in the version) are published as
**pre-releases** automatically.

> **Manual run.** The workflow can also be started via *Actions → Release → Run
> workflow* (`workflow_dispatch`). It then uses the current `pom.xml` version and
> creates the `vX.Y.Z` tag at the run's commit.

## Verifying a release

Download the jar and its checksum from the release, then:

```bash
sha256sum -c fishmating-X.Y.Z.jar.sha256        # integrity
gh attestation verify fishmating-X.Y.Z.jar \
  --repo mdw19873/FishMating                     # provenance (origin)
```

> Build-provenance attestations are available for public repositories; private repos
> require GitHub Advanced Security.

## After a release

Continue adding entries under `## [Unreleased]` in `CHANGELOG.md` as work lands, ready
for the next release.
