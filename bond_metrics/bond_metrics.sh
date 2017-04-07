#!/bin/bash
# 
# given a DIR from which to load the markup csv files, 
# a security TYPE,
# a PRICE,
# maturity MONTH,
# maturity DAY,
# maturity YEAR,
# and (if TYPE was MUNI) a RATING
#
# this script will print a marked up price to stdout.
#
# Example:
#
#       bond_metrics.sh src/sample_markup_sheets MUNI 101.2 12 11 2024 AAminus
# 
cd `dirname $0`

if [ -z "$1" ]; then
        echo "$0: error: expected a directory from which markup schedules will be loaded" 1>&2
        exit 1
fi
schedules_src_dir=$1
shift

if [ ! -d "$schedules_src_dir" ]; then
        echo "$0: error: could not find directory \"$schedules_src_dir\"" 1>&2
        exit 1
fi

case "$OS" in
        win*)
                schedules_src_dir=`cygpath --mixed $schedules_src_dir`
        ;;
esac

export CLASSPATH="bin;bond_metrics.jar;$schedules_src_dir;$CLASSPATH"
java bondmetrics/Markup $@
exit
$dp/qr/bond_metrics/bond_metrics.sh src/sample_markup_sheets MUNI 101.2 12 11 2024 AAminus