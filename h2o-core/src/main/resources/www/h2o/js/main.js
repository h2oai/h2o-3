// Initialize required javascript after page load
$(function () {
    // Redirect to Flow by default.
    if (window.location.pathname === '/') {
      return window.location.replace(window.location.protocol + '//' + window.location.host + '/flow/index.html');
    } else {
      $.fn.tooltip.defaults.animation = false;
      $('[rel=tooltip]').tooltip();
      $('[rel=popover]').popover();
    }
});
