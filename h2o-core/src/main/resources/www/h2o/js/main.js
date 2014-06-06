// Initialize required javascript after page load
$(function () {
    $.fn.tooltip.defaults.animation = false;
    $('[rel=tooltip]').tooltip();
    $('[rel=popover]').popover();
});
