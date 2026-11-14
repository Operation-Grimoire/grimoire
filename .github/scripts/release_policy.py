"""Platform impact, immutable changelog fragments and published release ranges.

Uses only Python's standard library. GitHub catalog input is the JSON returned by
`gh api --paginate --slurp repos/OWNER/REPO/releases?per_page=100`.
"""

import argparse
import json
import os
from pathlib import Path
import re
import subprocess


PLATFORMS = ("android", "desktop")
CATEGORIES = {"feature": "Features", "fix": "Bug fixes", "source": "Sources",
              "docs": "Documentation", "other": "Other changes"}
TAG = re.compile(r"^(desktop-)?v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\."
                 r"(0|[1-9][0-9]*)(?:-beta\.(0|[1-9][0-9]*))?$")
FRAGMENT = re.compile(r"^\.changes/[a-z0-9][a-z0-9-]*\.json$")


def git(*args):
    return subprocess.check_output(["git", *args]).decode("utf-8").strip()


def commit(ref):
    return git("rev-parse", "--verify", "--end-of-options", ref + "^{commit}")


def changes(base, head):
    # --no-renames includes both sides of moves across platform boundaries.
    raw = subprocess.check_output(["git", "diff", "--name-status", "--no-renames",
                                   "-z", base, head, "--"])
    fields = raw.decode("utf-8").split("\0")
    return list(zip(fields[0:-1:2], fields[1:-1:2]))


def impact(paths):
    result = {f"{p}_{kind}": False for p in PLATFORMS for kind in ("ci", "release")}
    for path in paths:
        if (path.startswith((".changes/", "docs/")) or
                ("/" not in path and path.endswith(".md")) or path in ("LICENSE", ".gitignore")):
            continue
        if path.startswith(".github/"):
            # Policy/workflow changes get checks, but never trigger a binary alone.
            targets = ("desktop",) if path == ".github/workflows/desktop.yml" else PLATFORMS
            for p in targets:
                result[f"{p}_ci"] = True
            continue
        if path.startswith("app/"):
            targets = ("android",)
        elif path.startswith("desktop/"):
            targets = ("desktop",)
        else:
            # Shared build inputs and unknown future modules fail conservatively.
            targets = PLATFORMS
        test_only = ("/src/test/" in path or "/src/androidTest/" in path or
                     "/src/integrationTest/" in path or
                     Path(path).name.startswith("test_"))
        for p in targets:
            result[f"{p}_ci"] = True
            result[f"{p}_release"] |= not test_only
    return result


def fragment_at(ref, path):
    if not FRAGMENT.fullmatch(path):
        raise ValueError(f"Invalid fragment filename: {path}")
    value = json.loads(git("show", f"{ref}:{path}"))
    if not isinstance(value, dict) or set(value) != set(PLATFORMS):
        raise ValueError(f"{path}: require android and desktop arrays")
    for platform, entries in value.items():
        if not isinstance(entries, list):
            raise ValueError(f"{path}: {platform} must be an array")
        for entry in entries:
            if (not isinstance(entry, dict) or set(entry) != {"category", "text"} or
                    not isinstance(entry["category"], str) or entry["category"] not in CATEGORIES or
                    not isinstance(entry["text"], str) or not entry["text"].strip() or
                    entry["text"] != entry["text"].strip() or
                    any(c in entry["text"] for c in "\r\n\0")):
                raise ValueError(f"{path}: invalid category or single-line text")
    return value


def validate(base, head):
    delta = changes(base, head)
    added = []
    for status, path in delta:
        if path.startswith(".changes/"):
            if status != "A":
                raise ValueError(f"Changelog fragments are immutable: {path}")
            fragment_at(head, path)
            added.append(path)
    affected = impact(path for _, path in delta)
    for p in PLATFORMS:
        # Both keys must exist even for internal changes; [] is an explicit decision.
        if affected[f"{p}_release"] and not added:
            raise ValueError(f"{p} runtime changes need a new .changes/*.json fragment (arrays may be empty)")
    return affected


def notes(base, head, platform):
    grouped = {category: [] for category in CATEGORIES}
    for status, path in sorted(changes(base, head), key=lambda change: change[1]):
        if status == "A" and path.startswith(".changes/"):
            for entry in fragment_at(head, path)[platform]:
                grouped[entry["category"]].append(entry["text"])
    return "\n\n".join(f"## {CATEGORIES[category]}\n\n" + "\n".join(f"- {text}" for text in texts)
                         for category, texts in grouped.items() if texts)


