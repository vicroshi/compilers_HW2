tools:
	java -jar ./jars/jtb132di.jar -te minijava.jj
	java -jar ./jars/javacc5.jar minijava-jtb.jj
	javac -cp ./javacc:./ Main.java

main_extra:
	javac -cp ./javacc:./ Main.java
	java -cp :javacc Main minijava-examples-new/minijava-extra/*.java

main_normal:
	javac -cp ./javacc:./ Main.java
	java -cp :javacc Main minijava-examples-new/*.java

main_extra_error:
	javac -cp ./javacc:./ Main.java
	java -cp :javacc Main minijava-examples-new/minijava-error-extra/*.java

main_baziotis:
	javac -cp :javacc Main.java
	java -cp :javacc Main minijava-testsuite/*.java

clean:
	rm -rf *.class *~
	rm -rf syntaxtree/*.class
	rm -rf visitor/*.class
	rm -rf javacc/*.class