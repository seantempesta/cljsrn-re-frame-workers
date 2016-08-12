#!/bin/bash

/usr/local/bin/node --expose-gc --max_old_space_size=4096 "./node_modules/react-native/local-cli/cli.js" bundle  --entry-file ./worker.js   --platform ios   --dev false  --reset-cache true   --bundle-output "ios/worker.jsbundle" 
