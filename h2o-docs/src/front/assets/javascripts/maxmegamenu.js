/*jslint browser: true, white: true */
/*global console,jQuery,megamenu,window,navigator*/

/**
 * Max Mega Menu jQuery Plugin
 */
(function($) {

    "use strict";

    $.maxmegamenu = function(menu, options) {

        var plugin = this;
        var $menu = $(menu);

        var defaults = {
            event: $menu.attr('data-event'),
            effect: $menu.attr('data-effect'),
            effect_speed: parseInt($menu.attr('data-effect-speed')),
            panel_width: $menu.attr('data-panel-width'),
            panel_inner_width: $menu.attr('data-panel-inner-width'),
            second_click: $menu.attr('data-second-click'),
            vertical_behaviour: $menu.attr('data-vertical-behaviour'),
            reverse_mobile_items: $menu.attr('data-reverse-mobile-items'),
            document_click: $menu.attr('data-document-click'),
            breakpoint: $menu.attr('data-breakpoint')
        };

        plugin.settings = {};

        var isTouchDevice = function() {
            return ('ontouchstart' in window || navigator.maxTouchPoints > 0 || navigator.msMaxTouchPoints > 0 || (window.DocumentTouch && document instanceof DocumentTouch));
        };

        plugin.hidePanel = function(anchor, immediate) {

            anchor.siblings('.mega-sub-menu').children('.mega-toggle-on').removeClass('mega-toggle-on');

            if (immediate) {
                anchor.siblings('.mega-sub-menu').removeClass('mega-toggle-on');
                anchor.parent().removeClass('mega-toggle-on').triggerHandler("close_panel");
                return;
            }

            var timeout = plugin.settings.effect_speed + parseInt(megamenu.timeout, 10);

            if ( megamenu.effect[plugin.settings.effect] ) {
                var effect = megamenu.effect[plugin.settings.effect]['out'];
                var speed = plugin.settings.effect_speed;

                if (effect.css) {
                    anchor.siblings('.mega-sub-menu').css(effect.css);
                }

                if (effect.animate) {

                    anchor.siblings('.mega-sub-menu').animate(effect.animate, speed, function() {
                        anchor.parent().removeClass('mega-toggle-on').triggerHandler("close_panel");
                    });
                    anchor.siblings('.mega-sub-menu').each( function() {
                        add_animating_class(anchor.parent());
                    });
                } else {
                    anchor.parent().removeClass('mega-toggle-on').triggerHandler("close_panel");
                    add_animating_class(anchor.parent());
                }
            } else {
                anchor.parent().removeClass('mega-toggle-on').triggerHandler("close_panel");
                add_animating_class(anchor.parent());
            }

        };


        var add_animating_class = function(element) {
            var timeout = plugin.settings.effect_speed + parseInt(megamenu.timeout, 10);

            element.addClass("mega-animating");

            setTimeout(function() {
               element.removeClass("mega-animating");
            }, timeout );
        }


        plugin.hideAllPanels = function() {
            $('.mega-toggle-on > a', $menu).each(function() {
                plugin.hidePanel($(this), false);
            });
        };


        plugin.hideSiblingPanels = function(anchor, immediate) {
            // jQuery 1.7.2 compatibility for themes/plugins that load old versions of jQuery
            if ( $.fn.addBack !== undefined ) {
                // all open children of open siblings
                anchor.parent().siblings().find('.mega-toggle-on').addBack().children('a').each(function() {
                    plugin.hidePanel($(this), immediate);
                });
            } else {
                // all open children of open siblings
                anchor.parent().siblings().find('.mega-toggle-on').andSelf().children('a').each(function() {
                    plugin.hidePanel($(this), immediate);
                });
            }
        }

        plugin.isDesktopView = function() {
            return $(window).width() > plugin.settings.breakpoint;
        }

        plugin.hideOpenSiblings = function() {
            // desktops, horizontal
            if ( plugin.isDesktopView() && ( $menu.hasClass('mega-menu-horizontal') || $menu.hasClass('mega-menu-vertical') ) ) {
                return 'immediately';
            }

            // mobile, keyboard navigation
            if ( ! plugin.isDesktopView() && $menu.hasClass('mega-keyboard-navigation') ) {
                return 'animated';
            }

            if ( plugin.settings.vertical_behaviour == 'accordion' ) {
                return 'animated';
            }
        }

        plugin.showPanel = function(anchor) {

            if ( !plugin.isDesktopView() && anchor.parent().hasClass('mega-hide-sub-menu-on-mobile') ) {
                return;
            }

            switch( plugin.hideOpenSiblings() ) {
                case 'immediately':
                    plugin.hideSiblingPanels(anchor, true);
                    break;
                case 'animated':
                    plugin.hideSiblingPanels(anchor, false);
                    break;
            }

            // apply dynamic width and sub menu position
            if ( anchor.parent().hasClass('mega-menu-megamenu') && $(plugin.settings.panel_width).length ) {
                var submenu_offset = $menu.offset();
                var target_offset = $(plugin.settings.panel_width).offset();

                anchor.siblings('.mega-sub-menu').css({
                    width: $(plugin.settings.panel_width).outerWidth(),
                    left: (target_offset.left - submenu_offset.left) + "px"
                });
            }


            // apply inner width to sub menu by adding padding to the left and right of the mega menu
            if ( anchor.parent().hasClass('mega-menu-megamenu') && plugin.settings.panel_inner_width && plugin.settings.panel_inner_width.length > 0 ) {

                if ( $(plugin.settings.panel_inner_width).length ) {
                    // jQuery selector
                    var target_width = parseInt($(plugin.settings.panel_inner_width).width(), 10);
                } else {
                    // we're using a pixel width
                    var target_width = parseInt(plugin.settings.panel_inner_width, 10);
                }

                var submenu_width = parseInt(anchor.siblings('.mega-sub-menu').innerWidth(), 10);

                if ( (target_width > 0) && (target_width < submenu_width) ) {
                    anchor.siblings('.mega-sub-menu').css({
                        'paddingLeft': (submenu_width - target_width) / 2 + 'px',
                        'paddingRight': (submenu_width - target_width) / 2 + 'px'
                    });
                }
            }


            if ( megamenu.effect[plugin.settings.effect] ) {
                var effect = megamenu.effect[plugin.settings.effect]['in'];
                var speed = plugin.settings.effect_speed;

                if (effect.css) {
                    anchor.siblings('.mega-sub-menu').css(effect.css);
                }

                if (effect.animate) {
                    anchor.siblings('.mega-sub-menu').animate(effect.animate, speed, 'swing', function() {
                        $(this).css('display', '');
                    });
                }
            }

            anchor.parent().addClass('mega-toggle-on').triggerHandler("open_panel");
        };

        var openOnClick = function() {
            // hide menu when clicked away from
            $(document).on('click touchstart', function(event) {
                if ( ( plugin.settings.document_click == 'collapse' || ! plugin.isDesktopView() ) && ! $(event.target).closest(".mega-menu li").length ) {
                    plugin.hideAllPanels();
                }
            });

            $('li.mega-menu-megamenu.mega-menu-item-has-children > a, li.mega-menu-flyout.mega-menu-item-has-children > a, li.mega-menu-flyout li.mega-menu-item-has-children > a', menu).on({
                click: function(e) {

                    // all clicks on parent items when sub menu is hidden on mobile
                    if ( ! plugin.isDesktopView() && $(this).parent().hasClass('mega-hide-sub-menu-on-mobile') ) {
                        return;
                    }

                    // check for second click
                    if ( plugin.settings.second_click == 'go' || $(this).parent().hasClass("mega-click-click-go") ) {
                        if ( ! $(this).parent().hasClass("mega-toggle-on") ) {
                            e.preventDefault();
                            plugin.showPanel($(this));
                        }
                    } else {
                        e.preventDefault();

                        if ( $(this).parent().hasClass("mega-toggle-on") ) {
                            plugin.hidePanel($(this), false);
                        } else {
                            plugin.showPanel($(this));
                        }
                    }
                }
            });
        };

        var openOnHover = function() {

            $('li.mega-menu-item-has-children', menu).not('li.mega-menu-megamenu li.mega-menu-item-has-children', menu).hoverIntent({
                over: function () {
                    plugin.showPanel($(this).children('a'));
                },
                out: function () {
                    if ($(this).hasClass("mega-toggle-on")) {
                        plugin.hidePanel($(this).children('a'), false);
                    }
                },
                timeout: megamenu.timeout,
                interval: megamenu.interval
            });

        };

        plugin.check_width = function() {

            if ( plugin.settings.reverse_mobile_items == 'true' ) {

                if ( $(window).width() <= plugin.settings.breakpoint && $menu.data('view') == 'desktop' ) {
                    $menu.data('view', 'mobile');
                    $menu.append($menu.children('li.mega-item-align-right').get().reverse());
                }

                if ( $(window).width() >= plugin.settings.breakpoint && $menu.data('view') == 'mobile' ) {
                    $menu.data('view', 'desktop');
                    $menu.append($menu.children('li.mega-item-align-right').get().reverse());
                }

            }

        }

        plugin.keyboard_navigation = function() {
            var tab_key = 9;
            var escape_key = 27;

            $('body').on('keyup', function(e) {
                var keyCode = e.keyCode || e.which;

                if (keyCode == escape_key) {
                    $menu.removeClass('mega-keyboard-navigation');
                    plugin.hideAllPanels();
                }

                if ( $menu.hasClass('mega-keyboard-navigation') && ! $(event.target).closest(".mega-menu li").length ) {
                    $menu.removeClass('mega-keyboard-navigation');
                    plugin.hideAllPanels();
                }
            });

            $menu.parent().on('keyup', function(e) {
                var keyCode = e.keyCode || e.which;
                var active_link = $(e.target);

                if (keyCode == tab_key) {
                    $menu.addClass('mega-keyboard-navigation');

                    if ( active_link.parent().hasClass('mega-menu-item-has-children') ) {
                        // menu item with sub menu
                        plugin.showPanel(active_link);
                    } else if ( active_link.parent().parent().hasClass('mega-menu') ) {
                        // top level item with no children
                        plugin.hideAllPanels();
                    }

                    if ( active_link.hasClass('mega-menu-toggle') ) {
                        active_link.toggleClass('mega-menu-open');
                    }
                }
            });
        }

        plugin.init = function() {
            plugin.settings = $.extend({}, defaults, options);

            $menu.removeClass('mega-no-js');

            $menu.siblings('.mega-menu-toggle').on('click', function(e) {
                if ( $(e.target).is('.mega-menu-toggle-block, .mega-menu-toggle') ) {
                    $(this).toggleClass('mega-menu-open');
                }
            });

            $('li.mega-menu-item, ul.mega-sub-menu', menu).unbind();

            if (isTouchDevice() || plugin.settings.event === 'click') {
                openOnClick();
            } else {
                openOnHover();
            }

            if ( plugin.isDesktopView() ) {
                $menu.data('view', 'desktop');
            } else {
                $menu.data('view', 'mobile');
            }

            plugin.check_width();

            $(window).resize(function() {
                plugin.check_width();
            });

            plugin.keyboard_navigation();

        };

        plugin.init();

    };

    $.fn.maxmegamenu = function(options) {
        return this.each(function() {
            if (undefined === $(this).data('maxmegamenu')) {
                var plugin = new $.maxmegamenu(this, options);
                $(this).data('maxmegamenu', plugin);
            }
        });
    };


    $(function() {
        $(".mega-menu").maxmegamenu();
    });


})(jQuery);