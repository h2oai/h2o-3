unless exports?
  format4f = d3.format '.4f' # precision = 4

aucCriteria = [
  key: 'maximum F1'
  caption: 'Max F1'
,
  key: 'maximum F2'
  caption: 'Max F2'
,
  key: 'maximum F0point5'
  caption: 'Max F0.5'
,
  key: 'maximum Accuracy'
  caption: 'Max Accuracy'
,
  key: 'maximum Precision'
  caption: 'Max Precision'
,
  key: 'maximum Recall'
  caption: 'Max Recall'
,
  key: 'maximum Specificity'
  caption: 'Max Specificity'
,
  key: 'maximum absolute MCC'
  caption: 'Max Absolute MCC'
,
  key: 'minimizing max per class Error'
  caption: 'Min MPCE'
]


aucOutputs = [
  key: 'threshold_for_criteria'
  caption: 'Threshold'
  domain: [0, 1]
,
  key: 'error_for_criteria'
  caption: 'Error'
  domain: [0, 1]
,
  key: 'F0point5_for_criteria'
  caption: 'F0.5'
  domain: [0, 1]
,
  key: 'F1_for_criteria'
  caption: 'F1'
  domain: [0, 1]
,
  key: 'F2_for_criteria'
  caption: 'F2'
  domain: [0, 1]
,
  key: 'accuracy_for_criteria'
  caption: 'Accuracy'
  domain: [0, 1]
,
  key: 'precision_for_criteria'
  caption: 'Precision'
  domain: [0, 1]
,
  key: 'recall_for_criteria'
  caption: 'Recall'
  domain: [0, 1]
,
  key: 'specificity_for_criteria'
  caption: 'Specificity'
  domain: [0, 1]
,
  key: 'mcc_for_criteria'
  caption: 'MCC'
  domain: [-1, 1]
,
  key: 'max_per_class_error_for_criteria'
  caption: 'MPCE'
  domain: [0, 1]
]

aucCategories = do ->
  categories = [
    index: 0
    key: 'AUC'
    caption: 'AUC'
    domain: [0, 1]
    isGrouped: no
  ,
    index: 0
    key: 'Gini'
    caption: 'Gini'
    domain: [0, 1]
    isGrouped: no
  ]
  for criterion, criterionIndex in aucCriteria
    for output in aucOutputs
      categories.push
        index: 0
        key: "#{criterion.caption}\0#{output.caption}"
        caption: "#{criterion.caption} - #{output.caption}"
        domain: output.domain
        isGrouped: yes
        criterion: criterion
        output: output
        criterionIndex: criterionIndex

  map categories, (category, index) ->
    category.index = index
    category

aucVariables = [
  [ 'Threshold', [0, 1] ]
  [ 'Error', [0, 1] ]
  [ 'F0.5', [0, 1] ]
  [ 'F1', [0, 1] ]
  [ 'F2', [0, 1] ]
  [ 'Accuracy', [0, 1] ]
  [ 'Precision', [0, 1] ]
  [ 'Recall', [0, 1] ]
  [ 'Specificity', [0, 1] ]
  [ 'MCC', [-1, 1] ]
  [ 'MPCE', [0, 1] ]
  [ 'TPR', [0, 1] ]
  [ 'FPR', [0, 1] ]
].map (attr) ->
  [ key, domain ] = attr
  key: key, caption: key, domain: domain

aucCriteriaMap = indexBy aucCriteria, (criterion) -> criterion.key
aucOutputMap = indexBy aucOutputs, (output) -> output.key
aucCategoryMap = indexBy aucCategories, (cateogory) -> cateogory.key
aucVariableMap = indexBy aucVariables, (variable) -> variable.key

createThresholdPlotInspection = (series, mark) ->
  [ div, h1, h2, table, grid, tbody, tr, th, td ] = geyser.generate words 'div h1 h2 table.table.table-condensed table.table.table-bordered tbody tr th td'

  formatConfusionMatrix = (domain, cm) ->
    [ d1, d2 ] = domain
    [[tn, fp], [fn, tp]] = cm
    grid [
      tr [
        th ''
        th d1
        th d2
      ]
      tr [
        th d1
        td tn
        td fp
      ]
      tr [
        th d2
        td fn
        td tp
      ]
    ]

  tabulateProperties = (mark) ->
    table tbody map aucVariables, (variable) ->
      [ term, value ] = property
      tr [
        th variable.caption
        td format4f mark[variable.key]
      ]

  div [
    h1 series.caption
    h2 'Outputs'
    tabulateProperties mark
    h2 'Confusion Matrix'
    formatConfusionMatrix series.metrics.auc.actual_domain, mark['Confusion Matrix']
  ]

