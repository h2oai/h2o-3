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
  label: parameter.label + ':'
  description: parameter.help
  required: parameter.required is 'true' #FIXME
  value: value
  defaultValue: parameter.default_value
  help: node$ 'Help goes here.'
  isInvalid: node$ no

createDropdownControl = (parameter) ->
  value = node$ parameter.actual_value

  kind: 'dropdown'
  name: parameter.name
  label: parameter.label + ':'
  description: parameter.help
  required: parameter.required is 'true' #FIXME
  values: parameter.values
  value: value
  defaultValue: parameter.default_value
  help: node$ 'Help goes here.'
  isInvalid: node$ no

createCheckboxControl = (parameter) ->
  value = node$ parameter.actual_value is 'true' #FIXME

  kind: 'checkbox'
  name: parameter.name
  label: parameter.label
  description: parameter.help
  required: parameter.required is 'true' #FIXME
  value: value
  defaultValue: parameter.default_value is 'true'
  help: node$ 'Help goes here.'
  isInvalid: node$ no

createControlFromParameter = (parameter) ->
  switch parameter.type
    when 'enum'
      createDropdownControl parameter
    when 'boolean'
      createCheckboxControl parameter
    else # dropdown
      createTextboxControl parameter

Steam.ModelBuilderForm = (_, _frameKey, _algorithm, _parameters, _go) ->
  _validationError = node$ null
  _parametersByLevel = groupBy _parameters, (parameter) -> parameter.level
  _controls = map [ 'critical', 'secondary', 'expert' ], (type) ->
    map _parametersByLevel[type], createControlFromParameter
  [ _criticalControls, _secondaryControls, _expertControls ] = _controls
  parameterTemplateOf = (control) -> "#{control.kind}-model-parameter"

  createModel = ->
    _validationError null
    parameters = training_frame: _frameKey
    for controls in _controls
      for control in controls
        parameters[control.name] = control.value()
    
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
        _modelForm Steam.ModelBuilderForm _, _frameKey, algorithm, result.model_builders[algorithm.data.key].parameters, _go
          #TODO proceed to next step
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

