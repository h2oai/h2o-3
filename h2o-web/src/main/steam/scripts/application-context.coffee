Steam.ApplicationContext = ->
  context$

    ready: do edges$

    error: do edge$
    warn: do edge$
    info: do edge$
    fatal: do edge$
    notify: do edge$

    callout: do edge$
    tooltip: do edge$

    measureTextSpan: do edge$

    schedule: do edge$
    unschedule: do edge$
    timeout: do edge$

    man: do edge$
    help: do edge$
    status: do edge$

    inspect: do edge$

    route: do edge$
    setRoute: do edge$
    getRoute: do edge$
    onRouteSucceeded: do edge$
    onRouteFailed: do edge$

    loadDialog: do edge$
    unloadDialog: do edge$
    positionDialog: do edge$

    alert: do edge$
    confirm: do edge$
    fail: do edge$

    h2oGet: do edge$
    h2oPost: do edge$

    requestRemoveAll: do edge$
    requestLogAndEcho: do edge$
    requestFileGlob: do edge$
    requestImportFile: do edge$
    requestImportFiles: do edge$
    requestParseSetup: do edge$
    requestParseFiles: do edge$
    requestInspect: do edge$
    requestFrames: do edge$
    requestFramesAndCompatibleModels: do edge$
    requestFrame: do edge$
    requestFrameAndCompatibleModels: do edge$
    requestColumnSummary: do edge$
    requestModelBuilders: do edge$
    requestModelBuild: do edge$
    requestModelMetrics: do edge$
    requestScoringOnFrame: do edge$
    requestModels: do edge$
    requestModel: do edge$
    requestModelsAndCompatibleFrames: do edge$
    requestModelAndCompatibleFrames: do edge$
    requestJobs: do edge$
    requestJob: do edge$
    requestCloud: do edge$
    requestTimeline: do edge$

    # Local Storage
    putLocalObject: do edge$
    getLocalObject: do edge$
    getLocalObjects: do edge$
    deleteLocalObject: do edge$


    # Cache
    putIntoCache: do edge$
    getFromCache: do edge$
    removeFromCache: do edge$

    switchTopic: do edge$
    switchToFrames: do edge$
    switchToModels: do edge$
    switchToScoring: do edge$
    switchToNotifications: do edge$
    switchToJobs: do edge$

    displayEmpty: do edge$
    displayView: do edge$

    promptImportFiles: do edge$
    promptParseFiles: do edge$

    loadFrames: do edge$
    refreshFrames: do edge$
    framesLoaded: do edge$
    displayFrame: do edge$
    promptForFrame: do edge$
    activeFrameChanged: do edge$

    promptCreateModel: do edge$

    loadModels: do edge$
    refreshModels: do edge$
    displayModel: do edge$
    activeModelChanged: do edge$
    modelSelectionChanged: do edge$
    modelSelectionCleared: do edge$
    deselectAllModels: do edge$
    clearModelSelection: do edge$

    loadNotifications: do edge$
    displayNotification: do edge$

    loadScorings: do edge$
    displayScoring: do edge$
    scoringSelectionChanged: do edge$
    scoringSelectionCleared: do edge$
    scoringsSelected: do edge$
    scoringsDeselected: do edge$
    deselectAllScorings: do edge$
    clearScoringSelection: do edge$
    deleteScorings: do edge$
    deleteActiveScoring: do edge$
    scoringAvailable: do edge$
    rescore: do edge$
    configureStripPlot: do edge$

    loadJobs: do edge$
    displayJob: do edge$
    jobsLoaded: do edge$
    refreshJobs: do edge$
    
    loadAdmin: do edge$


