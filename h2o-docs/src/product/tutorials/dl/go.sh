#!/bin/bash

TARGET=2015_08_deep-learning-performance
WEB_REPO=~/0xdata.com

awk '/INSERT1/{while((getline line < $2) > 0 ){print line}close($2);next}1' dlperf.md.template > dlperf.md

mkdir -p dlperf
rm -rf dlperf/*json
rm -rf dlperf/*csv
rm -f PerformanceH2ODeepLearning.plots

##################################
## Performance Plots
##################################
for i in network_topology scoring_overhead adaptive_rate train_samples_per_iteration activation_function large_deep_net
do
    cp -f /tmp/$i.csv dlperf/

    cat << EOF > dlperf/$i.json
    {
      "name": "$i",
      "format": "csv",
      "location": "/blog/$TARGET/$i.csv",
      "header": true,
      "schema": {
        "Parameters": "string",
        "Training Samples": "int",
        "Training Time": "real",
        "Training Speed": "real",
        "Test Set Error": "real"
      }
    }
EOF

    cat << EOF >> PerformanceH2ODeepLearning.plots
  plot(
    plot.rect(
      plot.position('Training Speed', plot.factor('Training Time')),
      plot.tooltip('Parameters')
    ),
    plot.from(
      plot.remote('/blog/$TARGET/$i')
    ),
    plot.bounds(800, null)
  )(renderPlot('plot_$i'));

  plot(
    plot.table(), 
    plot.from(
      plot.remote('/blog/$TARGET/$i')
    )
  )(renderPlot('table_$i'));

EOF
done



##################################
## Test Set Error Plots
##################################

for i in network_topology scoring_overhead adaptive_rate train_samples_per_iteration activation_function large_deep_net what_really_matters 
do
    sed '1d' /tmp/$i.csv >> dlperf/all.csv
done

cp -f dlperf/all.csv /tmp/

for i in what_really_matters all
do
    cp -f /tmp/$i.csv dlperf/

    cat << EOF > dlperf/$i.json
    {
      "name": "$i",
      "format": "csv",
      "location": "/blog/$TARGET/$i.csv",
      "header": true,
      "schema": {
        "Parameters": "string",
        "Training Samples": "real",
        "Training Time": "real",
        "Training Speed": "real",
        "Test Set Error": "real"
      }
    }
EOF

    cat << EOF >> PerformanceH2ODeepLearning.plots
  plot(
    plot.point(
      plot.position('Training Time', 'Test Set Error'),
      plot.tooltip('Parameters')
    ),
    plot.from(
      plot.remote('/blog/$TARGET/$i')
    ),
    plot.bounds(800, null)
  )(renderPlot('plot_$i'));

  plot(
    plot.table(), 
    plot.from(
      plot.remote('/blog/$TARGET/$i')
    )
  )(renderPlot('table_$i'));

EOF
done


awk '/INSERT2/{while((getline line < $2) > 0 ){print line}close($2);next}1' dlperf.md > dlperf.2.md
mv dlperf.2.md  dlperf.md

rm -f PerformanceH2ODeepLearning.plots

sed -i -e 's/{r}/r/' dlperf.md
rm *md-e

cp dlperf.md $WEB_REPO/src/blog/$TARGET.md
mkdir -p $WEB_REPO/src/blog/$TARGET
rsync -avu --delete --exclude=*.log --exclude=*.png dlperf/ $WEB_REPO/src/blog/$TARGET/
(cd $WEB_REPO && make build)
