Steam.FrameListView = (_) ->
  _predicate = node$ type: 'all'
  _items = do nodes$
  _hasItems = lift$ _items, (items) -> items.length > 0

  _canClearPredicate = lift$ _predicate, (predicate) -> predicate.type isnt 'all'
  _predicateCaption = lift$ _predicate, (predicate) ->
    switch predicate.type
      when 'all'
        'Showing\nall datasets'
      when 'one'
        'Showing\none dataset'
      when 'compatibleWithModel'
        "Showing datasets compatible with\n#{predicate.modelKey}"
      else
        throw new Error 'Invalid predicate type'

  displayItem = (item) ->
    if item
      _.displayFrame item.data
      _.activeFrameChanged item.data
    else
      _.displayEmpty()
      _.activeFrameChanged null

  displayActiveItem = ->
    displayItem find _items(), (item) -> item.isActive()

  activateAndDisplayItem = (item) ->
    for other in _items()
      if other is item
        other.isActive yes
      else
        other.isActive no

    displayItem item

  createItem = (frame) ->
    #TODO replace with type checking
    console.assert isArray frame.columns
    self =
      data: frame
      title: frame.key.name
      caption: (describeCount frame.columns.length, 'column') + ': ' + join (map (head frame.columns, 5), (column) -> column.label), ', '
      timestamp: frame.creation_epoch_time_millis
      display: -> activateAndDisplayItem self
      isActive: node$ no
  
  displayFrames = (frames) ->
    _items items = map frames, createItem
    activateAndDisplayItem head items
    _.framesLoaded()

  loadFrames = (predicate) ->
    switch predicate.type
      when 'all'
        _.requestFrames (error, result) ->
          if error
            _.error 'Error requesting all frames', null, error
          else
            displayFrames result.frames

      when 'one'
        _.requestFrame predicate.key, (error, result) ->
          if error
            _.error 'Error requesting frame', predicate.key, error
          else
            displayFrames result.frames

    _predicate predicate
    return

  importFile = ->
    _.promptImportFiles (action, job) ->
      switch action
        when 'confirm'
          _.switchToJobs()
      return

  clearPredicate = -> loadFrames type: 'all'

  link$ _.loadFrames, (predicate) ->
    if predicate
      loadFrames predicate
    else
      loadFrames type: 'all'

  link$ _.refreshFrames, -> loadFrames _predicate()

  items: _items
  predicateCaption: _predicateCaption
  clearPredicate: clearPredicate
  canClearPredicate: _canClearPredicate
  importFile: importFile
  hasItems: _hasItems
  template: 'frame-list-view'

