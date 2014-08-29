
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

test.only 'empty cloud', (t) ->
  t.plan 4
  createCloud (_, done) ->
    _.requestFrames (error, result) ->
      if error
        t.fail 'request failed'
      else
        t.pass 'got frames reply'
        tdiff t, (readGoldJson 'frames-empty.json'), result

        _.requestJobs (error, result) ->
          if error
            t.fail 'request failed'
          else
            t.pass 'got jobs reply'
            tdiff t, (readGoldJson 'jobs-empty.json'), result
            t.end()
            done()

test 'airlines ingest and model building flow', (t) ->
  t.plan 28
  createCloud (_, done) ->
    # UI loaded.
    # empty clound shouldn't have any frames...
    _.requestFrames (error, result) ->
      if error
        t.fail 'frames request failed'
      else
        t.pass 'got frames reply'
        tdiff t, (readGoldJson 'frames-empty.json'), result

        # bring up the import dialog.
        
        #
        # glob for a non existent path...
        #
        _.requestFileGlob '/non/existent/path', 10, (error, result) ->
          if error
            t.fail 'glob request failed'
          else
            t.pass 'got glob reply'
            tdiff t, (readGoldJson 'glob-empty.json'), result

            #
            # glob for the airlines zip...
            #
            _.requestFileGlob (path.join DATA_PATH, 'airlines'), 10, (error, result) ->
              if error
                t.fail 'glob request failed'
              else
                t.pass 'got glob reply'
                expectedGlobResponse =
                  src: null
                  limit: 0
                tdiff t, expectedGlobResponse, result, exclude: [ 'matches' ]
                t.ok isArray result.matches, 'has matches'
                t.ok result.matches.length > 0, 'has matches'
                airlinesZip = find result.matches, (match) -> (/allyears2k_headers\.zip$/).test match
                t.ok airlinesZip isnt null, 'found airlines zip'

                #
                # import this file
                #
                _.requestImportFile airlinesZip, (error, result) ->
                  if error
                    t.fail 'import request failed'
                  else
                    t.pass 'got import reply'
                    expectedImportResponse =
                      dels: []
                      fails: []
                      path: airlinesZip
                      files: [ airlinesZip ]
                      keys: [ "nfs://#{airlinesZip.substr 1}" ]
                    tdiff t, expectedImportResponse, result

                    airlinesZipKey = result.keys[0]

                    #
                    # try setting up parse for this file...
                    #
                    _.requestParseSetup [ airlinesZipKey ], (error, result) ->
                      if error
                        t.fail 'parse setup request failed'
                      else
                        t.pass 'got parse setup reply'
                        tdiff t, (readGoldJson 'parse-setup-allyears2k_headers-zip.json'), result, exclude: [ 'srcs' ]
                        t.ok isArray result.srcs, 'has srcs'
                        t.equal result.srcs.length, 1, 'has 1 src'
                        t.ok isString result.srcs[0].name, 'has src name'

                        #
                        # submit a parse request...
                        #
                        sourceKeys = map result.srcs, (src) -> src.name
                        _.requestParseFiles sourceKeys, result.hexName, result.pType, result.sep, result.ncols, result.singleQuotes, result.columnNames, yes, result.checkHeader, (error, result) ->
                          if error
                            t.fail 'parse request failed'
                          else
                            t.pass 'got parse reply'
                            tdiff t, (readGoldJson 'parse-allyears2k_headers-zip.json'), result, exclude: [ 'job' ]
                            t.ok isString result.job.name, 'has job name'


                            #
                            # this job should be present in the job list...
                            #
                            jobKey = result.job.name
                            _.requestJobs (error, result) ->
                              if error
                                t.fail 'jobs request failed'
                              else
                                t.pass 'got jobs reply'
                                t.ok result.jobs.length is 1
                                t.equal result.jobs[0].key.name, jobKey

                                pollJob = (go) ->
                                  _.requestJob jobKey, (error, job) ->
                                    if error
                                      t.fail 'job poll failed'
                                    else
                                      if job.progress < 1 or job.status is 'CREATED' or job.status is 'RUNNING'
                                        delay pollJob, 1000, go
                                      else
                                        go job

                                #
                                # poll the only job till it's done...
                                #
                                pollJob (job) ->
                                  t.equal job.progress, 1, 'job progress ok'
                                  t.equal job.status, 'DONE', 'job status ok'

                                  # 
                                  # inspect dest key
                                  #
                                  
                                  frameKey = job.dest.name

                                  _.requestInspect frameKey, (error, result) ->
                                    if error
                                      t.fail 'inspect request failed'
                                    else
                                      t.pass 'got inspect reply'
                                      tdiff t, (readGoldJson 'inspect-allyears2k_headers-zip.json'), result
                                      
                                      #
                                      # get frame...
                                      #
                                      _.requestFrame frameKey, (error, result) ->
                                        if error
                                          t.fail 'frame request failed'
                                        else
                                          t.pass 'got frame reply'
                                          tdiff t, (readGoldJson 'frames-allyears2k_headers-zip.json'), result


                                          t.end()
                                          done()
        



