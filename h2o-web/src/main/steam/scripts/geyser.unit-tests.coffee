test "geyser generates div (scalar arg)", (t) ->
  [div] = geyser.generate 'div'
  dom = div 'hello'
  t.deepEqual dom,
    params: undefined
    tag:
      name: 'div'
      classes: null
      attrs: null
    content: 'hello'
  t.equal (geyser.render dom), "<div>hello</div>"
  t.end()

test "geyser generates div (vector arg)", (t) ->
  [div] = geyser.generate [ 'div' ]
  dom = div 'hello'
  t.deepEqual dom,
    params: undefined
    tag:
      name: 'div'
      classes: null
      attrs: null
    content: 'hello'
  t.equal (geyser.render dom), "<div>hello</div>"
  t.end()

test "geyser generates div with id", (t) ->
  [div] = geyser.generate "div id='$id'"
  dom = div 'hello', $id:'foo'
  t.deepEqual dom,
    params: 
      $id: 'foo'
    tag:
      name: 'div'
      classes: null
      attrs: "id='$id'"
    content: 'hello'
  t.equal (geyser.render dom), "<div id='foo'>hello</div>"
  t.end()

test "geyser generates .foo", (t) ->
  [div] = geyser.generate '.foo'
  dom = div 'hello'
  t.deepEqual dom,
    params: undefined
    tag:
      name: 'div'
      classes: 'foo'
      attrs: null
    content: 'hello'
  t.equal (geyser.render dom), "<div class='foo'>hello</div>"
  t.end()

test "geyser generates .foo.bar", (t) ->
  [div] = geyser.generate '.foo.bar'
  dom = div 'hello'
  t.deepEqual dom,
    params: undefined
    tag:
      name: 'div'
      classes: 'foo bar'
      attrs: null
    content: 'hello'
  t.equal (geyser.render dom), "<div class='foo bar'>hello</div>"
  t.end()

test "geyser generates span.foo.bar", (t) ->
  [span] = geyser.generate 'span.foo.bar'
  dom = span 'hello'
  t.deepEqual dom,
    params: undefined
    tag:
      name: 'span'
      classes: 'foo bar'
      attrs: null
    content: 'hello'
  t.equal (geyser.render dom), "<span class='foo bar'>hello</span>"
  t.end()

test "geyser generates a.foo href='http://localhost/'", (t) ->
  [a] = geyser.generate "a.foo href='http://localhost/'"
  dom = a 'hello'
  t.deepEqual dom,
    params: undefined
    tag:
      name: 'a'
      classes: 'foo'
      attrs: "href='http://localhost/'"
    content: 'hello'
  t.equal (geyser.render dom), "<a class='foo' href='http://localhost/'>hello</a>"
  t.end()

test "geyser generates .foo data-id='bar'", (t) ->
  [div] = geyser.generate ".foo data-id='bar'"
  dom = div 'hello'
  t.deepEqual dom,
    params: undefined
    tag:
      name: 'div'
      classes: 'foo'
      attrs: "data-id='bar'"
    content: 'hello'
  t.equal (geyser.render dom), "<div class='foo' data-id='bar'>hello</div>"
  t.end()

test "geyser generates input type='checkbox' data-id='bar' checked", (t) ->
  [input] = geyser.generate "input type='checkbox' data-id='bar' checked"
  dom = input 'hello'
  t.deepEqual dom,
    params: undefined
    tag:
      name: 'input'
      classes: null
      attrs: "type='checkbox' data-id='bar' checked"
    content: 'hello'
  t.equal (geyser.render dom), "<input type='checkbox' data-id='bar' checked>hello</input>"
  t.end()

test "geyser generates input.foo type='checkbox' data-id='bar' checked", (t) ->
  [input] = geyser.generate "input.foo type='checkbox' data-id='bar' checked"
  dom = input 'hello'
  t.deepEqual dom,
    params: undefined
    tag:
      name: 'input'
      classes: 'foo'
      attrs: "type='checkbox' data-id='bar' checked"
    content: 'hello'
  t.equal (geyser.render dom), "<input class='foo' type='checkbox' data-id='bar' checked>hello</input>"
  t.end()

test "geyser generates 1 nested element", (t) ->
  [div] = geyser.generate '.foo'
  dom = div div 'hello'
  t.deepEqual dom,
    params: undefined
    tag:
      name: 'div'
      classes: 'foo'
      attrs: null
    content: [
      params: undefined
      tag:
        name: 'div'
        classes: 'foo'
        attrs: null
      content: 'hello'
    ]
  t.equal (geyser.render dom), "<div class='foo'><div class='foo'>hello</div></div>"
  t.end()

test "geyser generates 2 levels of nested elements", (t) ->
  [div] = geyser.generate '.foo'
  dom = div div div 'hello'
  t.deepEqual dom,
    params: undefined
    tag:
      name: 'div'
      classes: 'foo'
      attrs: null
    content: [
      params: undefined
      tag:
        name: 'div'
        classes: 'foo'
        attrs: null
      content: [
        params: undefined
        tag:
          name: 'div'
          classes: 'foo'
          attrs: null
        content: 'hello'
      ]
    ]
  t.equal (geyser.render dom), "<div class='foo'><div class='foo'><div class='foo'>hello</div></div></div>"
  t.end()

test "geyser generates 1+ nested elements", (t) ->
  [div] = geyser.generate '.foo'
  dom = div [
    div 'hello'
    div 'world'
  ]
  t.deepEqual dom,
    params: undefined
    tag:
      name: 'div'
      classes: 'foo'
      attrs: null
    content: [
      params: undefined
      tag:
        name: 'div'
        classes: 'foo'
        attrs: null
      content: 'hello'
    ,
      params: undefined
      tag:
        name: 'div'
        classes: 'foo'
        attrs: null
      content: 'world'
    ]
  t.equal (geyser.render dom), "<div class='foo'><div class='foo'>hello</div><div class='foo'>world</div></div>"
  t.end()
