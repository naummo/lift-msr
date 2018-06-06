#!/bin/bash
set -x

export LIFT_MYSQL_USERNAME=s1569687
export LIFT_MYSQL_PASSWORD=alpine
export LIFT_NN_RESOURCES=/home/s1569687/microbenchmark/
export LIFT_CNN_CONFIG_PATH=/home/s1569687/lift/src/test/nn/cnn/nnconfigs/
export LIFT_NN_KERNELS_LOCATION=/home/s1569687/caffe_clblas/vcs_caffes/caffe-android-lift-clblas/microbenchmark_kernels/
readonly servermode="${SERVERMODE}"
REDIRECTOUTPUT=/dev/stdout
if [[ -n $REDIRECT_OUTPUT ]]; then
	REDIRECTOUTPUT=$REDIRECT_OUTPUT
fi
if [[ -n $COMPILEONLY ]]; then
    export LIFT_NN_MICROBENCHMARK_COMPILE_ONLY=$COMPILEONLY
fi

if [ "$servermode" != "standalone" ]
then
	debugserver="-agentlib:jdwp=transport=dt_socket,address=8000,suspend=y,server=y"
fi

java ${debugserver} -ea -Djava.library.path=/home/s1569687/lift/src/main/resources/lib/ -Didea.test.cyclic.buffer.size=1048576 -Dfile.encoding=UTF-8 -classpath /home/s1569687/intellij/lib/idea_rt.jar:/home/s1569687/intellij/plugins/junit/lib/junit-rt.jar:/home/s1569687/jdk1.8.0_91/jre/lib/charsets.jar:/home/s1569687/jdk1.8.0_91/jre/lib/deploy.jar:/home/s1569687/jdk1.8.0_91/jre/lib/ext/cldrdata.jar:/home/s1569687/jdk1.8.0_91/jre/lib/ext/dnsns.jar:/home/s1569687/jdk1.8.0_91/jre/lib/ext/jaccess.jar:/home/s1569687/jdk1.8.0_91/jre/lib/ext/jfxrt.jar:/home/s1569687/jdk1.8.0_91/jre/lib/ext/localedata.jar:/home/s1569687/jdk1.8.0_91/jre/lib/ext/nashorn.jar:/home/s1569687/jdk1.8.0_91/jre/lib/ext/sunec.jar:/home/s1569687/jdk1.8.0_91/jre/lib/ext/sunjce_provider.jar:/home/s1569687/jdk1.8.0_91/jre/lib/ext/sunpkcs11.jar:/home/s1569687/jdk1.8.0_91/jre/lib/ext/zipfs.jar:/home/s1569687/jdk1.8.0_91/jre/lib/javaws.jar:/home/s1569687/jdk1.8.0_91/jre/lib/jce.jar:/home/s1569687/jdk1.8.0_91/jre/lib/jfr.jar:/home/s1569687/jdk1.8.0_91/jre/lib/jfxswt.jar:/home/s1569687/jdk1.8.0_91/jre/lib/jsse.jar:/home/s1569687/jdk1.8.0_91/jre/lib/management-agent.jar:/home/s1569687/jdk1.8.0_91/jre/lib/plugin.jar:/home/s1569687/jdk1.8.0_91/jre/lib/resources.jar:/home/s1569687/jdk1.8.0_91/jre/lib/rt.jar:/home/s1569687/lift/target/scala-2.11/test-classes:/home/s1569687/lift/target/scala-2.11/classes:/home/s1569687/.ivy2/cache/ch.qos.logback/logback-classic/jars/logback-classic-1.1.7.jar:/home/s1569687/.ivy2/cache/org.slf4j/slf4j-api/jars/slf4j-api-1.7.21.jar:/home/s1569687/.ivy2/cache/org.scoverage/scalac-scoverage-runtime_2.11/jars/scalac-scoverage-runtime_2.11-1.0.4.jar:/home/s1569687/.ivy2/cache/org.scoverage/scalac-scoverage-plugin_2.11/jars/scalac-scoverage-plugin_2.11-1.0.4.jar:/home/s1569687/.ivy2/cache/org.scalacheck/scalacheck_2.11/jars/scalacheck_2.11-1.13.0.jar:/home/s1569687/.ivy2/cache/org.scala-stm/scala-stm_2.11/jars/scala-stm_2.11-0.7.jar:/home/s1569687/.ivy2/cache/org.scala-sbt/test-interface/jars/test-interface-1.0.jar:/home/s1569687/.ivy2/cache/org.scala-lang.modules/scala-xml_2.11/bundles/scala-xml_2.11-1.0.4.jar:/home/s1569687/.ivy2/cache/org.scala-lang.modules/scala-parser-combinators_2.11/bundles/scala-parser-combinators_2.11-1.0.4.jar:/home/s1569687/.ivy2/cache/org.scala-lang.modules/scala-async_2.11/bundles/scala-async_2.11-0.9.1.jar:/home/s1569687/.ivy2/cache/org.scala-lang/scala-reflect/jars/scala-reflect-2.11.8.jar:/home/s1569687/.ivy2/cache/org.scala-lang/scala-library/jars/scala-library-2.11.8.jar:/home/s1569687/.ivy2/cache/org.scala-lang/scala-compiler/jars/scala-compiler-2.11.8.jar:/home/s1569687/.ivy2/cache/org.joda/joda-convert/jars/joda-convert-1.6.jar:/home/s1569687/.ivy2/cache/org.hamcrest/hamcrest-core/jars/hamcrest-core-1.3.jar:/home/s1569687/.ivy2/cache/org.clapper/grizzled-scala_2.11/jars/grizzled-scala_2.11-1.2.jar:/home/s1569687/.ivy2/cache/org.clapper/argot_2.11/jars/argot_2.11-1.0.3.jar:/home/s1569687/.ivy2/cache/junit/junit/jars/junit-4.11.jar:/home/s1569687/.ivy2/cache/joda-time/joda-time/jars/joda-time-2.3.jar:/home/s1569687/.ivy2/cache/jline/jline/jars/jline-2.12.1.jar:/home/s1569687/.ivy2/cache/commons-cli/commons-cli/jars/commons-cli-1.3.1.jar:/home/s1569687/.ivy2/cache/com.typesafe.scala-logging/scala-logging_2.11/jars/scala-logging_2.11-3.4.0.jar:/home/s1569687/.ivy2/cache/com.typesafe.play/play-json_2.11/jars/play-json_2.11-2.3.10.jar:/home/s1569687/.ivy2/cache/com.typesafe.play/play-iteratees_2.11/jars/play-iteratees_2.11-2.3.10.jar:/home/s1569687/.ivy2/cache/com.typesafe.play/play-functional_2.11/jars/play-functional_2.11-2.3.10.jar:/home/s1569687/.ivy2/cache/com.typesafe.play/play-datacommons_2.11/jars/play-datacommons_2.11-2.3.10.jar:/home/s1569687/.ivy2/cache/com.typesafe/config/bundles/config-1.2.1.jar:/home/s1569687/.ivy2/cache/com.novocode/junit-interface/jars/junit-interface-0.11.jar:/home/s1569687/.ivy2/cache/com.fasterxml.jackson.core/jackson-databind/bundles/jackson-databind-2.3.2.jar:/home/s1569687/.ivy2/cache/com.fasterxml.jackson.core/jackson-core/bundles/jackson-core-2.3.2.jar:/home/s1569687/.ivy2/cache/com.fasterxml.jackson.core/jackson-annotations/bundles/jackson-annotations-2.3.2.jar:/home/s1569687/.ivy2/cache/ch.qos.logback/logback-core/jars/logback-core-1.1.7.jar:/home/s1569687/.ivy2/cache/mysql/mysql-connector-java/jars/mysql-connector-java-5.1.45.jar com.intellij.rt.execution.junit.JUnitStarter -ideVersion5 nn.cnn.TestCNN_Conv > $REDIRECTOUTPUT 2>&1 
