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
    when 'enum', 'Frame', 'Column'
      createDropdownControl parameter
    when 'Column[]'
      createListControl parameter
    when 'boolean'
      createCheckboxControl parameter
    when 'int', 'long', 'float', 'double', 'int[]', 'long[]', 'float[]', 'double[]'
      createTextboxControl parameter
    when 'Key'
      # noop
    else
      console.error 'Invalid field', JSON.stringify parameter, null, 2
      null

findParameter = (parameters, name) ->
  find parameters, (parameter) -> parameter.name is name

Steam.ModelBuilderForm = (_, _frameKey, _algorithm, _parameters, _go) ->
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
        # Fetch frame list; pick column names from training frame
        _.requestFrames (error, result) ->
          if error
            #TODO handle properly
          else
            trainingFrameParameter = findParameter parameters, 'training_frame'
            trainingFrameParameter.values = map result.frames, (frame) -> frame.key.name
            if _frameKey
              trainingFrameParameter.actual_value = _frameKey


            if algorithm.data.key is 'deeplearning'
              validationFrameParameter = findParameter parameters, 'validation_frame'
              responseParameter = findParameter parameters, 'response_column'
              ignoredColumnsParameter = findParameter parameters, 'ignored_columns'

              validationFrameParameter.values = copy trainingFrameParameter.values

              if trainingFrame = (find result.frames, (frame) -> frame.key.name is _frameKey)
                columnLabels = map trainingFrame.columns, (column) -> column.label
                sort columnLabels

                #TODO HACK hard-coding DL column params for now - rework this when Vec type is supported.

                responseParameter.type = 'Column'
                responseParameter.values = columnLabels

                ignoredColumnsParameter.type = 'Column[]'
                ignoredColumnsParameter.values = columnLabels

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

