#!/usr/bin/env bash
# Publishes the version in gradle.properties as a GitHub release and points mods.txt at it, so players
# get it the next time they run the join script. Then run ./deploy.sh to put it on the server.
#
#   ./release.sh
set -euo pipefail

cd "$(dirname "$0")"
version="$(sed -n 's/^version=//p' gradle.properties)"
tag="v$version"
jar="build/libs/rings-$version.jar"

if gh release view "$tag" >/dev/null 2>&1; then
  echo "Release $tag already exists. Bump version in gradle.properties first."; exit 1
fi
[ -z "$(git status --porcelain)" ] || { echo "Commit your changes first."; exit 1; }

git push -q
./gradlew build --console=plain -q
gh release create "$tag" "$jar" --title "Rings $version" --generate-notes

repo="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
sed -i "s|^mod https://github.com/[^ ]*/rings-[^ ]*\.jar$|mod https://github.com/$repo/releases/download/$tag/rings-$version.jar|" mods.txt
if ! git diff --quiet mods.txt; then
  git commit -q -m "Point mods.txt at Rings $version" mods.txt
  git push -q
fi
echo "Released $tag. Now run ./deploy.sh to install it on the server."
