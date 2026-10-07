'use strict'

function firstPresent (object, keys) {
  if (!object) return undefined
  for (const key of keys) {
    const value = object[key]
    if (value !== undefined && value !== null) return value
  }
  return undefined
}

function profileId (entry) {
  const direct = firstPresent(entry, ['uuid', 'profileId'])
  if (direct !== undefined) return direct
  const profile = entry && entry.profile
  return profile && profile.id
}

module.exports = { firstPresent, profileId }
