
modelParameterLevelSortCriteria =
  critical: 1
  secondary: 2
  expert: 3

Steam.ModelView = (_, _model) ->
  stringify = (value) ->
    if isArray value
      join value, ', '
    else
      value

  kv = (key, value) -> key: key, value: stringify value

  determineModelAlgorithm = (model) ->
    hasRateAnnealing = find model.parameters, (parameter) -> parameter.name is 'rate_annealing'
    if hasRateAnnealing
      'Deep Learning'
    else
      'k-means'

  collateSummary = (model) ->
    [
      kv 'Algorithm', determineModelAlgorithm model
      #kv 'Response Column', 'Unknown' #model.response_column_name
      #kv 'Model Category', 'Unknown' #model.model_category
      #TODO uncomment when this is functional
      # kv 'State', model.state
    ]

  collateParameters = (model) ->
    parameters = sortBy model.parameters, (parameter) ->
      modelParameterLevelSortCriteria[parameter.level]
    map parameters, (parameter) -> kv parameter.label, parameter.actual_value or '-'
  
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
  #TODO
  timestamp: Date.now() #_model.creation_epoch_time_millis
  summary: collateSummary _model
  parameters: collateParameters _model
  inputColumns: _model.output.names
  inputColumnsCount: "(#{_model.output.names.length})"
  #TODO
  #compatibleFrames: compatibleFrames
  #compatibleFramesCount: compatibleFramesCount
  #loadCompatibleFrames: loadCompatibleFrames
  template: 'model-view'

