gulp = require 'gulp'
clean = require 'gulp-clean'
concat = require 'gulp-concat'
header = require 'gulp-header'
footer = require 'gulp-footer'
gutil = require 'gulp-util'
help = require 'gulp-task-listing'
coffee = require 'gulp-coffee'
jade = require 'gulp-jade'
stylus = require 'gulp-stylus'
nib = require 'nib'

config =
  dir:
    deploy: 'src/main/resources/www/steam'
  lib:
    js: [
      'lib/stacktrace/stacktrace.js'
      'lib/jquery/dist/jquery.js'
      'lib/lodash/dist/lodash.js'
      'lib/momentjs/min/moment.min.js'
      'lib/typeahead.js/dist/typeahead.jquery.min.js'
      'lib/js-signals/dist/signals.js'
      'lib/crossroads/dist/crossroads.js'
      'lib/hasher/dist/js/hasher.js'
      'lib/bootstrap/dist/js/bootstrap.js'
      'lib/d3/d3.js'
      'lib/knockout/knockout.js'
    ]
    css: [
      'lib/fontawesome/css/font-awesome.css'
      'lib/bootstrap/dist/css/bootstrap.css'
      'lib/bootstrap/dist/css/bootstrap.css.map'
    ]
    fonts: [
      'src/main/steam/fonts/*.*'
      'lib/bootstrap/dist/fonts/*.*'
      'lib/fontawesome/fonts/*.*'
    ]
    img: [
      'src/main/steam/images/*.*'
    ]

gulp.task 'build-scripts', ->
  gulp.src [ 'src/main/steam/scripts/*.coffee', '!src/main/steam/scripts/*.global.coffee' ]
    .pipe coffee bare: no
    .on 'error', gutil.log
    .pipe gulp.dest 'build/scripts/'

gulp.task 'build-global-scripts', ->
  gulp.src [ 'src/main/steam/scripts/*.global.coffee' ]
    .pipe coffee bare: yes
    .on 'error', gutil.log
    .pipe gulp.dest 'build/scripts/'

gulp.task 'build-browser-script', [ 'build-scripts', 'build-global-scripts' ], ->
  gulp.src [ 'build/scripts/prelude.global.js', 'build/scripts/*.global.js', 'build/scripts/*.js' ]
    .pipe concat 'steam.js'
    .pipe header 'use strict;(function(){'
    .pipe footer '}).call(this);'
    .pipe gulp.dest config.dir.deploy + '/js/'

gulp.task 'build-templates', ->
  gulp.src 'src/main/steam/templates/index.jade'
    .pipe jade { pretty: yes }
    .pipe gulp.dest config.dir.deploy

gulp.task 'build-styles', ->
  gulp.src 'src/main/steam/styles/steam.styl'
    .pipe stylus use: [ nib() ]
    .pipe gulp.dest config.dir.deploy + '/css/'

gulp.task 'compile-browser-assets', ->
  gulp.src config.lib.js
    .pipe concat 'lib.js'
    .pipe gulp.dest config.dir.deploy + '/js/'

  gulp.src config.lib.img
    .pipe gulp.dest config.dir.deploy + '/img/'

  gulp.src config.lib.fonts
    .pipe gulp.dest config.dir.deploy + '/fonts/'

  gulp.src config.lib.css
    .pipe gulp.dest config.dir.deploy + '/css/'

gulp.task 'watch', ->
  gulp.watch 'src/main/steam/scripts/*.coffee', [ 'build-browser-script' ]
  gulp.watch 'src/main/steam/templates/*.jade', [ 'build-templates' ]
  gulp.watch 'src/main/steam/styles/*.styl', [ 'build-styles' ]

gulp.task 'clean', ->
  gulp.src 'build/scripts/', read: no
    .pipe clean()

  gulp.src config.dir.deploy, read: no
    .pipe clean()

gulp.task 'build', [ 
  'compile-browser-assets'
  'build-browser-script'
  'build-templates'
  'build-styles'
]

gulp.task 'default', [ 'build' ]
