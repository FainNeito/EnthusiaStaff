'use strict'

const port = Number(process.argv[2])
const version = process.argv[3]
const mineflayer = require('mineflayer')
const { traceOwnPlayerInfo } = require('./player-info-trace')
const { createMarkerInspector } = require('./proof-markers')
let done = false

function log (line) {
  console.log(line)
}

function sendPosition (bot, x, y, z) {
  bot.entity.position.set(x, y, z)
  if (version !== '26.3') {
    bot._client.write('position', {
      x, y, z,
      onGround: false,
      flags: {
        onGround: false,
        hasHorizontalCollision: false
      }
    })
  }
  log('CLIENT_MOVE|x=' + x + '|y=' + y + '|z=' + z + '|mode=' + bot.game.gameMode)
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
log('CLIENT_PHYSICS|enabled=false|purpose=server-movement-acceptance-not-vanilla-collision-proof')

bot._client.on('player_info', packet => traceOwnPlayerInfo(bot, packet, log))

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

const inspect = createMarkerInspector({
  bot,
  sendPosition,
  log,
  markDone: () => { done = true }
})

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
