#Don't forget to kill the sipp uas process after the test run it might still hang 
killall sipp
../sipp 127.0.0.1:5060 -sf performance-proxy-uas.xml -trace_err -i 127.0.0.1 -p 5090 -bg
../sipp 127.0.0.1:5060 -sf performance-proxy-uac.xml -trace_err -i 127.0.0.1 -p 5055 -r 100 -m 10000000
sleep 10
