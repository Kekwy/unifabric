package com.kekwy.unifabric.fabric.discovery;

import com.kekwy.unifabric.proto.fabric.DiscoveryServiceGrpc;
import com.kekwy.unifabric.proto.fabric.NodeInfoGossipMessage;
import com.kekwy.unifabric.proto.fabric.NodeInfoGossipResponse;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 跨域 Gossip Push-Pull 一元 RPC 服务端（论文 3.3.2）：接收对端视图并返回本地视图。
 */
@Service
public class DiscoveryGrpcService extends DiscoveryServiceGrpc.DiscoveryServiceImplBase {

    private final NodeDiscoveryManager nodeDiscoveryManager;

    public DiscoveryGrpcService(NodeDiscoveryManager nodeDiscoveryManager) {
        this.nodeDiscoveryManager = nodeDiscoveryManager;
    }

    @Override
    public void gossipNodeInfo(NodeInfoGossipMessage request,
                               StreamObserver<NodeInfoGossipResponse> responseObserver) {
        String senderAddress = request.getSenderAddress();
        nodeDiscoveryManager.processGossipNodes(request.getNodesList(), senderAddress);

        NodeInfoGossipResponse response = NodeInfoGossipResponse.newBuilder()
                .setMessageId(request.getMessageId())
                .setTimestampMs(Instant.now().toEpochMilli())
                .addAllNodes(nodeDiscoveryManager.buildNodeInfoList())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
