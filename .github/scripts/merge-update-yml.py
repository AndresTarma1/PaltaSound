#!/usr/bin/env python3
"""Builds the electron-builder auto-update manifests (`latest*.yml`) for a Nucleus/Compose build.

Nucleus runs one electron-builder invocation per target format (Exe/NSIS, Msi, Deb, Rpm...). Each
format writes a single-artifact `<channel><osSuffix>.yml` into its own output directory, and all
formats of the same OS share the same manifest name (e.g. `latest.yml` for Windows). Uploading them
as-is would clobber each other in the GitHub release, leaving only one format.

This script normalizes them into ONE manifest per OS:
  - If electron-builder / the plugin already wrote manifests (Windows: NSIS + MSI), they are merged
    into a union manifest (deduplicated by url, sorted), like the plugin's own `UpdateYmlMerger`.
  - If NO manifest exists for a platform (observed on Linux: electron-builder does not emit
    `latest-linux.yml` for .deb/.rpm when publishing is disabled), it is GENERATED from the
    installer files, computing the SHA-512 (Base64) the updater's ChecksumVerifier expects.

Usage:
    merge-update-yml.py <input_root> <output_dir> [--copy]
        [--exclude-manifest SUBSTR] [--extra-copy GLOB]

- Input root: the `desktopApp/build/compose/binaries` tree.
- Output dir: where the `<channel><osSuffix>.yml` files are written (one per distinct name).

With `--copy`, the files referenced by each manifest's `files[].url` are also copied into
[output_dir]. This is the authoritative list of release assets: whatever the updater reads from the
YAML must be uploaded as a GitHub release asset with that exact name. Using the YAML as the source
of truth avoids sweeping in non-installer files (launcher .exe, java.exe, bundled yt-dlp, ...) that
live inside the app-image directories.

`--exclude-manifest SUBSTR` (repeatable): installer paths AND discovered `latest*.yml` files
containing SUBSTR are ignored when building manifests. Use it for artifacts that must ship in the
release but must NOT be auto-selected by the updater — e.g. a second Windows installer whose
format the updater cannot tell apart from the main one (both resolve to "nsis", so the file
selector would offer the wrong variant to half the installs).

`--extra-copy GLOB` (repeatable, relative to input root, `**` allowed): matching files are copied
verbatim into [output_dir] as plain release assets, without any manifest entry. Pair it with
`--exclude-manifest` for the manual-download variant.
"""

import base64
import datetime
import glob
import hashlib
import os
import re
import shutil
import sys

# target-format -> (output dir name, installer extension, OS group, manifest file name)
# `nsis` y `exe` son el mismo instalador electron-builder; el plugin nombra el directorio según el
# TargetFormat. El ext del .exe es `.exe` aunque el formato se llame `nsis`.
FORMAT_GROUPS = {
    "exe": ("exe", "exe", "windows", "latest.yml"),
    "nsis": ("nsis", "exe", "windows", "latest.yml"),
    "msi": ("msi", "msi", "windows", "latest.yml"),
    "deb": ("deb", "deb", "linux", "latest-linux.yml"),
    "rpm": ("rpm", "rpm", "linux", "latest-linux.yml"),
}
GROUP_ORDER = ["latest.yml", "latest-linux.yml", "latest-mac.yml"]
VERSION_RE = re.compile(r"(\d+\.\d+\.\d+)")


def sha512_base64(path):
    digest = hashlib.sha512()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            digest.update(chunk)
    return base64.b64encode(digest.digest()).decode("ascii")


