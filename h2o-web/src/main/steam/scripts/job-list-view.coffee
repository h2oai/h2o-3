_statusColors =
  failed: '#d9534f'
  done: '#ccc' #'#5cb85c'
  running: '#f0ad4e'

getStatusColor = (status) ->
  # CREATED   Job was created
  # RUNNING   Job is running
  # CANCELLED Job was cancelled by user
  # FAILED    Job crashed, error message/exception is available
  # DONE      Job was successfully finished
  switch status
    when 'DONE'
      _statusColors.done
    when 'CREATED', 'RUNNING'
      _statusColors.running
    else # 'CANCELLED', 'FAILED'
      _statusColors.failed

getProgressPercent = (progress) ->
  "#{Math.ceil 100 * progress}%"

Steam.JobListView = (_) ->
  _predicate = node$ type: 'all'
  _items = do nodes$
  _hasItems = lift$ _items, (items) -> items.length > 0

  _canClearPredicate = lift$ _predicate, (predicate) -> predicate.type isnt 'all'
  _predicateCaption = lift$ _predicate, (predicate) ->
    switch predicate.type
      when 'all'
        'Showing\nall jobs'
      else
        throw new Error 'Invalid predicate type'

  displayItem = (item) ->
    if item
      _.displayJob item
    else
      _.displayEmpty()

  displayActiveItem = ->
    displayItem find _items(), (item) -> item.isActive()

  activateAndDisplayItem = (item) ->
    for other in _items()
      if other is item
        other.isActive yes
      else
        other.isActive no

    displayItem item

  isJobRunning = (job) ->
    job.progress < 1 or job.status is 'CREATED' or job.status is 'RUNNING'

  pollJobStatus = (item)->
    _.requestJob item.data.key.name, (error, job) ->
      if error
        # Do nothing
      else
        if job
          updateItem item, job
          if isJobRunning job
            delay pollJobStatus, 1000, item
          else
            item.isRunning no

  createJobCaption = (job) ->
    "#{job.status}: #{getProgressPercent job.progress} (#{formatTimeDuration job.msec})"

  updateItem = (item, job) ->
    item.caption createJobCaption job
    item.status job.status
    item.statusColor getStatusColor job.status
    item.progress getProgressPercent job.progress
    item.duration formatTimeDuration job.msec
    item.exception job.exception

  createItem = (job) ->
    self =
      data: job
      title: job.key.name
      caption: node$ null
      status: node$ null
      statusColor: node$ null
      progress: node$ null
      duration: node$ null
      exception: node$ null
      destinationKey: job.dest.name
      display: -> activateAndDisplayItem self
      isActive: node$ no
      isRunning: node$ isJobRunning job
      result: node$ null

    updateItem self, job

    delay pollJobStatus, 1000, self if self.isRunning()

    self
  
  displayJobs = (jobs) ->
    _items items = map jobs, createItem
    activateAndDisplayItem head items
    _.jobsLoaded()

  loadJobs = (predicate) ->
    console.assert isDefined predicate
    switch predicate.type
      when 'all'
        _.requestJobs (error, jobs) ->
          if error
            #TODO handle errors
            _.error 'Error requesting job list', null, error
          else
            displayJobs reverse jobs

    _predicate predicate
    return

  clearPredicate = -> loadJobs type: 'all'

  link$ _.loadJobs, (predicate) ->
    if predicate
      loadJobs predicate
    else
      displayActiveItem()

  link$ _.refreshJobs, -> loadJobs _predicate()

  items: _items
  predicateCaption: _predicateCaption
  clearPredicate: clearPredicate
  canClearPredicate: _canClearPredicate
  hasItems: _hasItems
  template: 'job-list-view'
