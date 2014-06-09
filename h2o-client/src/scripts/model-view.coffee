Steam.ModelView = (_, _model) ->
  stringify = (value) ->
    if isArray value
      join value, ', '
    else
      value

  kv = (key, value) -> key: key, value: stringify value

  collateSummary = (model) ->
    [
      kv 'Response Column', model.response_column_name
      kv 'Model Category', model.model_category
      #TODO uncomment when this is functional
      # kv 'State', model.state
    ]

  collateParameters = (model) ->
    parameters = [
      pairs model.critical_parameters
      pairs model.secondary_parameters
      pairs model.expert_parameters
    ]
    map (flatten parameters, yes), ([key, value]) -> kv key, value
  
  collateCompatibleFrames = (frames) ->
    map frames, (frame) ->
      frameKey: frame.key
      columns: join frame.column_names, ', '
      inspect: -> _.inspect Steam.FrameInspectionView _, frame

  # PP-74 hide raw frames from list
  nonRawFrames = filter _model.compatible_frames, (frame) -> not frame.is_raw_frame
  compatibleFrames = collateCompatibleFrames nonRawFrames
  compatibleFramesCount = "(#{nonRawFrames.length})"

  loadCompatibleFrames = ->
    _.switchToFrames type: 'compatibleWithModel', modelKey: _model.key
  
  data: _model
  key: _model.key
  timestamp: _model.creation_epoch_time_millis
  summary: collateSummary _model
  parameters: collateParameters _model
  inputColumns: _model.input_column_names
  inputColumnsCount: "(#{_model.input_column_names.length})"
  compatibleFrames: compatibleFrames
  compatibleFramesCount: compatibleFramesCount
  loadCompatibleFrames: loadCompatibleFrames
  template: 'model-view'

