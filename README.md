TU - TCP to UDP
===============

This small Java program receives TCP connections and retransmits the data to localhost UDP.
The port mappings are symmetrical, the port the TCP connection listens on is the same as the UDP port.
It listens on events ports between 5000 and 5048 inclusive.
This is easily changeable by editing the code.

Why?
---

Some software, particularly audio and video transcoders, will only receive input via UDP formats like RTP.
Sometimes it necessary to feed that input over a non-perfect network link that may drop some packets.
When latency is not a big concern, using TCP instead makes the link reliable without the complication of things like forward error correction.

Packet Format
-------------

The data feed via the TCP input needs to be decorated to indicate the UDP packet boundaries.
Every packet in the TCP input needs to be preceded by two bytes indicating the packet length.

Compiling and Running
---------------------

This Java project is build using Maven, run `mvn clean install`.
This creates a runnable jar, `java -jar ./target/tu-1.1-SNAPSHOT-jar-with-dependencies.jar`.

Logging
-------

TU writes minimal logs to `c:/tu/`, starting a new file each day.
The logs are kept for 5 days and then deleted.
Configure logging by editing `./src/main/resources/logback.xml` and recompiling.

Performance
-----------

By default TU will sleep for 1 millisecond between sending UDP packets.
This is to avoid flooding the receiver.

You can pass argument for sleep time when application is started.

This application is multithreaded and there is a thread per every open incoming socket.
In order to avoid flooding receiver with concurrent writes the threads are synchronized.
