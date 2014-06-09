Steam.Typedef = do ->

  class Type
    constructor: ->

  class Builtin extends Type
    constructor: (@name, @checks, @inspect) ->

  class Reference extends Type
    constructor: (@name, @type, @checks) ->
    inspect: =>
      description = {}
      description[@name] = @type.inspect()
      description

  class Struct extends Type
    constructor: (@name, @definition, @checks) ->
    inspect: =>
      description = {}
      description[@name] = struct = {}
      for k, v of @definition
        struct[k] = v.inspect()
      description

  builtinChecks =
    'Any': -> yes
    'Number': isNumber
    'String': isString
    'Boolean': isBoolean
    'Function': isFunction
    'Error': isError
    'Date': isDate
    'RegExp': isRegExp
    'Object': isObject

  t_checkEnum = (validValues) ->
    (value) ->
      for validValue in validValues
        return null if value is validValue
      "Enum value [#{value}] does not match one of [#{validValues.join ', '}]"

  t_checkBuiltin = (value, type) ->
    errors = []
    for check in type.checks
      if error = check value
        errors.push error
    if errors.length > 0 then errors else null

  t_checkReference = (value, type) ->
    errors = []
    if error = t_check value, type.type
      errors.push error
    for check in type.checks
      if error = check value
        errors.push error
    if errors.length > 0 then errors else null

  t_checkStruct = (value, type) ->
    errors = []
    for attributeName, attributeType of type.definition
      if (isObject value) and attributeName of value
        attributeValue = value[attributeName]
        if error = t_check attributeValue, attributeType
          errors.push error
      else
        errors.push "Required attribute [#{attributeName}] not found."
    for check in type.checks
      if error = check value
        errors.push error
    if errors.length > 0 then errors else null

  t_check = (value, type) ->
    if type instanceof Builtin
      t_checkBuiltin value, type
    else if type instanceof Reference
      t_checkReference value, type
    else if type instanceof Struct
      t_checkStruct value, type
    else
      throw new Error "Unknown type [#{type}]"

  t_primitive = (name, args) ->
    primitiveCheck = builtinChecks[name]

    check = (value) ->
      if primitiveCheck value then null else "[#{value}] is not a #{name}"

    checks = [ check ]

    for arg in args
      if isArray arg
        for value in arg
          unless primitiveCheck value
            throw new Error "Enum value [#{value}] is not a #{name}"
        checks.push t_checkEnum arg

      else if isFunction arg
        checks.push arg

      else
        throw new Error "Invalid type arg [#{arg}]"
    
    new Builtin name, checks, -> name

  t_any = (args...) -> t_primitive 'Any', args
  t_number = (args...) -> t_primitive 'Number', args
  t_string = (args...) -> t_primitive 'String', args
  t_boolean = (args...) -> t_primitive 'Boolean', args
  t_function = (args...) -> t_primitive 'Function', args
  t_error = (args...) -> t_primitive 'Error', args
  t_date = (args...) -> t_primitive 'Date', args
  t_regexp = (args...) -> t_primitive 'RegExp', args
  t_object = (args...) -> t_primitive 'Object', args

  isBuiltin = (f) ->
    f is t_any or f is t_number or f is t_string or f is t_boolean or f is t_function or f is t_error or f is t_date or f is t_regexp or f is t_object

  t_array = (args...) ->
    types = []
    checks = []

    for arg in args
      if arg instanceof Type
        types.push arg

      else if isFunction arg
        if isBuiltin arg
          types.push arg()
        else
          checks.push arg

      else
        throw new Error "Invalid arg [#{arg}]"

    if types.length > 0
      type  = t_union.apply null, types
      checks.unshift (array) ->
        errors = []
        if isArray array
          for element, index in array
            if error = t_check element, type
              errors.push "Array[#{index}]: #{error}"
        else
          errors.push "[#{array}] is not an Array"
        if errors.length > 0 then errors else null
    else
      throw new Error "Array type not specified"
    
    new Builtin 'Array', checks, -> "Array[#{type.inspect()}]"

  t_union = (args...) ->
    if args.length is 0
      throw new Error "Variant types not specified"
    else if args.length is 1
      arg = head args
      if isBuiltin arg
        return arg()
      else if arg instanceof Type
        return arg
      else
        throw new Error "Invalid arg [#{arg}]"
    else
      types = []
      checks = []
      for arg in args
        if isBuiltin arg
          types.push arg()
        else if arg instanceof Type
          types.push arg
        else if isFunction arg
          checks.push arg
        else
          throw new Error "Invalid type arg [#{arg}]"

      checks.unshift (value) ->
        errors = []
        matched = no
        for type in types
          if error = t_check value, type
            errors.push error
          else
            matched = yes
            break
        if matched
          null
        else
          map errors, (error) -> "Variant [#{value}]: #{error}"

      new Builtin 'Variant', checks, -> "#{types.map((type) -> type.inspect()).join('|')}"

  t_tuple = (args...) ->
    types = []
    checks = []
    for arg in args
      if isBuiltin arg
        types.push arg()
      else if arg instanceof Type
        types.push arg
      else if isFunction arg
        checks.push arg
      else
        throw new Error "Invalid arg [#{arg}]"

    if types.length > 0
      checks.unshift (tuple) ->
        if isArray tuple
          errors = []

          for type, index in types
            value = tuple[index]
            if error = t_check value, type
              errors.push "Tuple[#{index}]: #{error}"

          if tuple.length isnt types.length
            errors.push "Invalid tuple length. Expected #{types.length}. Got #{tuple.length}"

          if errors.length > 0 then errors else null

        else
          "Value [#{tuple}] is not a Tuple"
      new Builtin 'Tuple', checks, -> "Tuple[#{types.map((type) -> type.inspect()).join(', ')}]"
    else
      throw new Error "Tuple types not specified"
  
  t_dump = (arg) ->
    tab = '  '
    dump = (lines, offset, arg) ->
      if isArray arg
        indent = offset + tab
        for item in arg
          dump lines, indent, item
      else if isString arg
        lines.push offset + arg
      return

    dump lines=[], '', arg
    lines

  typedef = (specification, checks...) ->
    [ name, arg ] = head pairs specification
    
    if isBuiltin arg
      new Reference name, arg(), checks
    else if arg instanceof Type
      new Reference name, arg, checks
    else if isFunction arg
      throw new Error "Arbitrary functions not allowed on structs"
    else if isObject arg
      definition = {}
      for attr, attrType of arg
        if isBuiltin attrType
          definition[attr] = attrType()
        else if isArray attrType
          definition[attr] = t_union.apply null, attrType
        else if attrType instanceof Type
          definition[attr] = attrType
        else
          throw new Error "Unrecognized type [#{attr}] : [#{attrType}]"
      new Struct name, definition, checks
    else
      throw new Error "Invalid arg [#{arg}]"

  typedef.any = t_any
  typedef.num = t_number
  typedef.str = t_string
  typedef.bool = t_boolean
  typedef.func = t_function
  typedef.err = t_error
  typedef.date = t_date
  typedef.regexp = t_regexp
  typedef.arr = t_array
  typedef.obj = t_object
  typedef.union = t_union
  typedef.tuple = t_tuple
  typedef.check = t_check
  typedef.dump = t_dump

  typedef

T = Steam.Typedef
