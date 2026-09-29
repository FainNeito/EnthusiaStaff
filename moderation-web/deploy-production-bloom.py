"""Install only the production Cloudflare Tunnel connector token on the Bloom Staff Bot split."""

from __future__ import annotations

import hashlib
import io
import json
import re
import sys
import tomllib
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse

import paramiko


TUNNEL_NAME = "enthusia-moderation-read-production"
REMOTE_NAME = "prod-tunnel"
REMOTE_JAR = "EnthusiaStaff-StaffBot.jar"
PREVIOUS_JAR_SHA256 = "9a12cefd06b5158829ec8df4c4ed28c2d07bb119b2f1adbe546bc3fd31a7ce01"
FIRST_WEB_JAR_SHA256 = "0e4a9c7c3cb4bceddd550843578d9b74e184f94c6fd132c3f35a7f9e1bc88ba8"
LOCAL_JAR = Path(__file__).resolve().parent.parent / "staff-bot/build/libs/EnthusiaStaff-StaffBot-0.1.0-SNAPSHOT.jar"
DETAILS_FILE = Path.home() / "OneDrive/Desktop/SFTP Details- ENTHUSIA NETWORK.md"
HOST_KEYS_FILE = Path.home() / ".ssh/known_hosts_sentinel_bloom"
WRANGLER_CREDENTIALS = Path.home() / ".wrangler/config/default.toml"


def bloom_connection() -> tuple[str, int, str, str]:
    lines = DETAILS_FILE.read_text(encoding="utf-8-sig").splitlines()
    first = lines[0]
    label, separator, value = first.partition(":")
    if label.strip() != "All passwords" or not separator or not value.strip():
        raise RuntimeError("Bloom SFTP details are unavailable")
    url = urlparse(lines[77].strip())
    host = url.netloc.rsplit(":", 1)[0]
    username = lines[79].strip()
    if url.scheme != "sftp" or not url.hostname or not url.port or host.lower() != url.hostname \
            or not re.fullmatch(r"[A-Za-z0-9._-]{3,120}", username):
        raise RuntimeError("Bloom Staff Bot SFTP endpoint is unavailable")
    return host, url.port, username, value.strip()


def connector_token() -> str:
    with WRANGLER_CREDENTIALS.open("rb") as stream:
        oauth_token = tomllib.load(stream).get("oauth_token")
    if not oauth_token:
        raise RuntimeError("Cloudflare authorization is unavailable")
    def get(url: str) -> dict:
        request = urllib.request.Request(url, headers={"Authorization": f"Bearer {oauth_token}"})
        with urllib.request.urlopen(request, timeout=20) as response:
            payload = json.load(response)
        if not payload.get("success"):
            raise RuntimeError("Cloudflare API rejected the tunnel lookup")
        return payload

    base = "https://api.cloudflare.com/client/v4"
    zones = get(f"{base}/zones?name=enthusia.info&status=active")["result"]
    if len(zones) != 1:
        raise RuntimeError("Enthusia Cloudflare zone is ambiguous")
    account_id = zones[0]["account"]["id"]
    tunnels = get(f"{base}/accounts/{account_id}/cfd_tunnel?is_deleted=false")["result"]
    matching = [tunnel for tunnel in tunnels if tunnel["name"] == TUNNEL_NAME]
    if len(matching) != 1 or matching[0].get("config_src") != "cloudflare":
        raise RuntimeError("Production Cloudflare tunnel is ambiguous")
    tunnel_id = matching[0]["id"]
    payload = get(f"{base}/accounts/{account_id}/cfd_tunnel/{tunnel_id}/token")
    token = payload.get("result") if payload.get("success") else None
    if not isinstance(token, str) or not re.fullmatch(r"[A-Za-z0-9._=-]{100,8192}", token):
        kind = type(token).__name__
        length = len(token) if isinstance(token, str) else 0
        raise RuntimeError(f"Cloudflare returned no valid connector token (type={kind}, length={length})")
    return token


def file_digest(stream) -> str:
    digest = hashlib.sha256()
    while chunk := stream.read(1024 * 1024):
        digest.update(chunk)
    return digest.hexdigest()


