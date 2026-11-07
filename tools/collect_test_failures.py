#!/usr/bin/env python3
"""Prints the assertion messages of every failing JUnit XML test result in a directory."""

import glob
import os
import sys
import xml.etree.ElementTree as ElementTree


def main(directory: str) -> int:
    paths = sorted(glob.glob(os.path.join(directory, "*.xml")))
    if not paths:
        print("No test result XML found in " + directory)
        return 1
    failures = 0
    for path in paths:
        try:
            root = ElementTree.parse(path).getroot()
        except ElementTree.ParseError as error:
            print("Could not parse {}: {}".format(path, error))
            continue
        for case in root.iter("testcase"):
            problems = list(case.findall("failure")) + list(case.findall("error"))
            for problem in problems:
                failures += 1
                message = (problem.get("message") or "") + " " + (problem.text or "")
                collapsed = " ".join(message.split())
                print(
                    "TEST FAILED: {}.{} :: {}".format(
                        case.get("classname", "?"), case.get("name", "?"), collapsed[:700]
                    )
                )
                first_frame = ""
                for line in (problem.text or "").splitlines():
                    if line.strip().startswith("at "):
                        first_frame = line.strip()
                        break
                if first_frame:
                    print("    " + first_frame)
    if failures == 0:
        print("No failing tests recorded; the failure was probably not a test assertion.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "app/build/test-results/testDebugUnitTest"))
