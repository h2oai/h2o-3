# "Samples" of various types.
s_undef = undefined
s_null = null
s_nan = Number.NaN
s_0 = 0
s_num = 42
s_empty = ''
s_str = 'foo'
s_true = yes
s_false = no
s_arr = [1, 2, 3]
s_func = -> yes
s_err = new Error 'fail'
s_date = new Date()
s_regexp = /(.+)/g
s_obj = {}

s_all = [ s_undef, s_null, s_nan, s_0, s_num, s_empty, s_str, s_true, s_false, s_arr, s_func, s_err, s_date, s_regexp, s_obj ]

s_nums = [ s_nan, s_0, s_num ]
s_not_nums = [ s_undef, s_null, s_empty, s_str, s_true, s_false, s_arr, s_func, s_err, s_date, s_regexp, s_obj ]

s_strs = [ s_empty, s_str ]
s_not_strs = [ s_undef, s_null, s_nan, s_0, s_num, s_true, s_false, s_arr, s_func, s_err, s_date, s_regexp, s_obj ]

s_not_nums_or_strs = [ s_undef, s_null, s_true, s_false, s_arr, s_func, s_err, s_date, s_regexp, s_obj ]

s_bools = [ s_true, s_false ]
s_not_bools = [ s_undef, s_null, s_nan, s_0, s_num, s_empty, s_str, s_arr, s_func, s_err, s_date, s_regexp, s_obj ]

s_not_arrs = [ s_undef, s_null, s_nan, s_0, s_num, s_empty, s_str, s_true, s_false, s_func, s_err, s_date, s_regexp, s_obj ]

s_not_funcs = [ s_undef, s_null, s_nan, s_0, s_num, s_empty, s_str, s_true, s_false, s_arr, s_err, s_date, s_regexp, s_obj ]

s_not_errs = [ s_undef, s_null, s_nan, s_0, s_num, s_empty, s_str, s_true, s_false, s_arr, s_func, s_date, s_regexp, s_obj ]

s_not_dates = [ s_undef, s_null, s_nan, s_0, s_num, s_empty, s_str, s_true, s_false, s_arr, s_func, s_err, s_regexp, s_obj ]

s_not_regexps = [ s_undef, s_null, s_nan, s_0, s_num, s_empty, s_str, s_true, s_false, s_arr, s_func, s_err, s_date, s_obj ]

s_not_objs = [ s_undef, s_null, s_nan, s_0, s_num, s_empty, s_str, s_true, s_false, s_arr, s_func, s_err, s_date, s_regexp ]

checkValid = (t, values, type) ->
  for value in values
    t.ok (null is T.check value, type), "#{JSON.stringify value} ~ #{JSON.stringify type.inspect()}"
  return

checkInvalid = (t, values, type) ->
  for value in values
    errors = T.check value, type
    # console.log errors
    t.ok (null isnt errors), "#{JSON.stringify value} !~ #{JSON.stringify type.inspect()}"
  return

check = (t, validValues, invalidValues, type) ->
  checkValid t, validValues, type
  checkInvalid t, invalidValues, type


test 'typedef T.num', (t) ->
  check t, s_nums, s_not_nums, T Foo: T.num
  t.end()
test 'typedef T.str', (t) ->
  check t, s_strs, s_not_strs, T Foo: T.str
  t.end()
test 'typedef T.bool', (t) ->
  check t, s_bools, s_not_bools, T Foo: T.bool
  t.end()
test 'typedef T.func', (t) ->
  check t, [s_func], s_not_funcs, T Foo: T.func
  t.end()
test 'typedef T.err', (t) ->
  check t, [s_err], s_not_errs, T Foo: T.err
  t.end()
test 'typedef T.date', (t) ->
  check t, [s_date], s_not_dates, T Foo: T.date
  t.end()
test 'typedef T.regexp', (t) ->
  check t, [s_regexp], s_not_regexps, T Foo: T.regexp
  t.end()

test 'typedef allows validation functions in simple type definitions', (t) ->
  type = T foo: T.str (value) -> if value?.length is 5 then null else 'Invalid length'
  validValues = [ 'alpha', 'gamma' ]
  invalidValues = s_all
  check t, validValues, invalidValues, type
  t.end()

test 'typedef allows validation functions with simple type definitions', (t) ->
  type = T foo: T.str, (value) -> if value?.length is 5 then null else 'Invalid length'
  validValues = [ 'alpha', 'gamma' ]
  invalidValues = s_all
  check t, validValues, invalidValues, type
  t.end()

