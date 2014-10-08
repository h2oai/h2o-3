#
# Reactive programming / Dataflow programming wrapper over KO
#

Steam.Hypergraph = do ->

  createEdge = ->
    arrow = null

    self = (args...) ->
      if arrow
        arrow.func.apply null, args
      else
        undefined

    self.subscribe = (func) ->
      console.assert isFunction func
      if arrow
        throw new Error 'Cannot re-attach edge'
      else
        arrow =
          func: func
          dispose: -> arrow = null

    self.dispose = ->
      arrow.dispose() if arrow

    self

  createHyperedge = ->
    arrows = []

    self = (args...) ->
      map arrows, (arrow) -> arrow.func.apply null, args

    self.subscribe = (func) ->
      console.assert isFunction func
      arrows.push arrow =
        func: func
        dispose: -> remove arrows, arrow
      arrow

    self.dispose = ->
      forEach (copy arrows), (arrow) -> arrow.dispose()

    self

  if ko?
    createObservable = ko.observable
    createObservableArray = ko.observableArray
    isObservable = ko.isObservable
  else
    createObservable = (initialValue) ->
      arrows = []
      currentValue = initialValue

      notifySubscribers = (arrows, newValue) ->
        for arrow in arrows
          arrow.func newValue
        return

      self = (newValue) ->
        if arguments.length is 0
          currentValue
        else
          unchanged = if self.equalityComparer
            self.equalityComparer currentValue, newValue
          else
            currentValue is newValue

          unless unchanged
            currentValue = newValue
            notifySubscribers arrows, newValue

      self.subscribe = (func) ->
        console.assert isFunction func
        arrows.push arrow =
          func: func
          dispose: -> remove arrows, arrow
        arrow

      self.__observable__ = yes

      self

    createObservableArray = createObservable

    isObservable = (obj) -> if obj.__observable__ then yes else no

  createNode = (value, equalityComparer) ->
    if arguments.length is 0
      createNode undefined, never
    else
      observable = createObservable value
      observable.equalityComparer = equalityComparer if isFunction equalityComparer
      observable

  isNode = isObservable

  createPolynode = (array) -> createObservableArray array or []

  createContext = (edges, parent=null) ->
    context = root: null, parent: null

    if parent
      console.assert isDefined parent.root
      context.root = parent.root
      context.parent = parent
    else
      # This is the root
      context.root = context.parent = context

    for name, edge of edges
      console.assert isFunction edge
      console.assert not(name of context)

      #TODO Policy injection for debugging
      context[name] = edge

    context

  link = (node, func) ->
    console.assert isFunction node, '[node] is not a function'
    console.assert isFunction node.subscribe, '[node] does not have a [dispose] method'
    console.assert isFunction func, '[func] is not a function'

    node.subscribe func

  unlink = (arrows) ->
    if isArray arrows
      for arrow in arrows
        console.assert isFunction arrow.dispose, '[arrow] does not have a [dispose] method'
        arrow.dispose()
    else
      console.assert isFunction arrows.dispose, '[arrow] does not have a [dispose] method'
      arrows.dispose()

  #
  # Combinators
  #

  _apply = (sources, func) ->
    func.apply null, map sources, (source) -> source()

  callOnChange = (sources..., func) ->
    func()
    map sources, (source) ->
      link source, -> func()

  applyOnChange = (sources..., func) ->
    _apply sources, func
    map sources, (source) ->
      link source, -> _apply sources, func

  joinNodes = (sources..., target, func) ->
    console.assert isFunction target, '[target] is not a function'
    target _apply sources, func
    map sources, (source) ->
      link source, -> target _apply sources, func

  zipNodes = (sources, func) ->
    evaluate = -> _apply sources, func
    target = createNode evaluate()
    map sources, (source) ->
      link source, -> target evaluate()
    target

  liftNodes = (sources..., func) ->
    zipNodes sources, func

  filterNode = (source, predicate) ->
    target = createNode if predicate value = source() then value else undefined
    link source, (value) -> target value if predicate value
    target

  switchNodes = (defaultValue, count) ->
    choices = []
    for i in [0 ... count]
      choices.push node$ defaultValue, (a, b) -> a is b

    forEach choices, (source) ->
      link source, (value) ->
        if value isnt defaultValue
          for target, i in choices when source isnt target
            target defaultValue
        return
    choices

  debounceNode = (source, wait, options) ->
    target = createNode undefined
    link source, debounce target, wait, options
    target

  throttleNode = (source, wait, options) ->
    target = createNode undefined
    link source, throttle target, wait, options
    target

  createEdge: createEdge
  createHyperedge: createHyperedge
  createNode: createNode
  isNode: isNode
  createPolynode: createPolynode
  createContext: createContext
  link: link
  unlink: unlink
  callOnChange: callOnChange
  applyOnChange: applyOnChange
  joinNodes: joinNodes
  zipNodes: zipNodes
  liftNodes: liftNodes
  filterNode: filterNode
  switchNodes: switchNodes
  debounceNode: debounceNode
  throttleNode: throttleNode


#
# Destructure into application scope with shorter names.
#

{ createEdge: edge$, createHyperedge: edges$, createNode: node$, isNode: isNode$, createPolynode: nodes$, createContext: context$, link: link$, unlink: unlink$, callOnChange: call$, applyOnChange: apply$, joinNodes: join$, zipNodes: zip$, liftNodes: lift$, filterNode: filter$, switchNodes: switch$, debounceNode: debounce$, throttleNode: throttle$ } = Steam.Hypergraph


#
# Common combinators easily expressed with zip$().
#

if$ = (condition, valueIfTrue, valueIfFalse) ->
  zip$ [condition, valueIfTrue, valueIfFalse], (c, t, f) -> if c then t else f

and$ = (sources...) ->
  zip$ sources, (values...) -> every values

or$ = (sources...) ->
  zip$ sources, (values...) -> some values

not$ = (source) ->
  zip$ [source], negate


