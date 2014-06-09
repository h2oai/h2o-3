Steam.Timers = (_) ->
  _timers = {}
  _timeouts = {}

  link$ _.timeout, (id, ms, func) ->
    console.assert isFunction func
    if timeout = _timeouts[id]
      clearInterval timeout.id
      timeout.id = setInterval func, ms
    else
      _timeouts[id] = id: setInterval func, ms

  link$ _.schedule, (ms, func) ->
    console.assert isFunction func
    if timer = _timers[ms]
      push timer.functions, func
    else
      tick = ->
        for f in _timers[ms].functions when f
          do f
        return
      timerId = setInterval tick, ms
      _timers[ms] =
        id: timerId
        functions: [ func ]

  link$ _.unschedule, (ms, func) ->
    if timer = _timers[ms]
      if func
        remove timer.functions, func 
        if timer.functions.length is 0
          clearInterval timer.id
          delete _timers[ms]
      else
        clearInterval timer.id
        delete _timers[ms]
    return


    

