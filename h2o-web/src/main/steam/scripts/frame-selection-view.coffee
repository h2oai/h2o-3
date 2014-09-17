Steam.FrameSelectionView = (_) ->
  _activeFrame = do node$
  _hasActiveFrame = lift$ _activeFrame, isTruthy

  createModel = ->
    _.promptCreateModel _activeFrame().key.name, null, (action) ->
      switch action
        when 'confirm'
          _.switchToJobs()

  deleteFrame = -> #TODO

  link$ _.activeFrameChanged, (frame) ->
    _activeFrame frame

  createModel: createModel
  hasActiveFrame: _hasActiveFrame
  deleteFrame: deleteFrame
  template: 'frame-selection-view'

