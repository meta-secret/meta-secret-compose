#!/usr/bin/env python3
"""Validate a coverage report; 80% is advisory unless --strict is requested."""
import argparse
import sys
import xml.etree.ElementTree as ET

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("report")
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()
    root = ET.parse(args.report).getroot()
    counters = [c for c in root.findall("counter") if c.get("type") == "LINE"]
    if len(counters) != 1:
        raise ValueError("Expected one report-level LINE counter")
    covered, missed = int(counters[0].get("covered")), int(counters[0].get("missed"))
    if min(covered, missed) < 0 or covered + missed == 0:
        raise ValueError("Invalid or empty line coverage")
    percent = 100 * covered / (covered + missed)
    print(f"Overall line coverage: {percent:.2f}% ({covered}/{covered + missed})")
    below = covered * 100 < (covered + missed) * 80
    if below:
        print("Below 80% goal: advisory; tests and valid report remain required" if not args.strict else "Below mandatory 80% threshold")
    return 1 if below and args.strict else 0

if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, OSError, ET.ParseError, TypeError) as error:
        print(f"Coverage report validation failed: {error}", file=sys.stderr)
        sys.exit(1)
