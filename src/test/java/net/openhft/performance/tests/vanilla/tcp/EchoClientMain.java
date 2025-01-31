/*
 *     Copyright (C) 2015  higherfrequencytrading.com
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.openhft.performance.tests.vanilla.tcp;

import net.openhft.affinity.Affinity;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Random;

/**
 * @author peter.lawrey
 */
/* Both ends are run with -Xmx64m -verbose:gc

///////// EchoServerMain
On a E5-2650 v2 over loopback with onload
Throughput was 2880.4 MB/s
Loop back echo latency was 5.8/6.2 9.6/19.4 23.2us for 50/90 99/99.9 99.99%tile

On an i7-3970X over loopback
Starting throughput test
Throughput was 2333.0 MB/s
Loop back echo latency was 7.5/16.7 28/41 55/64 69 us for 50/90 99/99.9 99.99/99.999 worst %tile

Two connections
Throughput was 2345.4 MB/s, clients=2
2 clients: Loop back echo latency was 7.8/17.4 29/42 54/63 69 us for 50/90 99/99.9 99.99/99.999 worst %tile

Three connections
Throughput was 2396.6 MB/s, clients=3
3 clients: Loop back echo latency was 8.3/18.0 30/43 56/67 84 us for 50/90 99/99.9 99.99/99.999 worst %tile

Four connections
Throughput was 2411.5 MB/s, clients=4
4 clients: Loop back echo latency was 8.2/17.7 30/43 56/70 90 us for 50/90 99/99.9 99.99/99.999 worst %tile

Six connections
Throughput was 2437.7 MB/s, clients=6
Starting latency test rate: 100000
6 clients: Loop back echo latency was 11.4/25.9 46/67 85/102 123 us for 50/90 99/99.9 99.99/99.999 worst %tile
Starting latency test rate: 70000
6 clients: Loop back echo latency was 8.5/15.4 25/35 45/56 76 us for 50/90 99/99.9 99.99/99.999 worst %tile

Eight connections
Throughput was 2479.7 MB/s, clients=8
Starting latency test rate: 100000
8 clients: Loop back echo latency was 21.1/57.2 109/161 214/249 271 us for 50/90 99/99.9 99.99/99.999 worst %tile
Starting latency test rate: 70000
8 clients: Loop back echo latency was 9.6/18.8 32/45 58/73 102 us for 50/90 99/99.9 99.99/99.999 worst %tile

Ten connections
Throughput was 2490.0 MB/s, clients=10
Starting latency test rate: 70000
Average time 13
10 clients: Loop back echo latency was 10.7/23.0 40/57 74/88 108 us for 50/90 99/99.9 99.99/99.999 worst %tile

14 connections
Throughput was 2494.3 MB/s, clients=14
Starting latency test rate: 70000
Average time 24
14 clients: Loop back echo latency was 18.9/47.5 88/129 169/202 245 us for 50/90 99/99.9 99.99/99.999 worst %tile
Starting latency test rate: 50000
Average time 14
14 clients: Loop back echo latency was 11.9/23.2 40/57 603/1717 2,018 us for 50/90 99/99.9 99.99/99.999 worst %tile

20 connections
Throughput was 2513.6 MB/s, clients=20
Starting latency test rate: 50000
20 clients: Loop back echo latency was 17.5/43.9 80/118 161/1581 2,028 us for 50/90 99/99.9 99.99/99.999 worst %tile
Starting latency test rate: 30000
20 clients: Loop back echo latency was 13.5/24.5 41/59 1,057/1693 1,967 us for 50/90 99/99.9 99.99/99.999 worst %tile

Between two servers via Solarflare with onload on server & client (no minor GCs)
Throughput was 1156.0 MB/s
Loop back echo latency was 12.2/12.5 21/25 28/465 us for 50/90 99/99.9 99.99/worst %tile

Between two servers via Solarflare with onload on client (no minor GCs)
Throughput was 867.5 MB/s
Loop back echo latency was 15.0/15.7 21/27 30/398 us for 50/90 99/99.9 99.99/worst %tile

//////// EchServerMain with lowlatency kernel
Throughput was 2450.3 MB/s
Starting latency test rate: 100000
Loop back echo latency was 9.1/20.5 35/50 65/76 81 us for 50/90 99/99.9 99.99/99.999 worst %tile

With 2 clients
Throughput was 2868.9 MB/s
Starting latency test rate: 100000
Loop back echo latency was 9.9/22.0 38/55 75/134 159 us for 50/90 99/99.9 99.99/99.999 worst %tile

//////// NettyEchoServer
Between two servers via Solarflare with onload on server & client (16 minor GCs)
Throughput was 968.7 MB/s
Loop back echo latency was 18.4/19.0 26/31 33/1236 us for 50/90 99/99.9 99.99/worst %tile

Between two servers via Solarflare with onload on client (16 minor GCs)
Throughput was 643.6 MB/s
Loop back echo latency was 20.8/21.8 29/34 38/2286 us for 50/90 99/99.9 99.99/worst %tile

*/

