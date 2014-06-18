defaultScoringComparisonMessage = 'Compare selected scorings.'
Steam.ScoringSelectionView = (_) ->
  _selections = do nodes$
  _hasSelection = lift$ _selections, (selections) -> selections.length > 0
  _caption = lift$ _selections, (selections) ->
    "#{describeCount selections.length, 'item'} selected"

  _scoringComparisonMessage = lift$ _selections, (selections) ->
    return 'Select two or more scorings to compare.' if selections.length < 2
    return 'Remove pending scorings from your selection.' if some selections, (selection) -> not selection.isReady()
    return 'Remove failed scorings from your selection.' if some selections, (selection) -> selection.hasFailed()
    return 'Remove comparison tables from your selection.' if some selections, (selection) -> selection.type is 'comparison'
    return 'Ensure that all selected scorings refer to conforming datasets.' unless valuesAreEqual selections, (selection) -> selection.data.input.frameKey
    return 'Ensure that all selected scorings belong to the same model category.' unless valuesAreEqual selections, (selection) -> selection.data.input.model.model_category
    return 'Ensure that all selected scorings refer to the same response column.' unless valuesAreEqual selections, (selection) -> selection.data.input.model.response_column_name

    # TODO is the following rule valid?
    # return 'Ensure that all selected scorings refer to the same input columns' unless valuesAreEqual selections, (selection) -> selection.data.input.model.input_column_names.join '\0'
    defaultScoringComparisonMessage

  _canCompareScorings = lift$ _scoringComparisonMessage, (message) -> message is defaultScoringComparisonMessage
  _hasScorings = node$ no

  compareScorings = ->
    _.loadScorings
      type: 'comparison'
      # Send a clone of selections because the selections gets cleared soon after.
      scorings: clone _selections()
      timestamp: Date.now()
    _.deselectAllScorings()

  tryCompareScorings = (hover) ->
    _.status if hover then _scoringComparisonMessage() else null

  deleteActiveScoring = ->
    confirmDialogOpts =
      title: 'Delete Scoring?'
      confirmCaption: 'Delete'
      cancelCaption: 'Keep'
    _.confirm 'This scoring will be permanently deleted. Are you sure?', confirmDialogOpts, (response) ->
      if response is 'confirm'
        _.deleteActiveScoring()

  deleteScorings = ->
    # Send a clone of selections because the selections gets cleared
    #  when deleted from the selection list.
    confirmDialogOpts =
      title: 'Delete Scorings?'
      confirmCaption: 'Delete'
      cancelCaption: 'Keep'
    _.confirm 'These scorings will be permanently deleted. Are you sure?', confirmDialogOpts, (response) ->
      if response is 'confirm'
        _.deleteScorings clone _selections()

  clearSelections = ->
    _.deselectAllScorings()

  rescore = -> _.rescore()

  link$ _.scoringSelectionChanged, (isSelected, scoring) ->
    if isSelected
      _selections.push scoring
    else
      _selections.remove scoring

  link$ _.scoringSelectionCleared, ->
    _selections.removeAll()

  link$ _.scoringAvailable, _hasScorings

  caption: _caption
  hasSelection: _hasSelection
  clearSelections: clearSelections
  canCompareScorings: _canCompareScorings
  tryCompareScorings: tryCompareScorings
  rescore: rescore
  compareScorings: compareScorings
  hasScorings: _hasScorings
  deleteScorings: deleteScorings
  deleteActiveScoring: deleteActiveScoring
  template: 'scoring-selection-view'

