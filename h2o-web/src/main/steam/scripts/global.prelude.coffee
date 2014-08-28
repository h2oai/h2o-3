
Steam = if exports? then exports else @Steam = {}

# Expand lodash functions into application scope.

#
# Arrays
#

compact = _.compact
difference = _.difference
# drop = _.drop → rest
findIndex = _.findIndex
findLastIndex = _.findLastIndex
# first = _.first
flatten = _.flatten
head = _.head # → first
indexOf = _.indexOf
initial = _.initial
intersection = _.intersection
last = _.last
lastIndexOf = _.lastIndexOf
# object = _.object → zipObject
pull = _.pull
range = _.range
removeWhere = _.remove
# rest = _.rest
sortedIndex = _.sortedIndex
tail = _.tail # → rest
# take = _.take → first
union = _.union
# uniq = _.uniq
unique = _.unique # → uniq
# unzip = _.unzip → zip
without = _.without
xor = _.xor
zip = _.zip
zipObject = _.zipObject

#
# Chaining
#

# _
# chain = _.chain
# tap = _.tap
# prototype = _.prototype.chain
# prototype = _.prototype.toString
# prototype = _.prototype.value → valueOf
# prototype = _.prototype.valueOf

#
# Collections
#

# all = _.all → every
# any = _.any → some
at = _.at
# collect = _.collect → map
contains = _.contains
countBy = _.countBy
# detect = _.detect → find
# each = _.each → forEach
# eachRight = _.eachRight → forEachRight
every = _.every
filter = _.filter
find = _.find
findLast = _.findLast
# findWhere = _.findWhere → find
# foldl = _.foldl → reduce
# foldr = _.foldr → reduceRight
forEach = _.forEach
forEachRight = _.forEachRight
groupBy = _.groupBy
# include = _.include → contains
indexBy = _.indexBy
# inject = _.inject → reduce
# invoke = _.invoke
map = _.map
max = _.max
min = _.min
pluck = _.pluck
reduce = _.reduce
reduceRight = _.reduceRight
reject = _.reject
sample = _.sample
# select = _.select → filter
shuffle = _.shuffle
size = _.size
some = _.some
sortBy = _.sortBy
toArray = _.toArray
where = _.where


#
# Functions
#

after = _.after
# bind = _.bind
# bindAll = _.bindAll
# bindKey = _.bindKey
compose = _.compose
curry = _.curry
debounce = _.debounce
defer = _.defer
delay = _.delay
memoize = _.memoize
once = _.once
partial = _.partial
partialRight = _.partialRight
throttle = _.throttle
wrap = _.wrap

#
# Objects
#

# assign = _.assign
clone = _.clone
cloneDeep = _.cloneDeep
# create = _.create
defaults = _.defaults
extend = _.extend # → assign
findKey = _.findKey
findLastKey = _.findLastKey
forIn = _.forIn
forInRight = _.forInRight
forOwn = _.forOwn
forOwnRight = _.forOwnRight
functions = _.functions
has = _.has
invert = _.invert
isArguments = _.isArguments
isArray = _.isArray
isBoolean = _.isBoolean
isDate = _.isDate
isElement = _.isElement
isEmpty = _.isEmpty
isEqual = _.isEqual
isFinite = _.isFinite
isFunction = _.isFunction
isNaN = _.isNaN
isNull = _.isNull
isNumber = _.isNumber
isObject = _.isObject
isPlainObject = _.isPlainObject
isRegExp = _.isRegExp
isString = _.isString
isUndefined = _.isUndefined
keys = _.keys
mapValues = _.mapValues
merge = _.merge
# methods = _.methods → functions
omit = _.omit
pairs = _.pairs
pick = _.pick
transform = _.transform
values = _.values

#
# Utilities
#

now = _.now
constant = _.constant
# createCallback = _.createCallback
escape = _.escape
identity = _.identity
# mixin = _.mixin
# noConflict = _.noConflict
noop = _.noop
# parseInt = _.parseInt
property = _.property
random = _.random
# result = _.result
# runInContext = _.runInContext
# template = _.template
times = _.times
unescape = _.unescape
uniqueId = _.uniqueId

#
# Methods
#

# templateSettings = _.templateSettings.imports._

# 
# Properties
#

