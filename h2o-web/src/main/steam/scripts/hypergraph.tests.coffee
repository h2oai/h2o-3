test 'hypergraph api should have functions available in application scope', (t) ->
  funcs = [
    edge$
    edges$
    node$
    isNode$
    nodes$
    link$
    unlink$
    call$
    apply$
    join$
    zip$
    lift$
    filter$
    switch$
    if$
    and$
    or$
    not$
    debounce$
    throttle$
  ]
  for func in funcs
    t.equal (typeof func), 'function'
  t.end()

test 'hypergraph edge should not fail when unlinked', (t) ->
  func = do edge$
  result = null
  t.doesNotThrow -> result = func 1, 2, 3
  t.ok isUndefined result
  t.end()

test 'hypergraph edge should propagate when linked', (t) ->
  func = do edge$
  link$ func, (a, b, c) -> a + b + c
  t.equal func(1, 2, 3), 6
  t.end()

test 'hypergraph edge should raise exception when re-linked', (t) ->
  func = do edge$
  link$ func, (a, b, c) -> a + b + c
  t.equal func(1, 2, 3), 6
  t.throws -> link$ func, (a, b, c) -> a * b * c
  t.end()

test 'hypergraph edge should stop propagating when unlinked', (t) ->
  func = do edge$
  target = (a, b, c) -> a + b + c
  arrow = link$ func, target
  t.equal func(1, 2, 3), 6
  unlink$ arrow
  result = null
  t.doesNotThrow -> result = func 1, 2, 3
  t.ok isUndefined result
  t.end()

test 'hypergraph edge should stop propagating when disposed', (t) ->
  func = do edge$
  target = (a, b, c) -> a + b + c
  arrow = link$ func, target
  t.equal func(1, 2, 3), 6
  func.dispose()
  result = null
  t.doesNotThrow -> result = func 1, 2, 3
  t.ok isUndefined result
  t.end()

test 'hypergraph edges should not fail when unlinked', (t) ->
  func = do edges$
  result = null
  t.doesNotThrow -> result = func 1, 2, 3
  t.deepEqual result, []
  t.end()

test 'hypergraph edges should propagate when linked', (t) ->
  func = do edges$
  link$ func, (a, b, c) -> a + b + c
  t.deepEqual func(1, 2, 3), [6]
  t.end()

test 'hypergraph edges should allow multicasting', (t) ->
  func = do edges$
  addition = (a, b, c) -> a + b + c
  multiplication = (a, b, c) -> a * b * c
  link$ func, addition
  link$ func, multiplication
  t.deepEqual func(2, 3, 4), [9, 24]
  t.end()

test 'hypergraph edges should stop propagating when unlinked', (t) ->
  func = do edges$
  addition = (a, b, c) -> a + b + c
  multiplication = (a, b, c) -> a * b * c
  additionArrow = link$ func, addition
  multiplicationArrow = link$ func, multiplication
  t.deepEqual func(2, 3, 4), [9, 24]
  unlink$ additionArrow
  t.deepEqual func(2, 3, 4), [24]
  unlink$ multiplicationArrow
  t.deepEqual func(2, 3, 4), []
  t.end()

test 'hypergraph edges should stop propagating when disposed', (t) ->
  func = do edges$
  addition = (a, b, c) -> a + b + c
  multiplication = (a, b, c) -> a * b * c
  additionArrow = link$ func, addition
  multiplicationArrow = link$ func, multiplication
  t.deepEqual func(2, 3, 4), [9, 24]
  func.dispose()
  t.deepEqual func(2, 3, 4), []
  t.end()

test 'hypergraph node should hold value when initialized', (t) ->
  node = node$ 42
  t.equal node(), 42
  t.end()

test 'hypergraph node should return value when called without arguments', (t) ->
  node = node$ 42
  t.equal node(), 42
  t.end()

test 'hypergraph node should hold new value when reassigned', (t) ->
  node = node$ 42
  t.equal node(), 42
  node 43
  t.equal node(), 43
  t.end()