test 'typedef allows validation functions on compound type definitions', (t) ->
  type = T foo: { bar: T.str }, (value) -> if value?.bar?.length is 5 then null else 'Invalid length'
  validValues = [
    bar: 'alpha'
  ,
    bar: 'gamma'
  ]
  invalidValues = s_all
  check t, validValues, invalidValues, type
  t.end()

test 'typedef does not allow arbitrary items in type definitions', (t) ->
  t.throws -> T foo: 'bar'
  t.throws -> T T.str foo: { bar: 'baz' }
  t.throws ->
    T foo:
      bar:
        baz: 'quux'
  t.end()

test 'typedef does not allow arbitrary items in type checks', (t) ->
  t.throws -> T.check { foo: 'bar' }, 'foo'
  t.throws -> T.check { foo: 'bar' }, 42
  t.throws -> T.check { foo: 'bar' }, { foo: 'bar' }
  t.end()

test 'typedef Any is allowed on top level definitions', (t) ->
  check t, s_all, [], T Foo: T.any
  t.end()

test 'typedef Any is allowed on attribute definitions', (t) ->
  validValues = [
    bar: s_num
  ,
    bar: s_arr
  ,
    bar: null
  ,
    bar: undefined
  ]

  invalidValues = [
    qux: s_num
  ]

  check t, validValues, invalidValues, T
    foo:
      bar: T.any
  t.end()

test 'typedef Array allows primitive string typed arrays', (t) ->
  type = T foo: T.arr T.str
  validValues = [
    []
    [ 'foo', 'bar' ]
  ]
  invalidValues = s_all
  check t, validValues, invalidValues, type
  t.end()

test 'typedef Array allows primitive number typed arrays', (t) ->
  type = T foo: T.arr T.num
  validValues = [
    []
    [ 1, 2, 3, 4, 5 ]
  ]
  invalidValues = s_not_arrs
  check t, validValues, invalidValues, type
  t.end()

test 'typedef Array allows compound typed arrays', (t) ->
  fooType = T
    Foo:
      foo: T.str
      bar: T.num

  arrayType = T foos: T.arr fooType

  t.ok null is T.check [ { foo: 'foo', bar: 42 } ], arrayType
  t.end()

test 'typedef Array does not allow untyped arrays', (t) ->
  t.throws -> T foo: T.arr()
  t.throws -> T foo: T.arr
  t.throws -> T foo: T.arr identity
  t.end()

test 'typedef Array does not allow arbitrary arguments', (t) ->
  t.throws -> T foo: T.arr bar: 'baz'
  t.end()

test 'typedef Array allows validators', (t) ->
  type = T foo: T.arr T.num, (array) -> if array.length > 5 then 'Fail' else null
  t.ok null is T.check [ 1, 2, 3, 4, 5 ], type
  t.ok null isnt T.check [ 1, 2, 3, 4, 5, 6 ], type
  t.end()

test 'typedef Tuple allows primitive types', (t) ->
  type = T foo: T.tuple T.str, T.num, T.date
  validValues = [
    [ 'foo', 41, new Date() ]
    [ 'bar', 42, new Date() ]
    [ 'baz', 43, new Date() ]
  ]
  invalidValues = [
    [ new Date(), 'foo', 42 ] # wrong order
    [ new Date(), new Date(), new Date() ] # array
    [ 'foo', 'bar', 'baz' ] # array
    [ 41, 42, 43 ] # array
    [ 'foo', 42, new Date(), 'baz' ] # extra items
    [ 'foo', 42 ] # missing items
    [] # empty tuple
    { foo: 'bar' } # arbitrary value
  ]
  check t, validValues, invalidValues, type
  t.end()

test 'typedef Tuple allows compound types', (t) ->
  fooType = T
    Foo:
      foo: T.str
      bar: T.num

  arrayType = T foos: T.tuple fooType

  t.ok null is T.check [ { foo: 'foo', bar: 42 } ], arrayType
  t.end()

test 'typedef Tuple does not allow untyped tuples', (t) ->
  t.throws -> T foo: T.tuple()
  t.throws -> T foo: T.tuple
  t.throws -> T foo: T.tuple identity
  t.end()

test 'typedef Tuple does not allow arbitrary arguments', (t) ->
  t.throws -> T foo: T.tuple bar: 'baz'
  t.end()

test 'typedef Tuple allows validators', (t) ->
  type = T foo: T.tuple T.str, T.num, T.date, (tuple) -> if tuple[0].length > 3 then 'Fail' else null
  t.ok null is T.check [ 'foo', 42, new Date()  ], type
  t.ok null isnt T.check [ 'quux', 42, new Date() ], type
  t.end()

test 'typedef Union allows single primitive types', (t) ->
  type = T foo: T.union T.num
  validValues = s_nums
  invalidValues = s_not_nums
  check t, validValues, invalidValues, type
  t.end()