# VERSION = _.VERSION
# support = _.support
# support = _.support.argsClass
# support = _.support.argsObject
# support = _.support.enumErrorProps
# support = _.support.enumPrototypes
# support = _.support.funcDecomp
# support = _.support.funcNames
# support = _.support.nonEnumArgs
# support = _.support.nonEnumShadows
# support = _.support.ownLast
# support = _.support.spliceObjects
# support = _.support.unindexedChars
# templateSettings = _.templateSettings
# templateSettings = _.templateSettings.escape
# templateSettings = _.templateSettings.evaluate
# templateSettings = _.templateSettings.interpolate
# templateSettings = _.templateSettings.variable
# templateSettings = _.templateSettings.imports

#
# Global, context-less apply
#

apply = (func, args) -> func.apply null, args


#
# Utility functions
#

isDefined = (value) -> not isUndefined value

isTruthy = (value) -> if value then yes else no

isFalsy = (value) -> if value then no else yes

isError = (value) -> value instanceof Error 

negate = (value) -> not value

always = -> yes

never = -> no


#
# String ops

join = (array, delimiter) -> array.join delimiter

split = (text, delimiter) -> text.split delimiter

words = (text) -> split text, /\s+/


#
# Array ops
#

sort = (array, comparator) -> array.sort comparator

copy = (array) -> array.slice 0

concat = (array, arrays...) -> array.concat.apply array, arrays

unshift = (array, arg) ->
  array.unshift arg
  array

unshiftAll = (array, elements) ->
  array.splice.apply array, [0, 0].concat elements
  array

shift = (array) -> array.shift()

push = (array, arg) ->
  array.push arg
  array

pushAll = (array, elements) ->
  for element in elements
    array.push element
  array

pop = (array) -> array.pop()

splice = (array, args...) -> array.splice.apply array, args

remove = (array, element) ->
  if -1 < index = array.indexOf element
    head array.splice index, 1
  else
    undefined

#TODO test
reverse = (array) -> (array.slice 0).reverse()

clear = (array) ->
  array.length = 0
  array

repeat = (count, value) ->
  array = []
  for i in [0 ... count]
    array.push value
  array

zipCompare = (array1, array2, areEqual) ->
  return no unless isArray array1
  return no unless isArray array2
  return no unless array1.length is array2.length
  if areEqual
    for a, i in array1
      b = array2[i]
      return no unless areEqual a, b
  else
    for a, i in array1
      b = array2[i]
      return no if a isnt b
  yes

same = (array, areEqual) ->
  if array.length > 1
    value = head array
    if isFunction areEqual
      for i in [ 1 ... array.length ]
        unless areEqual value, array[i]
          return no
    else
      for i in [ 1 ... array.length ]
        if value isnt array[i]
          return no
    yes
  else
    yes

valuesAreEqual = (array, pluck, areEqual) ->
  if array.length > 1
    value = pluck head array
    if isFunction areEqual
      for i in [ 1 ... array.length ]
        unless areEqual value, pluck array[i]
          return no
    else
      for i in [ 1 ... array.length ]
        if value isnt pluck array[i]
          return no
    yes
  else
    yes

# Object ops

mapWithKey = (obj, map) ->
  result = []
  for key, value of obj
    result.push map value, key
  result

# Utilities

#TODO test
describeCount = (count, singular, plural) ->
  plural = singular + 's' unless plural
  switch count
    when 0
      "No #{plural}"
    when 1
      "1 #{singular}"
    else
      "#{count} #{plural}"

# http://stackoverflow.com/a/8809472
luid = ->
  d = new Date().getTime()
  'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace /[xy]/g, (c) ->
    r = (d + Math.random() * 16) % 16 | 0
    d = Math.floor d / 16
    (if c is 'x' then r else (r & 0x7 | 0x8)).toString(16)

forEachAsync = (actions, go) ->
  queue = copy actions
  results = []
  runNext = ->
    if action = shift queue
      action (actions.length - queue.length),  (result) ->
        results.push result
        defer runNext
    else
      go results
  defer runNext

#TODO test
formatTimeDuration = (ms) ->
  if ms > 1000
    ms /= 1000
    units = 'sec'

    if ms > 60
      ms /= 60
      units = 'min'

      if ms > 60
        ms /= 60
        units = 'hrs'

    "#{ms.toFixed 2} #{units}"
  else
    "#{ms} ms"


  
  


