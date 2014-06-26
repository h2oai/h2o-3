Steam.ConfigureStripPlotDialog = (_, parameters, _go) ->

  wrap = (parameter) ->
    id: parameter.id
    caption: parameter.caption
    isSelected: node$ parameter.isSelected()
    source: parameter
  
  #TODO add mapObject to prelude
  _parameters =
    group1: map parameters.group1(), wrap
    group2: map parameters.group2(), wrap
    group3: map parameters.group3(), wrap

  confirm = ->
    _go _parameters

  cancel = ->
    _go null

  parameters: _parameters
  confirm: confirm
  cancel: cancel
  template: 'configure-strip-plot-dialog'

