#!/bin/sh

if [ -f exit_port.txt ]
then
	read port < exit_port.txt
	echo "Telneting to $port to quit the server instance..."
	telnet 127.0.0.1 $port
	rm exit_port.txt
fi
# remove serialized 'tables'
rm -f *.obj
# reinitialize real tables
mysql -uwifon -pcroco-2014 wifon_test < wifon-test.sql
# remove log file & data
rm -f logfile.log
ls | grep -E '^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$' | xargs rm -rf
# run again
nohup ./gradlew run </dev/null 2>&1 | tee logfile.log &
