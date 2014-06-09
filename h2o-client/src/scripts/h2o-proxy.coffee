Steam.H2OProxy = (_) ->

  composePath = (path, opts) ->
    if opts
      params = mapWithKey opts, (v, k) -> "#{k}=#{v}"
      path + '?' + join params, '&'
    else
      path

  request = (path, opts, go) ->
    _.invokeH2O 'GET', (composePath path, opts), (error, result) ->
      if error
        #TODO error logging / retries, etc.
        go error, result
      else
        if result.data.response?.status is 'error'
          go result.data.error, result.data
        else
          go error, result.data


  requestImportFiles = (filePath, go) ->
    opts = path: encodeURIComponent filePath
    request '/ImportFiles.json', opts, go

  requestParseFiles = (src, hex, deleteOnDone, go) ->
    opts =
      hex: encodeURIComponent hex
      srcs: "[#{encodeURIComponent src}]"
      # delete_on_done: deleteOnDone # TODO failing with "Attempting to set output field delete_on_done"
    request '/Parse.json', opts, go

  filterOutUnhandledModels = (models) -> filter models, (model) -> model.state is 'DONE' and model.model_category is 'Binomial'

  requestFrames = (go, opts) ->
    request '/2/Frames.json', opts, (error, result) ->
      if error
        go error, result
      else
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

        go error,
          response: response
          frames: values frames
          metrics: metrics

  requestModels = (go, opts) ->
    request '/2/Models.json', opts, (error, result) ->
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

  link$ _.requestImportFiles, requestImportFiles
  link$ _.requestParseFiles, requestParseFiles
  link$ _.requestFrames, (go) -> requestFrames go
  link$ _.requestFramesAndCompatibleModels, (go) -> requestFrames go, find_compatible_models: yes
  link$ _.requestFrame, (key, go) -> requestFrames go, key: (encodeURIComponent key)
  link$ _.requestFrameAndCompatibleModels, (key, go) -> requestFrames go, key: (encodeURIComponent key), find_compatible_models: yes
  #TODO test
  link$ _.requestScoringOnFrame, (frameKey, modelKey, go) -> requestFrames go, key: (encodeURIComponent frameKey), score_model: modelKey
  link$ _.requestModels, (go) -> requestModels go
  link$ _.requestModelsAndCompatibleFrames, (go) -> requestModels go, find_compatible_frames: yes
  link$ _.requestModel, (key, go) -> requestModels go, key: (encodeURIComponent key)
  link$ _.requestModelAndCompatibleFrames, (key, go) -> requestModels go, key: (encodeURIComponent key), find_compatible_frames: yes



