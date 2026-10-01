from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one guarded match in {path}, found {count}")
    file.write_text(text.replace(old, new))


replace_once(
    "moderation-web/src/backend.js",
    '''function validConfiguredReasonId(value) {
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
''',
    '''function validConfiguredReasonId(value) {
  if (typeof value !== 'string' || value.length < 1 || value.length > 96) return false;
  return value.split(/[.-]/).every(validReasonSegment);
}

function validReasonSegment(segment) {
  return segment.length > 0 && [...segment].every(lowerAlphaNumeric);
}

function lowerAlphaNumeric(character) {
  return (character >= 'a' && character <= 'z') || (character >= '0' && character <= '9');
}
''',
)

replace_once(
    "moderation-web/src/backend.js",
    '''async function actionRequestBody(session, input, targetKey) {
  const digest = await crypto.subtle.digest('SHA-256', textEncoder.encode(session.csrfToken));
  const sessionBinding = hex(new Uint8Array(digest));
  const body = JSON.stringify({actorId:session.actorId, guildId:session.guildId, targetKey,
    sessionBinding, intent:input.intent ?? null, confirmationId:input.confirmationId ?? null,
    minecraftTarget:input.minecraftTarget ?? null, minecraftIntent:input.minecraftIntent ?? null});
  if (textEncoder.encode(body).length > 65_536) throw new Error('action body too large');
  return body;
}
''',
    '''async function actionRequestBody(session, input, targetKey) {
  const digest = await crypto.subtle.digest('SHA-256', textEncoder.encode(session.csrfToken));
  const sessionBinding = hex(new Uint8Array(digest));
  const body = JSON.stringify({actorId:session.actorId, guildId:session.guildId, targetKey,
    sessionBinding, intent:nullableActionValue(input.intent), confirmationId:nullableActionValue(input.confirmationId),
    minecraftTarget:nullableActionValue(input.minecraftTarget), minecraftIntent:nullableActionValue(input.minecraftIntent)});
  if (textEncoder.encode(body).length > 65_536) throw new Error('action body too large');
  return body;
}

function nullableActionValue(value) {
  return value === undefined ? null : value;
}
''',
)

live_actions = Path("staff-bot/src/main/resources/moderation-preview/live-actions.js")
text = live_actions.read_text()
broken_join = "const explanation = [workflow.reason, ...evidence].filter(Boolean).join('" + "\n" + "');"
fixed_join = "const explanation = [workflow.reason, ...evidence].filter(Boolean).join('\\n');"
if text.count(broken_join) != 1:
    raise SystemExit(f"expected exactly one broken live-action newline join, found {text.count(broken_join)}")
live_actions.write_text(text.replace(broken_join, fixed_join))
