#!/usr/bin/env python3
"""Fail closed when a Kover XML report has less than 80% line coverage."""
import sys
import xml.etree.ElementTree as ET

def main():
    if len(sys.argv) != 2:
        raise ValueError("Expected Kover XML report path")
    root = ET.parse(sys.argv[1]).getroot()
    counters = [c for c in root.findall("counter") if c.get("type") == "LINE"]
    if len(counters) != 1:
        raise ValueError("Expected one report-level LINE counter")
    covered, missed = int(counters[0].get("covered")), int(counters[0].get("missed"))
    if min(covered, missed) < 0 or covered + missed == 0:
        raise ValueError("Invalid or empty line coverage")
    print(f"Line coverage: {100 * covered / (covered + missed):.2f}% ({covered}/{covered + missed})")
    return 0 if covered * 100 >= (covered + missed) * 80 else 1

if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, OSError, ET.ParseError, TypeError) as error:
        print(f"Coverage verification failed: {error}", file=sys.stderr)
        sys.exit(1)
