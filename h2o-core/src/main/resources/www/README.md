this time showing a categorical variable vs two numeric variables

predictive modeling example dataset showing different variables

--

a roomscale scatterplot targeting the [HTC Vive](https://en.wikipedia.org/wiki/HTC_Vive)

to enjoy this experience on the Vive, follow the instructions at [https://webvr.info/](https://webvr.info/) to download and install an experimental browser build that supports WebVR.  from there, click the `Enter VR` HMD icon on the bottom right corner of the browser window to enter the scene.  

for reference, this experience was developed on the `Aug 29 2016` version `55.0.2842.0` [build](https://drive.google.com/drive/u/1/folders/0BzudLt22BqGRQUExYzVoLU5VT2c) of Chromium with the flags `--enable-webvr` and `--enable-gamepad-extensions`

to explore the scene on a 2D screen, [open in a new tab](http://bl.ocks.org/micahstubbs/raw/bef97f728381aca3f803a585581e7dbd) and then hold the `S` key until the scatterplot and legend come into view.  from there you can navigate using the `W A S D` keys and look by clicking and dragging with the mouse

an iteration on the [#aframevr](https://twitter.com/search?q=%23aframevr) + [#d3js](https://twitter.com/search?q=%23d3js) [Iris Graph](http://bl.ocks.org/bryik/1a4d7eab9512400de3c03086f03016c8) from [@bryik_ws](https://twitter.com/bryik_ws)

featuring the famous [Iris Dataset](http://archive.ics.uci.edu/ml/datasets/Iris)

for more A-Frame + D3 experiments 
search for `aframe` on blockbuilder search
[http://blockbuilder.org/search#text=aframe](http://blockbuilder.org/search#text=aframe)

for earlier iterations of this example and a nice unified [commit history](https://github.com/micahstubbs/roomscale-scatter/commits/master), visit the [roomscale-scatter](https://github.com/micahstubbs/roomscale-scatter) repo on github

---

### Original `README.md`

---

This is a proof-of-concept 3D visualization of [Fisher's Iris data set](https://en.wikipedia.org/wiki/Iris_flower_data_set). There is a lot of room for improvement, but it's neat to "walk" around the plot with the **WASD keys** and highlight data points using the **mouse**. An HMD friendly variant could be created by swapping out the mouse-cursor component for a standard [gaze-cursor](https://aframe.io/docs/0.3.0/components/cursor.html) (for Google Cardboard) or [vive-cursor](https://github.com/bryik/aframe-vive-cursor-component) (for HTC Vive).

It was built using a customized version of the [aframe-scatter-component](aframe-scatter-component). The component source is hidden because the code is a bit long and gory, check the [Gist](https://gist.github.com/bryik/1a4d7eab9512400de3c03086f03016c8#file-hidden-aframe-scatter-component-js) if you must.

*Note*: click the display once before trying to move with WASD.
