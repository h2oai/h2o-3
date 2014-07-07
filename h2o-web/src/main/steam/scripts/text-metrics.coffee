Steam.TextMetrics = (_) ->
  _$box = null

  initialize = ->
    containerEl = document.createElement 'div'
    containerEl.className = 'y-cloaked'
    boxEl = document.createElement 'span'
    containerEl.appendChild boxEl
    $('.y-overlays').append containerEl
    _$box = $ boxEl

  measureTextSpan = (text) ->
    _$box.text text
    _$box.width() #TODO is getBoundingClientRect() quicker?

  link$ _.measureTextSpan, measureTextSpan
  link$ _.ready, initialize

