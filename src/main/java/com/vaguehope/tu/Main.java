package com.vaguehope.tu;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

	private static final int TRAFFIC_CLASS = 0x18; // == 0x10 | 0x08 == IPTOS_THROUGHPUT | IPTOS_LOWDELAY
	protected static final Logger LOG = LoggerFactory.getLogger(Main.class);

	public static void main (final String[] args) throws InterruptedException, ExecutionException {
		try {
			LOG.info("tu desu~");
			final ExecutorService es = Executors.newCachedThreadPool();
			final Collection<Future<?>> futures = new ArrayList<Future<?>>();
			for (int port = 5000; port < 5050; port += 2) {
				futures.add(es.submit(new Acceptor(port, es)));
			}
			for (final Future<?> future : futures) {
				future.get();
			}
		}
		finally {
			System.exit(0);
		}
	}

	private static class Acceptor implements Runnable {

		private final int port;
		private final ExecutorService es;

		public Acceptor (final int port, final ExecutorService es) {
			this.port = port;
			this.es = es;
		}

		@Override
		public void run () {
			try {
				accept();
			}
			catch (final Throwable t) {
				LOG.error("Acceptor tcp:" + this.port + " died.", t);
			}
		}

		private void accept () throws IOException {
			final ServerSocket serverSocket = new ServerSocket(this.port);
			try {
				for (;;) {
					LOG.info("Listening on tcp:{}...", serverSocket.getLocalPort());
					final Socket socket = serverSocket.accept();
					socket.setTrafficClass(TRAFFIC_CLASS);
					this.es.submit(new SocketToUdp(socket, socket.getLocalPort()));
				}
			}
			finally {
				IOUtils.closeQuietly(serverSocket);
			}
		}

	}

	private static class SocketToUdp implements Runnable {

		private final Socket socket;
		private final int udpPort;

		public SocketToUdp (final Socket socket, final int localPort) {
			this.socket = socket;
			this.udpPort = localPort;
		}

		@Override
		public void run () {
			try {
				LOG.info("start: {} --> udp:{}.", this.socket.getInetAddress(), this.udpPort);
				final long total = toUdp(this.socket.getInputStream(), this.udpPort);
				LOG.info("end: {} --> udp:{} {} bytes.", this.socket.getInetAddress(), this.udpPort, total);
			}
			catch (final Throwable e) {
				LOG.error("TCP to UDP stream died.", e);
			}
		}

		private static long toUdp (final InputStream is, final int udpPort) throws IOException {
			final byte[] lengthBuff = new byte[2];
			final byte[] contentBuff = new byte[1024 * 1024];
			final DatagramSocket udpSocket = new DatagramSocket();
			try {
				final DatagramPacket udpPacket = new DatagramPacket(contentBuff, contentBuff.length, InetAddress.getLocalHost(), udpPort);
				long total = 0;
				for (;;) {
					final int ll = ReadHelper.readFully(is, lengthBuff, 0, lengthBuff.length);
					if (ll < lengthBuff.length) break;
					total += ll;
					final int length = twoBytesToInt(lengthBuff);
					final int cl = ReadHelper.readFully(is, contentBuff, 0, length);
					if (cl < length) break;
					total += cl;
					udpPacket.setLength(cl);
					udpSocket.send(udpPacket);
					sleepQuietly(1);
				}
				return total;
			}
			finally {
				IOUtils.closeQuietly(udpSocket);
			}
		}

		private static int twoBytesToInt (final byte[] b) {
			return (b[0] << 8) | (b[1] & 0xFF);
		}

		private static void sleepQuietly (final int i) {
			try {
				Thread.sleep(1);
			}
			catch (final InterruptedException e) {}
		}

	}

}
