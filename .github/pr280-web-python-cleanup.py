from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one guarded match in {path}, found {count}")
    file.write_text(text.replace(old, new))


Path("moderation-web/deploy-production-bloom.py").write_text('''"""Install only the production Cloudflare Tunnel connector token on the Bloom Staff Bot split."""

from __future__ import annotations

import hashlib
import http.client
import importlib
import io
import json
import re
import sys
import tomllib
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


paramiko: Any = importlib.import_module("paramiko")

TUNNEL_NAME = "enthusia-moderation-read-production"
REMOTE_NAME = "prod-tunnel"
REMOTE_JAR = "EnthusiaStaff-StaffBot.jar"
PREVIOUS_JAR_SHA256 = "9a12cefd06b5158829ec8df4c4ed28c2d07bb119b2f1adbe546bc3fd31a7ce01"
FIRST_WEB_JAR_SHA256 = "0e4a9c7c3cb4bceddd550843578d9b74e184f94c6fd132c3f35a7f9e1bc88ba8"
PRODUCTION_WEB_JAR_SHA256 = "8cd85417ce26c66a851054fcb8ea2a4df29871917fd9bf86019271317d56ed4c"
ACTION_WEB_JAR_SHA256 = "4702525c31861cdf0692288e38583fd32a0adc586b93c318e302745bcb585caf"
HISTORY_WEB_JAR_SHA256 = "8d682c8edc8c7c88bae5441719d19dd10060c3b74b619d1073d35d36186a3708"
KNOWN_JAR_SHA256 = {
    PREVIOUS_JAR_SHA256,
    FIRST_WEB_JAR_SHA256,
    PRODUCTION_WEB_JAR_SHA256,
    ACTION_WEB_JAR_SHA256,
    HISTORY_WEB_JAR_SHA256,
}
LOCAL_JAR = Path(__file__).resolve().parent.parent / "staff-bot/build/libs/EnthusiaStaff-StaffBot-0.1.0-SNAPSHOT.jar"
DETAILS_FILE = Path.home() / "OneDrive/Desktop/SFTP Details- ENTHUSIA NETWORK.md"
HOST_KEYS_FILE = Path.home() / ".ssh/known_hosts_sentinel_bloom"
WRANGLER_CREDENTIALS = Path.home() / ".wrangler/config/default.toml"
CLOUDFLARE_HOST = "api.cloudflare.com"
CLOUDFLARE_PREFIX = "/client/v4/"


def bloom_connection() -> tuple[str, int, str, str]:
    lines = DETAILS_FILE.read_text(encoding="utf-8-sig").splitlines()
    password = bloom_password(lines)
    username = lines[79].strip()
    host, port = bloom_endpoint(lines[77].strip(), username)
    return host, port, username, password


def bloom_password(lines: list[str]) -> str:
    label, separator, value = lines[0].partition(":")
    if label.strip() != "All passwords" or not separator or not value.strip():
        raise RuntimeError("Bloom SFTP details are unavailable")
    return value.strip()


def bloom_endpoint(raw_url: str, username: str) -> tuple[str, int]:
    url = urlparse(raw_url)
    if url.scheme != "sftp":
        raise RuntimeError("Bloom Staff Bot SFTP endpoint is unavailable")
    if url.hostname is None or url.port is None:
        raise RuntimeError("Bloom Staff Bot SFTP endpoint is unavailable")
    if url.netloc.rsplit(":", 1)[0].lower() != url.hostname.lower():
        raise RuntimeError("Bloom Staff Bot SFTP endpoint is unavailable")
    if not re.fullmatch(r"[A-Za-z0-9._-]{3,120}", username):
        raise RuntimeError("Bloom Staff Bot SFTP endpoint is unavailable")
    return url.hostname, url.port


def wrangler_oauth_token() -> str:
    with WRANGLER_CREDENTIALS.open("rb") as stream:
        oauth_token = tomllib.load(stream).get("oauth_token")
    if not isinstance(oauth_token, str) or not oauth_token:
        raise RuntimeError("Cloudflare authorization is unavailable")
    return oauth_token


def cloudflare_get(oauth_token: str, path: str) -> dict[str, Any]:
    if not path.startswith(CLOUDFLARE_PREFIX) or "://" in path:
        raise RuntimeError("Cloudflare API path is invalid")
    connection = http.client.HTTPSConnection(CLOUDFLARE_HOST, timeout=20)
    try:
        connection.request("GET", path, headers={"Authorization": f"Bearer {oauth_token}"})
        response = connection.getresponse()
        if response.status < 200 or response.status >= 300:
            raise RuntimeError("Cloudflare API request failed")
        payload = json.load(response)
    finally:
        connection.close()
    if not isinstance(payload, dict) or not payload.get("success"):
        raise RuntimeError("Cloudflare API rejected the tunnel lookup")
    return payload


def cloudflare_id(value: object, label: str) -> str:
    if not isinstance(value, str) or not re.fullmatch(r"[A-Za-z0-9_-]{1,128}", value):
        raise RuntimeError(f"Cloudflare returned an invalid {label}")
    return value


def cloudflare_account_id(oauth_token: str) -> str:
    payload = cloudflare_get(oauth_token, f"{CLOUDFLARE_PREFIX}zones?name=enthusia.info&status=active")
    zones = payload.get("result")
    if not isinstance(zones, list) or len(zones) != 1:
        raise RuntimeError("Enthusia Cloudflare zone is ambiguous")
    account = zones[0].get("account") if isinstance(zones[0], dict) else None
    account_id = account.get("id") if isinstance(account, dict) else None
    return cloudflare_id(account_id, "account id")


def cloudflare_tunnel_id(oauth_token: str, account_id: str) -> str:
    path = f"{CLOUDFLARE_PREFIX}accounts/{account_id}/cfd_tunnel?is_deleted=false"
    tunnels = cloudflare_get(oauth_token, path).get("result")
    if not isinstance(tunnels, list):
        raise RuntimeError("Production Cloudflare tunnel is ambiguous")
    matching = [item for item in tunnels if isinstance(item, dict) and item.get("name") == TUNNEL_NAME]
    if len(matching) != 1 or matching[0].get("config_src") != "cloudflare":
        raise RuntimeError("Production Cloudflare tunnel is ambiguous")
    return cloudflare_id(matching[0].get("id"), "tunnel id")


def connector_token() -> str:
    oauth_token = wrangler_oauth_token()
    account_id = cloudflare_account_id(oauth_token)
    tunnel_id = cloudflare_tunnel_id(oauth_token, account_id)
    path = f"{CLOUDFLARE_PREFIX}accounts/{account_id}/cfd_tunnel/{tunnel_id}/token"
    token = cloudflare_get(oauth_token, path).get("result")
    if not isinstance(token, str) or not re.fullmatch(r"[A-Za-z0-9._=-]{100,8192}", token):
        kind = type(token).__name__
        length = len(token) if isinstance(token, str) else 0
        raise RuntimeError(f"Cloudflare returned no valid connector token (type={kind}, length={length})")
    return token


def file_digest(stream: Any) -> str:
    digest = hashlib.sha256()
    while chunk := stream.read(1024 * 1024):
        digest.update(chunk)
    return digest.hexdigest()


def upload_jar(sftp: Any) -> None:
    with LOCAL_JAR.open("rb") as local:
        expected = file_digest(local)
    with sftp.open(REMOTE_JAR, "rb") as current:
        current_digest = file_digest(current)
    if current_digest == expected:
        print(f"production_staff_jar_sha256={expected} (already installed)")
        return
    if current_digest not in KNOWN_JAR_SHA256:
        raise RuntimeError("Remote Staff Bot JAR differs from the verified previous deployment")

    temporary = ".EnthusiaStaff-StaffBot.jar.upload"
    backup = REMOTE_JAR + ".backup-web-" + str(int(datetime.now(timezone.utc).timestamp()))
    try:
        sftp.put(str(LOCAL_JAR), temporary, confirm=True)
        verify_uploaded_jar(sftp, temporary, expected)
        sftp.rename(REMOTE_JAR, backup)
        promote_uploaded_jar(sftp, temporary, backup)
    finally:
        remove_if_present(sftp, temporary)
    print(f"production_staff_jar_sha256={expected}")
    print(f"previous_jar_backup={backup}")


def verify_uploaded_jar(sftp: Any, temporary: str, expected: str) -> None:
    with sftp.open(temporary, "rb") as uploaded:
        if file_digest(uploaded) != expected:
            raise RuntimeError("Uploaded Staff Bot JAR checksum mismatch")


def promote_uploaded_jar(sftp: Any, temporary: str, backup: str) -> None:
    try:
        sftp.rename(temporary, REMOTE_JAR)
    except Exception:
        sftp.rename(backup, REMOTE_JAR)
        raise


def remove_if_present(sftp: Any, path: str) -> None:
    try:
        sftp.remove(path)
    except FileNotFoundError:
        pass


def command_mode(argv: list[str]) -> str:
    mode = argv[1] if len(argv) == 2 else "install-token"
    if mode not in ("install-token", "--audit", "--upload-jar"):
        raise RuntimeError("Unsupported command line argument")
    return mode


def connected_client(host: str, port: int, username: str, password: str) -> Any:
    host_keys = paramiko.HostKeys()
    host_keys.load(str(HOST_KEYS_FILE))
    host_label = f"[{host}]:{port}"
    if host_keys.lookup(host_label) is None:
        raise RuntimeError("Trusted Bloom host key is unavailable")
    client = paramiko.SSHClient()
    client.load_host_keys(str(HOST_KEYS_FILE))
    client.set_missing_host_key_policy(paramiko.RejectPolicy())
    client.connect(host, port=port, username=username, password=password,
                   look_for_keys=False, allow_agent=False, timeout=20)
    return client


def require_discord_enforcement_disabled(sftp: Any) -> None:
    with sftp.open("m", "r") as config:
        lines = config.read().decode("utf-8").splitlines()
    enforcement = [line.strip() for line in lines
                   if line.strip().startswith("discord-enforcement.enabled=")]
    if enforcement != ["discord-enforcement.enabled=false"]:
        raise RuntimeError("Discord enforcement must remain disabled")


def audit_remote(sftp: Any) -> None:
    with sftp.open(REMOTE_JAR, "rb") as current:
        digest = file_digest(current)
    print(f"remote_staff_jar_sha256={digest}")
    print("discord_enforcement_disabled=true")


def existing_token_matches(sftp: Any, token: bytes) -> bool:
    try:
        with sftp.open(REMOTE_NAME, "rb") as existing:
            installed = existing.read()
    except FileNotFoundError:
        return False
    if hashlib.sha256(installed).digest() != hashlib.sha256(token).digest():
        raise RuntimeError("Existing production tunnel token differs; refusing replacement")
    return True


def write_token(sftp: Any, token: bytes) -> None:
    temporary = f".{REMOTE_NAME}.upload"
    try:
        sftp.putfo(io.BytesIO(token), temporary)
        sftp.chmod(temporary, 0o600)
        sftp.rename(temporary, REMOTE_NAME)
    finally:
        remove_if_present(sftp, temporary)


def secure_installed_token(sftp: Any, token: bytes) -> None:
    attributes = sftp.stat(REMOTE_NAME)
    installed_mode = attributes.st_mode
    if installed_mode is None:
        raise RuntimeError("Production tunnel token mode is unavailable")
    if installed_mode & 0o077:
        sftp.chmod(REMOTE_NAME, 0o600)
    with sftp.open(REMOTE_NAME, "rb") as installed:
        installed_token = installed.read()
    if hashlib.sha256(installed_token).digest() != hashlib.sha256(token).digest():
        raise RuntimeError("Production tunnel token upload did not verify")


def install_connector_token(sftp: Any) -> None:
    token = connector_token().encode("ascii")
    if not existing_token_matches(sftp, token):
        write_token(sftp, token)
    secure_installed_token(sftp, token)


def execute_mode(sftp: Any, mode: str) -> None:
    require_discord_enforcement_disabled(sftp)
    if mode == "--audit":
        audit_remote(sftp)
    elif mode == "--upload-jar":
        upload_jar(sftp)
    else:
        install_connector_token(sftp)


def main() -> None:
    host, port, username, password = bloom_connection()
    mode = command_mode(sys.argv)
    client = connected_client(host, port, username, password)
    try:
        with client.open_sftp() as sftp:
            execute_mode(sftp, mode)
    finally:
        client.close()
    if mode == "install-token":
        print("production tunnel connector installed and verified")


if __name__ == "__main__":
    main()
''')

