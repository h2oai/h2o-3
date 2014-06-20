#
# Custom Knockout.js binding handlers
#
# init:
#   This will be called when the binding is first applied to an element
#   Set up any initial state, event handlers, etc. here
#
# update:
#   This will be called once when the binding is first applied to an element,
#    and again whenever the associated observable changes value.
#   Update the DOM element based on the supplied values here.
#
# Registering a callback on the disposal of an element
# 
# To register a function to run when a node is removed, you can call ko.utils.domNodeDisposal.addDisposeCallback(node, callback). As an example, suppose you create a custom binding to instantiate a widget. When the element with the binding is removed, you may want to call the destroy method of the widget:
# 
# ko.bindingHandlers.myWidget = {
#     init: function(element, valueAccessor) {
#         var options = ko.unwrap(valueAccessor()),
#             $el = $(element);
#  
#         $el.myWidget(options);
#  
#         ko.utils.domNodeDisposal.addDisposeCallback(element, function() {
#             // This will be called when the element is removed by Knockout or
#             // if some other part of your code calls ko.removeNode(element)
#             $el.myWidget("destroy");
#         });
#     }
# };
# 

ko.bindingHandlers.paragraph =
  update: (element, valueAccessor, allBindings, viewModel, bindingContext) ->
    html = ''
    if data = ko.unwrap valueAccessor()
      if -1 isnt data.indexOf '\n'
        html = "<span>#{data.replace /\n/g, '<br/>'}</span>"
      else
        html = data
    ko.utils.setHtml element, html

ko.bindingHandlers.json =
  update: (element, valueAccessor, allBindings, viewModel, bindingContext) ->
    data = ko.unwrap valueAccessor()

    $(element).text JSON.stringify data, null, 2

ko.bindingHandlers.geyser =
  update: (element, valueAccessor, allBindings, viewModel, bindingContext) ->
    if data = ko.unwrap valueAccessor()
      $element = $ element
      if data.markup
        $element.html geyser.render data.markup
        if data.behavior
          data.behavior $element
        if data.dispose
          ko.utils.domNodeDisposal.addDisposeCallback element, -> data.dispose()
      else
        $element.html geyser.render data
    else
      $(element).text '-'
    return

ko.bindingHandlers.icon =
  update: (element, valueAccessor, allBindings, viewModel, bindingContext) ->
    if icon = ko.unwrap valueAccessor()
      element.className = "fa fa-#{icon.image}"
      element.style.color = if icon.color then icon.color else null
      element.title = if icon.caption then icon.caption else ''
    return

ko.bindingHandlers.hover =
  init: (element, valueAccessor, allBindings, viewModel, bindingContext) ->
    handler = ko.unwrap valueAccessor()
    throw new Error 'Not a function' unless isFunction handler
    $element = $ element
    handlerIn = -> handler yes
    handlerOut = -> handler no
    $element.hover handlerIn, handlerOut

    #TODO can remove this callback if ko/jquery are disposing the tooltip's bindings properly.
    ko.utils.domNodeDisposal.addDisposeCallback element, ->
      $element.off 'mouseenter mouseleave'

ko.bindingHandlers.tooltip =
  update: (element, valueAccessor, allBindings, viewModel, bindingContext) ->
    title = ko.unwrap valueAccessor()
    $element = $ element

    #HACK simply setting a new title without calling 'destroy' does not update the tooltip.
    $element.tooltip 'destroy'
    if title
      $element.tooltip title: title

    #TODO can remove this callback if ko/jquery are disposing the tooltip's bindings properly.
    ko.utils.domNodeDisposal.addDisposeCallback element, ->
      $element.tooltip 'destroy'

timeagoUpdateInterval = 60000
momentTimestampFormat = 'MMMM Do YYYY, h:mm:ss a'
ko.bindingHandlers.timeago =
  update: (element, valueAccessor, allBindings, viewModel, bindingContext) ->
    timestamp = ko.unwrap valueAccessor()
    timestamp = parseInt timestamp if isString timestamp
    $element = $ element
    date = moment new Date timestamp
    $element.attr 'title', date.format momentTimestampFormat
    tick = ->
      label = date.fromNow()
      if $element.text() isnt label
        $element.text label
      return

    if window.steam
      window.steam.context.schedule timeagoUpdateInterval, tick

      ko.utils.domNodeDisposal.addDisposeCallback element, ->
        window.steam.context.unschedule timeagoUpdateInterval, tick

    tick()
    return