test 'hypergraph node should not propagate unless value is changed (without comparator)', (t) ->
  node = node$ 42
  propagated = no
  link$ node, (value) -> propagated = yes
  t.equal propagated, no
  node 42
  t.equal propagated, no
  t.end()

test 'hypergraph node should propagate value when value is changed (without comparator)', (t) ->
  node = node$ 42
  propagated = no
  propagatedValue = 0
  link$ node, (value) ->
    propagated = yes
    propagatedValue = value
  t.equal propagated, no
  node 43
  t.equal propagated, yes
  t.equal propagatedValue, 43
  t.end()

test 'hypergraph node should not propagate unless value is changed (with comparator)', (t) ->
  comparator = (a, b) -> a.answer is b.answer
  node = node$ { answer: 42 }, comparator
  propagated = no
  link$ node, (value) -> propagated = yes
  t.equal propagated, no
  node answer: 42
  t.equal propagated, no
  t.end()

test 'hypergraph node should propagate when value is changed (with comparator)', (t) ->
  comparator = (a, b) -> a.answer is b.answer
  node = node$ { answer: 42 }, comparator
  propagated = no
  propagatedValue = null
  link$ node, (value) ->
    propagated = yes
    propagatedValue = value
  t.equal propagated, no

  newValue = answer: 43
  node newValue
  t.equal propagated, yes
  t.equal propagatedValue, newValue
  t.end()

test 'hypergraph node should allow multicasting', (t) ->
  node = node$ 42
  propagated1 = no
  propagated2 = no
  target1 = (value) -> propagated1 = yes
  target2 = (value) -> propagated2 = yes
  link$ node, target1
  link$ node, target2
  t.equal propagated1, no
  t.equal propagated2, no

  node 43
  t.equal propagated1, yes
  t.equal propagated2, yes
  t.end()

test 'hypergraph node should stop propagating when unlinked', (t) ->
  node = node$ 42
  propagated1 = no
  propagated2 = no
  target1 = (value) -> propagated1 = yes
  target2 = (value) -> propagated2 = yes
  arrow1 = link$ node, target1
  arrow2 = link$ node, target2
  t.equal propagated1, no
  t.equal propagated2, no

  node 43
  t.equal propagated1, yes
  t.equal propagated2, yes

  propagated1 = no
  propagated2 = no
  unlink$ arrow2
  node 44
  t.equal propagated1, yes
  t.equal propagated2, no

  propagated1 = no
  propagated2 = no
  unlink$ arrow1
  node 45
  t.equal propagated1, no
  t.equal propagated2, no
  t.end()

test 'hypergraph empty nodes should always propagate', (t) ->
  event = do node$
  propagated = no
  link$ event, -> propagated = yes
  t.equal propagated, no
  event yes
  t.equal propagated, yes
  t.end()

test 'hypergraph context should enclose deeply nested subgraphs correctly', (t) ->
  foo1 = ->
  bar1 = ->
  baz1 = ->
  foo2 = ->
  bar2 = ->
  baz2 = ->
  foo3 = ->
  bar3 = ->
  baz3 = ->

  grandparent = context$ foo1: foo1, bar1: bar1, baz1: baz1
  parent = context$ { foo2: foo2, bar2: bar2, baz2: baz2 }, grandparent
  child = context$ { foo3: foo3, bar3: bar3, baz3: baz3 }, parent

  t.equal grandparent.root, grandparent
  t.equal grandparent.parent, grandparent
  t.equal grandparent.foo1, foo1
  t.equal grandparent.bar1, bar1
  t.equal grandparent.baz1, baz1

  t.equal parent.root, grandparent
  t.equal parent.parent, grandparent
  t.equal parent.foo2, foo2
  t.equal parent.bar2, bar2
  t.equal parent.baz2, baz2

  t.equal child.root, grandparent
  t.equal child.parent, parent
  t.equal child.foo3, foo3
  t.equal child.bar3, bar3
  t.equal child.baz3, baz3
  t.end()