def upload_jar(sftp: paramiko.SFTPClient) -> None:
    with LOCAL_JAR.open("rb") as local:
        expected = file_digest(local)
    with sftp.open(REMOTE_JAR, "rb") as current:
        current_digest = file_digest(current)
    if current_digest == expected:
        print(f"production_staff_jar_sha256={expected} (already installed)")
        return
    if current_digest not in (PREVIOUS_JAR_SHA256, FIRST_WEB_JAR_SHA256):
        raise RuntimeError("Remote Staff Bot JAR differs from the verified previous deployment")

    temporary = ".EnthusiaStaff-StaffBot.jar.upload"
    backup = REMOTE_JAR + ".backup-web-" + datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    try:
        sftp.put(str(LOCAL_JAR), temporary, confirm=True)
        with sftp.open(temporary, "rb") as uploaded:
            if file_digest(uploaded) != expected:
                raise RuntimeError("Uploaded Staff Bot JAR checksum mismatch")
        sftp.rename(REMOTE_JAR, backup)
        try:
            sftp.rename(temporary, REMOTE_JAR)
        except Exception:
            sftp.rename(backup, REMOTE_JAR)
            raise
    finally:
        try:
            sftp.remove(temporary)
        except FileNotFoundError:
            pass
    print(f"production_staff_jar_sha256={expected}")
    print(f"previous_jar_backup={backup}")


def main() -> None:
    host, port, username, password = bloom_connection()
    mode = sys.argv[1] if len(sys.argv) == 2 else "install-token"
    audit_only = mode == "--audit"
    if mode not in ("install-token", "--audit", "--upload-jar"):
        raise RuntimeError("Unsupported command line argument")
    token = connector_token().encode("ascii") if mode == "install-token" else None
    host_keys = paramiko.HostKeys()
    host_keys.load(str(HOST_KEYS_FILE))
    host_label = f"[{host}]:{port}"
    if host_keys.lookup(host_label) is None:
        raise RuntimeError("Trusted Bloom host key is unavailable")

    client = paramiko.SSHClient()
    client.load_host_keys(str(HOST_KEYS_FILE))
    client.set_missing_host_key_policy(paramiko.RejectPolicy())
    try:
        client.connect(host, port=port, username=username, password=password,
                       look_for_keys=False, allow_agent=False, timeout=20)
        with client.open_sftp() as sftp:
            with sftp.open("m", "r") as config:
                lines = config.read().decode("utf-8").splitlines()
            enforcement = [line.strip() for line in lines
                           if line.strip().startswith("discord-enforcement.enabled=")]
            if enforcement != ["discord-enforcement.enabled=false"]:
                raise RuntimeError("Discord enforcement must remain disabled")
            if audit_only:
                with sftp.open(REMOTE_JAR, "rb") as current:
                    digest = file_digest(current)
                print(f"remote_staff_jar_sha256={digest}")
                print("discord_enforcement_disabled=true")
                return
            if mode == "--upload-jar":
                upload_jar(sftp)
                return
            try:
                with sftp.open(REMOTE_NAME, "rb") as existing:
                    if hashlib.sha256(existing.read()).digest() != hashlib.sha256(token).digest():
                        raise RuntimeError("Existing production tunnel token differs; refusing replacement")
            except FileNotFoundError:
                temporary = f".{REMOTE_NAME}.upload"
                try:
                    sftp.putfo(io.BytesIO(token), temporary)
                    sftp.chmod(temporary, 0o600)
                    sftp.rename(temporary, REMOTE_NAME)
                finally:
                    try:
                        sftp.remove(temporary)
                    except FileNotFoundError:
                        pass
            attributes = sftp.stat(REMOTE_NAME)
            if attributes.st_mode & 0o077:
                sftp.chmod(REMOTE_NAME, 0o600)
            with sftp.open(REMOTE_NAME, "rb") as installed:
                if hashlib.sha256(installed.read()).digest() != hashlib.sha256(token).digest():
                    raise RuntimeError("Production tunnel token upload did not verify")
    finally:
        client.close()
    print("production tunnel connector installed and verified")


if __name__ == "__main__":
    main()
