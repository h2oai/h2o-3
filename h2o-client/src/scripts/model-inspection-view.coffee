Steam.ModelInspectionView = (_, _model) ->
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

  data: _model
  key: _model.key
  timestamp: _model.creation_epoch_time_millis
  summary: collateSummary _model
  parameters: collateParameters _model
  inputColumns: _model.input_column_names
  inputColumnsCount: "(#{_model.input_column_names.length})"
  template: 'model-inspection-view'
  

