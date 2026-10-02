'use strict'

const fs = require('node:fs')
const path = require('node:path')

const port = Number(process.argv[2])
const version = process.argv[3]
const output = process.argv[4]

if (version === '26.3') {
  const root = path.dirname(require.resolve('mineflayer'))
  const physics = path.join(root, 'lib', 'plugins', 'physics.js')
  let source = fs.readFileSync(physics, 'utf8')
  const marker = 'RESET_PROOF_DEFERRED_TELEPORT_ECHO'
  if (!source.includes(marker)) {
    const oldTail = `    sendPacketPositionAndLook(pos, newYaw, newPitch, bot.entity.onGround)

    shouldUsePhysics = true
    bot.jumpTicks = 0
    lastSentYaw = bot.entity.yaw
    lastSentPitch = bot.entity.pitch

    bot.emit('forcedMove')`
    if (!source.includes(oldTail)) throw new Error('unexpected 26.3 Mineflayer physics source')
    const replacement = `    // ${marker}
    if (!bot.physicsEnabled) {
      shouldUsePhysics = false
      bot.jumpTicks = 0
      lastSentYaw = bot.entity.yaw
      lastSentPitch = bot.entity.pitch
      bot.emit('forcedMove')
      return
    }
${oldTail}`
    fs.writeFileSync(physics, source.replace(oldTail, replacement))
  }
}

const mineflayer = require('mineflayer')
let done = false

function log (line) {
  fs.appendFileSync(output, line + '\n')
  console.log(line)
}

function sleep (ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function sendPosition (bot, x, y, z) {
  bot.entity.position.set(x, y, z)
  if (version === '26.3') {
    bot._client.write('position_look', {
      x, y, z, yaw: 0, pitch: 0,
      onGround: false,
      horizontalCollision: false
    })
  } else {
    bot._client.write('position', {
      x, y, z,
      onGround: false,
      flags: { onGround: false, hasHorizontalCollision: false }
    })
  }
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

bot._client.on('game_state_change', packet => {
  log('GAME_STATE|reason=' + String(packet.reason) + '|gameMode=' + String(packet.gameMode))
})

bot.once('spawn', () => log('SPAWN|mode=' + bot.game.gameMode))

async function inspect (value) {
  const text = String(value)
  const move = text.match(/RESET_PROOF:MOVE:([A-Z_]+):(-?[0-9.]+):(-?[0-9.]+):(-?[0-9.]+)/)
  if (move) {
    log('MARKER|MOVE|' + move[1] + '|mode=' + bot.game.gameMode)
    await sleep(150)
    sendPosition(bot, Number(move[2]), Number(move[3]), Number(move[4]))
    return
  }
  const state = text.match(/RESET_PROOF:STATE:([A-Z0-9_]+)/)
  if (state) {
    log('MARKER|STATE|' + state[1] + '|mode=' + bot.game.gameMode)
    return
  }
  if (text.includes('RESET_PROOF:DONE')) {
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
