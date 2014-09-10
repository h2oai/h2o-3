Steam.FrameSelectionView = (_) ->
  _activeFrame = do node$
  _hasFrame = lift$ _activeFrame, isTruthy

  createModel = ->
    _.promptCreateModel _activeFrame().key.name, (action) ->
      switch action
        when 'confirm'
          _.switchToJobs()

  deleteFrame = -> #TODO

  link$ _.frameSelectionChanged, (frame) ->
    _activeFrame frame

  createModel: createModel
  hasFrame: _hasFrame
  deleteFrame: deleteFrame
  template: 'frame-selection-view'

