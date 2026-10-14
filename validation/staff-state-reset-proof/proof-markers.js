'use strict'

function sleep (ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function createMarkerInspector ({ bot, sendPosition, log, markDone }) {
  const seen = new Set()

  async function handleMove (text) {
    const move = text.match(/RESET_PROOF:MOVE:([A-Z_]+):(-?[0-9.]+):(-?[0-9.]+):(-?[0-9.]+)/)
    if (!move) return false

    const key = 'MOVE:' + move[1]
    if (!seen.has(key)) {
      seen.add(key)
      log('MARKER|MOVE|' + move[1] + '|mode=' + bot.game.gameMode)
      await sleep(150)
      sendPosition(bot, Number(move[2]), Number(move[3]), Number(move[4]))
    }
    return true
  }

  function handleState (text) {
    const state = text.match(/RESET_PROOF:STATE:([A-Z0-9_]+)/)
    if (!state) return false

    const key = 'STATE:' + state[1]
    if (!seen.has(key)) {
      seen.add(key)
      log('MARKER|STATE|' + state[1] + '|mode=' + bot.game.gameMode)
    }
    return true
  }

  function handleDone (text) {
    if (!text.includes('RESET_PROOF:DONE') || seen.has('DONE')) return
    seen.add('DONE')
    markDone()
    log('DONE=true|mode=' + bot.game.gameMode)
  }

  return async value => {
    const text = String(value)
    if (await handleMove(text)) return
    if (handleState(text)) return
    handleDone(text)
  }
}

module.exports = { createMarkerInspector }
