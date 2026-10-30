"""Exercise the real formatter against new source and Git control files."""

import argparse
from pathlib import Path
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gradle", default="./gradlew")
    args = parser.parse_args()
    repository = Path(__file__).resolve().parents[3]
    command = [args.gradle, "--no-daemon", "spotlessCheck"]
    with tempfile.TemporaryDirectory(prefix=".cassi-newline-", dir=repository) as directory:
        probes = [Path(directory) / "NewFile.kt", Path(directory) / ".gitignore"]
        for probe in probes:
            probe.write_bytes(b"# Final newline regression")
        rejected = subprocess.run(command, cwd=repository, text=True,
                                  stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        if rejected.returncode == 0 or any(
            str(probe.relative_to(repository)) not in rejected.stdout for probe in probes
        ):
            raise AssertionError("Both missing newlines must be rejected:\n" + rejected.stdout)
        for probe in probes:
            probe.write_bytes(probe.read_bytes() + b"\n")
        subprocess.run(command, cwd=repository, check=True)
    print("PASS: new source and .gitignore files require final newlines")


if __name__ == "__main__":
    main()