def parse(path):
    version = None
    release_date = None
    entries = []
    url = sha512 = size = blockmap = None
    in_files = False

    def flush():
        nonlocal url, sha512, size, blockmap
        if url is None:
            return
        entries.append({"url": url, "sha512": sha512, "size": size, "blockmap": blockmap})
        url = sha512 = size = blockmap = None

    with open(path, encoding="utf-8", errors="replace") as fh:
        for raw in fh:
            line = raw.rstrip("\n").rstrip("\r")
            if line.startswith("version: "):
                version = line[len("version: "):].strip()
            elif line.startswith("releaseDate: "):
                release_date = line[len("releaseDate: "):].strip()
            elif line == "files:":
                in_files = True
            elif in_files and line.startswith("  - url: "):
                flush()
                url = line[len("  - url: "):].strip()
            elif in_files and url is not None and line.startswith("    "):
                field = line.strip()
                if field.startswith("sha512: "):
                    sha512 = field[len("sha512: "):]
                elif field.startswith("size: "):
                    size = field[len("size: "):]
                elif field.startswith("blockMapSize: "):
                    blockmap = field[len("blockMapSize: "):]
            elif line and not line.startswith(" "):
                flush()
                in_files = False
    flush()
    return version, release_date, entries


def serialize(version, release_date, entries):
    entries = sorted(entries, key=lambda e: e["url"])
    first = entries[0]
    out = [f"version: {version}", "files:"]
    for entry in entries:
        out.append(f"  - url: {entry['url']}")
        if entry["sha512"]:
            out.append(f"    sha512: {entry['sha512']}")
        if entry["size"]:
            out.append(f"    size: {entry['size']}")
        if entry["blockmap"]:
            out.append(f"    blockMapSize: {entry['blockmap']}")
    out.append(f"path: {first['url']}")
    if first["sha512"]:
        out.append(f"sha512: {first['sha512']}")
    if release_date:
        out.append(f"releaseDate: {release_date}")
    return "\n".join(out) + "\n"


def merge(manifests):
    version = next((v for v, _, _ in manifests if v), None)
    if version is None:
        raise SystemExit("No manifest declares a version")
    by_url = {}
    for _, _, entries in manifests:
        for entry in entries:
            by_url.setdefault(entry["url"], entry)
    if not by_url:
        raise SystemExit("No files entries found to merge")
    release_date = max((rd for _, rd, _ in manifests if rd), default=None)
    return version, release_date, list(by_url.values())


def discover_installers(root, exclude=()):
    """format -> list of installer files (files directly inside each target-format dir).

    The glob is anchored so that only files directly in the `<format>` directory are matched;
    nested `.app-image/...` trees (launcher .exe, java.exe, bundled yt-dlp, ...) are excluded.

    Both the JVM layout (`msi/`, `exe/`, ...) and the GraalVM layout (`graalvm-msi/`,
    `graalvm-exe/`, ...) are matched, but a given CI build only produces one of them.

    Paths containing any of the `exclude` substrings are skipped (see --exclude-manifest).
    """
    found = {}
    for fmt, (dirname, ext, _group, _yml) in FORMAT_GROUPS.items():
        for d in (dirname, f"graalvm-{dirname}"):
            for path in glob.glob(os.path.join(root, "**", d, f"*.{ext}"), recursive=True):
                if any(e in path for e in exclude):
                    continue
                found.setdefault(fmt, []).append(path)
    return found


def build_generated_manifest(installer_files, version):
    entries = []
    for path in sorted(installer_files):
        entries.append({
            "url": os.path.basename(path),
            "sha512": sha512_base64(path),
            "size": str(os.path.getsize(path)),
            "blockmap": None,
        })
    if version is None:
        version = next(
            (m.group(1) for p in installer_files for m in [VERSION_RE.search(os.path.basename(p))] if m),
            "0.0.0",
        )
    release_date = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")
    return version, release_date, entries


def find_file(root, name):
    for dirpath, _dirnames, filenames in os.walk(root):
        if name in filenames:
            return os.path.join(dirpath, name)
    return None


def copy_referenced_files(root, outdir, manifests):
    urls = []
    for _, _, entries in manifests:
        for entry in entries:
            if entry["url"] not in urls:
                urls.append(entry["url"])
    for url in urls:
        src = find_file(root, url)
        if src is None:
            print(f"warning: '{url}' referenced by a manifest but not found under {root}", file=sys.stderr)
            continue
        shutil.copy2(src, os.path.join(outdir, url))
        print(f"copied {os.path.basename(src)} ({os.path.getsize(src)} bytes)")


