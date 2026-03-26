package com.kekwy.unifabric.proto.provider;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * Provider 暴露给本地 Actor 的连接接口，仅流式 RPC，不定义新消息类型
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.62.2)",
    comments = "Source: iarnet/provider/actor_registration.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class ActorRegistrationServiceGrpc {

  private ActorRegistrationServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "iarnet.provider.ActorRegistrationService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.kekwy.unifabric.proto.actor.ActorEnvelope,
      com.kekwy.unifabric.proto.actor.ActorEnvelope> getActorChannelMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ActorChannel",
      requestType = com.kekwy.unifabric.proto.actor.ActorEnvelope.class,
      responseType = com.kekwy.unifabric.proto.actor.ActorEnvelope.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<com.kekwy.unifabric.proto.actor.ActorEnvelope,
      com.kekwy.unifabric.proto.actor.ActorEnvelope> getActorChannelMethod() {
    io.grpc.MethodDescriptor<com.kekwy.unifabric.proto.actor.ActorEnvelope, com.kekwy.unifabric.proto.actor.ActorEnvelope> getActorChannelMethod;
    if ((getActorChannelMethod = ActorRegistrationServiceGrpc.getActorChannelMethod) == null) {
      synchronized (ActorRegistrationServiceGrpc.class) {
        if ((getActorChannelMethod = ActorRegistrationServiceGrpc.getActorChannelMethod) == null) {
          ActorRegistrationServiceGrpc.getActorChannelMethod = getActorChannelMethod =
              io.grpc.MethodDescriptor.<com.kekwy.unifabric.proto.actor.ActorEnvelope, com.kekwy.unifabric.proto.actor.ActorEnvelope>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ActorChannel"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.unifabric.proto.actor.ActorEnvelope.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.unifabric.proto.actor.ActorEnvelope.getDefaultInstance()))
              .setSchemaDescriptor(new ActorRegistrationServiceMethodDescriptorSupplier("ActorChannel"))
              .build();
        }
      }
    }
    return getActorChannelMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ActorRegistrationServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ActorRegistrationServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ActorRegistrationServiceStub>() {
        @java.lang.Override
        public ActorRegistrationServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ActorRegistrationServiceStub(channel, callOptions);
        }
      };
    return ActorRegistrationServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ActorRegistrationServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ActorRegistrationServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ActorRegistrationServiceBlockingStub>() {
        @java.lang.Override
        public ActorRegistrationServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ActorRegistrationServiceBlockingStub(channel, callOptions);
        }
      };
    return ActorRegistrationServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ActorRegistrationServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ActorRegistrationServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ActorRegistrationServiceFutureStub>() {
        @java.lang.Override
        public ActorRegistrationServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ActorRegistrationServiceFutureStub(channel, callOptions);
        }
      };
    return ActorRegistrationServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * Provider 暴露给本地 Actor 的连接接口，仅流式 RPC，不定义新消息类型
   * </pre>
   */
  public interface AsyncService {

    /**
     */
    default io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.actor.ActorEnvelope> actorChannel(
        io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.actor.ActorEnvelope> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getActorChannelMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service ActorRegistrationService.
   * <pre>
   * Provider 暴露给本地 Actor 的连接接口，仅流式 RPC，不定义新消息类型
   * </pre>
   */
  public static abstract class ActorRegistrationServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return ActorRegistrationServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service ActorRegistrationService.
   * <pre>
   * Provider 暴露给本地 Actor 的连接接口，仅流式 RPC，不定义新消息类型
   * </pre>
   */
  public static final class ActorRegistrationServiceStub
      extends io.grpc.stub.AbstractAsyncStub<ActorRegistrationServiceStub> {
    private ActorRegistrationServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ActorRegistrationServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ActorRegistrationServiceStub(channel, callOptions);
    }

    /**
     */
    public io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.actor.ActorEnvelope> actorChannel(
        io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.actor.ActorEnvelope> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getActorChannelMethod(), getCallOptions()), responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service ActorRegistrationService.
   * <pre>
   * Provider 暴露给本地 Actor 的连接接口，仅流式 RPC，不定义新消息类型
   * </pre>
   */
  public static final class ActorRegistrationServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<ActorRegistrationServiceBlockingStub> {
    private ActorRegistrationServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ActorRegistrationServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ActorRegistrationServiceBlockingStub(channel, callOptions);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service ActorRegistrationService.
   * <pre>
   * Provider 暴露给本地 Actor 的连接接口，仅流式 RPC，不定义新消息类型
   * </pre>
   */
  public static final class ActorRegistrationServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<ActorRegistrationServiceFutureStub> {
    private ActorRegistrationServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ActorRegistrationServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ActorRegistrationServiceFutureStub(channel, callOptions);
    }
  }

  private static final int METHODID_ACTOR_CHANNEL = 0;

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
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_ACTOR_CHANNEL:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.actorChannel(
              (io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.actor.ActorEnvelope>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getActorChannelMethod(),
          io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
            new MethodHandlers<
              com.kekwy.unifabric.proto.actor.ActorEnvelope,
              com.kekwy.unifabric.proto.actor.ActorEnvelope>(
                service, METHODID_ACTOR_CHANNEL)))
        .build();
  }

  private static abstract class ActorRegistrationServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ActorRegistrationServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.kekwy.unifabric.proto.provider.ActorRegistration.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ActorRegistrationService");
    }
  }

  private static final class ActorRegistrationServiceFileDescriptorSupplier
      extends ActorRegistrationServiceBaseDescriptorSupplier {
    ActorRegistrationServiceFileDescriptorSupplier() {}
  }

  private static final class ActorRegistrationServiceMethodDescriptorSupplier
      extends ActorRegistrationServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    ActorRegistrationServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (ActorRegistrationServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ActorRegistrationServiceFileDescriptorSupplier())
              .addMethod(getActorChannelMethod())
              .build();
        }
      }
    }
    return result;
  }
}