replace_once(
    "moderation-web/scripts/build.mjs",
    "await writeFile(directRead, contents.replaceAll(staging, production));",
    "await writeFile(directRead, contents.split(staging).join(production));",
)

backend_old = '''export async function prepareModerationAction(env, session, operation, input) {
  if (env.RUNTIME_ENVIRONMENT !== 'production') throw new Error('live actions require production');
  if (!['capabilities', 'prepare', 'confirm', 'status'].includes(operation)) throw new Error('invalid action operation');
  requireFilterObject(input);
  requireFilterKeys(input, new Set(['targetKey', 'intent', 'confirmationId', 'minecraftTarget', 'minecraftIntent']));
  const minecraft = input.minecraftTarget !== undefined || input.minecraftIntent !== undefined;
  if (minecraft) {
    if (input.intent !== undefined || operation === 'capabilities') throw new Error('cannot mix action scopes');
    if (typeof input.minecraftTarget !== 'string' || !/^(?:[A-Za-z0-9_]{1,16}|[a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12})$/.test(input.minecraftTarget)) throw new Error('invalid Minecraft player');
  }
  const targetKey = input.targetKey === undefined ? session.targetKey : input.targetKey;
  if (typeof targetKey !== 'string' || !/^(channel:[1-9][0-9]{0,19}|discord:[1-9][0-9]{0,19}|discord-channel:[1-9][0-9]{0,19}:[1-9][0-9]{0,19}|message:[1-9][0-9]{0,19}:[1-9][0-9]{0,19}:[1-9][0-9]{0,19})$/.test(targetKey)) throw new Error('invalid action target');
  if (operation === 'prepare') {
    if (input.confirmationId !== undefined) throw new Error('invalid draft');
    if (minecraft) {
      requireFilterObject(input.minecraftIntent);
      requireFilterKeys(input.minecraftIntent, new Set(['reasonId', 'explanation']));
      if (typeof input.minecraftIntent.reasonId !== 'string' || input.minecraftIntent.reasonId.length > 96
          || !/^[a-z0-9]+(?:[.-][a-z0-9]+)*$/.test(input.minecraftIntent.reasonId)
          || typeof input.minecraftIntent.explanation !== 'string' || input.minecraftIntent.explanation.length > 4000) throw new Error('invalid configured Minecraft intent');
    } else {
      requireFilterObject(input.intent);
      requireFilterKeys(input.intent, new Set(['type', 'duration', 'reason', 'explanation', 'restriction']));
    }
  } else if (input.intent !== undefined || input.minecraftIntent !== undefined) throw new Error('cannot change prepared intent');
  if (operation === 'confirm' || operation === 'status') {
    if (typeof input.confirmationId !== 'string' || !/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(input.confirmationId)) throw new Error('invalid confirmation');
  } else if (input.confirmationId !== undefined) throw new Error('invalid confirmation');
  const keyHex = readSigningKey(env);
  if (!keyHex) return unavailable();
  const sessionBinding = hex(new Uint8Array(await crypto.subtle.digest('SHA-256', textEncoder.encode(session.csrfToken))));
  const body = JSON.stringify({actorId:session.actorId, guildId:session.guildId, targetKey,
    sessionBinding, intent:input.intent ?? null, confirmationId:input.confirmationId ?? null,
    minecraftTarget:input.minecraftTarget ?? null, minecraftIntent:input.minecraftIntent ?? null});
  if (textEncoder.encode(body).length > 65_536) throw new Error('action body too large');
  const path = '/v1/moderation/actions/' + operation;
  const timestamp = String(Math.floor(Date.now() / 1000));
  const nonce = randomToken(24);
  const signature = await signRequest(keyHex, 'POST', path, body, timestamp, nonce);
  return new Response(JSON.stringify({origin:PRODUCTION_READ_API_ORIGIN, path, method:'POST', body, timestamp, nonce, signature}),
    {headers:{'Content-Type':'application/json; charset=utf-8', 'Cache-Control':'private, no-store'}});
}
'''

