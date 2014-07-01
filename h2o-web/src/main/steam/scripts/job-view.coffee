Steam.JobView = (_, _jobItem) ->
  inspectJobTarget = (key, go) ->

  getJobResult = (go) ->
    unless jobResult = _jobItem.result()
      _.requestInspect _jobItem.destinationKey, (error, result) ->
        if error
          #TODO
          _.error 'Error inspecting job result', _jobItem.destinationKey, error
        else
          switch result.className 
            when 'water.fvec.Frame'
              jobResult = type: 'frame', key: _jobItem.destinationKey
              _jobItem.result jobResult
              go jobResult
    else
      go jobResult

  viewResult = ->
    getJobResult (jobResult) ->
      switch jobResult.type
        when 'frame'
          _.switchToFrames type: 'one', key: jobResult.key

  job: _jobItem
  viewResult: viewResult
  dispose: ->
  template: 'job-view'
