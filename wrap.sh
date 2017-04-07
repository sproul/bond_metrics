#!/bin/bash
cd $dp/qr/bond_metrics

jar -cvf bond_metrics.jar -C . .project .classpath src doc `find bin/bondmetrics -type f | sed -e 's;^bin;-C bin ;'`
jar tf bond_metrics.jar
exit