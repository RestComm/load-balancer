#mvn clean package -f ../../jar/pom.xml -Dmaven.test.skip=true
export version=7.0.0-TelScale-SNAPSHOT
rm -rf logs
mkdir logs
java -Djava.util.logging.config.file=./lb-logging.properties -jar ../../jar/target/sip-balancer-jar-$version-jar-with-dependencies.jar -mobicents-balancer-config=lb-configuration.properties -Xms6414m -Xmx6414m -XX:PermSize=512M -XX:MaxPermSize=1024M -XX:+UseConcMarkSweepGC -XX:+CMSIncrementalMode -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.local.only=false
