Steam.ErrorMonitor = (_) ->
  window.onerror = (message, url, lineNumber) ->
    _.fatal message,
      url: url
      lineNumber: lineNumber