def release_key(release, platform, channel):
    match = TAG.fullmatch(release.get("tag_name", ""))
    if not match or bool(match[1]) != (platform == "desktop") or release.get("draft", False):
        return None
    beta = match[5] is not None
    if release.get("prerelease", False) != beta or (channel == "stable" and beta):
        return None
    if platform == "android":
        assets = {asset.get("name") for asset in release.get("assets", [])}
        # Do not advance beyond a failed/incomplete upload. Legacy stable releases
        # can lack the checksum; all current beta publications require both files.
        if "grimoire.apk" not in assets or (beta and "grimoire.apk.sha256" not in assets):
            return None
    version = tuple(int(match[i]) for i in (2, 3, 4))
    # Preserve Android's deployed ordering: same-base beta builds follow stable.
    return (*version, int(match[5] or 0))


def previous(catalog, platform, channel, head, exclude=""):
    candidates = []
    for page in catalog:
        for release in page if isinstance(page, list) else [page]:
            key = release_key(release, platform, channel)
            tag = release.get("tag_name", "")
            if key is None or tag == exclude:
                continue
            try:
                sha = commit("refs/tags/" + tag)
            except subprocess.CalledProcessError:
                continue
            result = subprocess.run(["git", "merge-base", "--is-ancestor", sha, head], check=False)
            if result.returncode == 0:
                candidates.append((key, tag))
            elif result.returncode != 1:
                raise RuntimeError("Could not establish release ancestry")
    return max(candidates)[1] if candidates else ""


def empty_tree():
    return subprocess.check_output(["git", "hash-object", "-t", "tree", "--stdin"], input=b"").decode().strip()


def emit(values):
    lines = [f"{key}={str(value).lower() if isinstance(value, bool) else value}" for key, value in values.items()]
    print("\n".join(lines))
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            output.write("\n".join(lines) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    ci = sub.add_parser("ci")
    ci.add_argument("--event", default=os.environ.get("GITHUB_EVENT_PATH"))
    for command in ("impact", "validate", "notes"):
        child = sub.add_parser(command)
        child.add_argument("--base", required=True)
        child.add_argument("--head", default="HEAD")
        if command == "notes":
            child.add_argument("--platform", choices=PLATFORMS, required=True)
            child.add_argument("--output", required=True)
    plan = sub.add_parser("plan")
    plan.add_argument("--catalog", required=True)
    plan.add_argument("--platform", choices=PLATFORMS, required=True)
    plan.add_argument("--channel", choices=("stable", "beta"), required=True)
    plan.add_argument("--head", default="HEAD")
    plan.add_argument("--exclude-tag", default="")
    plan.add_argument("--force", action="store_true")
    plan.add_argument("--notes", required=True)
    args = parser.parse_args()
    if args.command == "ci":
        event = json.loads(Path(args.event).read_text(encoding="utf-8"))
        if "pull_request" in event:
            head = commit(event["pull_request"]["head"]["sha"])
            base = git("merge-base", commit(event["pull_request"]["base"]["sha"]), head)
        else:
            head = commit(event["after"])
            before = event.get("before", "")
            base = commit(before) if before and set(before) != {"0"} else empty_tree()
        emit(validate(base, head))
    elif args.command == "plan":
        head = commit(args.head)
        catalog = json.loads(Path(args.catalog).read_text(encoding="utf-8-sig"))
        prior = previous(catalog, args.platform, args.channel, head, args.exclude_tag)
        base = commit("refs/tags/" + prior) if prior else empty_tree()
        affected = impact(path for _, path in changes(base, head))
        Path(args.notes).parent.mkdir(parents=True, exist_ok=True)
        Path(args.notes).write_text(notes(base, head, args.platform), encoding="utf-8")
        # Desktop publication remains disabled until installer acceptance. The
        # eligibility output supports dry runs without claiming shipped artifacts.
        emit({"previous": prior, **affected,
              "publish": args.platform == "android" and (args.force or affected["android_release"]),
              "make_latest": args.platform == "android" and args.channel == "stable"})
    else:
        base, head = commit(args.base), commit(args.head)
        if args.command == "notes":
            Path(args.output).write_text(notes(base, head, args.platform), encoding="utf-8")
        else:
            emit(validate(base, head) if args.command == "validate" else
                 impact(path for _, path in changes(base, head)))


if __name__ == "__main__":
    main()
