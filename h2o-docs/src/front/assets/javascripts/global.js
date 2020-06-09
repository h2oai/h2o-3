// Use $ instead of jQuery without replacing global $
(function ($) {
  // On DOM ready
//  $(function () {
//      $('.footer .col-four-outer .col-four').matchHeight();
//  });
//    $(window).on("resize",function () {
//        $('.footer .col-four-outer .col-four').matchHeight();
//    });
})(jQuery);


// Use $ instead of $ without replacing global $
(function ($) {
    var counter= 0;
    // Detect touch device
    function touchDetect() {
        if ($('html').hasClass('ua-mobile'))
            $('body').addClass('touch').removeClass('no-touch');
        else
            $('body').addClass('no-touch').removeClass('touch');
    }
    // Add Classes on Nav above 991 on click
    function navOnTouch($th) {
        if ($th.hasClass('nav-active')) {
            $th.removeClass('nav-active');
        } else {
            $('.h2o header .nav-wrapper > div > ul > li').removeClass('nav-active');
            $th.addClass('nav-active');
        }
    }
    function copyTopNav() {
        var copy = $('.h2o header .nav-wrapper .top-nav > ul').html();
        $(copy).insertBefore(".h2o header .nav-wrapper .main-nav .search-responsive");
    }

    function innerNav() {
        $('.h2o header .nav-wrapper > div > ul > li').each(function () {
            var length = $(this).children('ul').length,
                newLength = $(this).children('.sub-nav').length;
            if (length > 0 || newLength > 0) {
                $(this).addClass('inner-nav');
            } else {
                $(this).removeClass('inner-nav');
            }
        });
    }

    function toggleInnerNav($th) {
        if ($th.hasClass('sub-active')) {
            $th.removeClass('sub-active');
            $th.children('ul').stop(true, true).slideUp();
            $th.children('.sub-nav').stop(true, true).slideUp();
        } else {
            $('.h2o header .nav-wrapper > div > ul > li').removeClass('sub-active');
            $th.addClass('sub-active');
            $('.h2o header .nav-wrapper > div > ul > li > ul').stop(true, true).slideUp();
            $('.h2o header .nav-wrapper > div > ul > li > .sub-nav').stop(true, true).slideUp();
            $th.children('ul').stop(true, true).slideDown();
            $th.children('.sub-nav').stop(true, true).slideDown();
        }
    }

  // On DOM ready
  $(function () {
    // Detect Touch Device
    var windowWidth = $(window).width();
    touchDetect();
    innerNav();
    var url = window.location.href,
        params = url.split('/');
          $('.h2o header .nav-wrapper > div > ul > li').each(function(){
              var href = $(this).children('a').attr('href');
              if(href === '/'+params[3]){
                  $(this).children('a').addClass('url-active');         
              }
          });
          
    if ((window.matchMedia("(max-width: 991px)").matches)) {
      if(counter==0){
        copyTopNav();
        counter = 1;
      }
    }

    // Add Class main nav has inner nav

    $('.h2o header .hamburger').click(function () {
      var windowheight = $(window).height() - parseInt($('.h2o header nav').css('top'));
      if ($(this).hasClass('active')) {
        $('.h2o header .nav-wrapper > div > ul > li').removeClass('sub-active');
        $('.h2o header .nav-wrapper > div > ul > li > ul').stop(true, true).slideUp();
        $('.h2o header .nav-wrapper > div > ul > li > .sub-nav').stop(true, true).slideUp();
        $(this).parent().find('nav').removeClass('animate');
        $(this).removeClass('active');
        $('.h2o header nav').css('height', '');
        $('body').css('overflow', '');
      } else {
        $(this).addClass('active');
        $('.h2o header nav').css('height', windowheight + 'px');
        $(this).parent().find('nav').addClass('animate');
        $('body').css('overflow', 'hidden');
      }
    });
    
    $('.h2o header .nav-wrapper > div > ul > li.inner-nav').click(function (e) {
      e.stopPropagation();
      var $this = $(this);
      if ($('body').hasClass('touch') && (window.matchMedia("(min-width: 992px)").matches)) {
        navOnTouch($this);
      } else if (window.matchMedia("(max-width: 991px)").matches) {
        toggleInnerNav($this);
      }
    });
    
    $('.h2o header .nav-wrapper > div > ul > li.inner-nav > a').click(function (e) {
      var windowWidth = $(window).width();
      if ($('body').hasClass('touch') || (window.matchMedia("(max-width: 991px)").matches)) {
        e.preventDefault();
      }
    });
    
    $(document).click(function () {
      $('.h2o header .nav-wrapper > div > ul > li').removeClass('nav-active');
    });


    
    $('.search-button').click(function (e) {
      if ((window.matchMedia("(min-width: 992px)").matches)) {
        e.stopPropagation();
        $('body').addClass('search-open');
      }
    });
    
    $('header .search-div form').click(function(e){
      e.stopPropagation();
    });
    
    $('.close-icon').click(function (e) {
      $('body').removeClass('search-open');
    });
    
    $(document).click(function(){
      $('body').removeClass('search-open');
    });
    // Add Class on Scroll

            
    if (scrolled >= bannerHeight) {
      $(".h2o header").addClass("fixed");
      isNavSticky = false;
    }

  });
    var scroll = $(document).scrollTop(),
        bannerHeight = ($('.h2o header').height() + parseInt($('.h2o header').css('top'))) / 2,
        scrolled = $(document).scrollTop(),
        isNavSticky = true;
    $(window).on('scroll load', function () {
        var scrolled = jQuery(document).scrollTop();
        if ((scrolled > scroll)) {
            if (scrolled >= bannerHeight && isNavSticky === true) {
                $(".h2o header").addClass("fixed");
                isNavSticky = false;
            }
            $(".h2o header").removeClass("off-canvas");
        } else if ((scrolled < scroll)) {
            if (scrolled > bannerHeight && isNavSticky === false) {
                $(".h2o header.fixed").addClass("off-canvas");
                isNavSticky = true;
            } else if (scrolled < bannerHeight && isNavSticky === true) {
                $(".h2o header").removeClass("off-canvas");
                $(".h2o header").removeClass("fixed");
                isNavSticky = false;
            }
            isNavSticky = true;
        }
        scroll = $(document).scrollTop();
    });
    
    $(window).on("resize",function () {
        touchDetect();
        if ((window.matchMedia("(max-width: 991px)").matches)) {
            if(counter==0){
                copyTopNav();
                counter = 1;
            }
        } else {
            counter = 0;
            $(".h2o header .nav-wrapper .main-nav > ul > li ").remove(".top-list");
        }
        if (window.matchMedia("(min-width: 992px)").matches) {
            $('.h2o header .nav-wrapper > div > ul > li > ul').removeAttr('style');
            $('.h2o header .nav-wrapper > div > ul > li > .sub-nav').removeAttr('style');
            $('.h2o header .hamburger').removeClass('active');
            $('.h2o header nav').removeClass('animate');
            $('.h2o header .nav-wrapper > div > ul > li').removeClass('sub-active');
            $('.h2o header nav').css('height', '');
            $('body').css('overflow', '');
        } else {
            $('body').removeClass('search-open');
            if ($('.h2o header .hamburger').hasClass('active')) {
                var windowheight = $(window).height() - parseInt($('.h2o header nav').css('top'));
                $('.h2o header nav').css('height', windowheight + 'px');
            } else {
                $('.h2o header nav').css('height', '');
            }
        }
    });
})(jQuery);


