package com.kekwy.unifabric.proto.fabric;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.62.2)",
    comments = "Source: iarnet/fabric/discovery_service.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class DiscoveryServiceGrpc {

  private DiscoveryServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "iarnet.fabric.DiscoveryService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.kekwy.unifabric.proto.fabric.NodeInfoGossipMessage,
      com.kekwy.unifabric.proto.fabric.NodeInfoGossipResponse> getGossipNodeInfoMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GossipNodeInfo",
      requestType = com.kekwy.unifabric.proto.fabric.NodeInfoGossipMessage.class,
      responseType = com.kekwy.unifabric.proto.fabric.NodeInfoGossipResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.kekwy.unifabric.proto.fabric.NodeInfoGossipMessage,
      com.kekwy.unifabric.proto.fabric.NodeInfoGossipResponse> getGossipNodeInfoMethod() {
    io.grpc.MethodDescriptor<com.kekwy.unifabric.proto.fabric.NodeInfoGossipMessage, com.kekwy.unifabric.proto.fabric.NodeInfoGossipResponse> getGossipNodeInfoMethod;
    if ((getGossipNodeInfoMethod = DiscoveryServiceGrpc.getGossipNodeInfoMethod) == null) {
      synchronized (DiscoveryServiceGrpc.class) {
        if ((getGossipNodeInfoMethod = DiscoveryServiceGrpc.getGossipNodeInfoMethod) == null) {
          DiscoveryServiceGrpc.getGossipNodeInfoMethod = getGossipNodeInfoMethod =
              io.grpc.MethodDescriptor.<com.kekwy.unifabric.proto.fabric.NodeInfoGossipMessage, com.kekwy.unifabric.proto.fabric.NodeInfoGossipResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GossipNodeInfo"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.unifabric.proto.fabric.NodeInfoGossipMessage.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.unifabric.proto.fabric.NodeInfoGossipResponse.getDefaultInstance()))
              .setSchemaDescriptor(new DiscoveryServiceMethodDescriptorSupplier("GossipNodeInfo"))
              .build();
        }
      }
    }
    return getGossipNodeInfoMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static DiscoveryServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DiscoveryServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DiscoveryServiceStub>() {
        @java.lang.Override
        public DiscoveryServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DiscoveryServiceStub(channel, callOptions);
        }
      };
    return DiscoveryServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static DiscoveryServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DiscoveryServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DiscoveryServiceBlockingStub>() {
        @java.lang.Override
        public DiscoveryServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DiscoveryServiceBlockingStub(channel, callOptions);
        }
      };
    return DiscoveryServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static DiscoveryServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DiscoveryServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DiscoveryServiceFutureStub>() {
        @java.lang.Override
        public DiscoveryServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DiscoveryServiceFutureStub(channel, callOptions);
        }
      };
    return DiscoveryServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     * <pre>
     * 交换节点信息（gossip 协议）。
     * 调用方将自己及部分已知节点信息打包到 NodeInfoGossipMessage 中，
     * 接收方更新本地视图后，返回一批自己视角下的节点信息。
     * </pre>
     */
    default void gossipNodeInfo(com.kekwy.unifabric.proto.fabric.NodeInfoGossipMessage request,
        io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.fabric.NodeInfoGossipResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGossipNodeInfoMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service DiscoveryService.
   */
  public static abstract class DiscoveryServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return DiscoveryServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service DiscoveryService.
   */
  public static final class DiscoveryServiceStub
      extends io.grpc.stub.AbstractAsyncStub<DiscoveryServiceStub> {
    private DiscoveryServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DiscoveryServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DiscoveryServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * 交换节点信息（gossip 协议）。
     * 调用方将自己及部分已知节点信息打包到 NodeInfoGossipMessage 中，
     * 接收方更新本地视图后，返回一批自己视角下的节点信息。
     * </pre>
     */
    public void gossipNodeInfo(com.kekwy.unifabric.proto.fabric.NodeInfoGossipMessage request,
        io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.fabric.NodeInfoGossipResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGossipNodeInfoMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service DiscoveryService.
   */
  public static final class DiscoveryServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<DiscoveryServiceBlockingStub> {
    private DiscoveryServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DiscoveryServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DiscoveryServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * 交换节点信息（gossip 协议）。
     * 调用方将自己及部分已知节点信息打包到 NodeInfoGossipMessage 中，
     * 接收方更新本地视图后，返回一批自己视角下的节点信息。
     * </pre>
     */
    public com.kekwy.unifabric.proto.fabric.NodeInfoGossipResponse gossipNodeInfo(com.kekwy.unifabric.proto.fabric.NodeInfoGossipMessage request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGossipNodeInfoMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service DiscoveryService.
   */
  public static final class DiscoveryServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<DiscoveryServiceFutureStub> {
    private DiscoveryServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DiscoveryServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DiscoveryServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * 交换节点信息（gossip 协议）。
     * 调用方将自己及部分已知节点信息打包到 NodeInfoGossipMessage 中，
     * 接收方更新本地视图后，返回一批自己视角下的节点信息。
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.kekwy.unifabric.proto.fabric.NodeInfoGossipResponse> gossipNodeInfo(
        com.kekwy.unifabric.proto.fabric.NodeInfoGossipMessage request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGossipNodeInfoMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_GOSSIP_NODE_INFO = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_GOSSIP_NODE_INFO:
          serviceImpl.gossipNodeInfo((com.kekwy.unifabric.proto.fabric.NodeInfoGossipMessage) request,
              (io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.fabric.NodeInfoGossipResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getGossipNodeInfoMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.kekwy.unifabric.proto.fabric.NodeInfoGossipMessage,
              com.kekwy.unifabric.proto.fabric.NodeInfoGossipResponse>(
                service, METHODID_GOSSIP_NODE_INFO)))
        .build();
  }

  private static abstract class DiscoveryServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    DiscoveryServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.kekwy.unifabric.proto.fabric.DiscoveryServiceOuterClass.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("DiscoveryService");
    }
  }

  private static final class DiscoveryServiceFileDescriptorSupplier
      extends DiscoveryServiceBaseDescriptorSupplier {
    DiscoveryServiceFileDescriptorSupplier() {}
  }

  private static final class DiscoveryServiceMethodDescriptorSupplier
      extends DiscoveryServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    DiscoveryServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (DiscoveryServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new DiscoveryServiceFileDescriptorSupplier())
              .addMethod(getGossipNodeInfoMethod())
              .build();
        }
      }
    }
    return result;
  }
}
