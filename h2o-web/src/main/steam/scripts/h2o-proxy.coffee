Steam.H2OProxy = (_) ->
  request = (path, go) ->
    _.invokeH2O 'GET', path, (error, result) ->
      if error
        #TODO error logging / retries, etc.
        go error, result
      else
        if result.data.response?.status is 'error'
          go result.data.error, result.data
        else
          go error, result.data

  composePath = (path, opts) ->
    if opts
      params = mapWithKey opts, (v, k) -> "#{k}=#{v}"
      path + '?' + join params, '&'
    else
      path

  requestWithOpts = (path, opts, go) ->
    request (composePath path, opts), go

  requestTypeaheadFiles = (path, limit, go) ->
    opts =
      src: encodeURIComponent path
      limit: limit
    requestWithOpts '/Typeahead.json/files', opts, go

  requestImportFile = (path, go) ->
    opts = path: encodeURIComponent path
    requestWithOpts '/ImportFiles.json', opts, go

  requestImportFiles = (paths, go) ->
    actions = map paths, (path) ->
      (index, go) ->
        requestImportFile path, (error, result) ->
          go error: error, result: result
    forEachAsync actions, go

  requestParseSetup = (sources, go) ->
    encodedPaths = map sources, encodeURIComponent
    opts =
      srcs: "[#{join encodedPaths, ','}]"
    requestWithOpts '/ParseSetup.json', opts, go

  encodeArray = (array) -> "[#{join (map array, encodeURIComponent), ','}]"

  requestParseFiles = (sourceKeys, destinationKey, parserType, separator, columnCount, useSingleQuotes, columnNames, deleteOnDone, checkHeader, go) ->
    opts =
      hex: encodeURIComponent destinationKey
      srcs: encodeArray sourceKeys
      pType: parserType
      sep: separator
      ncols: columnCount
      singleQuotes: useSingleQuotes
      columnNames: encodeArray columnNames
      checkHeader: checkHeader
      delete_on_done: deleteOnDone
    requestWithOpts '/Parse.json', opts, go

  requestInspect = (key, go) ->
    opts = key: encodeURIComponent key
    requestWithOpts '/Inspect.json', opts, go

  filterOutUnhandledModels = (models) -> filter models, (model) -> model.state is 'DONE' and model.model_category is 'Binomial'

  requestFrames = (go, opts) ->
    requestWithOpts '/3/Frames.json', opts, (error, result) ->
      if error
        go error, result
      else
        ###
        # Flatten response so that keys are attributes on the objects, 
        # and linked objects are direct refs instead of keys.
        { frames, models, metrics, response } = result
        for modelKey, model of models
          model.key = modelKey

        for frameKey, frame of frames
          frame.key = frameKey
          #TODO remove 'filterOutUnhandledModels' when issue with non-DONE models is resolved.
          frame.compatible_models = filterOutUnhandledModels map frame.compatible_models, (modelKey) ->
            models[modelKey]
        ###

        go error, result.frames

  requestModels = (go, opts) ->
    requestWithOpts '/2/Models.json', opts, (error, result) ->
      if error
        go error, result
      else
        # Flatten response so that keys are attributes on the objects, 
        # and linked objects are direct refs instead of keys.
        { frames, models, response } = result
        for frameKey, frame of frames
          frame.key = frameKey

        for modelKey, model of models
          model.key = modelKey
          model.compatible_frames = map model.compatible_frames, (frameKey) ->
            frames[frameKey]

        #TODO remove 'filterOutUnhandledModels' when issue with non-DONE models is resolved.
        go error, response: response, models: filterOutUnhandledModels values models

  requestJobs = (go) ->
    request '/Jobs.json', (error, result) ->
      if error
        go error, result
      else
        go error, result.jobs

  requestJob = (key, go) ->
    #opts = key: encodeURIComponent key
    #requestWithOpts '/Job.json', opts, go
    request "/Jobs.json/#{encodeURIComponent key}", (error, result) ->
      if error
        go error, result
      else
        go error, result.jobs[0]

  requestCloud = (go) ->
    request '/Cloud.json', go

  requestTimeline = (go) ->
    request '/Timeline.json', go

  link$ _.requestTypeaheadFiles, requestTypeaheadFiles
  link$ _.requestImportFiles, requestImportFiles
  link$ _.requestParseSetup, requestParseSetup
  link$ _.requestParseFiles, requestParseFiles
  link$ _.requestInspect, requestInspect
  link$ _.requestFrames, (go) -> requestFrames go
  link$ _.requestFramesAndCompatibleModels, (go) -> requestFrames go, find_compatible_models: yes
  link$ _.requestFrame, (key, go) ->
    request "/3/Frames/#{encodeURIComponent key}", (error, result) -> go error, result.frames
  link$ _.requestColumnSummary, (key, column, go) ->
    request "/3/Frames/#{encodeURIComponent key}/columns/#{column}/summary", go
  link$ _.requestFrameAndCompatibleModels, (key, go) -> requestFrames go, key: (encodeURIComponent key), find_compatible_models: yes
  #TODO test
  link$ _.requestScoringOnFrame, (frameKey, modelKey, go) -> requestFrames go, key: (encodeURIComponent frameKey), score_model: modelKey
  link$ _.requestModels, (go) -> requestModels go
  link$ _.requestModelsAndCompatibleFrames, (go) -> requestModels go, find_compatible_frames: yes
  link$ _.requestModel, (key, go) -> requestModels go, key: (encodeURIComponent key)
  link$ _.requestModelAndCompatibleFrames, (key, go) -> requestModels go, key: (encodeURIComponent key), find_compatible_frames: yes
  link$ _.requestJobs, requestJobs
  link$ _.requestJob, requestJob
  link$ _.requestCloud, requestCloud
  link$ _.requestTimeline, requestTimeline