def main():
    if len(sys.argv) < 3:
        raise SystemExit("usage: merge-update-yml.py <input_root> <output_dir> [--copy] [--exclude-manifest SUBSTR] [--extra-copy GLOB]")
    root, outdir = sys.argv[1], sys.argv[2]
    do_copy = "--copy" in sys.argv

    def flag_values(name):
        vals = []
        args = sys.argv[3:]
        for i, a in enumerate(args):
            if a == name and i + 1 < len(args):
                vals.append(args[i + 1])
        return vals

    exclude_manifest = flag_values("--exclude-manifest")
    extra_copy = flag_values("--extra-copy")

    os.makedirs(outdir, exist_ok=True)

    # 1) Collect manifests electron-builder / the plugin already wrote, skipping the
    # excluded trees (their yml, if any, must not leak into the merged manifests).
    by_name = {}
    for path in glob.glob(os.path.join(root, "**", "latest*.yml"), recursive=True):
        if any(e in path for e in exclude_manifest):
            continue
        by_name.setdefault(os.path.basename(path), []).append(path)

    # 2) Map installers to the manifest name that must describe them.
    installers = discover_installers(root, exclude_manifest)
    group_manifests = {}  # yml name -> list of manifests (parsed or generated)
    for fmt, (_dirname, _ext, _group, yml_name) in FORMAT_GROUPS.items():
        files = installers.get(fmt, [])
        if not files:
            continue
        manifests = [parse(p) for p in by_name.get(yml_name, [])]
        if not manifests:
            print(f"No '{yml_name}' manifest found; generating it from {fmt} installers")
            manifests = [build_generated_manifest(files, None)]
        # Asegura que TODOS los instaladores descubiertos queden listados, aunque los manifests
        # encontrados no los mencionen (p.ej. electron-builder no emite latest.yml para NSIS en
        # Windows, así que el .exe quedaría fuera si solo usáramos los manifests existentes).
        referenced = {e["url"] for _, _, entries in manifests for e in entries}
        for path in files:
            name = os.path.basename(path)
            if name in referenced:
                continue
            entry = {
                "url": name,
                "sha512": sha512_base64(path),
                "size": str(os.path.getsize(path)),
                "blockmap": None,
            }
            blockmap = os.path.join(os.path.dirname(path), name + ".blockmap")
            if os.path.exists(blockmap):
                entry["blockmap"] = str(os.path.getsize(blockmap))
            manifests.append((None, None, [entry]))
            print(f"added missing installer entry: {name}")
        group_manifests.setdefault(yml_name, []).extend(manifests)

    if not group_manifests:
        print("No installers or update manifests found under", root)
        return

    for yml_name in GROUP_ORDER:
        manifests = group_manifests.get(yml_name)
        if not manifests:
            continue
        version = next((v for v, _, _ in manifests if v), None)
        by_url = {}
        for _, _, entries in manifests:
            for entry in entries:
                by_url.setdefault(entry["url"], entry)
        entries = sorted(by_url.values(), key=lambda e: e["url"])
        release_date = max((rd for _, rd, _ in manifests if rd), default=None)
        content = serialize(version, release_date, entries)

        with open(os.path.join(outdir, yml_name), "w", encoding="utf-8") as fh:
            fh.write(content)
        sources = by_name.get(yml_name, [])
        print(f"wrote {yml_name} ({len(entries)} entr(ies); sources: {', '.join(sources) or 'generated'})")
        if do_copy:
            copy_referenced_files(root, outdir, [(version, release_date, entries)])

    # 3) Plain assets: ship in the release without any manifest entry (manual download).
    copied = {os.path.basename(p) for p in glob.glob(os.path.join(outdir, "*"))}
    for pattern in extra_copy:
        matches = sorted(glob.glob(os.path.join(root, pattern), recursive=True))
        if not matches:
            print(f"warning: --extra-copy '{pattern}' matched nothing under {root}", file=sys.stderr)
        for path in matches:
            name = os.path.basename(path)
            if name in copied:
                print(f"warning: --extra-copy '{name}' already shipped, skipping", file=sys.stderr)
                continue
            shutil.copy2(path, os.path.join(outdir, name))
            copied.add(name)
            print(f"copied extra {name} ({os.path.getsize(path)} bytes)")


if __name__ == "__main__":
    main()
