test = require 'tape'
coffee = require 'coffee-script'
shorthand = require './shorthand.coffee'

symbols =
  # qualification
  round: type: 'qualify', object: 'Math'
  PI: type: 'qualify', object: 'Math'
  rotate: type: 'qualify', object: 'context'
  transform: type: 'qualify', object: 'context', property: 'setTransform'
  fillStyle: type: 'qualify', object: 'context'
  canvas: type: 'qualify', object: 'context', property: 'domElement'
  debug: type: 'qualify', object: 'console'
  rect$0: type: 'qualify', object: 'routines', property: 'drawPixel'
  rect$1: type: 'qualify', object: 'routines', property: 'drawSquare'
  rect$2: type: 'qualify', object: 'routines', property: 'drawBox'
  rect$4: type: 'qualify', object: 'routines', property: 'drawRect'
  rect: type: 'qualify', object: 'routines', property: 'drawPolygon'
  # invocation
  push: type: 'invoke'
  shift: type: 'invoke'
  dataOf: type: 'invoke', property: 'toDataURL'
  contextOf: type: 'invoke', property: 'getContext'
  

diag = (lines) ->
  for line in lines.split /\n/g
    console.log '# ' + line
  return

gold = '''
require() is variadic
---
require 'context', 'console'
rotate debug
---
context.rotate(console.debug);
===

Does not affect qualified require()
---
context = require 'context'
---
var context;
context = require('context');
===

Processes require() when resolved
---
require 'context'
rotate bar
---
context.rotate(bar);
===

Removes unqualified require() when resolved
---
require 'context'
---
===

Does not remove unqualified require() when unresolved
---
require 'unknown'
f1 bar
---
require('unknown');
f1(bar);
===

Does not affect identifiers when unresolved
---
rotate bar
---
rotate(bar);
===

Affects functions
---
require 'context'
rotate bar
---
context.rotate(bar);
===

Affects vars
---
require 'context'
fillStyle = bar
---
context.fillStyle = bar;
===

Affects objects
---
require 'context'
canvas.width = bar
---
context.domElement.width = bar;
===

Affects functions in blocks
---
require 'context'
foo -> bar -> rotate bar
---
foo(function () {
    return bar(function () {
        return context.rotate(bar);
    });
});
===

Affects vars in blocks
---
require 'context'
foo -> bar -> fillStyle = bar
---
foo(function () {
    return bar(function () {
        return context.fillStyle = bar;
    });
});
===

Affects objects in blocks
---
require 'context'
foo -> bar -> canvas.width = bar
---
foo(function () {
    return bar(function () {
        return context.domElement.width = bar;
    });
});
===

Affects functions in arguments
---
require 'context'
rotate round PI
---
context.rotate(Math.round(Math.PI));
===

Affects arguments
---
require 'context'
rotate PI
---
context.rotate(Math.PI);
===

Affects objects in arguments
---
require 'context'
rotate canvas.angle
---
context.rotate(context.domElement.angle);
===

Aliases functions
---
require 'context'
rotate foo
---
context.rotate(foo);
===

Aliases vars
---
require 'context'
canvas = bar
---
context.domElement = bar;
===

Aliases objects
---
require 'context'
canvas.width = bar
---
context.domElement.width = bar;
===

Dispatches variadic arguments properly
---
require 'routines'
rect()
rect a
rect a, b
rect a, b, c
rect a, b, c, d
rect a, b, c, d, e
---
routines.drawPixel();
routines.drawSquare(a);
routines.drawBox(a, b);
routines.drawPolygon(a, b, c);
routines.drawRect(a, b, c, d);
routines.drawPolygon(a, b, c, d, e);
===

Does not affect functions with aliased names
---
require 'context'
setTransform foo
---
setTransform(foo);
===

Does not affect vars with aliased names
---
require 'context'
domElement = bar
---
var domElement;
domElement = bar;
===

Does not affect object methods
---
require 'context'
foo.rotate bar
---
foo.rotate(bar);
===

Does not affect object properties
---
require 'context'
foo.fillStyle bar
---
foo.fillStyle(bar);
===

Does not affect object chains
---
require 'context'
foo.canvas.foo = bar
---
foo.canvas.foo = bar;
===

Transforms functions to invocations
---
shift foo
push foo, bar
push foo, bar, baz, qux
push [], bar, baz, qux
push (foo), bar, baz, qux
push ([]), bar, baz, qux
---
foo.shift();
foo.push(bar);
foo.push(bar, baz, qux);
[].push(bar, baz, qux);
foo.push(bar, baz, qux);
[].push(bar, baz, qux);
===

Does not transform object properties to invocations
---
foo.push bar
foo.push.apply null, []
[].push.apply null, []
---
foo.push(bar);
foo.push.apply(null, []);
[].push.apply(null, []);
===

Does not transform functions without arguments to invocations
---
shift()
push()
---
shift();
push();
===

Transforms function aliases into object invocations
---
dataOf foo
dataOf ''
contextOf foo, bar
contextOf foo, bar, baz, qux
contextOf [], bar, baz, qux
contextOf (foo), bar, baz, qux
contextOf ([]), bar, baz, qux
---
foo.toDataURL();
''.toDataURL();
foo.getContext(bar);
foo.getContext(bar, baz, qux);
[].getContext(bar, baz, qux);
foo.getContext(bar, baz, qux);
[].getContext(bar, baz, qux);
'''

gold.split(/\={3,}/g).forEach (testCase) ->
  [ title, input, expected ] = testCase.split /\-{3,}/g
  if title and input
    test title.trim(), (t) ->
      t.plan 1
      js = coffee.compile input.trim(), bare: yes
      actual = shorthand symbols, js, implicits: [ 'Math' ]
      #diag actual
      t.equal actual.trim(), expected.trim()

