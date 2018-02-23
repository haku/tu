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

	public static final int DEFAULT_RTP_PACKAGE_DELAY_MS = 1;
	private static final int TRAFFIC_CLASS = 0x18; // == 0x10 | 0x08 == IPTOS_THROUGHPUT | IPTOS_LOWDELAY
	protected static final Logger LOG = LoggerFactory.getLogger(Main.class);

	public static void main (final String[] args) throws InterruptedException, ExecutionException {
		try {
			LOG.info("tu starting");

			int rtpPackageDelay = DEFAULT_RTP_PACKAGE_DELAY_MS;
			if (args.length > 0) {
				try {
					rtpPackageDelay = Integer.parseInt(args[0]);
				} catch (NumberFormatException e) {
					LOG.error("Argument" + args[0] + " must be an integer.");
					System.exit(1);
				}
			}
			LOG.info("Using " + rtpPackageDelay + "ms delay between RTP packages");
			final ExecutorService es = Executors.newCachedThreadPool();
			final Collection<Future<?>> futures = new ArrayList<Future<?>>();
			for (int port = 5000; port < 5050; port += 2) {
				futures.add(es.submit(new Acceptor(port, es, rtpPackageDelay)));
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
		private int rtpPackageDelay;

		public Acceptor (final int port, final ExecutorService es, final int rtpPackageDelay) {
			this.port = port;
			this.es = es;
			this.rtpPackageDelay = rtpPackageDelay;
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
					this.es.submit(new SocketToUdp(socket, socket.getLocalPort(), rtpPackageDelay));
				}
			}
			finally {
				IOUtils.closeQuietly(serverSocket);
			}
		}

	}

	private static class TransferStats {
		private final long bytesReceived;
		private final long packetsSent;

		public TransferStats (final long bytesReceived, final long packetsSent) {
			this.bytesReceived = bytesReceived;
			this.packetsSent = packetsSent;
		}

		@Override
		public String toString () {
			return String.format("%s bytes --> %s packets", this.bytesReceived, this.packetsSent);
		}
	}

	private static class SocketToUdp implements Runnable {

		private final Socket socket;
		private final int udpPort;
		private int rtpPackageDelay;

		public SocketToUdp (final Socket socket, final int localPort, final int rtpPackageDelay) {
			this.socket = socket;
			this.udpPort = localPort;
			this.rtpPackageDelay = rtpPackageDelay;
		}

		@Override
		public void run () {
			try {
				LOG.info("start: {} --> udp:{}.", this.socket.getInetAddress(), this.udpPort);
				final TransferStats stats = toUdp(this.socket.getInputStream(), this.udpPort, this.rtpPackageDelay);
				LOG.info("end: {} --> udp:{} ({}).", this.socket.getInetAddress(), this.udpPort, stats);
			}
			catch (final Throwable e) {
				LOG.error("TCP to UDP stream died.", e);
			}
		}

		private static TransferStats toUdp (final InputStream is, final int udpPort, final int rtpPackageDelay) throws IOException {
			final byte[] lengthBuff = new byte[2];
			final byte[] contentBuff = new byte[1024 * 1024];
			final DatagramSocket udpSocket = new DatagramSocket();
			try {
				final DatagramPacket udpPacket = new DatagramPacket(contentBuff, contentBuff.length, InetAddress.getLocalHost(), udpPort);
				long totalBytesReceived = 0;
				long totalPacketsSent = 0;
				for (;;) {
					final int ll = ReadHelper.readFully(is, lengthBuff, 0, lengthBuff.length);
					if (ll < lengthBuff.length) break;
					totalBytesReceived += ll;
					final int length = twoBytesToInt(lengthBuff);
					final int cl = ReadHelper.readFully(is, contentBuff, 0, length);
					if (cl < length) break;
					totalBytesReceived += cl;
					udpPacket.setLength(cl);
					udpSocket.send(udpPacket);
					totalPacketsSent++;
					sleepQuietly(rtpPackageDelay);
				}
				return new TransferStats(totalBytesReceived, totalPacketsSent);
			} finally {
				IOUtils.closeQuietly(udpSocket);
			}
		}

		private static int twoBytesToInt (final byte[] b) {
			return (b[0] << 8) | (b[1] & 0xFF);
		}

		private static void sleepQuietly (final int t) {
			try {
				Thread.sleep(t);
			}
			catch (final InterruptedException e) {/* do not care. */}
		}

	}

}