backend_new = '''export async function prepareModerationAction(env, session, operation, input) {
  requireLiveActionEnvironment(env);
  requireActionOperation(operation);
  const targetKey = validateActionInput(session, operation, input);
  const keyHex = readSigningKey(env);
  if (!keyHex) return unavailable();
  const body = await actionRequestBody(session, input, targetKey);
  return signedActionResponse(keyHex, operation, body);
}

function requireLiveActionEnvironment(env) {
  if (env.RUNTIME_ENVIRONMENT !== 'production') throw new Error('live actions require production');
}

function requireActionOperation(operation) {
  if (!['capabilities', 'prepare', 'confirm', 'status'].includes(operation)) {
    throw new Error('invalid action operation');
  }
}

function validateActionInput(session, operation, input) {
  requireFilterObject(input);
  requireFilterKeys(input, new Set(['targetKey', 'intent', 'confirmationId', 'minecraftTarget', 'minecraftIntent']));
  const minecraft = minecraftAction(input);
  validateActionScope(operation, input, minecraft);
  const targetKey = actionTargetKey(session, input);
  validateActionPayload(operation, input, minecraft);
  return targetKey;
}

function minecraftAction(input) {
  return input.minecraftTarget !== undefined || input.minecraftIntent !== undefined;
}

function validateActionScope(operation, input, minecraft) {
  if (!minecraft) return;
  if (input.intent !== undefined || operation === 'capabilities') throw new Error('cannot mix action scopes');
  if (!validMinecraftTarget(input.minecraftTarget)) throw new Error('invalid Minecraft player');
}

function validMinecraftTarget(value) {
  return typeof value === 'string'
    && /^(?:[A-Za-z0-9_]{1,16}|[a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12})$/.test(value);
}

function actionTargetKey(session, input) {
  const targetKey = input.targetKey === undefined ? session.targetKey : input.targetKey;
  if (typeof targetKey !== 'string'
      || !/^(channel:[1-9][0-9]{0,19}|discord:[1-9][0-9]{0,19}|discord-channel:[1-9][0-9]{0,19}:[1-9][0-9]{0,19}|message:[1-9][0-9]{0,19}:[1-9][0-9]{0,19}:[1-9][0-9]{0,19})$/.test(targetKey)) {
    throw new Error('invalid action target');
  }
  return targetKey;
}

function validateActionPayload(operation, input, minecraft) {
  if (operation === 'prepare') {
    validatePreparedAction(input, minecraft);
    return;
  }
  requireUnchangedPreparedIntent(input);
  validateConfirmation(operation, input.confirmationId);
}

function validatePreparedAction(input, minecraft) {
  if (input.confirmationId !== undefined) throw new Error('invalid draft');
  if (minecraft) {
    validateMinecraftIntent(input.minecraftIntent);
    return;
  }
  requireFilterObject(input.intent);
  requireFilterKeys(input.intent, new Set(['type', 'duration', 'reason', 'explanation', 'restriction']));
}

function validateMinecraftIntent(intent) {
  requireFilterObject(intent);
  requireFilterKeys(intent, new Set(['reasonId', 'explanation']));
  if (!validConfiguredReasonId(intent.reasonId)
      || typeof intent.explanation !== 'string' || intent.explanation.length > 4000) {
    throw new Error('invalid configured Minecraft intent');
  }
}

function validConfiguredReasonId(value) {
  if (typeof value !== 'string' || value.length < 1 || value.length > 96) return false;
  let separator = false;
  for (let index = 0; index < value.length; index += 1) {
    const character = value[index];
    if (lowerAlphaNumeric(character)) {
      separator = false;
      continue;
    }
    if ((character !== '.' && character !== '-') || separator || index === 0 || index === value.length - 1) {
      return false;
    }
    separator = true;
  }
  return true;
}

function lowerAlphaNumeric(character) {
  return (character >= 'a' && character <= 'z') || (character >= '0' && character <= '9');
}

function requireUnchangedPreparedIntent(input) {
  if (input.intent !== undefined || input.minecraftIntent !== undefined) {
    throw new Error('cannot change prepared intent');
  }
}

function validateConfirmation(operation, confirmationId) {
  const required = operation === 'confirm' || operation === 'status';
  if (required && !validConfirmationId(confirmationId)) throw new Error('invalid confirmation');
  if (!required && confirmationId !== undefined) throw new Error('invalid confirmation');
}

function validConfirmationId(value) {
  return typeof value === 'string' && /^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(value);
}

async function actionRequestBody(session, input, targetKey) {
  const digest = await crypto.subtle.digest('SHA-256', textEncoder.encode(session.csrfToken));
  const sessionBinding = hex(new Uint8Array(digest));
  const body = JSON.stringify({actorId:session.actorId, guildId:session.guildId, targetKey,
    sessionBinding, intent:input.intent ?? null, confirmationId:input.confirmationId ?? null,
    minecraftTarget:input.minecraftTarget ?? null, minecraftIntent:input.minecraftIntent ?? null});
  if (textEncoder.encode(body).length > 65_536) throw new Error('action body too large');
  return body;
}

async function signedActionResponse(keyHex, operation, body) {
  const path = '/v1/moderation/actions/' + operation;
  const timestamp = String(Math.floor(Date.now() / 1000));
  const nonce = randomToken(24);
  const signature = await signRequest(keyHex, 'POST', path, body, timestamp, nonce);
  return new Response(JSON.stringify({origin:PRODUCTION_READ_API_ORIGIN, path, method:'POST', body, timestamp, nonce, signature}),
    {headers:{'Content-Type':'application/json; charset=utf-8', 'Cache-Control':'private, no-store'}});
}
'''

