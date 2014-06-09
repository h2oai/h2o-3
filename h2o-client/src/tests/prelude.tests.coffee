
truthy = [ [1, 2, 3], true, new Date(), { 'a' : 1 }, 42, /x/, 'a' ]
falsy = [ '', 0, false, NaN, null, undefined, ]

test 'prelude availability in application scope', (t) ->
  funcs = [
    compact
    difference
    findIndex
    findLastIndex
    flatten
    head
    indexOf
    initial
    intersection
    last
    lastIndexOf
    pull
    range
    removeWhere
    sortedIndex
    tail
    union
    unique
    without
    xor
    zip
    zipObject
    at
    contains
    countBy
    every
    filter
    find
    findLast
    forEach
    forEachRight
    groupBy
    indexBy
    map
    max
    min
    pluck
    reduce
    reduceRight
    reject
    sample
    shuffle
    size
    some
    sortBy
    toArray
    where
    after
    compose
    curry
    debounce
    defer
    delay
    memoize
    once
    partial
    partialRight
    throttle
    wrap
    clone
    cloneDeep
    defaults
    extend
    findKey
    findLastKey
    forIn
    forInRight
    forOwn
    forOwnRight
    functions
    has
    invert
    isArguments
    isArray
    isBoolean
    isDate
    isElement
    isEmpty
    isEqual
    isFinite
    isFunction
    isNaN
    isNull
    isNumber
    isObject
    isPlainObject
    isRegExp
    isString
    isUndefined
    keys
    mapValues
    merge
    omit
    pairs
    pick
    transform
    values
    now
    constant
    escape
    identity
    noop
    property
    random
    times
    unescape
    uniqueId
    apply
    isDefined
    isTruthy
    isFalsy
    isError
    negate
    always
    never
    join
    split
    words
    sort
    copy
    concat
    unshift
    unshiftAll
    shift
    push
    pushAll
    pop
    splice
    remove
    clear
    repeat
    mapWithKey
    zipCompare
    same
    valuesAreEqual
  ]
  for func in funcs
    t.equal (typeof func), 'function'
  t.end()

test 'isDefined', (t) ->
  for arg in falsy
    t.equal (isDefined arg), arg isnt undefined

  for arg in truthy
    t.equal (isDefined arg), yes

  t.end()
  
test 'isTruthy', (t) ->
  for arg in falsy
    t.equal (isTruthy arg), if arg then yes else no

  for arg in truthy
    t.equal (isTruthy arg), if arg then yes else no

  t.end()

test 'isFalsy', (t) ->
  for arg in falsy
    t.equal (isFalsy arg), if arg then no else yes

  for arg in truthy
    t.equal (isFalsy arg), if arg then no else yes

  t.end()

test 'isError', (t) ->
  t.equal (isError new Error()), yes
  t.equal (isError {}), no
  t.end()
    
test 'join', (t) ->
  array = [ 'foo', 'bar' ]
  delims = [
    undefined
    null
    ''
    ' '
    ','
  ]
  for delim in delims
    t.equal (join array, delim), (array.join delim)
  t.end()

test 'split', (t) ->
  args = [
    [ 'foo bar   baz', /\s+/ ]
    [ 'foo, bar, baz', ', ' ]
  ]
  for arg in args
    [ str, delim ] = arg
    t.deepEqual (split str, delim), (str.split delim)
  t.end()

test 'words', (t) ->
  t.deepEqual (words 'foo bar baz'), [ 'foo', 'bar', 'baz' ]
  t.deepEqual (words ' foo   bar    baz   '), [ '', 'foo', 'bar', 'baz', '' ]
  t.end()

test 'sort without comparator', (t) ->
  array = [ 'foo', 'bar', 'baz' ]
  expected = array.slice(0).sort()
  t.deepEqual (sort array), expected
  t.end()

test 'sort with comparator', (t) ->
  array = [ 40, 100, 1, 5, 25, 10 ]
  comparator = (a, b) -> b - a
  expected = array.slice(0).sort comparator
  t.deepEqual (sort array, comparator), expected
  t.end()

test 'copy', (t) ->
  o1 = {}
  o2 = {}
  o3 = {}
  array = [ o1, o2, o3 ]
  expected = array.slice 0
  actual = copy array
  t.deepEqual actual, expected
  t.notEqual actual, expected
  t.end()

test 'concat', (t) ->
  array1 = [ 1, 2, 3 ]
  array2 = [ 4, 5, 6 ]
  array3 = [ 7, 8, 9 ]
  actual = concat array1, array2, array3
  t.notEqual array1, actual
  t.deepEqual actual, [ 1, 2, 3, 4, 5, 6, 7, 8, 9 ]
  t.end()


