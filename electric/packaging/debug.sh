# jdb -attach 8000 -sourcepath../electric-java
# jdb -launch -sourcepath../electric-java -classpathantBuild -Dapple.laf.useScreenMenuBar=true -Dcom.apple.mrj.application.apple.menu.about.name=Electric -J-Xms64m -J-Xmx2048m -J-Xss64m com.sun.electric.Launcher
jdb -launch -sourcepath ../electric-java -classpath antBuild:gluegen-rt-natives-macosx-universal.jar:gluegen-rt.jar:jogl-all-natives-macosx-universal.jar:jogl-all.jar -J-Xms64m -J-Xmx2048m -J-Xss64m -Dapple.laf.useScreenMenuBar=true -Dcom.apple.mrj.application.apple.menu.about.name=Electric com.sun.electric.Launcher
