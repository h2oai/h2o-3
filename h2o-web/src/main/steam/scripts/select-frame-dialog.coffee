Steam.SelectFrameDialog = (_, _frames, _go) ->
  _items = do nodes$
  _selectedItem = node$ null
  _hasSelection = lift$ _selectedItem, isTruthy

  initialize = (frames) ->
    _items items = map frames, createItem
    selectItem head items

  selectItem = (target) ->
    for item in _items()
      item.isActive item is target
    _selectedItem target

  createItem = (frame) ->
    self =
      data: frame
      key: frame.key
      count: describeCount frame.column_names.length, 'column'
      timestamp: frame.creation_epoch_time_millis
      select: -> selectItem self
      isActive: node$ no

  confirm = -> _go 'confirm', _selectedItem().data.key
  cancel = -> _go 'cancel'

  initialize _frames
  
  items: _items
  hasSelection: _hasSelection
  confirm: confirm
  cancel: cancel
  template: 'select-frame-dialog'
