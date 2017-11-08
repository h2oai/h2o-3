def call(stageName) {
  if (stageName != null) {
    return stageName.toLowerCase().replace(' ', '-')
  }
  return null
}

return this
