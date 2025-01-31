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

package net.openhft.performance.tests.network;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.network.AcceptorEventHandler;
import net.openhft.chronicle.network.TCPRegistry;
import net.openhft.chronicle.network.VanillaSessionDetails;
import net.openhft.chronicle.network.WireTcpHandler;
import net.openhft.chronicle.network.api.session.SessionDetailsProvider;
import net.openhft.chronicle.network.connection.TcpChannelHub;
import net.openhft.chronicle.threads.EventGroup;
import net.openhft.chronicle.wire.*;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.function.Function;

/*
Running on an i7-3970X

TextWire: Loop back echo latency was 7.4/8.9 12/20 108/925 us for 50/90 99/99.9 99.99/worst %tile
BinaryWire: Loop back echo latency was 6.6/8.0 9/11 19/3056 us for 50/90 99/99.9 99.99/worst %tile
RawWire: Loop back echo latency was 5.9/6.8 8/10 12/80 us for 50/90 99/99.9 99.99/worst %tile
 */

public class SessionTest {

    private static String testSessionId(@NotNull SocketChannel... sockets) throws IOException {

        final StringBuilder session = new StringBuilder();

        for (SocketChannel socket : sockets) {

            TextWire out = new TextWire(Bytes.wrapForWrite(ByteBuffer.allocate(1024)));

            out.clear();
            out.writeDocument(false, w -> w.write(() -> "test-key").text("test"));

            final ByteBuffer buffer = (ByteBuffer) out.bytes().underlyingObject();
            buffer.limit((int) out.bytes().writePosition());
            socket.write(buffer);

            if (buffer.remaining() > 0)
                throw new AssertionError("Unable to write in one go.");
        }

        for (SocketChannel socket : sockets) {

            TextWire in = new TextWire(Bytes.wrapForRead(ByteBuffer.allocate(1024)));

            final ByteBuffer buffer = (ByteBuffer) in.bytes().underlyingObject();

            while (buffer.position() < 2) {
                socket.read(buffer);
            }

            final long len = in.getValueIn().int32();

            while (buffer.position() < Wires.lengthOf(len)) {
                socket.read(buffer);
            }

            in.bytes().readPosition(0);
            in.bytes().readLimit(buffer.position());
            in.readDocument(null, i -> {
                final String id = i.read(() -> "sessionId").text();
                session.append(id);
                System.out.println("session=" + id);
            });

        }

        return session.toString();

    }

    /**
     * test that the same sesson returns the same session id
     */
    @Test
    public void testProcess() throws IOException {
        EventGroup eg = new EventGroup(true);
        eg.start();
        TCPRegistry.createServerSocketChannelFor("test");

        AcceptorEventHandler eah = new AcceptorEventHandler("test", () -> new SessionIdRefector
                (WireType.TEXT), VanillaSessionDetails::new, 0,0);
        eg.addHandler(eah);

        SocketChannel[] sc = new SocketChannel[2];
        for (int i = 0; i < sc.length; i++) {
            SocketAddress localAddress = TCPRegistry.lookup("test");
            System.out.println("Connecting to " + localAddress);
            sc[i] = SocketChannel.open(localAddress);
            sc[i].configureBlocking(false);
        }

        final String s0 = testSessionId(sc[0]);
        final String s1 = testSessionId(sc[1]);

        Assert.assertTrue(s0.length() > 0);
        Assert.assertTrue(s1.length() > 0);

        Assert.assertEquals(s0, testSessionId(sc[0]));
        Assert.assertEquals(s1, testSessionId(sc[1]));

        eg.stop();
        // shutdown all servers
        TcpChannelHub.closeAllHubs();
        TCPRegistry.reset();
    }

    public static class SessionIdRefector extends WireTcpHandler {

        public SessionIdRefector(@NotNull Function<Bytes, Wire> bytesToWire) {
            super(bytesToWire);
        }

        @Override
        protected void process(@NotNull WireIn inWire,
                               @NotNull WireOut outWire,
                               @NotNull SessionDetailsProvider sd) {
            outWire.writeDocument(false, w -> w.write(() -> "sessionId").text(sd.sessionId()
                    .toString()));
        }
    }
}