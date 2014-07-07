Steam.TooltipManager = (_) ->
  _offset = 5 # Keep this in sync with the size of the arrows in tooltip.styl
  _containerEl = null
  _tooltipEl = null
  _tooltipArrowUpEl = null
  _tooltipArrowRightEl = null
  _tooltipArrowDownEl = null
  _tooltipArrowLeftEl = null
  _tooltipArrows = null
  _tooltipTextEl = null
  _$tooltipText = null

  [ table, tbody, tr, th, td ] = geyser.generate words 'table.y-tooltip-table tbody tr th td'

  createEl = (className, tag='div') ->
    el = document.createElement tag
    el.className = className if className
    el

  initialize = ->
    _containerEl = $('.y-main').get 0
    $overlays = $ '.y-overlays'

    _tooltipEl = createEl 'y-tooltip'
    _tooltipArrowUpEl = createEl 'y-tooltip-arrow-up'
    _tooltipArrowRightEl = createEl 'y-tooltip-arrow-right'
    _tooltipArrowDownEl = createEl 'y-tooltip-arrow-down'
    _tooltipArrowLeftEl = createEl 'y-tooltip-arrow-left'
    _tooltipArrows = [
      _tooltipArrowUpEl
      _tooltipArrowRightEl
      _tooltipArrowDownEl
      _tooltipArrowLeftEl
    ]

    _tooltipTextEl = createEl 'y-tooltip-text'
    _$tooltipText = $ _tooltipTextEl

    _tooltipEl.appendChild _tooltipArrowUpEl
    _tooltipEl.appendChild _tooltipArrowRightEl
    _tooltipEl.appendChild _tooltipArrowDownEl
    _tooltipEl.appendChild _tooltipArrowLeftEl
    _tooltipEl.appendChild _tooltipTextEl
    
    $overlays.append _tooltipEl

  tabulate = (obj) ->
    rows = []
    for key, value of obj
      rows.push tr [ (th key), (td value) ]
    geyser.render table tbody rows

  wrap = (text) ->
    geyser.render table tbody tr td text

  switchArrow = (arrowEl) ->
    for el in _tooltipArrows
      el.style.display = if el is arrowEl then 'block' else 'none'
    return

  applyStyle = (style, attr, value) ->
    if isNull value
      style.removeProperty attr
    else
      style[attr] = value + 'px'

  moveTooltip = (x, y, width, height, left, top, bottom, right) ->
    _tooltipEl.style.left = x + 'px'
    _tooltipEl.style.top = y + 'px'

    style = _tooltipTextEl.style
    style.width = width + 'px'
    style.height = height + 'px'

    applyStyle style, 'left', left
    applyStyle style, 'top', top
    applyStyle style, 'right', right
    applyStyle style, 'bottom', bottom

  tooltip = (el, tip, anchor) ->
    if el
      _tooltipEl.style.display = 'block'
    else
      _tooltipEl.style.display = 'none'
      return

    content = if isObject tip then tabulate tip else wrap tip
    _$tooltipText.html content
    _contentEl = _$tooltipText.children().first().get 0
    contentBounds = _contentEl.getBoundingClientRect()
    return unless contentBounds
    
    width = contentBounds.right - contentBounds.left
    height = contentBounds.bottom - contentBounds.top

    bounds = el.getBoundingClientRect()
    return unless bounds

    { left, top, right, bottom } = bounds
    x = left + (right - left)/2
    y = top + (bottom - top)/2

    # Expensive: if anchor is not specified, auto-position.
    unless anchor
      parentBounds = _containerEl.getBoundingClientRect()
      return unless bounds

      topFits = top - _offset - height > parentBounds.top
      rightFits = right + _offset + width < parentBounds.right
      if topFits
        if rightFits
          anchor = 'top'
        else
          anchor = 'left'
      else
        if rightFits
          anchor = 'right'
        else
          anchor = 'left'

    switch anchor
      when 'top'
        moveTooltip x, top, width, height, -width/2, null, _offset, null
        switchArrow _tooltipArrowDownEl
      when 'right'
        moveTooltip right, y, width, height, _offset, -height/2, null, null
        switchArrow _tooltipArrowLeftEl
      when 'left'
        moveTooltip left, y, width, height, null, -height/2, null, _offset
        switchArrow _tooltipArrowRightEl
      when 'bottom'
        moveTooltip x, bottom, width, height, -width/2, _offset, null, null
        switchArrow _tooltipArrowUpEl

    _$tooltipText.html content

  link$ _.tooltip, tooltip
  link$ _.ready, initialize