ko.bindingHandlers.collapse =
  init: (element, valueAccessor, allBindings, viewModel, bindingContext) ->
    angleDown = 'fa-angle-down'
    angleRight = 'fa-angle-right'
    isCollapsed = ko.unwrap valueAccessor()
    disclosureEl = document.createElement 'i'
    disclosureEl.className = 'fa'
    element.appendChild disclosureEl
    $el = $ element
    $nextEl = $el.next()
    throw new Error 'No collapsible sibling found' unless $nextEl.length
    $disclosureEl = $ disclosureEl
    toggle = ->
      if isCollapsed
        $disclosureEl
          .removeClass angleDown
          .addClass angleRight
        $nextEl.hide()
      else
        $disclosureEl
          .removeClass angleRight
          .addClass angleDown
        $nextEl.show()
      isCollapsed = not isCollapsed

    $el.css 'cursor', 'pointer'
    $el.attr 'title', 'Click to expand/collapse'
    $disclosureEl.css 'margin-left', '10px'
    $el.on 'click', toggle
    toggle()
    ko.utils.domNodeDisposal.addDisposeCallback element, ->
      $el.off 'click'
    return

ko.bindingHandlers.raw =
  update: (element, valueAccessor, allBindings, viewModel, bindingContext) ->
    arg = ko.unwrap valueAccessor()
    if arg
      $element = $ element
      $element.empty()
      $element.append arg
    return


ko.bindingHandlers.help =
  init: (element, valueAccessor, allBindings, viewModel, bindingContext) ->
    if steam = window.steam
      $element = $ element
      arg = ko.unwrap valueAccessor()
      if isFunction arg
        $element.on 'click', -> do arg
      else if isString arg
        $element.on 'click', -> steam.context.help arg
      else
        throw new Error 'Invalid argument'
      ko.utils.domNodeDisposal.addDisposeCallback element, -> $element.off 'click'
    return

captureClickAndDrag = ($el, onClick, onDrag, onRelease) ->
  $document = $ document

  $el.on 'mousedown', (e) ->
    return if e.which isnt 1 # Left clicks only
    zIndex = $el.css 'z-index'
    $el.css 'z-index', 1000

    onMouseMove = (e) ->
      onDrag e.pageX, e.pageY

    onMouseUp = (e) ->
      # restore z-index
      $el.css 'z-index', zIndex
      $document.off 'mousemove', onMouseMove
      $document.off 'mouseup', onMouseUp
      onRelease e.pageX, e.pageY

    $document.on 'mousemove', onMouseMove
    $document.on 'mouseup', onMouseUp

    # disable selection
    e.preventDefault()
    onClick e.pageX, e.pageY
    return

makeGrabBar = ($el, _opts, go) ->
  _offset = null
  _left = _top = _width = _height = _x = _y = 0

  readElementSize = ->
    _width = $el.outerWidth()
    _height = $el.outerHeight()
    _offset = $el.offset()

  onClick = (x, y) ->
    readElementSize()
    _x = _offset.left + _width - x
    _y = _offset.top + _height - y

  onDrag = (x, y) ->
    left = if _opts.allowHorizontalMovement then x + _x - _width else _offset.left 
    top = if _opts.allowVerticalMovement then y + _y - _height else _offset.top
    if left isnt _left or top isnt _top
      _left = left
      _top = top
      $el.offset left: left, top: top

  onRelease = (x, y) ->
    readElementSize()
    go
      left: _offset.left
      top: _offset.top
      width: _width
      height: _height

  captureClickAndDrag $el, onClick, onDrag, onRelease

ko.bindingHandlers.draggable =
  init: (element, valueAccessor, allBindings, viewModel, bindingContext) ->
    $el = $ element
    grabBarOpts =  allowVerticalMovement: yes, allowHorizontalMovement: no 
    makeGrabBar $el, grabBarOpts, (rect) ->
      #TODO - resize linked elements
      console.log rect

ko.bindingHandlers.enterKey =
  init: (element, valueAccessor, allBindings, viewModel, bindingContext) ->
    if action = ko.unwrap valueAccessor() 
      if isFunction action
        $element = $ element
        $element.keydown (e) -> 
          if e.which is 13
            action viewModel
          return
      else
        throw 'Enter key action is not a function'
    return

ko.bindingHandlers.typeahead =
  init: (element, valueAccessor, allBindings, viewModel, bindingContext) ->
    if action = ko.unwrap valueAccessor() 
      if isFunction action
        $element = $ element
        $element.typeahead null,
          displayKey: 'value'
          source: action
      else
        throw 'Typeahead action is not a function'
    return

