
typedump = (key, value, indent='') ->
  if isNumber value
    console.log indent + "#{key}: T.num"
  else if isString value
    console.log indent + "#{key}: T.str"
  else if isBoolean value
    console.log indent + "#{key}: T.bool"
  else if isFunction value
    console.log indent + "#{key}: T.func"
  else if isError value
    console.log indent + "#{key}: T.error"
  else if isDate value
    console.log indent + "#{key}: T.date"
  else if isRegExp value
    console.log indent + "#{key}: T.regexp"
  else if isArray value
    console.log indent + "#{key}: T.arr T.any"
  else if isObject value
    console.log indent + "#{key}:"
    for k, v of value
      typedump k, v, indent + '  '
  return

typecheck = (value, type) ->
  if error = T.check value, type
    lines = T.dump error
    message = "Typecheck failed for #{type.name}"
    if exports?
      console.error message, lines
    else
      if window.steam
        window.steam.context.fatal message, errors: lines
      else
        console.error message, lines
    no
  else
    yes

