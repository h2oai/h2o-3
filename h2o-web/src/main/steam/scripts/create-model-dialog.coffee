algorithms = [
  key: 'kmeans'
  title: 'k-means'
  description: 'A method of vector quantization that is popular for cluster analysis. k-means clustering aims to partition n observations into k clusters in which each observation belongs to the cluster with the nearest mean, serving as a prototype of the cluster.'
,
  key: 'deeplearning'
  title: 'Deep Learning'
  description: 'Model high-level abstractions in data by using model architectures composed of multiple non-linear transformations.'
,
  key: 'glm'
  title: 'GLM'
  description: 'No description available'
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
    when 'enum', 'Frame', 'Column'
      createDropdownControl parameter
    when 'boolean'
      createCheckboxControl parameter
    when 'int', 'long', 'float', 'double', 'int[]', 'long[]', 'float[]', 'double[]'
      createTextboxControl parameter
    when 'Key'
      # noop
    else
      console.error 'Invalid field', JSON.stringify parameter, null, 2
      null

Steam.ModelBuilderForm = (_, _frameKey, _algorithm, _parameters, _go) ->
  _validationError = node$ null

  _parametersByLevel = groupBy _parameters, (parameter) -> parameter.level
  _controls = map [ 'critical', 'secondary', 'expert' ], (type) ->
    filter (map _parametersByLevel[type], createControlFromParameter), isTruthy
  [ _criticalControls, _secondaryControls, _expertControls ] = _controls
  parameterTemplateOf = (control) -> "#{control.kind}-model-parameter"

  createModel = ->
    _validationError null
    parameters = training_frame: _frameKey
    console.debug _controls
    for controls in _controls
      for control in controls
        if control.defaultValue isnt value = control.value()
          unless control.kind is 'dropdown' and value is ''
            parameters[control.name] = value
    
    _.requestModelBuild _algorithm.data.key, parameters, (error, result) ->
      if error
        _validationError message: error.data.errmsg
      else
        console.log result
        _go 'confirm'

  title: "#{_algorithm.title} on #{_frameKey}"
  criticalControls: _criticalControls
  secondaryControls: _secondaryControls
  expertControls: _expertControls
  validationError: _validationError
  parameterTemplateOf: parameterTemplateOf
  createModel: createModel

Steam.CreateModelDialog = (_, _frameKey, _go) ->
  [ _isAlgorithmSelectionMode, _isModelCreationMode ] = switch$ no, 2
  _isAlgorithmSelectionMode yes

  _modelForm = node$ null

  selectAlgorithm = (algorithm) ->
    _.requestModelBuilders algorithm.data.key, (error, result) ->
      if error
        #TODO handle properly
      else

        parameters = result.model_builders[algorithm.data.key].parameters

        if algorithm.data.key is 'deeplearning'

          #TODO HACK remove DL source parameter - training_frame takes care of this
          sourceParameter = find parameters, (parameter) -> parameter.name is 'source'
          remove parameters, sourceParameter if sourceParameter

          #TODO HACK hard-coding DL response param for now - rework this when Vec type is supported.
          responseParameter = find parameters, (parameter) -> parameter.name is 'response' and parameter.type is 'string'
          responseParameter.type = 'Column' if responseParameter

          validationParameter = find parameters, (parameter) -> parameter.name is 'validation'

          # Fetch frame list; pick column names from training frame
          _.requestFrames (error, result) ->
            if error
              #TODO handle properly
            else
              validationParameter.values = map result.frames, (frame) -> frame.key.name
              unshift validationParameter.values, ''
              trainingFrame = find result.frames, (frame) -> frame.key.name is _frameKey
              if trainingFrame
                responseParameter.values = map trainingFrame.columns, (column) -> column.label
                sort responseParameter.values
                unshift responseParameter.values, ''
              _modelForm Steam.ModelBuilderForm _, _frameKey, algorithm, parameters, _go
              _isModelCreationMode yes
        else
          _modelForm Steam.ModelBuilderForm _, _frameKey, algorithm, parameters, _go
          _isModelCreationMode yes


  _algorithms = map algorithms, (algorithm) ->
    self =
      title: algorithm.title
      description: algorithm.description
      data: algorithm
      select: -> selectAlgorithm self

  backToAlgorithms = -> _isAlgorithmSelectionMode yes

  createModel = -> _modelForm().createModel()

  cancel = -> _go 'cancel'

  isAlgorithmSelectionMode: _isAlgorithmSelectionMode
  isModelCreationMode: _isModelCreationMode
  algorithms: _algorithms
  modelForm: _modelForm
  cancel: cancel
  backToAlgorithms: backToAlgorithms
  createModel: createModel
  template: 'create-model-dialog'

