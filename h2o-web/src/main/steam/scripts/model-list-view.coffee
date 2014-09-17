Steam.ModelListView = (_) ->
  _predicate = node$ type: 'all'
  _items = do nodes$
  _hasItems = lift$ _items, (items) -> items.length > 0
  _isSelectAll = node$ no

  #TODO ugly
  _isLive = node$ yes

  apply$ _isSelectAll, (isSelected) ->
    _isLive no
    for item in _items()
      item.isSelected isSelected
    _isLive yes
    return

  _canClearPredicate = lift$ _predicate, (predicate) -> predicate.type isnt 'all'
  _predicateCaption = lift$ _predicate, (predicate) ->
    switch predicate.type
      when 'all'
        'Showing\nall models'
      when 'one'
        'Showing\none model'
      when 'compatibleWithFrame'
        "Showing models compatible with\n#{predicate.frameKey}"
      else
        ''

  displayItem = (item) ->
    if item
      _.displayModel item.data
      _.activeModelChanged item.data
    else
      _.displayEmpty()
      _.activeModelChanged null

  displayActiveItem = ->
    displayItem find _items(), (item) -> item.isActive()

  activateAndDisplayItem = (item) ->
    for other in _items()
      if other is item
        other.isActive yes
      else
        other.isActive no

    displayItem item

  createItem = (model) ->
    self =
      data: model
      title: model.key
      #TODO 
      caption: "Unknown response column name / category" #"#{model.response_column_name} (#{model.model_category})"
      #TODO
      timestamp: Date.now() #model.creation_epoch_time_millis
      display: -> activateAndDisplayItem self
      isActive: node$ no
      isSelected: node$ no

    apply$ _isLive, self.isSelected, (isLive, isSelected) ->
      _.modelSelectionChanged isSelected, self if isLive

    self

  displayModels = (models) ->
    _items items = map models, createItem
    activateAndDisplayItem head items

  loadModels = (predicate) ->
    _.modelSelectionCleared()

    switch predicate.type
      when 'all'
        _.requestModels (error, models) -> #TODO request compatible frames as well
          if error
            #TODO handle errors
          else
            displayModels models

      when 'one'
        _.requestModel predicate.key, (error, model) ->
          if error
            #TODO handle errors
          else
            displayModels [ model ]

      when 'compatibleWithFrame'
        #FIXME Need an api call to get "models and compatible frames for all models compatible with a frame"
        _.requestFrameAndCompatibleModels predicate.frameKey, (error, data) ->
          if error
            #TODO handle errors
          else
            compatibleModelsByKey = indexBy (head data.frames).compatible_models, (model) -> model.key
            _.requestModelsAndCompatibleFrames (error, data) ->
              if error
                #TODO handle errors
              else
                displayModels filter data.models, (model) -> if compatibleModelsByKey[model.key] then yes else no
    _predicate predicate
    return
  
  deselectAllModels = ->
    #TODO ugly
    _isLive no
    for item in _items()
      item.isSelected no
    _isLive yes

  clearPredicate = ->
    deselectAllModels()
    _predicate type: 'all'

  link$ _.loadModels, (predicate) ->
    if predicate
      loadModels predicate
    else
      loadModels type: 'all'
      displayActiveItem()

  link$ _.deselectAllModels, deselectAllModels

  createModel = ->
    _.promptCreateModel null, null, (action) ->
      switch action
        when 'confirm'
          console.log 'TODO CONFIRMED'

  items: _items
  hasItems: _hasItems
  predicateCaption: _predicateCaption
  clearPredicate: clearPredicate
  canClearPredicate: _canClearPredicate
  isSelectAll: _isSelectAll
  createModel: createModel
  template: 'model-list-view'