test 'unshift', (t) ->
  o1 = {}
  o2 = {}
  o3 = {}
  array = [ o2, o3 ]
  actual = unshift array, o1
  t.deepEqual actual, [ o1, o2, o3 ]
  t.equal actual, array
  t.end()

test 'unshiftAll', (t) ->
  o1 = {}
  o2 = {}
  o3 = {}
  array = [ o1 ]
  actual = unshiftAll array, [ o2, o3 ]
  t.deepEqual actual, [ o2, o3, o1 ]
  t.equal actual, array
  t.end()

test 'shift', (t) ->
  o1 = {}
  o2 = {}
  o3 = {}
  array = [ o1, o2, o3 ]
  actual = shift array
  t.deepEqual array, [ o2, o3 ]
  t.equal actual, o1
  t.end()

test 'push', (t) ->
  o1 = {}
  o2 = {}
  o3 = {}
  array = [ o1, o2 ]
  actual = push array, o3
  t.deepEqual actual, [ o1, o2, o3 ]
  t.equal actual, array
  t.end()

test 'pushAll', (t) ->
  o1 = {}
  o2 = {}
  o3 = {}
  array = [ o1 ]
  actual = pushAll array, [ o2, o3 ]
  t.deepEqual actual, [ o1, o2, o3 ]
  t.equal actual, array
  t.end()

test 'pop', (t) ->
  o1 = {}
  o2 = {}
  o3 = {}
  array = [ o1, o2, o3 ]
  actual = pop array
  t.deepEqual array, [ o1, o2 ]
  t.equal actual, o3
  t.end()

test 'splice', (t) ->
  o1 = {}
  o2 = {}
  o3 = {}
  array = [ o1, o2, o3 ]
  actual = splice array, 1, 1
  t.deepEqual array, [ o1, o3 ]
  t.deepEqual actual, [ o2 ]
  t.end()

test 'remove', (t) ->
  o1 = {}
  o2 = {}
  o3 = {}
  array = [ o1, o2, o3 ]

  o4 = remove array, o2
  t.equal o4, o2
  t.equal array.length, 2
  t.equal array[0], o1
  t.equal array[1], o3

  o5 = remove array, o2
  t.equal o5, undefined
  t.equal array.length, 2
  t.end()

test 'clear', (t) ->
  array1 = [ 'foo', 'bar' ]
  array2 = clear array1
  t.equal array1.length, 0
  t.equal array1, array2
  t.end()

test 'repeat', (t) ->
  t.deepEqual (repeat 3, 5), [ 5, 5, 5 ]
  t.deepEqual (repeat 3, 'a'), [ 'a', 'a', 'a' ]
  t.end()

test 'zipCompare', (t) ->
  t.ok not zipCompare undefined, undefined
  t.ok not zipCompare null, null
  t.ok not zipCompare 1, 2
  t.ok not zipCompare 1, 1
  t.ok not zipCompare [], [10]
  t.ok not zipCompare [], null
  t.ok not zipCompare null, []
  t.ok zipCompare [10], [10]
  t.ok zipCompare [10, 20], [10, 20]
  t.ok not zipCompare { foo: 'bar' }, { foo: 'bar' }
  t.ok zipCompare [{ foo: 'bar' }], [{ foo: 'bar' }], (a, b) -> a.foo is b.foo
  t.end()

test 'same', (t) ->
  compareBaz = (a, b) -> a.baz is b.baz
  t.ok same []
  t.ok same [ 'bar' ]
  t.ok same [ 'bar', 'bar' ]
  t.ok not same [ 'bar', 'qux' ]
  t.ok same [ { baz: 'bar' }, { baz: 'bar'} ], compareBaz
  t.ok not same [ { baz: 'bar' }, { baz: 'qux' } ], compareBaz
  t.end()

test 'valuesAreEqual', (t) ->
  pluckFoo = (obj) -> obj.foo
  compareBaz = (a, b) -> a.baz is b.baz
  t.ok valuesAreEqual [], pluckFoo
  t.ok valuesAreEqual [{ foo: 'bar' }], pluckFoo
  t.ok valuesAreEqual [{ foo: 'bar' }, { foo: 'bar' }], pluckFoo
  t.ok not valuesAreEqual [{ foo: 'bar' }, { foo: 'qux' }], pluckFoo
  t.ok valuesAreEqual [{ foo: { baz: 'bar' } }, { foo: { baz: 'bar'} }], pluckFoo, compareBaz
  t.ok not valuesAreEqual [{ foo: { baz: 'bar' } }, { foo: { baz: 'qux'} }], pluckFoo, compareBaz
  t.end()

test 'mapWithKey', (t) ->
  obj =
    foo: 10
    bar: 20
    baz: 30
  mapper = (v, k) -> k + '=' + v
  t.equal (mapWithKey obj, mapper).join(','), 'foo=10,bar=20,baz=30'
  t.end()
