package net.openhft.chronicle.network;

import net.openhft.chronicle.bytes.RandomDataInput;
import net.openhft.chronicle.core.Jvm;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Created by peter.lawrey on 15/07/2015.
 */
public class NetworkLog {
    @NotNull
    private final String desc;
    private long lastOut = System.currentTimeMillis();

    private static final Logger LOG =
            LoggerFactory.getLogger(NetworkLog.class.getName());


    public NetworkLog(@NotNull SocketChannel channel, String op) throws IOException {
        this.desc = op
                + " " + ((InetSocketAddress) channel.getLocalAddress()).getPort()
                + " " + ((InetSocketAddress) channel.getRemoteAddress()).getPort();
    }

    public void idle() {
        if (!Jvm.isDebug() || !LOG.isDebugEnabled()) return;
        long now = System.currentTimeMillis();
        if (now - lastOut > 2000) {
            lastOut = now;
            LOG.debug(desc + " idle");
        }
    }

    public void log(@NotNull ByteBuffer bytes, int start, int end) {
        if (!Jvm.isDebug() || !LOG.isDebugEnabled()) return;

        final StringBuilder sb = new StringBuilder(desc);
        sb.append(" len: ").append(end - start)
                .append(" - ");
        if (end - start > 128) {
            for (int i = start; i < start + 64; i++)
                appendByte(bytes, sb, i);
            sb.append(" ... ");
            for (int i = end - 64; i < end; i++)
                appendByte(bytes, sb, i);
        } else {
            for (int i = start; i < end; i++)
                appendByte(bytes, sb, i);
        }

        LOG.debug(sb.toString());
    }

    private void appendByte(@NotNull ByteBuffer bytes, @NotNull StringBuilder sb, int i) {
        sb.append(RandomDataInput.charToString[bytes.get(i) & 0xFF]);
    }
}
