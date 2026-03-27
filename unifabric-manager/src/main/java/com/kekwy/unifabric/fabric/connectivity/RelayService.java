package com.kekwy.unifabric.fabric.connectivity;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 管理节点 TCP 中继：两路客户端接入同一监听端口后双向桥接（论文 3.5.3 跨域兜底）。
 */
@Component
public class RelayService {

    private static final Logger log = LoggerFactory.getLogger(RelayService.class);

    private final String bindAddress;
    private final String advertiseHost;
    private final List<ServerSocket> liveSockets = new CopyOnWriteArrayList<>();

    public RelayService(
            @Value("${unifabric.fabric.connectivity.relay-bind-address:0.0.0.0}") String bindAddress,
            @Value("${unifabric.fabric.connectivity.relay-advertise-host:}") String relayAdvertiseHost,
            @Value("${unifabric.fabric.advertise-address:127.0.0.1:9090}") String fabricAdvertiseAddress) {
        this.bindAddress = bindAddress != null && !bindAddress.isBlank() ? bindAddress : "0.0.0.0";
        if (relayAdvertiseHost != null && !relayAdvertiseHost.isBlank()) {
            this.advertiseHost = relayAdvertiseHost;
        } else {
            String host = fabricAdvertiseAddress;
            int colon = host.lastIndexOf(':');
            this.advertiseHost = colon > 0 ? host.substring(0, colon) : host;
        }
    }

    public record RelayEndpoint(String host, int port) {}

    /**
     * 分配中继监听端口；接受两条入站连接后桥接并关闭 {@link ServerSocket}。
     */
    public synchronized RelayEndpoint allocateRelay(String connectId) throws IOException {
        InetAddress addr = InetAddress.getByName(bindAddress);
        ServerSocket ss = new ServerSocket(0, 8, addr);
        liveSockets.add(ss);
        int port = ss.getLocalPort();
        Thread t = new Thread(() -> runSession(connectId, ss), "unifabric-relay-" + connectId);
        t.setDaemon(true);
        t.start();
        log.info("已分配中继端口: connectId={}, {}:{}", connectId, advertiseHost, port);
        return new RelayEndpoint(advertiseHost, port);
    }

    private void runSession(String connectId, ServerSocket ss) {
        try (ss) {
            Socket a = ss.accept();
            Socket b = ss.accept();
            log.info("中继会话已配对: connectId={}", connectId);
            TcpRelayBridge.bridge(a, b);
        } catch (IOException e) {
            log.warn("中继会话异常结束: connectId={}, {}", connectId, e.getMessage());
        } finally {
            liveSockets.remove(ss);
        }
    }

    @PreDestroy
    public void shutdown() {
        for (ServerSocket ss : liveSockets) {
            try {
                ss.close();
            } catch (IOException ignored) {
            }
        }
        liveSockets.clear();
    }
}
