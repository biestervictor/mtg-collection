#!/bin/bash
cd /home/victor/mtg-springboot
/mnt/usb/java17/bin/java -jar target/mtg-collection-manager-0.1.0.jar > app.log 2>&1 &
