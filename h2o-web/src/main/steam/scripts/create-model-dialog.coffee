algorithms = [
  key: 'kmeans'
  title: 'k-means'
  description: 'A method of vector quantization that is popular for cluster analysis. k-means clustering aims to partition n observations into k clusters in which each observation belongs to the cluster with the nearest mean, serving as a prototype of the cluster.'
,
  key: 'deeplearning'
  title: 'Deep Learning'
  description: 'Model high-level abstractions in data by using model architectures composed of multiple non-linear transformations.'
]

Steam.ModelBuilderForm = (_, _algorithm, _parameters, _go) ->
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
  
  parametersByLevel = groupBy _parameters, (parameter) -> parameter.level
  _criticalParameters = map parametersByLevel.critical, createControlFromParameter
  _secondaryParameters = map parametersByLevel.secondary, createControlFromParameter
  _expertParameters = map parametersByLevel.expert, createControlFromParameter
  parameterTemplateOf = (control) -> "#{control.kind}-model-parameter"

  createModel = ->

  title: _algorithm.title
  criticalParameters: _criticalParameters
  secondaryParameters: _secondaryParameters
  expertParameters: _expertParameters
  parameterTemplateOf: parameterTemplateOf
  createModel: createModel

Steam.CreateModelDialog = (_, _go) ->
  _isModelCreationMode = node$ no
  _isAlgorithmSelectionMode = lift$ _isModelCreationMode, negate
  _modelForm = node$ null

  selectAlgorithm = (algorithm) ->
    _.requestModelBuilders algorithm.data.key, (error, result) ->
      if error
        #TODO handle properly
      else
        _modelForm Steam.ModelBuilderForm _, algorithm, result.model_builders[algorithm.data.key].parameters, ->
          #TODO proceed to next step
        _isModelCreationMode yes

  _algorithms = map algorithms, (algorithm) ->
    self =
      title: algorithm.title
      description: algorithm.description
      data: algorithm
      select: -> selectAlgorithm self

  backToAlgorithms = -> _isModelCreationMode no

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

