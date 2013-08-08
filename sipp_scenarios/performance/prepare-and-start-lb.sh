#mvn clean package -f ../../jar/pom.xml -Dmaven.test.skip=true
export version=7.0.0-TelScale-SNAPSHOT
rm -rf logs
mkdir logs
/data/devWorkspace/jdk6/jdk6_latest/bin/java -Xms5120m -Xmx5120m -XX:PermSize=512M -XX:MaxPermSize=1024M -XX:+UseConcMarkSweepGC -XX:+CMSIncrementalMode -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.local.only=false -DlogConfigFile=./lb-log4j.xml  -jar ../../jar/target/sip-balancer-jar-$version-jar-with-dependencies.jar -mobicents-balancer-config=lb-configuration.properties 

