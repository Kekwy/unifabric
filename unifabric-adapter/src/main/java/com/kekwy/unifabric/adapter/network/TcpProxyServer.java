package com.kekwy.unifabric.adapter.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 适配器侧按需 TCP 监听，将入站连接转发到指定远端或已建立的上游套接字（论文 3.5.2–3.5.3）。
 */
public class TcpProxyServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TcpProxyServer.class);

    private final ProxyPortAllocator portAllocator;
    private final ExecutorService workers;
    private final List<ServerSocket> serverSockets = new CopyOnWriteArrayList<>();
    private volatile boolean closed;

    public TcpProxyServer() {
        this(null);
    }

    public TcpProxyServer(ProxyPortAllocator portAllocator) {
        this.portAllocator = portAllocator;
        this.workers = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "unifabric-tcp-proxy");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 监听本地端口；每个入站连接与 {@code remoteHost:remotePort} 新建连接双向桥接。
     */
    public int openInboundProxy(String remoteHost, int remotePort) throws IOException {
        ServerSocket ss = bindListenSocket(32);
        serverSockets.add(ss);
        int port = ss.getLocalPort();
        workers.submit(() -> acceptLoop(ss, remoteHost, remotePort));
        return port;
    }

    /**
     * 监听本地端口；接受<strong>单个</strong>本地连接后与已连接的 {@code upstream} 双向桥接（用于中继路径）。
     */
    public int attachLocalAcceptToUpstream(Socket upstream) throws IOException {
        ServerSocket ss = bindListenSocket(4);
        serverSockets.add(ss);
        int port = ss.getLocalPort();
        workers.submit(() -> {
            try {
                Socket local = ss.accept();
                bridge(local, upstream);
            } catch (IOException e) {
                log.debug("单会话本地代理结束: {}", e.getMessage());
            } finally {
                closeQuietly(ss);
                serverSockets.remove(ss);
                closeQuietly(upstream);
            }
        });
        return port;
    }

    private ServerSocket bindListenSocket(int backlog) throws IOException {
        InetAddress addr = InetAddress.getByName("0.0.0.0");
        if (portAllocator != null && portAllocator.isEnabled()) {
            int n = portAllocator.rangeSize();
            for (int i = 0; i < n; i++) {
                int p = portAllocator.nextCandidatePort();
                try {
                    return new ServerSocket(p, backlog, addr);
                } catch (IOException ignored) {
                    // 端口占用则尝试下一个
                }
            }
            log.warn("代理端口区间内无可用端口，回退为系统分配临时端口");
        }
        return new ServerSocket(0, backlog, addr);
    }

    private void acceptLoop(ServerSocket ss, String remoteHost, int remotePort) {
        try {
            while (!closed && !ss.isClosed()) {
                Socket client = ss.accept();
                workers.submit(() -> bridgeToRemote(client, remoteHost, remotePort));
            }
        } catch (IOException e) {
            if (!closed) {
                log.debug("代理 accept 循环结束: {}", e.getMessage());
            }
        }
    }

    private void bridgeToRemote(Socket client, String remoteHost, int remotePort) {
        try (Socket clientSock = client;
             Socket remote = new Socket(remoteHost, remotePort)) {
            bridge(clientSock, remote);
        } catch (IOException e) {
            log.debug("桥接到远端失败: {}:{} -> {}", remoteHost, remotePort, e.getMessage());
        } finally {
            closeQuietly(client);
        }
    }

    public static void bridge(Socket a, Socket b) {
        ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "adapter-bridge");
            t.setDaemon(true);
            return t;
        });
        try {
            var f1 = pool.submit(() -> pump(a, b));
            var f2 = pool.submit(() -> pump(b, a));
            f1.get();
            f2.get();
        } catch (Exception e) {
            log.trace("桥接结束: {}", e.getMessage());
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
                if (n > 0) {
                    out.write(buf, 0, n);
                    out.flush();
                }
            }
        } catch (IOException e) {
            log.trace("pump: {}", e.getMessage());
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

    private static void closeQuietly(ServerSocket s) {
        if (s == null) {
            return;
        }
        try {
            s.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() {
        closed = true;
        for (ServerSocket ss : serverSockets) {
            closeQuietly(ss);
        }
        serverSockets.clear();
        workers.shutdownNow();
    }
}
