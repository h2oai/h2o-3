format4f = unless exports? then d3.format '.4f' else null

metricCriteria = [
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


metricTypes = [
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

metricVariables = []
metricVariables.push
  id: uniqueId()
  name: 'auc'
  caption: 'AUC'
  type: 'float'
  read: (metrics) -> +metrics.auc.AUC
  domain: [ 0, 1 ]
  format: format4f

metricVariables.push
  id: uniqueId()
  name: 'gini'
  caption: 'Gini'
  type: 'float'
  read: (metrics) -> +metrics.auc.Gini
  domain: [ 0, 1 ]
  format: format4f

forEach metricCriteria, (criterion, criterionIndex) ->
  forEach metricTypes, (metricType) -> 
    metricVariables.push
      id: uniqueId()
      name: "#{criterion.key}-#{metricType.key}"
      caption: "#{criterion.caption} #{metricType.caption}"
      type: 'float'
      read: (metrics) -> +metrics.auc[metricType.key][criterionIndex]
      domain: [ 0, 1 ]
      format: format4f

computeTPR = (cm) ->
  [[tn, fp], [fn, tp]] = cm
  tp / (tp + fn)

computeFPR = (cm) ->
  [[tn, fp], [fn, tp]] = cm
  fp / (fp + tn)

thresholdVariables = [
  name: 'threshold'
  caption: 'Threshold'
  type: 'float'
  read: (metrics, index) -> +metrics.auc.thresholds[index]
  domain: [ 0, 1 ]
  format: format4f
,
  name: 'error'
  caption: 'Error'
  type: 'float'
  read: (metrics, index) -> +metrics.auc.errorr[index]
  domain: [ 0, 1 ]
  format: format4f
,
  name: 'f0.5'
  caption: 'F0.5'
  type: 'float'
  read: (metrics, index) -> +metrics.auc.F0point5[index]
  domain: [ 0, 1 ]
  format: format4f
,
  name: 'f1'
  caption: 'F1'
  type: 'float'
  read: (metrics, index) -> +metrics.auc.F1[index]
  domain: [ 0, 1 ]
  format: format4f
,
  name: 'f2'
  caption: 'F2'
  type: 'float'
  read: (metrics, index) -> +metrics.auc.F2[index]
  domain: [ 0, 1 ]
  format: format4f
,
  name: 'accuracy'
  caption: 'Accuracy'
  type: 'float'
  read: (metrics, index) -> +metrics.auc.accuracy[index]
  domain: [ 0, 1 ]
  format: format4f
,
  name: 'precision'
  caption: 'Precision'
  type: 'float'
  read: (metrics, index) -> +metrics.auc.precision[index]
  domain: [ 0, 1 ]
  format: format4f
,
  name: 'recall'
  caption: 'Recall'
  type: 'float'
  read: (metrics, index) -> +metrics.auc.recall[index]
  domain: [ 0, 1 ]
  format: format4f
,
  name: 'specificity'
  caption: 'Specificity'
  type: 'float'
  read: (metrics, index) -> +metrics.auc.specificity[index]
  domain: [ 0, 1 ]
  format: format4f
,
  name: 'mcc'
  caption: 'MCC'
  type: 'float'
  read: (metrics, index) -> +metrics.auc.mcc[index]
  domain: [ -1, 1 ]
  format: format4f
,
  name: 'mpce'
  caption: 'MPCE'
  type: 'float'
  read: (metrics, index) -> +metrics.auc.max_per_class_error[index]
  domain: [ 0, 1 ]
  format: format4f
,
  name: 'cm'
  caption: 'Confusion Matrix'
  type: 'blob'
  read: (metrics, index) -> metrics.auc.confusion_matrices[index]
  domain: null
  format: -> #TODO
,
  name: 'tpr'
  caption: 'TPR'
  type: 'float'
  read: (metrics, index) -> computeTPR metrics.auc.confusion_matrices[index]
  domain: [ 0, 1 ]
  format: format4f
,
  name: 'fpr'
  caption: 'FPR'
  type: 'float'
  read: (metrics, index) -> computeFPR metrics.auc.confusion_matrices[index]
  domain: [ 0, 1 ]
  format: format4f
]

importMetricData = (metrics, variables) ->
  datum = {}
  forEach variables, (variable) ->
    datum[variable.name] = variable.read metrics
  datum

importThresholdData = (metrics, variables, count) ->
  times count, (index) ->
    datum = {}
    forEach thresholdVariables, (variable) ->
      datum[variable.name] = variable.read metrics, index
    datum

computeExtents = (variables, data) ->
  map variables, (variable) ->
    d3.extent data, (datum) -> datum[variable.name]

createMetricFrameFromScorings = (scores) ->
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

  metrics = map scores, (score, index) ->
    model = score.data.input.model
    metrics = head score.data.output.metrics

    metricData = importMetricData metrics, metricVariables
    thresholdData = importThresholdData metrics, thresholdVariables, metrics.auc.thresholds.length

    name: index
    caption: createUniqueScoringName metrics.frame.key, metrics.model.key
    model: model
    data: metricData
    thresholdFrame:
      data: thresholdData
      metadata:
        extents: computeExtents thresholdVariables, thresholdData
    color: colorScale index

  #TODO create variables for model inputs.
  modelVariables = null

  metrics: metrics
  modelVariables: modelVariables
  metricVariables: metricVariables
  thresholdVariables: thresholdVariables

Steam.ScoringSheetView = (_, _scorings) ->
  _metricFrame = null
  _metricTable = node$ null

  initialize = (scorings) ->
    console.log 'scorings', scorings
    _metricFrame = createMetricFrameFromScorings scorings
    _metricTable createMetricTable _metricFrame, 'auc', no

  createMetricTable = (metricFrame, sortByVariableName, sortAscending) ->
    [ table, thead, tbody, tr, th, td ] = geyser.generate words 'table.table.table-condensed thead tbody tr th td'
    [ span ] = geyser.generate [ "a data-variable-id='$id'" ]

    # Sort
    metricFrame.metrics.sort (a, b) -> if sortAscending
      a.data[sortByVariableName] - b.data[sortByVariableName]
    else 
      b.data[sortByVariableName] - a.data[sortByVariableName]
    
    header = tr map metricFrame.metricVariables, (variable) ->
      th span variable.caption, $id: variable.id

    rows = map metricFrame.metrics, (metric) ->
      tr map metricFrame.metricVariables, (variable) ->
        td variable.format metric.data[variable.name] #TODO can use variable.read() to reduce mem footprint

    markup = table [
      thead header
      tbody rows
    ]

    behavior = ($element) ->
      $('a', $element).each ->
        $anchor = $ @
        $anchor.click ->
          #TODO toggle sort direction
          sortById = $anchor.attr 'data-variable-id'
          sortByVariable = find metricFrame.metricVariables, (variable) -> variable.id is sortById
          sortByVariableName = sortByVariable.name
          _metricTable createMetricTable metricFrame, sortByVariableName, no

    markup: markup
    behavior: behavior

  initialize _scorings

  metricTable: _metricTable



