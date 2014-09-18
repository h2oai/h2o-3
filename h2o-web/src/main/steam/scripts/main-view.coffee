defaultStatusMessage = 'Ready.'
Steam.MainView = (_) ->
  _help = node$ null
  _helpHistory = []
  _helpHistoryIndex = -1
  _canNavigateHelpBack = node$ no
  _canNavigateHelpForward = node$ no
  _status = node$ defaultStatusMessage
  _listViews = do nodes$
  _selectionViews = do nodes$
  _pageViews = do nodes$
  _modalViews = do nodes$
  _modalDialogs = do nodes$
  _inspectorViews = nodes$ null
  _hasInspections = lift$ _inspectorViews, (inspections) -> inspections.length > 0
  _isInspectorHidden = node$ no
  _topic = node$ null

  _isDisplayingTopics = node$ no
  _hasModalView = lift$ _modalViews, (modalViews) -> modalViews.length > 0
  _hasModalDialog = lift$ _modalDialogs, (modalDialogs) -> modalDialogs.length > 0
  _isNavigatorMasked = lift$ _hasModalDialog, _hasModalView, (hasModalDialog, hasModalView) ->
    hasModalDialog or hasModalView
  _isListMasked = lift$ _hasModalDialog, identity
  _isViewMasked = lift$ _hasModalDialog, _isDisplayingTopics, (hasModalDialog, isDisplayingTopics) ->
    hasModalDialog or isDisplayingTopics

  _topicTitle = lift$ _topic, _isDisplayingTopics, (topic, isDisplayingTopics) ->
    if isDisplayingTopics then 'Menu' else if topic then topic.title else ''
  toggleTopics = -> _isDisplayingTopics not _isDisplayingTopics()
  toggleInspector = -> _isInspectorHidden not _isInspectorHidden()
  apply$ _isDisplayingTopics, (isDisplayingTopics) ->
    if isDisplayingTopics
      _listViews.push _topicListView
    else
      _listViews.remove _topicListView

  initialize = ->
    navigateHelpHome()

    #TODO do this through hash uris
    switchToFrames type: 'all'

  createTopic = (title, handle) ->
    self =
      title: title
      isDisabled: not isFunction handle
      display: -> handle() if handle

  switchTopic = (topic) ->
    switch topic
      when _frameTopic
        unless _topic() is topic
          _topic topic
          switchListView _frameListView
          switchSelectionView _frameSelectionView
      when _modelTopic
        unless _topic() is topic
          _topic topic
          switchListView _modelListView
          switchSelectionView _modelSelectionView
      when _scoringTopic
        unless _topic() is topic
          _topic topic
          switchListView _scoringListView
          switchSelectionView _scoringSelectionView
      when _notificationTopic
        unless _topic() is topic
          _topic topic
          switchListView _notificationListView
          switchSelectionView null
      when _jobTopic
        unless _topic() is topic
          _topic topic
          switchListView _jobListView
          switchSelectionView null
      when _adminTopic
        unless _topic() is topic
          _topic topic
          switchListView _adminListView
          switchSelectionView null
          
    _isDisplayingTopics no
    return
  
  switchToFrames = (predicate) ->
    switchTopic _frameTopic
    _.loadFrames predicate

  switchToModels = (predicate) ->
    switchTopic _modelTopic
    _.loadModels predicate

  switchToScoring = (predicate) ->
    switchTopic _scoringTopic
    _.loadScorings predicate

  switchToNotifications = (predicate) ->
    switchTopic _notificationTopic
    _.loadNotifications predicate

  switchToJobs = (predicate) ->
    switchTopic _jobTopic
    _.loadJobs if predicate then predicate else type: 'all'

  switchToAdmin = ->
    switchTopic _adminTopic
    _.loadAdmin()

  _topics = node$ [
    _frameTopic = createTopic 'Datasets', switchToFrames
    _modelTopic = createTopic 'Models', switchToModels
    _scoringTopic = createTopic 'Scoring', null #switchToScoring
    _timelineTopic = createTopic 'Timeline', null
    _notificationTopic = createTopic 'Notifications', switchToNotifications
    _jobTopic = createTopic 'Jobs', switchToJobs
    _adminTopic = createTopic 'Administration', switchToAdmin
  ]

  # List views
  _topicListView = Steam.TopicListView _, _topics
  _frameListView = Steam.FrameListView _
  _modelListView = Steam.ModelListView _
  #_scoringListView = Steam.ScoringListView _
  _notificationListView = Steam.NotificationListView _
  _jobListView = Steam.JobListView _
  _adminListView = Steam.AdminListView _

  # Selection views
  _frameSelectionView = Steam.FrameSelectionView _
  _modelSelectionView = Steam.ModelSelectionView _
  _scoringSelectionView = Steam.ScoringSelectionView _

  switchView = (views, view) ->
    for oldView in views()
      oldView.dispose() if isFunction oldView.dispose
    if view
      views [ view ]
    else
      views []

  switchListView = (view) -> switchView _listViews, view
  switchSelectionView = (view) -> switchView _selectionViews, view
  switchPageView = (view) -> switchView _pageViews, view
  switchModalView = (view) -> switchView _modalViews, view
  fixDialogPlacement = (element) -> _.positionDialog element
  refresh = ->
    switch _topic()
      when _frameTopic
        _.refreshFrames()
      when _modelTopic
        _.refreshModels()
      when _jobTopic
        _.refreshJobs()
    return



  #
  # Status bar
  #

  displayStatus = (message) ->
    if message
      _status message
      # Reset status bar after 7000ms
      _.timeout 'status', 7000, -> _.status null
    else
      _status defaultStatusMessage

  #
  # Inspection
  #
  inspect = (view) ->
    if view
      switchView _inspectorViews, view
    else
      # Clear the inspector
      # TODO put some default content here.
      switchView _inspectorViews, null
    return

  #
  # Help
  #

  navigateHelpHome = -> help 'home'

  navigateHelp = ->
    _help _.man _helpHistory[_helpHistoryIndex]
    _canNavigateHelpBack _helpHistoryIndex > 0
    _canNavigateHelpForward _helpHistoryIndex < _helpHistory.length - 1

  navigateHelpBack = ->
    if _helpHistoryIndex > 0
      _helpHistoryIndex--
      navigateHelp()

  navigateHelpForward = ->
    if _helpHistoryIndex < _helpHistory.length - 1
      _helpHistoryIndex++
      navigateHelp()

  help = (id) ->
    unless _helpHistory[_helpHistoryIndex] is id
      if _helpHistoryIndex < _helpHistory.length - 1
        # Chop off tail
        _helpHistory.length = _helpHistoryIndex + 1 
        _helpHistoryIndex = _helpHistory.length - 1
      if _helpHistory.length > 50
        # Chop off head
        _helpHistory.splice 0, _helpHistory.length - 50
        _helpHistoryIndex = _helpHistory.length - 1
      _helpHistory.push id
      navigateHelpForward()
 
  template = (view) -> view.template

  #
  # Links
  #

  link$ _.loadDialog, (dialog) ->
    _modalDialogs.push dialog

  link$ _.unloadDialog, (dialog) ->
    _modalDialogs.remove dialog

  link$ _.displayEmpty, ->
    switchPageView template: 'empty-view'

  link$ _.displayView, (view) ->
    switchPageView view

  link$ _.displayFrame, (frame) ->
    switchPageView Steam.FrameView _, frame if _topic() is _frameTopic

  link$ _.displayModel, (model) ->
    switchPageView Steam.ModelView _, model if _topic() is _modelTopic

  link$ _.displayScoring, (scoring) ->
    switchPageView Steam.ScoringView _, scoring if _topic() is _scoringTopic

  link$ _.displayNotification, (notification) ->
    switchPageView Steam.NotificationView _, notification if _topic() is _notificationTopic

  link$ _.displayJob, (job) ->
    switchPageView Steam.JobView _, job if _topic() is _jobTopic

  link$ _.switchToFrames, switchToFrames
  link$ _.switchToModels, switchToModels
  link$ _.switchToScoring, switchToScoring
  link$ _.switchToNotifications, switchToNotifications
  link$ _.switchToJobs, switchToJobs
  link$ _.inspect, inspect

  # Not in use. Leaving this here as an example of how a modal view can be displayed.
  # link$ _.modelsSelected, -> switchModalView _modelSelectionView
  # link$ _.modelsDeselected, -> _modalViews.remove _modelSelectionView

  link$ _.status, displayStatus
  link$ _.help, help


  do initialize

  topicTitle: _topicTitle
  toggleTopics: toggleTopics
  toggleInspector: toggleInspector
  listViews: _listViews
  selectionViews: _selectionViews
  pageViews: _pageViews
  modalViews: _modalViews
  modalDialogs: _modalDialogs
  hasModalView: _hasModalView
  hasModalDialog: _hasModalDialog
  isNavigatorMasked: _isNavigatorMasked
  isListMasked: _isListMasked
  isViewMasked: _isViewMasked
  isInspectorHidden: _isInspectorHidden
  inspectorViews: _inspectorViews
  hasInspections: _hasInspections
  refresh: refresh
  help: _help
  navigateHelpHome: navigateHelpHome
  canNavigateHelpBack: _canNavigateHelpBack
  navigateHelpBack: navigateHelpBack
  canNavigateHelpForward: _canNavigateHelpForward
  navigateHelpForward: navigateHelpForward
  status: _status
  fixDialogPlacement: fixDialogPlacement
  template: template

