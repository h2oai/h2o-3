# TODO automatically batch scorings into colored groups
# HSL( (((i % 3) * n / 3) + (i / 3)) * 255.0 / n, 255, 128)

Steam.ScoringListView = (_) ->
  _predicate = node$ type: 'all'
  _items = do nodes$
  _hasItems = lift$ _items, (items) -> items.length > 0
  _isSelectAll = node$ no

  #TODO ugly
  _isLive = node$ yes

  apply$ _isSelectAll, (isSelected) ->
    _isLive no
    for item in _items()
      item.isSelected isSelected
    _isLive yes
    return

  _canClearPredicate = no
  _predicateCaption = 'Showing\nall scorings'

  displayItem = (item) ->
    if item
      _.displayScoring item
      _.scoringAvailable yes
    else
      _.displayEmpty()
      _.scoringAvailable no

  findActiveItem = ->
    find _items(), (item) -> item.isActive()

  activateAndDisplayItem = (item) ->
    for other in _items()
      if other is item
        other.isActive yes
      else
        other.isActive no

    displayItem item

  createScoringItem = (scoring) ->
    #TODO replace with type checking
    #TODO dispose isSelected properly
    self =
      type: 'scoring'
      data:
        input: scoring
        output: null
      title: "Scoring on #{scoring.frameKey}"
      caption: "#{scoring.model.key} (#{scoring.model.response_column_name})"
      timestamp: scoring.timestamp
      display: -> activateAndDisplayItem self
      isActive: node$ no
      isSelected: node$ no
      state: node$ 'waiting'
      isReady: node$ no
      hasFailed: node$ no

    apply$ _isLive, self.isSelected, (isLive, isSelected) ->
      _.scoringSelectionChanged isSelected, self if isLive

    self

  createComparisonItem = (comparison) ->
    self =
      type: 'comparison'
      data: comparison
      title: 'Comparison' #TODO needs a better caption
      caption: describeCount comparison.scorings.length, 'scoring'
      timestamp: node$ comparison.timestamp
      display: -> activateAndDisplayItem self
      isActive: node$ no
      isSelected: node$ no
      isReady: node$ yes
      hasFailed: node$ no

    apply$ _isLive, self.isSelected, (isLive, isSelected) ->
      _.scoringSelectionChanged isSelected, self if isLive

    self

  runScoringJobs = (jobs, go) ->
    queue = copy jobs
    runNext = ->
      if job = shift queue
        job.run -> defer runNext
      else
        go()
    defer runNext

  createScoringJobs = (items) ->
    map items, (item) ->
      frameKey = item.data.input.frameKey
      modelKey = item.data.input.model.key
      run: (go) ->
        item.state 'running'
        _.requestScoringOnFrame frameKey, modelKey, (error, result) ->
          if error
            _.error 'Scoring failed', { frameKey: frameKey, modelKey: modelKey }, error
            #item.time if error.response then error.response.time else 0
            item.state 'error'
            item.hasFailed yes
            item.data.output = result
          else
            #TODO what does it mean to have > 1 metrics
            #TODO put this in the comparison table
            #item.time if result.metrics and result.metrics.length > 0 then (head result.metrics).duration_in_ms else 0
            item.state 'success'
            item.hasFailed no
            item.data.output = result
          item.isReady yes

          do go

  loadScorings = (predicate) ->
    console.assert isDefined predicate
    #pastScorings = (_.getFromCache 'scoring') or _.putIntoCache 'scoring', []

    switch predicate.type
      when 'scoring'
        items = map predicate.scorings, createScoringItem
        _items.splice.apply _items, [0, 0].concat items
        jobs = createScoringJobs items
        runScoringJobs jobs, ->
          for item in items
            unless item.hasFailed()
              item.timestamp = (head item.data.output.metrics).scoring_time
          # Automatically create a comparison if the selection has > 1 items
          if items.length > 1
            _.loadScorings 
              type: 'comparison'
              scorings: items
              timestamp: Date.now()
          else
            activateAndDisplayItem head items
      when 'comparison'
        item = createComparisonItem
          scorings: predicate.scorings
          timestamp: predicate.timestamp
        _items.unshift item
        activateAndDisplayItem item

    _predicate predicate
    return

  link$ _.loadScorings, (predicate) ->
    if predicate
      loadScorings predicate
    else
      displayItem findActiveItem()

  deselectAllScorings = ->
    #TODO ugly
    _isLive no
    for item in _items()
      item.isSelected no
    _isLive yes
    _.scoringSelectionCleared()

  link$ _.rescore, ->
    scoring = findActiveItem()
    # Collect all frames from all models
    models = []
    allFrames = []
    switch scoring.type
      when 'scoring'
        model = scoring.data.input.model
        push models, model
        pushAll allFrames, model.compatible_frames
      when 'comparison'
        for item in scoring.data.scorings
          model = item.data.input.model
          push models, model
          pushAll allFrames, model.compatible_frames

    # Build a unique list of frames
    compatibleFrames = unique allFrames, (frame) -> frame.id

    #TODO remove current frame

    _.promptForFrame compatibleFrames, (action, frameKey) ->
      switch action
        when 'confirm'
          _.switchToScoring type: 'scoring', scorings: map models, (model) ->
            frameKey: frameKey
            model: model
            status: null
            time: null
            result: null
            timestamp: Date.now()


  deleteActiveScoring = ->
    _items.remove findActiveItem()
    displayItem null

  deleteScorings = (scorings) ->
    deselectAllScorings()
    _items.removeAll scorings 
    unless findActiveItem()
      displayItem null

  clearPredicate = ->

  link$ _.deselectAllScorings, deselectAllScorings
  link$ _.deleteScorings, deleteScorings
  link$ _.deleteActiveScoring, deleteActiveScoring

  items: _items
  hasItems: _hasItems
  predicateCaption: _predicateCaption
  clearPredicate: clearPredicate
  canClearPredicate: _canClearPredicate
  isSelectAll: _isSelectAll
  template: 'scoring-list-view'

