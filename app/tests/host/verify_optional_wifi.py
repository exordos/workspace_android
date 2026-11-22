import argparse
import subprocess


def main():
    parser = argparse.ArgumentParser(description="Verify Wi-Fi does not filter release installs.")
    parser.add_argument("apk", help="Path to the packaged APK")
    parser.add_argument("--aapt2", default="aapt2", help="Android SDK Build Tools aapt2 binary")
    args = parser.parse_args()
    badging = subprocess.run(
        [args.aapt2, "dump", "badging", args.apk],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    wifi = "name='android.hardware.wifi'"
    entries = [line.strip() for line in badging.splitlines() if wifi in line]
    if any(line.startswith(("uses-feature:", "uses-implied-feature:")) for line in entries):
        parser.exit(1, "Packaged APK requires Wi-Fi hardware.\n")
    if not any(line.startswith("uses-feature-not-required:") for line in entries):
        parser.exit(1, "Packaged APK does not explicitly declare Wi-Fi optional.\n")
    print("Packaged APK declares Wi-Fi optional.")


if __name__ == "__main__":
    main()
