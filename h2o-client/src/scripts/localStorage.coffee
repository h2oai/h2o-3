Steam.LocalStorage = (_) ->
  _hasLocalStorage = do ->
    try
      localStorage.setItem 'test', 'test'
      localStorage.removeItem 'test'
      yes
    catch error
      no

  return unless _hasLocalStorage

  putLocalObject = (key, value) ->
    localStorage.setItem key, JSON.stringify value

  getLocalObject = (key) ->
    value = localStorage.getItem key
    if isDefined value
      JSON.parse value
    else
      undefined

  getLocalObjects = (predicate) ->
    values = []
    hasPredicate = isFunction predicate
    for index in [ 0 ... localStorage.length ]
      value = getLocalObject localStorage.key index
      if hasPredicate
        if predicate value
          values.push value
      else
        values.push value
    values

  deleteLocalObject = (key) ->
    localStorage.removeItem key

  link$ _.putLocalObject, putLocalObject
  link$ _.getLocalObject, getLocalObject
  link$ _.getLocalObjects, getLocalObjects
  link$ _.deleteLocalObject, deleteLocalObject

  return


  



