defaultScoringSelectionMessage = 'Score selected models.'
Steam.ModelSelectionView = (_) ->
  _selections = nodes$ []
  _hasSelection = lift$ _selections, (selections) -> selections.length > 0
  _caption = lift$ _selections, (selections) ->
    "#{describeCount selections.length, 'model'} selected."

  _compatibleFrames = lift$ _selections, (selections) ->
    framesPerModel = map selections, (selection) -> selection.data.compatible_frames
    framesByKey = indexBy (flatten framesPerModel), (frame) -> frame.key
    commonFrameKeys = sortBy intersection.apply null, map framesPerModel, (frames) -> map frames, (frame) -> frame.key
    map commonFrameKeys, (key) -> framesByKey[key]

  _canScoreSelections = lift$ _compatibleFrames, (frames) -> frames.length > 0

  _modelSelectionMessage = lift$ _compatibleFrames, (frames) ->
    if frames.length
      defaultScoringSelectionMessage
    else
      'No compatible datasets found.'

  scoreSelections = ->
    _.promptForFrame _compatibleFrames(), (action, frameKey) ->
      switch action
        when 'confirm'
          scorings = map _selections(), (selection) ->
            frameKey: frameKey
            model: selection.data
            status: null
            time: null
            result: null
            timestamp: Date.now()

          _.switchToScoring type: 'scoring', scorings: scorings
          _.deselectAllModels()

  tryScoreSelections = (hover) ->
    _.status if hover then _modelSelectionMessage() else null

  clearSelections = ->
    _.deselectAllModels()

  link$ _.modelSelectionChanged, (isSelected, model) ->
    if isSelected
      _selections.push model
    else
      _selections.remove model

  link$ _.modelSelectionCleared, ->
    _selections.removeAll()

  caption: _caption
  hasSelection: _hasSelection
  clearSelections: clearSelections
  canScoreSelections: _canScoreSelections
  tryScoreSelections: tryScoreSelections
  scoreSelections: scoreSelections
  template: 'model-selection-view'
  
