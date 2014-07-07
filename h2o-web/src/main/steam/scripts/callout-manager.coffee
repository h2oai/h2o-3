Steam.CalloutManager = (_) ->
  pointerSize = 20
  s2 = pointerSize * 2
  s3 = pointerSize * 3

  _containerEl = null
  _calloutEl = null
  _textEl = null
  _pointerSW = null
  _pointerSE = null
  _pointerNW = null
  _pointerNE = null
  _pointerEls = null
  _$text = null

  [ table, tbody, tr, th, td ] = geyser.generate words 'table tbody tr th td'
  initialize = ->
    $overlays = $ '.y-overlays'
    _containerEl = $overlays.parent().get 0

    _calloutEl = document.createElement 'div'
    _calloutEl.className = 'y-callout y-monospace'
    _pointerSW = createCallout 'w', 's'
    _pointerSE = createCallout 'e', 's'
    _pointerNW = createCallout 'w', 'n'
    _pointerNE = createCallout 'e', 'n'
    _pointerEls = [ _pointerSW, _pointerSE, _pointerNW, _pointerNE ]
    _textEl = document.createElement 'div'
    _textEl.className = 'y-callout-text'
    _$text = $ _textEl

    _calloutEl.appendChild _pointerSW
    _calloutEl.appendChild _pointerSE
    _calloutEl.appendChild _pointerNW
    _calloutEl.appendChild _pointerNE
    _calloutEl.appendChild _textEl

    $overlays.append _calloutEl

  switchPointer = (target) ->
    for source in _pointerEls
      source.style.display = if source is target then null else 'none'
    return

  createCallout = (ew, ns) ->
    x = if ew is 'e' then 0 else -s3
    y = if ns is 's' then 0 else -s3
    translateX = if ew is 'e' then 0 else s3
    translateY = if ns is 's' then 0 else s3
    scaleX = if ew is 'e' then 1 else -1
    scaleY = if ns is 's' then 1 else -1

    svg = document.createElementNS 'http://www.w3.org/2000/svg', 'svg'
    d3.select svg
      .attr 'width', s3
      .attr 'height', s3
      .style 'position', 'absolute'
      .style 'display', 'none'
      .style 'left', "#{x}px"
      .style 'top', "#{y}px"
      .append 'g'
      .attr 'class', 'y-svg-callout' 
      .attr 'transform', "translate(#{translateX}, #{translateY}) scale(#{scaleX},#{scaleY})" 
      .append 'polyline'
      .attr 'points', "0,0 #{s2},#{s2} #{s3},#{s2}"

    svg


  __style = 'none'
  __message = null
  __pointer = null
  __offsetX = __offsetY = __textX = __textY = 0
  setCallout = (message, offsetX, offsetY, textX, textY, pointer) ->
    if __message isnt message
      _$text.html __message = message
    
    if __textX isnt textX
      __textX = textX
      _textEl.style.left = "#{textX}px"

    if __textY isnt textY
      __textY = textY
      _textEl.style.top = "#{textY}px"

    if __offsetX isnt offsetX
      __offsetX = offsetX
      _calloutEl.style.left = "#{offsetX}px"

    if __offsetY isnt offsetY
      __offsetY = offsetY
      _calloutEl.style.top = "#{offsetY}px"

    if __pointer isnt pointer
      __pointer = pointer
      switchPointer pointer

    return

  hideCallout = ->
    if __style isnt 'none'
      _calloutEl.style.display = __style = 'none'

  tabulate = (obj) ->
    rows = []
    for k, v of obj
      rows.push tr [ (th k), (td v) ]
    geyser.render table tbody rows

  displayCallout = (x, y, message) ->
    if __style isnt 'block'
      _calloutEl.style.display = __style = 'block'

    containerRect = _containerEl.getBoundingClientRect()
    containerWidth = containerRect.right - containerRect.left
    containerHeight = containerRect.bottom - containerRect.top

    calloutTextRect = _textEl.getBoundingClientRect()
    calloutTextWidth = calloutTextRect.right - calloutTextRect.left
    calloutTextHeight = calloutTextRect.bottom - calloutTextRect.top

    bleedsRight = x + s3 + calloutTextWidth > containerRect.right
    bleedsTop = y - s2 - calloutTextHeight / 2 < containerRect.top

    ew = if bleedsRight then 'w' else 'e'
    ns = if bleedsTop then 's' else 'n'

    offset = 3
    offsetX = if ew is 'w' then -offset else offset
    offsetY = if ns is 'n' then -offset else offset
    textX = if ew is 'w' then -(calloutTextWidth + s3) else s3
    textY = (2 * 20 + calloutTextHeight / 2) * if ns is 'n' then -1 else 1

    switch ns + ew
      when 'ne'
        pointer = _pointerNE
      when 'nw'
        pointer = _pointerNW
      when 'se'
        pointer = _pointerSE
      when 'sw'
        pointer = _pointerSW

    setCallout message, x + offsetX, y + offsetY, textX, textY, pointer
    return

  callout = (message, x, y) ->
    if message
      displayCallout x, y, if isObject message then tabulate message else message
    else
      hideCallout()

  link$ _.callout, callout #throttle callout, 200

  link$ _.ready, initialize

  