public class EchoClientMain {
    public static final int PORT = Integer.getInteger("port", 8007);
    public static final int CLIENTS = Integer.getInteger("clients", 1);
    public static final int TARGET_THROUGHPUT = Integer.getInteger("throughput", 20_000);

    public static void main(@NotNull String... args) throws IOException, InterruptedException {
        Affinity.setAffinity(2);
        String[] hostnames = args.length > 0 ? args : "localhost".split(",");

        SocketChannel[] sockets = new SocketChannel[CLIENTS];
        openConnections(hostnames, PORT, sockets);
        testThroughput(sockets);
        closeConnections(sockets);
        openConnections(hostnames, PORT, sockets);
        for (int i : new int[]{/*100000, 100000, */70000, 50000, 30000, 20000})
            testByteLatency(i, sockets);
        closeConnections(sockets);
    }

    private static void openConnections(@NotNull String[] hostname, int port, @NotNull SocketChannel... sockets) throws IOException {
        for (int j = 0; j < sockets.length; j++) {
            sockets[j] = SocketChannel.open(new InetSocketAddress(hostname[j % hostname.length], port));
            sockets[j].socket().setTcpNoDelay(true);
            sockets[j].configureBlocking(false);
        }
    }

    private static void closeConnections(@NotNull SocketChannel... sockets) throws IOException {
        for (Closeable socket : sockets)
            socket.close();
    }

    private static void testThroughput(@NotNull SocketChannel... sockets) throws IOException, InterruptedException {
        System.out.println("Starting throughput test, clients=" + CLIENTS);
        int bufferSize = 32 * 1024;
        ByteBuffer bb = ByteBuffer.allocateDirect(bufferSize);
        int count = 0, window = 2;
        long start = System.nanoTime();
        while (System.nanoTime() - start < 10e9) {
            for (SocketChannel socket : sockets) {
                bb.clear();
                if (socket.write(bb) < 0)
                    throw new AssertionError("Socket " + socket + " unable to write in one go.");
            }
            if (count >= window)
                for (SocketChannel socket : sockets) {
                    bb.clear();
                    while (socket.read(bb) >= 0 && bb.remaining() > 0) ;
                }
            count++;
        }
        for (SocketChannel socket : sockets) {
            try {
                do {
                    bb.clear();
                } while (socket.read(bb) > 0);
            } catch (ClosedChannelException expected) {
            }
        }
        long time = System.nanoTime() - start;
        System.out.printf("Throughput was %.1f MB/s, clients=%d%n",
                1e3 * count * bufferSize * sockets.length / time, CLIENTS);
    }

    private static void testByteLatency(int targetThroughput, @NotNull SocketChannel... sockets) throws IOException {
        System.out.println("Starting latency test rate: " + targetThroughput);
        int tests = Math.min(18 * targetThroughput, 1000000);
        long[] times = new long[tests * sockets.length];
        int count = 0;
        long now = System.nanoTime();
        int interval = (int) (1e9 / targetThroughput);

        ByteBuffer bb = ByteBuffer.allocateDirect(4);
        bb.putInt(0, 0x12345678);
        Random rand = new Random();
        for (int i = -20000; i < tests; i++) {

            for (SocketChannel socket : sockets) {
                now += rand.nextInt(2 * interval);
                while (System.nanoTime() < now)
                    ;

                bb.position(0);
                while (bb.remaining() > 0)
                    if (socket.write(bb) < 0)
                        throw new EOFException();

                bb.position(0);
                while (bb.remaining() > 0)
                    if (socket.read(bb) < 0)
                        throw new EOFException();

                if (bb.getInt(0) != 0x12345678)
                    throw new AssertionError("read error");

                if (i >= 0)
                    times[count++] = System.nanoTime() - now;
            }
        }
        System.out.println("Average time " + (Arrays.stream(times).sum()/times.length)/1000);
        Arrays.sort(times);
        System.out.printf("%d clients: Loop back echo latency was %.1f/%.1f %,d/%,d %,d/%d %,d us for 50/90 99/99.9 99.99/99.999 worst %%tile%n",
                CLIENTS,
                times[times.length / 2] / 1e3,
                times[times.length * 9 / 10] / 1e3,
                times[times.length - times.length / 100] / 1000,
                times[times.length - times.length / 1000] / 1000,
                times[times.length - times.length / 10000] / 1000,
                times[times.length - times.length / 100000] / 1000,
                times[times.length - 1] / 1000
        );
    }
}
