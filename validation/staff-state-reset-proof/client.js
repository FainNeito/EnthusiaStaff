'use strict'

const fs = require('node:fs')

const port = Number(process.argv[2])
const version = process.argv[3]
const output = process.argv[4]

const mineflayer = require('mineflayer')
let done = false
const seen = new Set()

function log (line) {
  fs.appendFileSync(output, line + '\n')
  console.log(line)
}

function sleep (ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function sendPosition (bot, x, y, z) {
  bot.entity.position.set(x, y, z)
  bot._client.write('position', {
    x, y, z,
    onGround: false,
    flags: {
      onGround: false,
      hasHorizontalCollision: false
    }
  })
  log(`CLIENT_MOVE|x=${x}|y=${y}|z=${z}|mode=${bot.game.gameMode}`)
}

const bot = mineflayer.createBot({
  host: '127.0.0.1',
  port,
  username: 'ResetProof',
  auth: 'offline',
  version,
  physicsEnabled: false
})
bot.physicsEnabled = false

if (version === '26.3') {
  const originalWrite = bot._client.write.bind(bot._client)
  bot._client.write = (name, packet) => {
    if (name === 'position_look' && bot.physicsEnabled === false) {
      log('FORCED_POSITION_ECHO_SUPPRESSED=true')
      return
    }
    if (name !== 'teleport_confirm') return originalWrite(name, packet)
    const position = bot.entity?.position
    const normalized = {
      ...packet,
      x: Number.isFinite(packet?.x) ? packet.x : position?.x,
      y: Number.isFinite(packet?.y) ? packet.y : position?.y,
      z: Number.isFinite(packet?.z) ? packet.z : position?.z,
      yaw: Number.isFinite(packet?.yaw) ? packet.yaw : 0,
      pitch: Number.isFinite(packet?.pitch) ? packet.pitch : 0
    }
    log('TELEPORT_CONFIRM|' + JSON.stringify(normalized))
    return originalWrite(name, normalized)
  }
}

bot._client.on('game_state_change', packet => {
  log('GAME_STATE|reason=' + String(packet.reason) + '|gameMode=' + String(packet.gameMode))
})

bot.once('spawn', () => {
  log('SPAWN|mode=' + bot.game.gameMode)
  if (version === '26.3') {
    bot._client.write('player_loaded', {})
    log('PLAYER_LOADED_SENT=true')
  }
})

async function inspect (value) {
  const text = String(value)
  const move = text.match(/RESET_PROOF:MOVE:([A-Z_]+):(-?[0-9.]+):(-?[0-9.]+):(-?[0-9.]+)/)
  if (move) {
    const key = 'MOVE:' + move[1]
    if (seen.has(key)) return
    seen.add(key)
    log('MARKER|MOVE|' + move[1] + '|mode=' + bot.game.gameMode)
    await sleep(150)
    sendPosition(bot, Number(move[2]), Number(move[3]), Number(move[4]))
    return
  }
  const state = text.match(/RESET_PROOF:STATE:([A-Z0-9_]+)/)
  if (state) {
    const key = 'STATE:' + state[1]
    if (seen.has(key)) return
    seen.add(key)
    log('MARKER|STATE|' + state[1] + '|mode=' + bot.game.gameMode)
    return
  }
  if (text.includes('RESET_PROOF:DONE')) {
    if (seen.has('DONE')) return
    seen.add('DONE')
    done = true
    log('DONE=true|mode=' + bot.game.gameMode)
  }
}

bot.on('messagestr', text => { void inspect(text) })
bot.on('message', message => { void inspect(message.toString()) })
bot.on('kicked', reason => log('KICKED|' + String(reason)))
bot.on('error', error => log('ERROR|' + (error.stack || error)))
bot.on('end', reason => {
  log('END|' + String(reason) + '|done=' + done)
  if (done) process.exit(0)
})

setTimeout(() => {
  if (!done) {
    log('CLIENT_TIMEOUT=true')
    process.exit(2)
  }
}, 120000)
