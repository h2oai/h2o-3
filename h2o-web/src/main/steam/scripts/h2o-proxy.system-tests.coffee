
###
t_h2o_response = T
  h2o_response:
    h2o: T.str
    node: T.str
    status: T.str
    time: T.num

t_h2o_frame = T
  h2o_frame:
    id: T.str
    key: T.str
    column_names: T.arr T.str
    #TODO handle recursive references in typedef
    #compatible_models: T.arr t_h2o_model
    creation_epoch_time_millis: T.num
    is_raw_frame: T.bool

t_h2o_frames_response = T
  h2o_frames_response:
    response: t_h2o_response
    frames: T.arr t_h2o_frame

t_h2o_model = T
  h2o_model:
    id: T.str
    key: T.str
    model_algorithm: T.str
    model_category: T.str
    state: T.str
    input_column_names: T.arr T.str
    response_column_name: T.str
    critical_parameters: T.obj
    secondary_parameters: T.obj
    expert_parameters: T.obj
    #TODO handle recursive references in typedef
    #compatible_frames: T.arr t_h2o_frame
    creation_epoch_time_millis: T.num

t_h2o_models_response = T
  h2o_models_response:
    response: t_h2o_response
    models: T.arr t_h2o_model

typecheckFramesResult = (t, error, result) ->
  t.equal error, null
  { response, frames } = result
  t.equal response.status, 'done'
  t.ok frames.length > 0
  t.ok typecheck result, t_h2o_frames_response
  t.ok every frames, (frame) ->
    #TODO handle recursive references in typedef
    for compatibleModel in frame.compatible_models
      t.ok typecheck compatibleModel, t_h2o_model
  return

typecheckModelsResult = (t, error, result) ->
  t.equal error, null
  { response, models } = result
  t.equal response.status, 'done'
  t.ok models.length > 0
  t.ok typecheck result, t_h2o_models_response
  t.ok every models, (model) ->
    #TODO handle recursive references in typedef
    for compatibleFrame in model.compatible_frames
      t.ok typecheck compatibleFrame, t_h2o_frame
  return

test 'requestFrames', (t) ->
  _ = Steam.ApplicationContext()
  Steam.Xhr _
  Steam.H2OProxy _
  t.plan 6
  _.requestFrames (error, result) ->
    typecheckFramesResult t, error, result
    { frames } = result
    t.ok every frames, (frame) -> frame.compatible_models.length is 0
    t.end()

test 'requestFrame', (t) ->
  _ = Steam.ApplicationContext()
  Steam.Xhr _
  Steam.H2OProxy _
  t.plan 8
  _.requestFrame 'airlines_train.hex', (error, result) ->
    typecheckFramesResult t, error, result
    { frames } = result
    t.equal frames.length, 1
    frame0 = head frames
    t.ok frame0.key, 'airlines_train.hex'
    t.equal frame0.compatible_models.length, 0
    t.end()

test 'requestFramesAndCompatibleModels', (t) ->
  _ = Steam.ApplicationContext()
  Steam.Xhr _
  Steam.H2OProxy _
  t.plan 23
  _.requestFramesAndCompatibleModels (error, result) ->
    typecheckFramesResult t, error, result
    { frames } = result
    t.ok frames.length > 0
    t.ok every frames, (frame) -> frame.compatible_models.length > 0
    t.end()

test 'requestFrameAndCompatibleModels', (t) ->
  _ = Steam.ApplicationContext()
  Steam.Xhr _
  Steam.H2OProxy _
  t.plan 15
  _.requestFrameAndCompatibleModels 'airlines_train.hex', (error, result) ->
    typecheckFramesResult t, error, result
    { response, frames } = result
    t.equal frames.length, 1
    frame0 = head frames
    t.ok frame0.key, 'airlines_train.hex'
    t.ok frame0.compatible_models.length > 0
    t.end()

test 'requestModels', (t) ->
  _ = Steam.ApplicationContext()
  Steam.Xhr _
  Steam.H2OProxy _
  t.plan 7
  _.requestModels (error, result) ->
    typecheckModelsResult t, error, result
    { models } = result
    t.ok models.length > 0
    t.ok every models, (model) -> model.compatible_frames.length is 0
    t.end()

test 'requestModel', (t) ->
  _ = Steam.ApplicationContext()
  Steam.Xhr _
  Steam.H2OProxy _
  t.plan 8
  _.requestFrameAndCompatibleModels 'airlines_train.hex', (error, result) ->
    { response, frames } = result
    frame0 = head frames
    model0 = head frame0.compatible_models
    modelKey = model0.key
    _.requestModel modelKey, (error, result) ->
      typecheckModelsResult t, error, result
      { response, models } = result
      t.equal models.length, 1
      model = head models
      t.equal model.key, modelKey
      t.equal model.compatible_frames.length, 0 
      t.end()

test 'requestModelsAndCompatibleFrames', (t) ->
  _ = Steam.ApplicationContext()
  Steam.Xhr _
  Steam.H2OProxy _
  t.plan 23
  _.requestModelsAndCompatibleFrames (error, result) ->
    typecheckModelsResult t, error, result
    { models } = result
    t.ok models.length > 0
    t.ok every models, (model) -> model.compatible_frames.length > 0
    t.end()

test 'requestModelAndCompatibleFrames', (t) ->
  _ = Steam.ApplicationContext()
  Steam.Xhr _
  Steam.H2OProxy _
  t.plan 10
  _.requestFrameAndCompatibleModels 'airlines_train.hex', (error, result) ->
    { response, frames } = result
    frame0 = head frames
    model0 = head frame0.compatible_models
    modelKey = model0.key
    _.requestModelAndCompatibleFrames modelKey, (error, result) ->
      typecheckModelsResult t, error, result
      { response, models } = result
      t.equal models.length, 1
      model = head models
      t.equal model.key, modelKey
      t.ok model.compatible_frames.length > 0
      t.end()
###

test 'empty cloud', (t) ->
  t.plan 4
  createCloud (_, done) ->
    _.requestFrames (error, frames) ->
      if error
        t.fail 'request failed'
      else
        t.ok isArray frames
        t.equal frames.length, 0

        _.requestJobs (error, jobs) ->
          if error
            t.fail 'request failed'
          else
            t.ok isArray jobs
            t.equal jobs.length, 0

            t.end()
            done()

test 'airlines ingest and model building flow', (t) ->
  t.plan 0
  createCloud (_, done) ->
    # no frames exist
    _.requestFrames (error, frames) ->
      if error
        t.fail 'request failed'
      else
        t.ok isArray frames
        t.equal frames.length, 0

        # import files dialog
        
        # searching for a file pattern
        _.requestFileGlob './smalldata/airlines', (error, result) ->
          if error
            t.fail 'request failed'
          else
            t.ok isArray result.matches
            t.equal result.matches.length, 1
            t.equal result.matches[0], './smalldata/airlines/allyears2k_headers.zip'
        



