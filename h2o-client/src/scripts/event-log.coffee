Steam.EventLog = (_) ->
  log = (level, message, data, cause) ->
    _.notify
      level: level
      message: message
      data: data
      cause: cause
      timestamp: new Date()

  logError = (level, message, data, error) ->
    stackTrace = if error
      printStackTrace e:error
    else
      printStackTrace()

    log level, message, data,
      error: error
      stackTrace: stackTrace

  link$ _.info, (message='', data) ->
    log 'info', message, data

  link$ _.warn, (message='', data) ->
    log 'warn', message, data

  link$ _.error, (message='', data, error) ->
    logError 'error', message, data, error

  link$ _.fatal, (message='', data, error) ->
    logError 'fatal', message, data, error

  return