replace_once("moderation-web/src/backend.js", backend_old, backend_new)

Path("staff-bot/src/main/resources/moderation-preview/live-actions.js").write_text('''\'use strict\';

let liveActionCapabilities = null;
const actionLoadSession = window.loadSession;
window.loadSession = async function () {
  await actionLoadSession();
  if (state.session?.staging !== false) return;
  try { liveActionCapabilities = await requestModerationAction('capabilities', {}); }
  catch { liveActionCapabilities = null; }
};

async function requestModerationAction(operation, input) {
  requireModerationActionSession(operation);
  const proofResponse = await requestActionProof(operation, actionRequestOptions(input));
  if (!proofResponse.ok) throw new Error('Action session rejected. Reopen from Discord.');
  const proof = await proofResponse.json();
  requireValidActionProof(proof, operation);
  const result = await submitActionProof(operation, directReadRequest(proof));
  if (!result.ok) throw moderationActionError(result.status);
  return result.json();
}

function requireModerationActionSession(operation) {
  if (!state.session || !['capabilities','prepare','confirm','status'].includes(operation)) {
    throw new Error('Session unavailable');
  }
}

function actionRequestOptions(input) {
  return {
    method:'POST', cache:'no-store', headers:{'Content-Type':'application/json','X-Preview-Csrf':state.session.csrfToken},
    body:JSON.stringify(input)
  };
}

function requestActionProof(operation, options) {
  switch (operation) {
    case 'capabilities': return fetch('/api/actions/capabilities', options);
    case 'prepare': return fetch('/api/actions/prepare', options);
    case 'confirm': return fetch('/api/actions/confirm', options);
    case 'status': return fetch('/api/actions/status', options);
    default: throw new Error('Session unavailable');
  }
}

function requireValidActionProof(proof, operation) {
  if (proof.origin !== DIRECT_READ_ORIGIN || proof.path !== '/v1/moderation/actions/' + operation
      || proof.method !== 'POST' || !validDirectReadBody(proof.body) || !validDirectReadAuthentication(proof)) {
    throw new Error('Action proof rejected');
  }
}

function submitActionProof(operation, request) {
  switch (operation) {
    case 'capabilities': return fetch('https://moderation-read-staging.enthusia.info/v1/moderation/actions/capabilities', request);
    case 'prepare': return fetch('https://moderation-read-staging.enthusia.info/v1/moderation/actions/prepare', request);
    case 'confirm': return fetch('https://moderation-read-staging.enthusia.info/v1/moderation/actions/confirm', request);
    case 'status': return fetch('https://moderation-read-staging.enthusia.info/v1/moderation/actions/status', request);
    default: throw new Error('Session unavailable');
  }
}

function moderationActionError(status) {
  if (status === 403) return new Error('Current staff authority denied this action.');
  if (status === 400) return new Error('Action rejected. Check the target, duration and permissions, then prepare again.');
  return new Error('Moderation service unavailable. Check status before submitting another action.');
}

function liveActionInput(w) {
  requireLiveActionContext(w);
  const type = liveConsequenceType(w.actual.action);
  const duration = type === 'WARNING' || type === 'KICK' ? 'instant' : actionDuration(w.duration);
  const intent = {type, duration, reason:w.offense.label, explanation:liveExplanation(w), restriction:null};
  intent.restriction = liveRestriction(w, type);
  return {targetKey:liveModeration.bootstrap?.targetKey, intent};
}

function requireLiveActionContext(workflow) {
  if (!liveActionCapabilities?.discordEnabled) throw new Error('Discord enforcement is not enabled yet.');
  if (workflow.scope !== 'Discord') throw new Error('Minecraft enforcement has not passed activation checks.');
  if (state.deleting.size) throw new Error('Clear deletion selections. Message deletion is not enabled.');
  if (!workflow.dm) throw new Error('Live actions require a target notification.');
}

function liveConsequenceType(action) {
  const types = {Warning:'WARNING', Mute:'MUTE', Kick:'KICK', Ban:'BAN', Restrict:'CHANNEL_RESTRICTION'};
  const type = types[action];
  if (!type) throw new Error('Unsupported action');
  return type;
}

function liveExplanation(workflow) {
  const evidence = [...state.evidence].map(id => 'Discord message reference: ' + id);
  if (workflow.externalEvidence) evidence.push('External evidence reference: ' + workflow.externalEvidence);
  const explanation = [workflow.reason, ...evidence].filter(Boolean).join('\n');
  if (explanation.length > 2000) throw new Error('Evidence references and explanation exceed 2000 characters.');
  return explanation;
}

function liveRestriction(workflow, type) {
  if (type !== 'CHANNEL_RESTRICTION') return null;
  const targets = restrictionTargetSelections(workflow);
  if (targets.length !== 1) throw new Error('Select exactly one channel or category per restriction.');
  return {kind:targets[0].type.toUpperCase(), snowflake:targets[0].id,
    mode:workflow.restrictMode === 'read-only' ? 'READ_ONLY' : 'NO_ACCESS'};
}

function actionDuration(label) {
  if (label === 'Permanent') return 'permanent';
  const match = /^([1-9][0-9]*) (minutes?|hours?|days?)$/.exec(label);
  if (!match) throw new Error('Select a valid duration.');
  return match[1] + ({m:'m',h:'h',d:'d'}[match[2][0]]);
}

const simulationReviewStep = window.renderReviewStep;
window.renderReviewStep = function () {
  simulationReviewStep();
  if (state.session?.staging !== false) return;
  const w = state.workflow;
  const confirm = $('[data-confirm]');
  if (confirm) { confirm.disabled = true; confirm.textContent = 'Preparing live action…'; }
  w.livePrepared = null;
  try {
    const input = liveActionInput(w);
    requestModerationAction('prepare', input).then(prepared => {
      if (state.workflow !== w || w.step !== 'review') return;
      w.livePrepared = {...prepared, targetKey:input.targetKey};
      $('#workflowBody').appendChild(element('div',{className:'alert warning'},
        element('strong',{text:'Server-prepared live action'}),
        element('span',{text:`${prepared.intent.type} · ${prepared.targetUserId} · ${prepared.intent.length.kind}. Target notifications are included. Authority is checked again on confirmation.`})));
      if (confirm) { confirm.disabled = false; confirm.textContent = 'Confirm live action'; }
    }).catch(error => { if (state.workflow === w) showToast(error.message, true); });
  } catch (error) {
    if (confirm) confirm.textContent = 'Live action unavailable';
    $('#workflowBody').appendChild(element('div',{className:'alert warning',text:error.message}));
  }
};

const simulationConfirm = window.confirmSimulation;
window.confirmSimulation = async function () {
  if (state.session?.staging !== false) return simulationConfirm();
  const workflow = state.workflow;
  if (!readyForLiveConfirmation(workflow)) return;
  await submitLiveConfirmation(workflow);
};

function readyForLiveConfirmation(workflow) {
  if (!workflow.livePrepared || workflow.submitting) return false;
  if (workflow.stale || workflow.recommendationEvidenceRevision !== state.evidenceRevision) {
    showToast('Evidence changed. Recalculate and prepare the action again.',true);
    return false;
  }
  return true;
}

async function submitLiveConfirmation(workflow) {
  workflow.submitting = true;
  $('[data-confirm]').disabled = true;
  const input = {targetKey:workflow.livePrepared.targetKey, confirmationId:workflow.livePrepared.confirmationId};
  try {
    workflow.liveStatus = await requestModerationAction('confirm', input);
    workflow.step = 'complete';
    renderWorkflow();
    await pollLiveActionStatus(workflow, input);
  } catch (error) {
    await recoverLiveActionStatus(workflow, input, error);
  } finally {
    workflow.submitting = false;
  }
}

async function pollLiveActionStatus(workflow, input) {
  for (let attempt = 0; attempt < 12 && workflow.liveStatus.state === 'PENDING_APPLY'; attempt += 1) {
    await new Promise(resolve => setTimeout(resolve, 1500));
    workflow.liveStatus = await requestModerationAction('status', input);
    if (state.workflow === workflow) renderWorkflow();
  }
}

async function recoverLiveActionStatus(workflow, input, error) {
  try {
    workflow.liveStatus = await requestModerationAction('status', input);
    workflow.step = 'complete';
    renderWorkflow();
  } catch {
    showToast(error.message + ' Do not submit a replacement until its status is checked.',true);
  }
}

const simulationComplete = window.renderCompleteStep;
window.renderCompleteStep = function () {
  if (state.session?.staging !== false) return simulationComplete();
  const result = state.workflow?.liveStatus;
  $('#workflowTitle').textContent = 'Live action status';
  $('#workflowSteps').replaceChildren();
  replaceChildrenOf($('#workflowBody'), element('section',{className:'card'},
    element('h3',{text:result?.state || 'Status unavailable'}),
    element('p',{text:result?.externalApplied ? 'Discord applied the action.' : 'Discord has not confirmed the effect.'}),
    element('p',{text:'Target notification: ' + (result?.dmOutcome || 'Unknown')}),
    element('p',{text:'Punishment ID: ' + (result?.punishmentId || 'Unknown')})));
  replaceChildrenOf($('#workflowFooter'),buttonNode('Done','button primary',{done:''}));
  $('[data-done]').addEventListener('click',closeWorkflow);
};

const simulationBoundary = window.testEnvironmentBoundary;
window.testEnvironmentBoundary = function () {
  if (state.session?.staging !== false) return simulationBoundary();
  return element('div',{className:'simulation-boundary'},element('strong',{text:liveActionCapabilities?.discordEnabled ? 'Live Discord moderation' : 'Discord enforcement disabled'}),
    element('span',{text:liveActionCapabilities?.discordEnabled
      ? 'Confirming applies a durable Discord punishment and queues a target notification. Evidence references are recorded; message contents are not archived and no messages are deleted.'
      : 'Live messages and staff data are connected. Punishment confirmation is unavailable until the enforcement policy is activated.'}));
};

const simulationApprovalRequired = window.workflowApprovalRequired;
window.workflowApprovalRequired = function (workflow) {
  return state.session?.staging === false ? false : simulationApprovalRequired(workflow);
};
const simulationApprovalText = window.approvalReviewText;
window.approvalReviewText = function (workflow) {
  return state.session?.staging === false ? 'Current authority checked by the server' : simulationApprovalText(workflow);
};
const simulationScopeField = window.scopeField;
window.scopeField = function (workflow) {
  if (state.session?.staging !== false) return simulationScopeField(workflow);
  workflow.scope = 'Discord';
  return fieldLabel('Scope',element('select',{id:'customScope',disabled:true},optionNode('Discord','Discord',true)));
};
const simulationOffenseStep = window.renderOffenseStep;
window.renderOffenseStep = function () {
  simulationOffenseStep();
  if (state.session?.staging !== false) return;
  $('[data-offense-tab="game"]')?.remove();
};
''')