createRocMarkInspection = (metrics, mark) ->
  [ div, h1, h2, table, grid, tbody, tr, th, td ] = geyser.generate words 'div h1 h2 table.table.table-condensed table.table.table-bordered tbody tr th td'

  formatConfusionMatrix = (domain, cm) ->
    [ d1, d2 ] = domain
    [[tn, fp], [fn, tp]] = cm
    grid [
      tr [
        th ''
        th d1
        th d2
      ]
      tr [
        th d1
        td tn
        td fp
      ]
      tr [
        th d2
        td fn
        td tp
      ]
    ]

  collectProperties = (auc, index) ->
    [
      [ 'Threshold', format4f auc.thresholds[index] ]
      [ 'Error', format4f auc.errorr[index] ]
      [ 'F0.5', format4f auc.F0point5[index] ]
      [ 'F1', format4f auc.F1[index] ]
      [ 'F2', format4f auc.F2[index] ]
      [ 'Accuracy', format4f auc.accuracy[index] ]
      [ 'Precision', format4f auc.precision[index] ]
      [ 'Recall', format4f auc.recall[index] ]
      [ 'Specificity', format4f auc.specificity[index] ]
      [ 'MCC', format4f auc.mcc[index] ]
      [ 'MPCE', format4f auc.max_per_class_error[index] ]
      [ 'False Positive Rate', format4f mark.fpr ]
      [ 'True Positive Rate', format4f mark.tpr ]
    ]

  tabulateProperties = (auc, index) ->
    properties = collectProperties auc, index

    table tbody map properties, (property) ->
      [ term, value ] = property
      tr [
        th term
        td value
      ]

  auc = metrics.metrics.auc
  div [
    h1 metrics.caption
    h2 'Outputs'
    tabulateProperties auc, mark.index
    h2 'Confusion Matrix'
    formatConfusionMatrix auc.actual_domain, auc.confusion_matrices[mark.index]
  ]

createStripPlotRowInspection = (series, category) ->
  [ div, h1, table, tbody, tr, th, td ] = geyser.generate words 'div h1 table.table.table-condensed tbody tr th td'

  rows = map series, (series) ->
    caption: series.caption
    value: series.outputs[category.key]

  sortedRows = sortBy rows, (row) -> -row.value

  div [
    h1 category.caption
    table tbody map sortedRows, (row) ->
      tr [
        th row.caption
        td if isNaN row.value then 'NaN' else format4f row.value
      ]
  ]

createStripPlotValueInspection = (series) ->
  [ div, h1, h2, table, tbody, tr, th, td ] = geyser.generate words 'div h1 h2 table.table.table-condensed tbody tr th td'

  div [
    h1 series.caption
    table tbody map aucCategories, (category) ->
      value = series.outputs[category.key]
      tr [
        th category.caption
        td if isNaN value then 'NaN' else format4f value
      ]
  ]

createScoringInspection = (series) ->
  [ div, h1, h2, table, tbody, tr, th, td ] = geyser.generate words 'div h1 h2 table.table.table-condensed tbody tr th td'
  createStripPlotMarkInspectionTable = (series, categories) ->
    table tbody map categories, (category) ->
      value = series.outputs[category.key]
      tr [
        th if category.isGrouped then category.criterion.caption else category.caption
        td if isNaN value then 'NaN' else format4f value
      ]
  ungroupedCategories = filter aucCategories, (category) -> not category.isGrouped
  groupedCategories = filter aucCategories, (category) -> category.isGrouped
  groupedCategoriesByOutput = groupBy groupedCategories, (category) -> category.output.caption

  div [
    h1 series.caption
    h2 'Outputs'
    createStripPlotMarkInspectionTable series, ungroupedCategories
    div mapWithKey groupedCategoriesByOutput, (categories, caption) ->
      div [
        h2 caption
        createStripPlotMarkInspectionTable series, categories
      ]
  ]