test 'typedef Union allows multiple primitive types', (t) ->
  type = T foo: T.union T.str, T.num
  validValues = [ s_str, s_num ]
  invalidValues = s_not_nums_or_strs
  check t, validValues, invalidValues, type
  t.end()

test 'typedef Union allows compound types', (t) ->
  fooType = T
    Foo:
      foo: T.str
      bar: T.num

  barType = T
    Bar:
      baz: T.str

  varType = T qux:
    foobar: T.union fooType, barType

  validValues = [
    foobar:
      foo: 'foo'
      bar: 42
  ,
    foobar:
      baz: 'quux'
  ]
  invalidValues = [
    foobar:
      qux: 'quux'
  ]

  check t, validValues, invalidValues, varType
  t.end()

test 'typedef Union can create unions without T.union', (t) ->
  fooType = T
    Foo:
      foo: T.str
      bar: T.num

  barType = T
    Bar:
      baz: T.str

  varType = T qux:
    foobar: [ fooType, barType ] # shorthand notation

  validValues = [
    foobar:
      foo: 'foo'
      bar: 42
  ,
    foobar:
      baz: 'quux'
  ]
  invalidValues = [
    foobar:
      qux: 'quux'
  ]

  check t, validValues, invalidValues, varType
  t.end()

test 'typedef Union does not allow untyped unions', (t) ->
  t.throws -> T foo: T.union()
  t.throws -> T foo: T.union
  t.throws -> T foo: T.union identity
  t.end()

test 'typedef Union does not allow arbitrary arguments', (t) ->
  t.throws -> T foo: T.union bar: 'baz'
  t.throws -> T foo: T.union { bar: 'baz' }, { qux: 'qux' }
  t.end()

test 'typedef Union allows validators', (t) ->
  type = T foo: T.union T.str, T.num, (value) -> if value is 42 or value is '42' then 'Fail' else null
  t.ok null is T.check 41, type
  t.ok null is T.check '41', type
  t.ok null isnt T.check 42, type
  t.ok null isnt T.check '42', type
  t.end()

test 'typedef Enum does not allow invalid definitions', (t) ->
  t.throws ->
    T foo: T.str [41, 42, 43]
  t.throws ->
    T foo: T.num ['Windows', 'OSX', 'Linux']
  t.end()

test 'typedef Enum Number', (t) ->
  validValues = [ 1, 3, 5, 7, 9 ]
  invalidValues = [ 2, 4, 6, 8 ].concat s_not_nums
  type = T foo: T.num validValues
  check t, validValues, invalidValues, type
  t.end()

test 'typedef Enum String', (t) ->
  validValues = ['Windows', 'OSX', 'Linux']
  invalidValues = s_all
  type = T foo: T.str validValues
  check t, validValues, invalidValues, type
  t.end()

test 'typedef Enum Boolean', (t) ->
  validValues = [ yes, no ]
  invalidValues = s_not_bools
  type = T foo: T.bool validValues
  check t, validValues, invalidValues, type
  t.end()
 
test 'typedef Enum Function', (t) ->
  f1 = ->
  f2 = ->
  validValues = [ f1, f2 ]
  invalidValues = s_all
  type = T foo: T.func validValues
  check t, validValues, invalidValues, type
  t.end()

test 'typedef Enum Error', (t) ->
  e1 = new Error 'foo'
  e2 = new Error 'bar'
  validValues = [ e1, e2 ]
  invalidValues = s_all
  type = T foo: T.err validValues
  check t, validValues, invalidValues, type
  t.end()

test 'typedef Enum Date', (t) ->
  d1 = new Date Date.now() - 1000
  d2 = new Date Date.now() - 10000
  validValues = [ d1, d2 ]
  invalidValues = s_all
  type = T foo: T.date validValues
  check t, validValues, invalidValues, type
  t.end()

test 'typedef Enum RegExp', (t) ->
  rx1 = /\s+/
  rx2 = /.+/g
  validValues = [ rx1, rx2 ]
  invalidValues = s_all
  type = T foo: T.regexp validValues
  check t, validValues, invalidValues, type
  t.end()

test 'typedef inspect', (t) ->
  type = T
    Foo:
      a: T.any
      b: T.num
      c: T.str
      d: T.bool
      e: T.func
      f: T.err
      g: T.date
      h: T.regexp
  description =
    Foo:
      a: 'Any'
      b: 'Number'
      c: 'String'
      d: 'Boolean'
      e: 'Function'
      f: 'Error'
      g: 'Date'
      h: 'RegExp'

  t.deepEqual type.inspect(), description
  t.end()

