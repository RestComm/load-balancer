#Don't forget to kill the sipp uas process after the test run it might still hang 

killall sipp
echo "Performance Testing"
sipp 127.0.0.1:5065 -sf performance-proxy-uas.xml -trace_err -i 127.0.0.1 -p 5090 -fd 1 -nd -timeout 160 -bg 
sipp 127.0.0.1:5065 -sf performance-proxy-uas.xml -trace_err -i 127.0.0.1 -p 5091 -fd 1 -nd -timeout 160 -bg 
sipp 127.0.0.1:5060 -sf performance-proxy-uac.xml -trace_err -i 127.0.0.1 -p 5055 -fd 1 -nd -timeout 160 -r 1000 -m 1000000
sleep 10
killall sipp
