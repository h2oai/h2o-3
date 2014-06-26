dumpAssertion = (path, value) ->
  if isUndefined value
    console.log "t.ok (isUndefined #{path}), '#{path} is undefined'"
  else if value is null
    console.log "t.ok (null is #{path}), '#{path} is null'"
  else if isString value
    escapedValue = value.replace(/\n/g, '\\n').replace(/\t/g, '\\t')
    console.log "t.equal #{path}, '#{escapedValue}', 'String #{path} equals [#{escapedValue}]'"
  else if isBoolean value
    console.log "t.equal #{path}, #{value}, 'Boolean #{path} equals [#{value}]'"
  else if isNumber value
    console.log "t.equal #{path}, #{value}, 'Number #{path} equals [#{value}]'"
  else if isRegExp value
    console.log "t.equal #{path}.toString(), #{value}.toString(), 'Regexp #{path} equals [#{value}]'"
  else if isDate value
    console.log "t.equal #{path}.toString(), #{value}.toString(), 'Date #{path} equals [#{value}]'"
  else if isFunction value
    console.log "t.ok (isFunction #{path}), '#{path} is a function'"
  else if isArray value
    console.log "t.equal #{path}.length, #{value.length}, '#{path} array lengths match'"
    for element, index in value
      dumpAssertion "#{path}[#{index}]", element
  else if isObject value
    dumpAssertions value, path
  else
    throw new Error "Cannot dump #{path}"
  return

dumpAssertions = (obj, name='subject') ->
  throw new Error 'Not an object' unless isObject obj
  for key, value of obj
    if isNode$ value
      dumpAssertion "#{name}.#{key}()", value()
    else
      dumpAssertion "#{name}.#{key}", value
  return
