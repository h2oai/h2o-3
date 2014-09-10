Steam.JobView = (_, _jobItem) ->
  getJobResult = (go) ->
    unless jobResult = _jobItem.result()
      _.requestInspect _jobItem.destinationKey, (error, result) ->
        if error
          #TODO
          _.error 'Error inspecting job result', _jobItem.destinationKey, error
        else
          jobResult = kind: result.kind, key: _jobItem.destinationKey
          _jobItem.result jobResult
          go jobResult
    else
      go jobResult

  viewResult = ->
    getJobResult (jobResult) ->
      switch jobResult.kind
        when 'frame'
          _.switchToFrames type: 'one', key: jobResult.key
        when 'model'
          _.switchToModels type: 'one', key: jobResult.key

  job: _jobItem
  viewResult: viewResult
  dispose: ->
  template: 'job-view'
