#!/usr/bin/env bash

export DISPLAY=${DISPLAY=":99"}

sh ./run_browser_dependencies.sh

export ARGS="\"-Denvironment=local\",\"-Dbrowser=browserstack\",\"-Dusername=$1\",\"-Dkey=$2\""

sbt "; set javaOptions in Test ++= Seq($ARGS,\"-DtestDevice=BS_Win10_IE_11\"); selenium:test"