Path("staff-bot/src/main/resources/moderation-preview/live-minecraft-actions.js").write_text('''\'use strict\';

let minecraftWorkflow = null;
const originalMinecraftBoundary = window.testEnvironmentBoundary;
window.testEnvironmentBoundary = function () {
  if (state.session?.staging !== false || !liveActionCapabilities?.minecraftEnabled) return originalMinecraftBoundary();
  return element('div',{className:'simulation-boundary'},
    element('strong',{text:'Live Minecraft moderation'}),
    element('span',{text:'Configured punishments apply to the network after confirmation or required staff approval. Discord enforcement is '
      + (liveActionCapabilities.discordEnabled ? 'enabled.' : 'disabled.') + ' Message deletion is unavailable.'}));
};
const originalLiveOpenWorkflow = window.openWorkflow;
const originalLiveRenderWorkflow = window.renderWorkflow;
window.renderWorkflow = function () {
  return minecraftWorkflow ? renderMinecraftPunishment() : originalLiveRenderWorkflow();
};
window.openWorkflow = function () {
  if (state.session?.staging !== false || !liveActionCapabilities?.minecraftEnabled) return originalLiveOpenWorkflow();
  if (state.deleting.size) return showToast('Clear message deletion selections before issuing a Minecraft punishment.', true);
  minecraftWorkflow = {target:'', reason:'', explanation:'', prepared:null, result:null, busy:false, uncertain:false};
  state.workflow = {minecraft:true};
  const accounts = liveModeration.bootstrap?.linkedAccounts || [];
  if (accounts.length === 1) minecraftWorkflow.target = accounts[0].playerId;
  if (liveActionCapabilities.discordEnabled) {
    renderScopeChoice();
    return;
  }
  renderMinecraftPunishment();
  $('#punishmentDialog').showModal();
  $('#minecraftTarget')?.focus();
};

function renderScopeChoice() {
  $('#workflowTitle').textContent = 'Choose punishment scope';
  $('#workflowSteps').replaceChildren();
  $('#workflowBody').replaceChildren(element('p',{text:'Choose the account and service this punishment applies to.'}));
  const minecraft = buttonNode('Minecraft','button primary',{});
  minecraft.addEventListener('click',renderMinecraftPunishment);
  const discord = buttonNode('Discord','button secondary',{});
  discord.addEventListener('click',() => {
    minecraftWorkflow = null;
    $('#punishmentDialog').close();
    originalLiveOpenWorkflow();
  });
  $('#workflowFooter').replaceChildren(minecraft,discord);
  $('#punishmentDialog').showModal();
}

$('#punishmentDialog').addEventListener('cancel', event => {
  if (minecraftWorkflow?.busy) event.preventDefault();
});
$('#punishmentDialog').addEventListener('close', () => {
  minecraftWorkflow = null;
  $('#closeWorkflow').disabled = false;
});

function minecraftActionPayload(workflow, operation) {
  const payload = {targetKey:liveModeration.bootstrap?.targetKey, minecraftTarget:workflow.prepared?.targetId || workflow.target};
  if (operation === 'prepare') payload.minecraftIntent = {reasonId:workflow.reason, explanation:workflow.explanation};
  else payload.confirmationId = workflow.prepared.confirmationId;
  return payload;
}

async function performMinecraftAction(operation) {
  const workflow = minecraftWorkflow;
  if (!workflow || workflow.busy) return;
  workflow.busy = true;
  renderMinecraftPunishment();
  try {
    const result = await requestModerationAction(operation, minecraftActionPayload(workflow, operation));
    if (minecraftWorkflow !== workflow) return;
    applyMinecraftResult(workflow, operation, result);
  } catch (error) {
    recordMinecraftFailure(workflow, operation, error);
  } finally {
    workflow.busy = false;
    if (minecraftWorkflow === workflow) renderMinecraftPunishment();
  }
}

function applyMinecraftResult(workflow, operation, result) {
  if (operation === 'prepare') {
    workflow.prepared = result;
    return;
  }
  workflow.result = result;
  workflow.uncertain = false;
}

function recordMinecraftFailure(workflow, operation, error) {
  if (operation === 'confirm' || operation === 'status') workflow.uncertain = true;
  showToast(operation === 'prepare' ? error.message
    : 'The action outcome is unconfirmed. Check this confirmation’s status before preparing another punishment.', true);
}

function renderMinecraftPunishment() {
  const workflow = minecraftWorkflow;
  if (!workflow) return;
  const body = $('#workflowBody');
  const footer = $('#workflowFooter');
  prepareMinecraftFrame(workflow, body, footer);
  if (!workflow.prepared) {
    renderMinecraftPrepare(workflow, body, footer);
    return;
  }
  renderPreparedMinecraftSummary(workflow, body);
  if (completedMinecraftResult(workflow)) {
    renderMinecraftResult(workflow, body, footer);
    return;
  }
  renderMinecraftConfirmation(workflow, body, footer);
}

function prepareMinecraftFrame(workflow, body, footer) {
  $('#workflowTitle').textContent = workflow.result && workflow.result.state !== 'PREPARED'
    ? 'Minecraft punishment status' : workflow.prepared ? 'Review Minecraft punishment' : 'Minecraft punishment';
  $('#workflowSteps').replaceChildren();
  $('#closeWorkflow').disabled = workflow.busy;
  $('#punishmentDialog').setAttribute('aria-busy', String(workflow.busy));
  body.replaceChildren();
  footer.replaceChildren();
  body.appendChild(element('p',{className:'muted',text:'Uses the network’s configured reasons, escalation rules, and current staff authority. Discord enforcement stays separate.'}));
}

function renderMinecraftPrepare(workflow, body, footer) {
  const target = element('input',{id:'minecraftTarget',value:workflow.target,placeholder:'Minecraft username or UUID',attrs:{maxlength:36,autocomplete:'off'}});
  const reasons = element('select',{id:'minecraftReason'},optionNode('','Select a configured reason',true));
  for (const reason of liveActionCapabilities.minecraftReasons || []) {
    reasons.appendChild(optionNode(reason.id, reason.family + ' — ' + reason.label, workflow.reason === reason.id));
  }
  const explanation = element('textarea',{id:'minecraftExplanation',value:workflow.explanation,attrs:{maxlength:4000,rows:5},placeholder:'Internal explanation and evidence references'});
  target.addEventListener('input',() => { workflow.target = target.value.trim(); });
  reasons.addEventListener('change',() => { workflow.reason = reasons.value; });
  explanation.addEventListener('input',() => { workflow.explanation = explanation.value; });
  body.appendChild(fieldLabel('Minecraft player',target));
  body.appendChild(fieldLabel('Configured reason',reasons));
  body.appendChild(fieldLabel('Internal explanation',explanation));
  const prepare = buttonNode(workflow.busy ? 'Preparing…' : 'Review punishment','button primary',{});
  prepare.disabled = workflow.busy;
  prepare.addEventListener('click',() => {
    if (!workflow.target || !workflow.reason) return showToast('Select a Minecraft player and configured reason.',true);
    performMinecraftAction('prepare');
  });
  footer.appendChild(prepare);
}

function renderPreparedMinecraftSummary(workflow, body) {
  const prepared = workflow.prepared;
  body.appendChild(summaryList([['Minecraft player',prepared.targetName],['Player UUID',prepared.targetId],['Reason',prepared.reason],
    ['Consequences',(prepared.consequences || []).map(value => value.type + ' · ' + value.duration).join(', ')],
    ['Internal explanation',prepared.explanation || 'None']]));
}

function completedMinecraftResult(workflow) {
  return workflow.result && workflow.result.state !== 'PREPARED';
}

function renderMinecraftResult(workflow, body, footer) {
  body.appendChild(element('h3',{text:workflow.result.state === 'APPLIED' ? 'Punishment committed' : 'Approval requested'}));
  body.appendChild(element('p',{text:workflow.result.state === 'APPLIED' ? 'Case: ' + workflow.result.caseId
    : 'Request: ' + workflow.result.requestId + '. No punishment is applied until an authorized reviewer approves it.'}));
  const done = buttonNode('Done','button primary',{});
  done.addEventListener('click',closeWorkflow);
  footer.appendChild(done);
}

function renderMinecraftConfirmation(workflow, body, footer) {
  const prepared = workflow.prepared;
  body.appendChild(element('p',{text:'Confirm before ' + new Date(prepared.expiresAt).toLocaleTimeString()
    + '. The server rechecks authority, target protection, and current policy when you confirm.'}));
  const status = buttonNode(workflow.busy ? 'Checking…' : 'Check status','button secondary',{});
  status.disabled = workflow.busy;
  status.addEventListener('click',() => performMinecraftAction('status'));
  footer.appendChild(status);
  const confirm = buttonNode(workflow.busy ? 'Working…' : 'Confirm Minecraft punishment','button primary',{});
  confirm.disabled = workflow.busy || workflow.uncertain;
  confirm.addEventListener('click',() => performMinecraftAction('confirm'));
  footer.appendChild(confirm);
}
''')

replace_once(
    "velocity/src/main/java/net/enthusia/staff/velocity/WebsiteTunnelConnector.java",
    '''        Process process = new ProcessBuilder(command()).directory(directory.toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
''',
    '''        Process process = new ProcessBuilder(
                "./cloudflared", "tunnel", "--protocol", "http2", "--no-autoupdate",
                "run", "--token-file", "connector-token")
                .directory(directory.toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
''',
)
