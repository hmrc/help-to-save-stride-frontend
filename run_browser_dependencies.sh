#!/bin/sh

#USAGE="run_browser_dependencies.sh [--key -k=key]"

export JAVA_HOME=/usr/lib/jvm/java-9-openjdk-amd64
echo "BrowserStackLocal instances:"
pidof BrowserStackLocal
cd /tmp
if pidof BrowserStackLocal; then
    echo "BrowserStackLocal running already"
else
    if [ ! -e BrowserStackLocal ]; then
        wget https://www.browserstack.com/browserstack-local/BrowserStackLocal-linux-x64.zip
        unzip BrowserStackLocal-linux-x64.zip
    fi
    ./BrowserStackLocal --key 56HndczBWgvboCkByhta
fi