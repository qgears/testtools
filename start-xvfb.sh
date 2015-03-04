#!/bin/bash

# Starts the XVFB embedded X server for testing purposes

# NOTE: temporary files are written into the working directory!

export XVFB_DISPLAY=${TEST_DISPLAY-":"$(echo $(($(($(ls -t /tmp/.X11-unix | head -n 1 | sed 's/X//g')))+1)))}

# Forcing software rendering to attempt to provide identic results over 
# different platforms
(LD_PRELOAD=/usr/lib/x86_64-linux-gnu/mesa/libGL.so.1 Xvfb $XVFB_DISPLAY \
	-screen 0 1024x768x24 -ac +extension GLX +render -dpi 92) &

XVFB_PID=$!

echo "Xvfb started with PID $XVFB_PID; display number: $XVFB_DISPLAY" 

sleep 0.1s 

# Waiting for the X server to be ready - note that this might result in an
# infinite loop, so it is the responsibility of the caller to kill this process
# after a timeout
while ! $(xset -display $XVFB_DISPLAY q &> /dev/null)
do
	echo "Waiting for the X server to be ready"
	sleep 0.1s
done

(DISPLAY=$XVFB_DISPLAY openbox --startup "echo openbox_started") &
(sleep 3s && setxkbmap -display $XVFB_DISPLAY hu) &

if [ -z "$1" ];
then
	# Waiting for an 'enter' key press to end the session
	read

	# Cleaning up embedded X server
	kill -9 $XVFB_PID
else
	sleep 10s
	echo 'Asycnronous execution : saving display and process id into file: '"$1"
	echo 'bgxserver-display='$XVFB_DISPLAY > $1
	echo 'bgxserver-pid='$XVFB_PID >> $1
fi

exit 0
