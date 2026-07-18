"""Normalize a hosts/DNS/URL list into domain-only ShieldFocus assets."""

from __future__ import annotations

import argparse
import ipaddress
import re
from pathlib import Path
from urllib.parse import urlsplit

HOST_RE = re.compile(r"^(?=.{1,253}$)(?:[a-z0-9_](?:[a-z0-9_-]{0,61}[a-z0-9_])?\.)+[a-z0-9_](?:[a-z0-9_-]{0,61}[a-z0-9_])?$", re.I)
GAMBLING_RE = re.compile(r"(?:^|[.-])(bet|betting|casino|gambl|poker|sportsbook|slots?)(?:[.-]|$)", re.I)
GAMBLING_CONFIRMED_RE = re.compile(r"(?:^|[.-])(casino|gambling|poker|sportsbook)(?:[.-]|$)|(?:^|\.)bet-at-home\.com$|(?:^|\.)gg-?bet(?:[.-]|$)", re.I)
TRACKING_RE = re.compile(r"(?:^|[.-])(analytics?|beacons?|metrics?|pixels?|telemetry|track(?:er|ing)?)(?:[.-]|$)", re.I)
MALWARE_SECTIONS = {"add.risk", "add.spam", "urlhaus"}


def hostname_from_token(token: str) -> str | None:
    value = token.strip().strip("[](),;\"'").lower().rstrip(".")
    if not value:
        return None
    try:
        ipaddress.ip_address(value.split("%", 1)[0])
        return None
    except ValueError:
        pass
    if "://" in value:
        value = (urlsplit(value).hostname or "").lower().rstrip(".")
    else:
        value = value.split("/", 1)[0].split(":", 1)[0]
    if value.startswith("www."):
        value = value[4:]
    return value if HOST_RE.fullmatch(value) else None


def extract(lines: list[str], start_marker: str | None) -> tuple[list[str], list[str]]:
    domains: set[str] = set()
    started = start_marker is None
    for raw_line in lines:
        if not started:
            started = start_marker.lower() in raw_line.lower()
            if not started:
                continue
        content = raw_line.split("#", 1)[0].strip()
        if not content:
            continue
        tokens = content.split()
        candidates = tokens[1:] if len(tokens) > 1 and hostname_from_token(tokens[0]) is None else tokens
        for token in candidates:
            hostname = hostname_from_token(token)
            if hostname:
                domains.add(hostname)
    ordered = sorted(domains)
    gambling_review = [domain for domain in ordered if GAMBLING_RE.search(domain)]
    return ordered, gambling_review


def classify_stevenblack_base(lines: list[str]) -> dict[str, list[str]]:
    buckets: dict[str, set[str]] = {"advertising": set(), "tracking": set(), "malware": set(), "gambling": set(), "gambling_review": set()}
    section = ""
    for raw_line in lines:
        lowered = raw_line.strip().lower()
        if lowered.startswith("# title: hostsvn adult vn"):
            break
        if lowered.startswith("# start "):
            section = lowered.removeprefix("# start ").strip()
            continue
        if lowered.startswith("# end "):
            section = ""
            continue
        content = raw_line.split("#", 1)[0].strip()
        if not content:
            continue
        tokens = content.split()
        candidates = tokens[1:] if len(tokens) > 1 and hostname_from_token(tokens[0]) is None else tokens
        for token in candidates:
            domain = hostname_from_token(token)
            if not domain or domain in {"localhost", "local"}:
                continue
            if GAMBLING_CONFIRMED_RE.search(domain):
                buckets["gambling"].add(domain)
                continue
            if GAMBLING_RE.search(domain):
                buckets["gambling_review"].add(domain)
            if section in MALWARE_SECTIONS:
                buckets["malware"].add(domain)
            elif TRACKING_RE.search(domain) or section == "add.2o7net":
                buckets["tracking"].add(domain)
            else:
                buckets["advertising"].add(domain)
    return {name: sorted(values) for name, values in buckets.items()}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--start-marker")
    parser.add_argument("--review-output", type=Path)
    parser.add_argument("--classify-base-dir", type=Path)
    parser.add_argument("--base-review-output", type=Path)
    args = parser.parse_args()
    domains, gambling_review = extract(args.source.read_text(encoding="utf-8", errors="replace").splitlines(), args.start_marker)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n".join(domains) + "\n", encoding="utf-8")
    if args.review_output:
        args.review_output.parent.mkdir(parents=True, exist_ok=True)
        args.review_output.write_text("\n".join(gambling_review) + ("\n" if gambling_review else ""), encoding="utf-8")
    if args.classify_base_dir:
        classified = classify_stevenblack_base(args.source.read_text(encoding="utf-8", errors="replace").splitlines())
        args.classify_base_dir.mkdir(parents=True, exist_ok=True)
        filenames = {
            "advertising": "stevenblack_advertising_hosts.txt",
            "tracking": "stevenblack_tracking_hosts.txt",
            "malware": "stevenblack_malware_hosts.txt",
            "gambling": "stevenblack_gambling_hosts.txt",
            "gambling_review": "stevenblack_gambling_candidates.txt",
        }
        for name, domains_for_category in classified.items():
            if name == "gambling_review" and args.base_review_output:
                args.base_review_output.parent.mkdir(parents=True, exist_ok=True)
                args.base_review_output.write_text("\n".join(domains_for_category) + ("\n" if domains_for_category else ""), encoding="utf-8")
                continue
            destination = args.classify_base_dir / filenames[name]
            destination.write_text("\n".join(domains_for_category) + ("\n" if domains_for_category else ""), encoding="utf-8")
        print(" ".join(f"{name}={len(values)}" for name, values in classified.items()))
    print(f"domains={len(domains)} gambling_review={len(gambling_review)}")


if __name__ == "__main__":
    main()