test 'hypergraph context should unlink multiple arrows at once', (t) ->
  node = node$ 42
  propagated1 = no
  propagated2 = no
  target1 = (value) -> propagated1 = yes
  target2 = (value) -> propagated2 = yes
  arrow1 = link$ node, target1
  arrow2 = link$ node, target2
  t.equal propagated1, no
  t.equal propagated2, no

  node 43
  t.equal propagated1, yes
  t.equal propagated2, yes

  propagated1 = no
  propagated2 = no
  unlink$ [ arrow1, arrow2 ]
  node 44
  t.equal propagated1, no
  t.equal propagated2, no
  t.end()

test 'hypergraph call$', (t) ->
  width = node$ 2
  height = node$ 6
  area = 0
  arrow = call$ width, height, -> area = width() * height()
  t.equal area, 12

  width 7
  t.equal area, 42

  unlink$ arrow
  width 2
  t.equal area, 42
  t.end()

test 'hypergraph apply$', (t) ->
  width = node$ 2
  height = node$ 6
  area = 0
  arrow = apply$ width, height, (w, h) -> area = w * h
  t.equal area, 12

  width 7
  t.equal area, 42

  unlink$ arrow
  width 2
  t.equal area, 42
  t.end()

test 'hypergraph join$', (t) ->
  width = node$ 2
  height = node$ 6
  area = node$ 0
  arrow = join$ width, height, area, (w, h) -> w * h
  t.equal area(), 12

  width 7
  t.equal area(), 42

  unlink$ arrow
  width 2
  t.equal area(), 42
  t.end()

test 'hypergraph zip$', (t) ->
  width = node$ 2
  height = node$ 6
  area = zip$ [width, height], (w, h) -> w * h
  t.equal area(), 12

  width 7
  t.equal area(), 42
  t.end()

test 'hypergraph lift$', (t) ->
  width = node$ 2
  height = node$ 6
  area = lift$ width, height, (w, h) -> w * h
  t.equal area(), 12

  width 7
  t.equal area(), 42
  t.end()


test 'hypergraph filter$', (t) ->
  integers = node$ 10
  evens = filter$ integers, (n) -> n % 2 is 0
  t.equal evens(), 10
  integers 9
  t.equal evens(), 10
  integers 8
  t.equal evens(), 8
  t.end()

test 'hypergraph switch$', (t) ->
  defaultValue = {}
  someValue = {}
  [choice1, choice2, choice3] = switch$ defaultValue, 3
  
  t.equal choice1(), defaultValue
  t.equal choice2(), defaultValue
  t.equal choice3(), defaultValue

  choice1 someValue
  t.equal choice1(), someValue
  t.equal choice2(), defaultValue
  t.equal choice3(), defaultValue

  choice2 someValue
  t.equal choice1(), defaultValue
  t.equal choice2(), someValue
  t.equal choice3(), defaultValue

  choice3 someValue
  t.equal choice1(), defaultValue
  t.equal choice2(), defaultValue
  t.equal choice3(), someValue
  t.end()

test 'hypergraph if$', (t) ->
  english = node$ 'thank you'
  spanish = node$ 'gracias'
  language = node$ 'english'
  isEnglish = lift$ language, (language) -> language is 'english'
  greeting = if$ isEnglish, english, spanish
  t.equal greeting(), 'thank you'
  language 'spanish'
  t.equal greeting(), 'gracias'
  t.end()


test 'hypergraph and$', (t) ->
  hasRed = node$ yes
  hasGreen = node$ yes
  hasBlue = node$ yes
  hasColor = and$ hasRed, hasGreen, hasBlue
  t.equal hasColor(), yes
  hasRed no
  t.equal hasColor(), no
  hasRed yes
  t.equal hasColor(), yes
  t.end()

test 'hypergraph or$', (t) ->
  hasRed = node$ no
  hasGreen = node$ no
  hasBlue = node$ no
  hasComponent = or$ hasRed, hasGreen, hasBlue
  t.equal hasComponent(), no
  hasRed yes
  t.equal hasComponent(), yes
  hasRed no
  t.equal hasComponent(), no
  t.end()

test 'hypergraph not$', (t) ->
  isTransparent = node$ yes
  isOpaque = not$ isTransparent
  t.equal isOpaque(), no
  isTransparent no
  t.equal isOpaque(), yes
  t.end()

