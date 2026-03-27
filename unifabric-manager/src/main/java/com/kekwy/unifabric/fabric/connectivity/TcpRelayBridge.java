package com.kekwy.unifabric.fabric.connectivity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 双向字节流转发（管理节点中继，论文 3.5.3）。
 */
public final class TcpRelayBridge {

    private static final Logger log = LoggerFactory.getLogger(TcpRelayBridge.class);

    private TcpRelayBridge() {
    }

    public static void bridge(Socket a, Socket b) {
        ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "relay-bridge");
            t.setDaemon(true);
            return t;
        });
        try {
            Future<?> f1 = pool.submit(() -> pump(a, b));
            Future<?> f2 = pool.submit(() -> pump(b, a));
            f1.get();
            f2.get();
        } catch (Exception e) {
            log.debug("中继桥接结束: {}", e.getMessage());
        } finally {
            pool.shutdownNow();
            closeQuietly(a);
            closeQuietly(b);
        }
    }

    private static void pump(Socket from, Socket to) {
        try (InputStream in = from.getInputStream();
             OutputStream out = to.getOutputStream()) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n == 0) {
                    continue;
                }
                out.write(buf, 0, n);
                out.flush();
            }
        } catch (IOException e) {
            log.trace("relay pump: {}", e.getMessage());
        }
    }

    private static void closeQuietly(Socket s) {
        if (s == null) {
            return;
        }
        try {
            s.close();
        } catch (IOException ignored) {
        }
    }
}
