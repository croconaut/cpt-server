#!/bin/sh

if [ -f exit_port.txt ]
then
	read port < exit_port.txt
	echo "Telneting to $port to quit the server instance..."
	telnet 127.0.0.1 $port
	rm exit_port.txt
fi
# remove log file
rm -f logfile.log
# run again
nohup ./gradlew run </dev/null 2>&1 | tee logfile.log &