#TODO check for memory leaks
Steam.ScoringView = (_, _scoring) ->
  _tag = node$ ''
  _caption = node$ ''
  _timestamp = node$ Date.now()
  _comparisonTable = node$ null
  _scoringList = node$ null
  _categories = node$ null
  _selectedCategory = node$ aucCategoryMap.AUC
  _comparisonPlot = node$ null
  _inputOutputCategories = node$ null
  _inputOutputPlotX = node$ null
  _inputOutputPlotY = node$ null
  _inputOutputPlot = node$ null
  _multiRocPlot = node$ null
  _thresholdPlotVariables = sortBy aucVariables, (variable) -> variable.caption
  _thresholdPlotX = node$ aucVariableMap.Threshold
  _thresholdPlotY = node$ aucVariableMap.Error
  _thresholdPlot = node$ null
  _stripPlot = node$ null
  _stripPlotParameters =
    group1: node$ null
    group2: node$ null
    group3: node$ null
  _modelSummary = nodes$ []
  _hasFailed = node$ no
  _failure = node$ null
  _scoringType = node$ null
  _isScoringView = lift$ _scoringType, (type) -> type is 'scoring'
  _isComparisonView = lift$ _scoringType, (type) -> type is 'comparison'

  #TODO make this a property of the comparison object
  _comparisonMode = node$ 'tabular'
  _isTabularComparisonView = lift$ _comparisonMode, (mode) -> mode is 'tabular'
  _isAdvancedComparisonView = lift$ _comparisonMode, (mode) -> mode is 'advanced'
  _isSheetView = lift$ _comparisonMode, (mode) -> mode is 'sheet'
  switchToTabularView = -> _comparisonMode 'tabular'
  switchToAdvancedView = -> _comparisonMode 'advanced'
  switchToSheetView = -> _comparisonMode 'sheet'
  _sheetView = node$ null

  createModelSummary = (model) ->
    [
      key: 'Model Category'
      value: model.model_category
    ,
      key: 'Response Column'
      value: model.response_column_name
    ]

  initialize = (item) ->
    switch item.type
      when 'scoring'
        scoring = item
        input = scoring.data.input
        _tag 'Scoring'
        _caption "Scoring on #{input.frameKey}"
        _modelSummary createModelSummary input.model
        apply$ scoring.isReady, scoring.hasFailed, (isReady, hasFailed) ->
          if isReady
            if hasFailed
              _hasFailed yes
              _failure scoring.data.output
            else
              _timestamp (head scoring.data.output.metrics).scoring_time
              _comparisonTable createComparisonTable [ scoring ]
      when 'comparison'
        comparison = item
        _tag 'Comparison'
        _caption "Scoring Comparison"
        _timestamp comparison.data.timestamp
        _modelSummary null #TODO populate model summary
        scorings = comparison.data.scorings
        apply$ _comparisonMode, (mode) ->
          switch mode
            when 'tabular'
              if scorings.length > 0
                _comparisonTable createComparisonTable scorings
              else
                _comparisonTable null
            when 'advanced'
              if scorings.length > 0
                series = createSeriesFromMetrics scorings
                areModelsComparable = same series, (a, b) -> a.model.model_category is b.model.model_category and a.model.model_algorithm is b.model.model_algorithm
                if areModelsComparable
                  [inputParameterKeys, inputParameters] = collateInputParameters series
                  [ inputAndOutputVariables, defaultInputVariable, defaultOutputVariable ] = createInputAndOutputVariables inputParameterKeys, inputParameters, aucCategories
                else
                  [ inputAndOutputVariables, defaultInputVariable, defaultOutputVariable ] = createInputAndOutputVariables [], [], aucCategories

                _inputOutputPlotX defaultInputVariable
                _inputOutputPlotY defaultOutputVariable
                _inputOutputCategories inputAndOutputVariables
                apply$ _inputOutputPlotX, _inputOutputPlotY, (x, y) ->
                  _inputOutputPlot createMetricsPlot series, x, y

                #TODO sort by AUC
                _scoringList createScoringList series
                _categories aucCategories
                apply$ _selectedCategory, (category) ->
                  _comparisonPlot generateComparison category, series
                _multiRocPlot createThresholdPlot series, 'FPR', 'TPR', yes
                apply$ _thresholdPlotX, _thresholdPlotY, (x, y) ->
                  _thresholdPlot createThresholdPlot series, x.key, y.key, no
                #_stripPlot createStripPlot metricsArray
                _stripPlotParameters.group1 []
                _stripPlotParameters.group2 map aucCriteria, createStripPlotParameters2
                _stripPlotParameters.group3 map aucOutputs, createStripPlotParameters3
                _stripPlot createStripPlot series, aucCategories
              else
                _scoringList null
                _comparisonPlot null
                _multiRocPlot null
                _thresholdPlot null
                _stripPlot null
            when 'sheet'
              _sheetView Steam.ScoringSheetView _, scorings

    _scoringType item.type

  createStripPlotParameters2 = (criterion) ->
    id: "y-#{uniqueId()}"
    caption: criterion.caption
    criterion: criterion
    isSelected: node$ no

  createStripPlotParameters3 = (output) ->
    isSelected = node$ no
    id: "y-#{uniqueId()}"
    caption: output.caption
    output: output
    isSelected: node$ no

  renderRocCurve = (data) ->
    margin = top: 20, right: 20, bottom: 20, left: 30
    width = 175
    height = 175

    x = d3.scale.linear()
      .domain [ 0, 1 ]
      .range [ 0, width ]

    y = d3.scale.linear()
      .domain [ 0, 1 ]
      .range [ height, 0 ]

    axisX = d3.svg.axis()
      .scale x
      .orient 'bottom'
      .ticks 5

    axisY = d3.svg.axis()
      .scale y
      .orient 'left'
      .ticks 5

    line = d3.svg.line()
      .x (d) -> x d.fpr
      .y (d) -> y d.tpr

    el = document.createElementNS 'http://www.w3.org/2000/svg', 'svg'

    svg = (d3.select el)
      .attr 'class', 'y-roc-curve'
      .attr 'width', width + margin.left + margin.right
      .attr 'height', height + margin.top + margin.bottom
      .append 'g'
      .attr 'transform', "translate(#{margin.left},#{margin.top})"

    svg.append 'g'
      .attr 'class', 'x axis'
      .attr 'transform', "translate(0, #{height})"
      .call axisX
      .append 'text'
      .attr 'x', width
      .attr 'y', -6
      .style 'text-anchor', 'end'
      .text 'FPR'

    svg.append 'g'
      .attr 'class', 'y axis'
      .call axisY
      .append 'text'
      .attr 'transform', 'rotate(-90)'
      .attr 'y', 6
      .attr 'dy', '.71em'
      .style 'text-anchor', 'end'
      .text 'TPR'

    svg.append 'line'
      .attr 'class', 'guide'
      .attr 'stroke-dasharray', '3,3'
      .attr
        x1: x 0
        y1: y 0
        x2: x 1
        y2: y 1

    svg.selectAll '.dot'
      .data data
      .enter()
      .append 'circle'
      .attr 'class', 'dot'
      .attr 'r', 1
      .attr 'cx', (d) -> x d.fpr
      .attr 'cy', (d) -> y d.tpr

    svg.append 'path'
      .datum data
      .attr 'class', 'line'
      .attr 'd', line

    el

  computeTPRandFPR2 = (cm, index) ->
    [[tn, fp], [fn, tp]] = cm
    [ tp / (tp + fn), fp / (fp + tn) ]

  computeTPRandFPR = (cm, index) ->
    [[tn, fp], [fn, tp]] = cm

    cm: cm
    index: index
    tpr: tp / (tp + fn)
    fpr: fp / (fp + tn)

  createRocCurve = (cms) ->
    rates = map cms, computeTPRandFPR
    renderRocCurve rates

  createInputParameterForDisplay = (key, value, isVisible) ->
    # DL, DRF have array-valued params, so handle that case properly
    scalarValue = if isArray value then value.join ', ' else value
    key: key
    value: scalarValue
    isVisible: isVisible
    isDifferent: no

  createInputParameterAndValue = (value, key) ->
    # DL, DRF have array-valued params, so handle that case properly
    scalarValue = if isArray value then value.join ', ' else value
    key: key
    value: scalarValue
    isNumber: isNumber value

  createInputAndOutputVariables = (inputKeys, inputParameters, aucVariables) ->
    inputVariables = map inputKeys, (inputKey) ->
      values = map inputParameters, (parameter) -> parameter[inputKey]

      # Compute domain
      type: 'input'
      key: inputKey
      caption: "Input: #{inputKey}"
      domain: d3.extent values

    outputVariables = map aucVariables, (variable) ->
      type: 'output'
      key: variable.key
      caption: "Output: #{variable.caption}"
      variable: variable

    allVariables = sortBy (flatten [ inputVariables, outputVariables ]), (variable) -> variable.caption
    defaultInputVariable = if inputVariables.length > 0 then head inputVariables else outputVariables[1]
    defaultOutputVariable = head outputVariables

    [ allVariables, defaultInputVariable, defaultOutputVariable ]

  collateInputParameters = (series) ->
    models = map series, (series) -> series.model

    # Collate critcal, secondary, expert parameters for all models
    parametersByModel = map models, (model) ->
      flatten [
        mapWithKey model.critical_parameters, createInputParameterAndValue
        mapWithKey model.secondary_parameters, createInputParameterAndValue
        mapWithKey model.expert_parameters, createInputParameterAndValue
      ]

    # Filter only numeric parameters
    numericParametersByModel = map parametersByModel, (parameters) -> filter parameters, (parameter) -> parameter.isNumber

    # Find parameters that have numeric values defined for all models.
    # This is to reject any parameters that have numeric values for some models and non-numeric ones for the others (in which case the number of parameter values will not match the number of models).
    parametersByKey = groupBy (flatten numericParametersByModel), (parameter) -> parameter.key
    plottableParameterKeys = []
    for key, parameters of parametersByKey
      if parameters.length is models.length
        plottableParameterKeys.push key

    # Create a dictionary of input parameters per series
    inputParameters = times models.length, -> {}
    for key in plottableParameterKeys
      for parameter, i in parametersByKey[key]
        inputParameters[i][key] = parameter.value

    forEach series, (series, i) -> series.inputs = inputParameters[i]

    [plottableParameterKeys, inputParameters]

  combineInputParameters = (model) ->
    critical = mapWithKey model.critical_parameters, (value, key) ->
      createInputParameterForDisplay key, value, yes
    secondary = mapWithKey model.secondary_parameters, (value, key) ->
      createInputParameterForDisplay key, value, no
    concat critical, secondary

  # Side-effects!
  markAsDifferent = (parametersArray, index) ->
    for parameters in parametersArray
      parameter = parameters[index]
      # mark this as different to enable special highlighting
      parameter.isDifferent = yes
      parameter.isVisible = yes
    return

  # Side-effects!
  compareInputParameters = (parametersArray) ->
    headParameters = head parametersArray
    tailParametersArray = tail parametersArray
    for parameters, index in headParameters
      for tailParameters in tailParametersArray
        if parameters.value isnt tailParameters[index].value
          markAsDifferent parametersArray, index
          break
    return

  renderStripPlot = (series, categories) ->
    margin = top: 20, right: 70, bottom: 20, left: 140
    width = 140
    rowHeight = 18
    height = categories.length * rowHeight

    scaleX = zipObject map categories, (category) ->
      scaleX = d3.scale.linear()
        #.domain d3.extent scorings, (d) -> +d.outputs[category.id].value
        .domain category.domain
        .range [ 0, width ]
      [ category.key, scaleX ]

    scaleY = d3.scale.ordinal()
      .domain map categories, (category) -> category.key
      .rangePoints [ 0, height ], 1

    line = d3.svg.line()

    axis = d3.svg.axis()
      .orient 'left'

    x = (value) -> if isNaN value then 0 else value

    path = (d) ->
      line map categories, (category) ->
        key = category.key
        [ (scaleX[key] x d.outputs[key]), (scaleY key) ]

    el = document.createElementNS 'http://www.w3.org/2000/svg', 'svg'
    svg = (d3.select el)
      .attr 'class', 'y-strip-plot'
      .attr 'width', width + margin.left + margin.right
      .attr 'height', height + margin.top + margin.bottom
      .append 'g'
      .attr 'transform', "translate(#{margin.left},#{margin.top})"

    line = svg.append 'g'
      .attr 'class', 'line'
      .selectAll 'path'
      .data series
      .enter()
      .append 'path'
      .attr 'd', path
      .attr 'id', (d) -> "strip-plot-#{d.id}-path"

    forEach series, (series) ->
      svg.append 'g'
        .attr 'id', "strips-#{series.id}"
        .selectAll '.strip'
        .data categories
        .enter()
        .append 'line'
        .attr 'class', 'strip'
        .attr 'x1', (d) -> scaleX[d.key] x series.outputs[d.key]
        .attr 'y1', (d) -> -5 + scaleY d.key
        .attr 'x2', (d) -> scaleX[d.key] x series.outputs[d.key]
        .attr 'y2', (d) -> 5 + scaleY d.key
        .attr 'stroke', series.color
        .on 'mouseover', (d) ->
          svg.select("#strip-plot-#{series.id}-path").style 'stroke', '#ddd'
          svg.select("#strip-plot-#{series.id}-labels").style 'display', 'block'
        .on 'mouseout', (d) ->
          svg.select("#strip-plot-#{series.id}-path").style 'stroke', 'none'
          svg.select("#strip-plot-#{series.id}-labels").style 'display', 'none'
        .on 'click', (d) ->
          _.inspect
            content: createStripPlotValueInspection series
            template: 'geyser'

    g = svg.selectAll '.category'
      .data categories
      .enter()
      .append 'g'
      .attr 'transform', (d) -> "translate(#{-margin.left}, #{scaleY d.key})"

    g.append 'text'
      .attr 'class', 'labels'
      .attr 'dy', 5
      .text (d) -> d.caption
      .on 'click', (d) ->
        _.inspect
          content: createStripPlotRowInspection series, d
          template: 'geyser'

    forEach series, (series) ->
      svg.append 'g'
        .attr 'id', "strip-plot-#{series.id}-labels"
        .attr 'transform', (d) -> "translate(#{width + 10})"
        .style 'display', 'none'
        .selectAll '.label'
        .data categories
        .enter()
        .append 'text'
        .attr 'transform', (d) -> "translate(0, #{scaleY d.key})"
        .attr 'dy', 5
        .text (d) ->
          value = series.outputs[d.key]
          if isNaN value then 'NaN' else format4f value

    g.append 'line'
      .attr 'class', 'guide'
      .attr 'x1', 0
      .attr 'y1', rowHeight / 2
      .attr 'x2', margin.left + width
      .attr 'y2', rowHeight / 2

    el

  buildAucCategories = ->
    categories = []
    id = 0
    for criterion, criterionIndex in aucCriteria
      for output in aucOutputs
        categories.push
          id: id
          caption: "#{criterion.caption} - #{output.caption}"
          criterion: criterion
          output: output
          criterionIndex: criterionIndex
          domain: [0, 1]
        id++

    criteria: aucCriteria
    outputs: aucOutputs
    categories: categories

  generateComparison = (category, series) ->
    filteredSeries = filter series, (series) -> not isNaN series.outputs[category.key]
    sortedSeries = sortBy filteredSeries, (series) -> -series.outputs[category.key]

    span = 300

    scale = d3.scale.linear()
      .domain category.domain
      .range [0, span]

    map sortedSeries, (series) ->
      value = series.outputs[category.key] 
      origin = scale 0
      x = scale value
      if x > origin
        left = origin
        width = x - origin
      else if x < origin
        left = x
        width = origin - x
      else
        left = 0
        width = 1

      caption: series.caption
      value: format4f value
      span: "#{span}px"
      width: "#{width}px"
      left: "#{left}px"
      color: series.color
      inspect: -> _.inspect
        content: createScoringInspection series
        template: 'geyser'

  createScoringList = (series) ->
    map series, (series) ->
      caption: series.caption
      color: series.color
      inspect: -> _.inspect
        content: createScoringInspection series
        template: 'geyser'

  createSeriesFromMetrics = (scores) ->
    uniqueScoringNames = {}
    createUniqueScoringName = (frameKey, modelKey) ->
      name = "#{modelKey} on #{frameKey}"
      if index = uniqueScoringNames[name]
        uniqueScoringNames[name] = index++
        name += ' #' + index
      else
        uniqueScoringNames[name] = 1
      name

    # Go for higher contrast when comparing fewer scorings.
    palette = if scores.length > 10 then d3.scale.category20 else d3.scale.category10
    colorScale = palette().domain d3.range scores.length

    map scores, (score, index) ->
      metrics = head score.data.output.metrics
      model = score.data.input.model

      id: index
      caption: createUniqueScoringName metrics.frame.key, metrics.model.key
      model: model
      inputs: null
      metrics: metrics
      outputs: createMarkForScoringMetrics metrics
      thresholdMarks: createMarksForThresholdMetrics metrics
      color: colorScale index

  createMarkForScoringMetrics = (metrics) ->
    auc = metrics.auc
    mark = {}
    for category in aucCategories
      if category.isGrouped
        mark[category.key] = +auc[category.output.key][category.criterionIndex]
      else
        mark[category.key] = +auc[category.key]
    mark

  createMarksForThresholdMetrics = (metrics) ->
    auc = metrics.auc
    map auc.thresholds, (threshold, index) ->
      cm = auc.confusion_matrices[index]
      [ tpr, fpr ] = computeTPRandFPR2 cm

      'Threshold': +threshold
      'Error': +auc.errorr[index]
      'F0.5': +auc.F0point5[index]
      'F1': +auc.F1[index]
      'F2': +auc.F2[index]
      'Accuracy': +auc.accuracy[index]
      'Precision': +auc.precision[index]
      'Recall': +auc.recall[index]
      'Specificity': +auc.specificity[index]
      'MCC': +auc.mcc[index]
      'MPCE': +auc.max_per_class_error[index]
      'Confusion Matrix': cm
      'TPR': tpr
      'FPR': fpr

  renderMetricsPlot = (series, variableX, variableY) ->
    margin = top: 20, right: 20, bottom: 20, left: 30
    width = 300
    height = 300

    isInputX = variableX.type is 'input'
    isInputY = variableY.type is 'input'

    readX = (series) ->
      if isInputX
        series.inputs[variableX.key]
      else
        series.outputs[variableX.key]

    readY = (series) ->
      if isInputY
        series.inputs[variableY.key]
      else
        series.outputs[variableY.key]

    scaleX = d3.scale.linear()
      .domain if isInputX then variableX.domain else variableX.variable.domain
      .range [ 0, width ]
      .nice()

    scaleY = d3.scale.linear()
      .domain if isInputY then variableY.domain else variableY.variable.domain
      .range [ height, 0 ]
      .nice()

    axisX = d3.svg.axis()
      .scale scaleX
      .orient 'bottom'
      .ticks 5

    axisY = d3.svg.axis()
      .scale scaleY
      .orient 'left'
      .ticks 5

    line = d3.svg.line()
      .x (d) -> scaleX readX d
      .y (d) -> scaleY readY d

    el = document.createElementNS 'http://www.w3.org/2000/svg', 'svg'

    svg = (d3.select el)
      .attr 'class', 'y-metrics-plot'
      .attr 'width', width + margin.left + margin.right
      .attr 'height', height + margin.top + margin.bottom
      .append 'g'
      .attr 'transform', "translate(#{margin.left},#{margin.top})"

    svg.append 'g'
      .attr 'class', 'x axis'
      .attr 'transform', "translate(0, #{height})"
      .call axisX
      .append 'text'
      .attr 'x', width
      .attr 'y', -6
      .style 'text-anchor', 'end'
      .text variableX.caption

    svg.append 'g'
      .attr 'class', 'y axis'
      .call axisY
      .append 'text'
      .attr 'transform', 'rotate(-90)'
      .attr 'y', 6
      .attr 'dy', '.71em'
      .style 'text-anchor', 'end'
      .text variableY.caption

    svg.selectAll '.dot'
      .data series
      .enter()
      .append 'circle'
      .attr 'class', 'dot'
      .attr 'r', 5
      .attr 'cx', (d) -> scaleX readX d
      .attr 'cy', (d) -> scaleY readY d
      .attr 'stroke', (d) -> d.color
      .on 'click', (d) ->
        _.inspect
          content: createStripPlotValueInspection d
          template: 'geyser'
    el

  renderThresholdPlot = (series, attrX, attrY, showReferenceLine) ->
    #TODO make this function accept variables instead of attrs
    variableX = aucVariableMap[attrX]
    variableY = aucVariableMap[attrY]

    margin = top: 20, right: 20, bottom: 20, left: 30
    width = 300
    height = 300

    validMarks = zipObject map series, (series) ->
      marks = filter series.thresholdMarks, (mark) ->
        (not isNaN mark[attrX]) and (not isNaN mark[attrY])
      [ series.id, marks ]

    scaleX = d3.scale.linear()
      .domain variableX.domain
      .range [ 0, width ]

    scaleY = d3.scale.linear()
      .domain variableY.domain
      .range [ height, 0 ]

    axisX = d3.svg.axis()
      .scale scaleX
      .orient 'bottom'
      .ticks 5

    axisY = d3.svg.axis()
      .scale scaleY
      .orient 'left'
      .ticks 5

    line = d3.svg.line()
      .x (d) -> scaleX d[attrX]
      .y (d) -> scaleY d[attrY]

    el = document.createElementNS 'http://www.w3.org/2000/svg', 'svg'

    svg = (d3.select el)
      .attr 'class', 'y-custom-plot'
      .attr 'width', width + margin.left + margin.right
      .attr 'height', height + margin.top + margin.bottom
      .append 'g'
      .attr 'transform', "translate(#{margin.left},#{margin.top})"

    svg.append 'g'
      .attr 'class', 'x axis'
      .attr 'transform', "translate(0, #{height})"
      .call axisX
      .append 'text'
      .attr 'x', width
      .attr 'y', -6
      .style 'text-anchor', 'end'
      .text variableX.caption

    svg.append 'g'
      .attr 'class', 'y axis'
      .call axisY
      .append 'text'
      .attr 'transform', 'rotate(-90)'
      .attr 'y', 6
      .attr 'dy', '.71em'
      .style 'text-anchor', 'end'
      .text variableY.caption

    if showReferenceLine
      svg.append 'line'
        .attr 'class', 'guide'
        .attr 'stroke-dasharray', '3,3'
        .attr
          x1: scaleX 0
          y1: scaleY 0
          x2: scaleX 1
          y2: scaleY 1

    curve = svg.selectAll '.y-curve'
      .data series
      .enter()
      .append 'g'
      .attr 'class', 'y-curve'

    curve.append 'path'
      .attr 'id', (d) -> "curve#{d.id}"
      .attr 'class', 'line'
      .attr 'd', (d) -> line validMarks[d.id]
      .style 'stroke', (d) -> d.color
      #.on 'mouseover', (d) -> _.inspect div d.caption
      #.on 'mouseout', (d) -> console.log 'mouseout', d

    forEach series, (series) ->
      svg.append 'g'
        .selectAll '.dot'
        .data validMarks[series.id]
        .enter()
        .append 'circle'
        .attr 'class', 'dot'
        .attr 'r', 5
        .attr 'cx', (d) -> scaleX d[attrX]
        .attr 'cy', (d) -> scaleY d[attrY]
        .on 'click', (d) ->
          _.inspect
            content: createThresholdPlotInspection series, d
            template: 'geyser'
        .on 'mouseover', (d) ->
          d3.select(@).style 'stroke', series.color
        .on 'mouseout', (d) ->
          d3.select(@).style 'stroke', 'none'
    el


  createMetricsPlot = (series, variableX, variableY) ->
    [ div ] = geyser.generate [ 'div' ]
    render = ($element) ->
      plot = renderMetricsPlot series, variableX, variableY
      $element.empty().append plot
    markup: div()
    behavior: render

  createThresholdPlot = (series, attrX, attrY, showReferenceLine) ->
    [ div ] = geyser.generate [ 'div' ]
    render = ($element) ->
      plot = renderThresholdPlot series, attrX, attrY, showReferenceLine
      $element.empty().append plot
    markup: div()
    behavior: render

  createStripPlot = (series, categories) ->
    [ div ] = geyser.generate [ 'div' ]
    render = ($element) ->
      stripPlot = renderStripPlot series, categories
      $element.empty().append stripPlot

    markup: div()
    behavior: render

  createComparisonTable = (scores) ->
    [ div, table, kvtable, thead, tbody, tr, trExpert, diffSpan, th, thIndent, td, hyperlink] = geyser.generate words 'div table.table.table-condensed table.table-kv thead tbody tr tr.y-expert span.y-diff th th.y-indent td div.y-link'
    [ tdId, thWide ] = geyser.generate "td id='$id'", "th colspan='#{scores.length + 1}' style='background:#f5f5f5'"

    createParameterTable = ({ parameters }) ->
      kvtable [
        tbody map parameters, (parameter) ->
          trow = if parameter.isVisible then tr else trExpert
          trow [
            th parameter.key
            td if parameter.isDifferent then diffSpan parameter.value else parameter.value
          ]
      ]

    createComparisonGrid = (scores) ->
      comparisonRows = []

      comparisonRows.push algorithmRow = [ th 'Method' ]
      comparisonRows.push nameRow = [ th 'Name' ]
      comparisonRows.push rocCurveRow = [ th 'ROC Curve' ]
      comparisonRows.push inputParametersRow = [
        th [
          (div 'Input Parameters')
          (hyperlink 'Show more', 'toggle-advanced-parameters')
        ]
      ]
      comparisonRows.push durationRow = [ th 'Time' ]
      comparisonRows.push aucRow = [ th 'AUC' ]


      #TODO what does it mean to have > 1 metrics
      scoreWithLowestError = min scores, (score) -> (head score.data.output.metrics).error_measure

      inputParamsWithAlgorithm = map scores, (score) ->
        model = score.data.input.model
        algorithm: model.model_algorithm
        parameters: combineInputParameters model

      inputParamsByScoreIndex = map inputParamsWithAlgorithm, (a) -> a.parameters

      inputParamsByAlgorithm = values groupBy inputParamsWithAlgorithm, (a) -> a.algorithm
      # Side-effects!
      forEach inputParamsByAlgorithm, (groups) ->
        compareInputParameters map groups, (group) -> group.parameters

      for score, scoreIndex in scores
        model = score.data.input.model
        #TODO what does it mean to have > 1 metrics
        metrics = head score.data.output.metrics
        auc = metrics.auc
        cm = metrics.cm
        errorBadge = if scores.length > 1 and score is scoreWithLowestError then ' (Lowest)' else ''

        algorithmRow.push td model.model_algorithm
        nameRow.push td model.key
        rocCurveRow.push tdId 'Loading...', $id:"roc-#{scoreIndex}"
        inputParametersRow.push td createParameterTable parameters: inputParamsByScoreIndex[scoreIndex]
        durationRow.push td "#{metrics.duration_in_ms} ms"
        aucRow.push td format4f auc.AUC
        #TODO fix error badge
        #errorRow.push td (format4f metrics.error_measure) + errorBadge #TODO change to bootstrap badge

      for criterion, criterionIndex in aucCriteria
        comparisonRows.push [ thWide "Outputs by #{criterion.caption}" ]
        for aucOutput in aucOutputs
          outputRow = [[ th aucOutput.caption ]]
          for score, scoreIndex in scores
            model = score.data.input.model
            #TODO what does it mean to have > 1 metrics
            metrics = head score.data.output.metrics
            auc = metrics.auc
            value = auc[aucOutput.key][criterionIndex]
            outputRow.push td if value is 'NaN' then value else format4f value

          comparisonRows.push outputRow

      renderRocCurves = ($element) ->
        forEach scores, (score, scoreIndex) ->
          defer ->
            #TODO what does it mean to have > 1 metrics
            rocCurve = createRocCurve (head score.data.output.metrics).auc.confusion_matrices
            $("#roc-#{scoreIndex}", $element).empty().append rocCurve
        return

      toggleAdvancedParameters = ($element) ->
        isHidden = yes
        $toggleLink = $ '#toggle-advanced-parameters', $element
        $toggleLink.click ->
          if isHidden
            $('.y-expert', $element).show()
            $toggleLink.text 'Show less'
          else
            $('.y-expert', $element).hide()
            $toggleLink.text 'Show more'

          isHidden = not isHidden
          return
        return


      markup: table tbody map comparisonRows, (row) -> tr row 
      behavior: ($element) ->
        renderRocCurves $element
        toggleAdvancedParameters $element

    createComparisonGrid scores

  configureStripPlot = ->
    _.configureStripPlot _stripPlotParameters, (parameters) ->
      if parameters
        for parameter in parameters.group1
          parameter.source.isSelected parameter.isSelected()
        for parameter in parameters.group2
          parameter.source.isSelected parameter.isSelected()
        for parameter in parameters.group3
          parameter.source.isSelected parameter.isSelected()
      return

  initialize _scoring

  tag: _tag
  caption: _caption
  timestamp: _timestamp
  isScoringView: _isScoringView
  isComparisonView: _isComparisonView
  isTabularComparisonView: _isTabularComparisonView
  isAdvancedComparisonView: _isAdvancedComparisonView
  isSheetView: _isSheetView
  switchToTabularView: switchToTabularView
  switchToAdvancedView: switchToAdvancedView
  switchToSheetView: switchToSheetView
  modelSummary: _modelSummary
  comparisonTable: _comparisonTable
  scoringList: _scoringList
  multiRocPlot: _multiRocPlot
  categories: _categories
  selectedCategory: _selectedCategory
  comparisonPlot: _comparisonPlot
  inputOutputCategories: _inputOutputCategories
  inputOutputPlotX: _inputOutputPlotX
  inputOutputPlotY: _inputOutputPlotY
  inputOutputPlot: _inputOutputPlot
  thresholdPlot: _thresholdPlot
  thresholdPlotX: _thresholdPlotX
  thresholdPlotY: _thresholdPlotY
  thresholdPlotVariables: _thresholdPlotVariables
  stripPlot: _stripPlot
  configureStripPlot: configureStripPlot
  sheetView: _sheetView
  hasFailed: _hasFailed
  failure: _failure
  template: 'scoring-view'
