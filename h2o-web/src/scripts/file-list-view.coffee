Steam.FileListView = (_) ->
  _predicate = node$ type: 'all'
  _items = do nodes$
  _hasItems = lift$ _items, (items) -> items.length > 0

  _canClearPredicate = lift$ _predicate, (predicate) -> predicate.type isnt 'all'
  _predicateCaption = lift$ _predicate, (predicate) ->
    switch predicate.type
      when 'all'
        'Showing\nall datasets'
      else
        throw new Error 'Invalid predicate type'

  displayItem = (item) ->
    if item
      _.displayFile item
    else
      _.displayEmpty()

  displayActiveItem = ->
    displayItem find _items(), (item) -> item.isActive()

  activateAndDisplayItem = (item) ->
    for other in _items()
      if other is item
        other.isActive yes
      else
        other.isActive no

    displayItem item

  createItem = (result) ->
    self =
      data: result
      title: result.path
      caption: head result.keys
      timestamp: Date.now()
      display: -> activateAndDisplayItem self
      isActive: node$ no
  
  displayFiles = (files) ->
    _items items = map files, createItem
    activateAndDisplayItem head items
    _.filesLoaded()

  displayFile = (result) ->
    _items.push item = createItem result
    activateAndDisplayItem item
    _.filesLoaded()

  loadFiles = (predicate) ->
    console.assert isDefined predicate
    switch predicate.type
      when 'all'
        ###
        _.requestFilesAndCompatibleModels (error, data) ->
          if error
            #TODO handle errors
          else
            displayFiles data.files
        ###

    _predicate predicate
    return

  clearPredicate = -> loadFiles type: 'all'

  link$ _.loadFiles, (predicate) ->
    if predicate
      loadFiles predicate
    else
      displayActiveItem()


  items: _items
  predicateCaption: _predicateCaption
  clearPredicate: clearPredicate
  importFile: importFile
  canClearPredicate: _canClearPredicate
  hasItems: _hasItems
  template: 'file-list-view'


