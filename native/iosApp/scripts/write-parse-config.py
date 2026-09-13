"""Bundle Parse client settings from environment or ignored local properties."""
import os
from pathlib import Path
import plistlib


def configuration(environ, local_text):
    local = {}
    for line in local_text.splitlines():
        line = line.strip()
        if line and not line.startswith(("#", "!")) and "=" in line:
            key, value = line.split("=", 1)
            local[key.strip()] = value.strip()
    values = {}
    for key in ("REPORTED_PARSE_SERVER_URL", "REPORTED_PARSE_APPLICATION_ID", "REPORTED_PARSE_JAVASCRIPT_KEY"):
        value = environ.get(key, local.get(key, "")).strip()
        if not value:
            raise ValueError(f"Set {key} in the environment or native/local.properties")
        values[key] = value
    return values


if __name__ == "__main__":
    local = Path(__file__).resolve().parents[2] / "local.properties"
    try:
        values = configuration(os.environ, local.read_text() if local.exists() else "")
    except ValueError as error:
        raise SystemExit(str(error))
    output = Path(os.environ["TARGET_BUILD_DIR"]) / os.environ["UNLOCALIZED_RESOURCES_FOLDER_PATH"] / "ParseConfig.plist"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(plistlib.dumps(values))
