all: compile

compile:
	java -jar ./jars/jtb132di.jar -te minijava.jj
	java -jar ./jars/javacc5.jar minijava-jtb.jj
	make main
main:
	javac -cp ./jars/javatuples-1.2.jar:./javacc:./ Main.java


clean:
	rm -rf *.class *~
	rm -rf syntaxtree/*.class
	rm -rf visitor/*.class
	rm -rf javacc/*.class