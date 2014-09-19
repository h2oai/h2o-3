algorithms = [
  key: 'kmeans'
  title: 'k-means'
  description: 'A method of vector quantization that is popular for cluster analysis. k-means clustering aims to partition n observations into k clusters in which each observation belongs to the cluster with the nearest mean, serving as a prototype of the cluster.'
,
  key: 'deeplearning'
  title: 'Deep Learning'
  description: 'Model high-level abstractions in data by using model architectures composed of multiple non-linear transformations.'
]

createTextboxControl = (parameter) ->
  value = node$ parameter.actual_value

  kind: 'textbox'
  name: parameter.name
  label: parameter.label
  description: parameter.help
  required: parameter.required
  value: value
  defaultValue: parameter.default_value
  help: node$ 'Help goes here.'
  isInvalid: node$ no

createDropdownControl = (parameter) ->
  value = node$ parameter.actual_value

  kind: 'dropdown'
  name: parameter.name
  label: parameter.label
  description: parameter.help
  required: parameter.required
  values: parameter.values
  value: value
  defaultValue: parameter.default_value
  help: node$ 'Help goes here.'
  isInvalid: node$ no

createListControl = (parameter) ->
  value = node$ parameter.actual_value or []
  selection = lift$ value, (values) ->
    caption = "#{describeCount values.length, 'column'} selected"
    caption += ": #{values.join ', '}" if values.length > 0
    "(#{caption})"

  kind: 'list'
  name: parameter.name
  label: parameter.label
  description: parameter.help
  required: parameter.required
  values: parameter.values
  value: value
  selection: selection
  defaultValue: parameter.default_value
  help: node$ 'Help goes here.'
  isInvalid: node$ no

createCheckboxControl = (parameter) ->
  value = node$ parameter.actual_value is 'true' #FIXME

  clientId: do uniqueId
  kind: 'checkbox'
  name: parameter.name
  label: parameter.label
  description: parameter.help
  required: parameter.required
  value: value
  defaultValue: parameter.default_value is 'true'
  help: node$ 'Help goes here.'
  isInvalid: node$ no

createControlFromParameter = (parameter) ->
  switch parameter.type
    when 'enum', 'Frame', 'Vec'
      createDropdownControl parameter
    when 'Vec[]'
      createListControl parameter
    when 'boolean'
      createCheckboxControl parameter
    when 'Key', 'int', 'long', 'float', 'double', 'int[]', 'long[]', 'float[]', 'double[]'
      createTextboxControl parameter
    else
      console.error 'Invalid field', JSON.stringify parameter, null, 2
      null

findParameter = (parameters, name) ->
  find parameters, (parameter) -> parameter.name is name

Steam.ModelBuilderForm = (_, _algorithm, _parameters, _go) ->
  _validationError = node$ null

  _parametersByLevel = groupBy _parameters, (parameter) -> parameter.level
  _controls = map [ 'critical', 'secondary', 'expert' ], (type) ->
    filter (map _parametersByLevel[type], createControlFromParameter), isTruthy
  [ _criticalControls, _secondaryControls, _expertControls ] = _controls
  parameterTemplateOf = (control) -> "#{control.kind}-model-parameter"

  createModel = ->
    _validationError null
    parameters = {}
    for controls in _controls
      for control in controls
        if control.defaultValue isnt value = control.value()
          switch control.kind
            when 'dropdown'
              if value
                parameters[control.name] = value
            when 'list'
              if value.length
                parameters[control.name] = "[#{value.join ','}]"
            else
              parameters[control.name] = value
    
    _.requestModelBuild _algorithm.key, parameters, (error, result) ->
      if error
        _validationError message: error.data.errmsg
      else
        _go 'confirm'

  title: "Configure #{_algorithm.title} Model"
  criticalControls: _criticalControls
  secondaryControls: _secondaryControls
  expertControls: _expertControls
  validationError: _validationError
  parameterTemplateOf: parameterTemplateOf
  createModel: createModel

Steam.CreateModelDialog = (_, _frameKey, _sourceModel, _go) ->
  [ _isAlgorithmSelectionMode, _isModelCreationMode ] = switch$ no, 2
  _title = node$ 'New Model'
  _canChangeAlgorithm = node$ yes
  _algorithms = nodes$ []
  _modelForm = node$ null

  populateFramesAndColumns = (frameKey, algorithm, parameters, go) ->
    # Fetch frame list; pick column names from training frame
    _.requestFrames (error, result) ->
      if error
        #TODO handle properly
      else
        trainingFrameParameter = findParameter parameters, 'training_frame'
        trainingFrameParameter.values = map result.frames, (frame) -> frame.key.name
        if frameKey
          trainingFrameParameter.actual_value = frameKey
        else
          frameKey = trainingFrameParameter.actual_value

        if algorithm.key is 'deeplearning'
          validationFrameParameter = findParameter parameters, 'validation_frame'
          responseColumnParameter = findParameter parameters, 'response_column'
          #TODO HACK hard-coding DL column params for now - rework this when Vec type is supported.
          responseColumnParameter.type = 'Vec'
          ignoredColumnsParameter = findParameter parameters, 'ignored_columns'
          #TODO HACK hard-coding DL column params for now - rework this when Vec type is supported.
          ignoredColumnsParameter.type = 'Vec[]'

          validationFrameParameter.values = copy trainingFrameParameter.values

          if trainingFrame = (find result.frames, (frame) -> frame.key.name is frameKey)
            columnLabels = map trainingFrame.columns, (column) -> column.label
            sort columnLabels
            responseColumnParameter.values = columnLabels
            ignoredColumnsParameter.values = columnLabels
        go()

  # If a source model is specified, we already know the algo, so skip algo selection
  if _sourceModel
    _title 'Clone Model'
    _canChangeAlgorithm no
    _isModelCreationMode yes
    selectAlgorithm = noop
    parameters = _sourceModel.parameters

    #TODO INSANE SUPERHACK
    hasRateAnnealing = find _sourceModel.parameters, (parameter) -> parameter.name is 'rate_annealing'
    algorithm = if hasRateAnnealing
        find algorithms, (algorithm) -> algorithm.key is 'deeplearning'
      else
        find algorithms, (algorithm) -> algorithm.key is 'kmeans'

    populateFramesAndColumns _frameKey, algorithm, parameters, ->
      _modelForm Steam.ModelBuilderForm _, algorithm, parameters, _go

  else
    _isAlgorithmSelectionMode yes
    selectAlgorithm = (algorithm) ->
      _.requestModelBuilders algorithm.key, (error, result) ->
        if error
          #TODO handle properly
        else
          parameters = result.model_builders[algorithm.key].parameters
          populateFramesAndColumns _frameKey, algorithm, parameters, ->
            _modelForm Steam.ModelBuilderForm _, algorithm, parameters, _go
            _isModelCreationMode yes

    _algorithms map algorithms, (algorithm) ->
      self =
        title: algorithm.title
        description: algorithm.description
        data: algorithm
        select: -> selectAlgorithm self.data

  backToAlgorithms = -> _isAlgorithmSelectionMode yes

  createModel = -> _modelForm().createModel()

  cancel = -> _go 'cancel'

  title: _title
  isAlgorithmSelectionMode: _isAlgorithmSelectionMode
  isModelCreationMode: _isModelCreationMode
  algorithms: _algorithms
  modelForm: _modelForm
  cancel: cancel
  canChangeAlgorithm: _canChangeAlgorithm
  backToAlgorithms: backToAlgorithms
  createModel: createModel
  template: 'create-model-dialog'

