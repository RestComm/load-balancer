#mvn clean package -f ../../jar/pom.xml -Dmaven.test.skip=true
export version=7.0.3-TelScale-SNAPSHOT
rm -rf logs
mkdir logs
java -Xms2048m -Xmx2048m -XX:PermSize=512M -XX:MaxPermSize=1024M -XX:+UseConcMarkSweepGC -XX:+CMSIncrementalMode -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.local.only=false -DlogConfigFile=./lb-log4j.xml  -jar ../../jar/target/sip-balancer-jar-$version-jar-with-dependencies.jar -mobicents-balancer-config=lb-configuration.properties 

