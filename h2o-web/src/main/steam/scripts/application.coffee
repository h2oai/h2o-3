Steam.Application = (_) ->

  unless exports?
    Steam.ErrorMonitor _
    Steam.TextMetrics _
    Steam.CalloutManager _
    Steam.TooltipManager _

  Steam.Timers _
  Steam.EventLog _
  Steam.Cache _
  Steam.LocalStorage _
  Steam.Xhr _
  Steam.H2OProxy _
  Steam.HelpServer _

  #update URL fragment generating new history record
  #_.route 'home'
  
  _view = Steam.MainView _
  Steam.Router _, Steam.Routes _
  Steam.DialogManager _

  context: _
  view: _view

