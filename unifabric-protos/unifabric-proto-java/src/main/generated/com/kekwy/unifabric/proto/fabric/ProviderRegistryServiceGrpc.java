package com.kekwy.unifabric.proto.fabric;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.64.0)",
    comments = "Source: unifabric/fabric/provider_registration.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class ProviderRegistryServiceGrpc {

  private ProviderRegistryServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "unifabric.fabric.ProviderRegistryService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.kekwy.unifabric.proto.provider.RegisterProviderRequest,
      com.kekwy.unifabric.proto.provider.RegisterProviderResponse> getRegisterProviderMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RegisterProvider",
      requestType = com.kekwy.unifabric.proto.provider.RegisterProviderRequest.class,
      responseType = com.kekwy.unifabric.proto.provider.RegisterProviderResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.kekwy.unifabric.proto.provider.RegisterProviderRequest,
      com.kekwy.unifabric.proto.provider.RegisterProviderResponse> getRegisterProviderMethod() {
    io.grpc.MethodDescriptor<com.kekwy.unifabric.proto.provider.RegisterProviderRequest, com.kekwy.unifabric.proto.provider.RegisterProviderResponse> getRegisterProviderMethod;
    if ((getRegisterProviderMethod = ProviderRegistryServiceGrpc.getRegisterProviderMethod) == null) {
      synchronized (ProviderRegistryServiceGrpc.class) {
        if ((getRegisterProviderMethod = ProviderRegistryServiceGrpc.getRegisterProviderMethod) == null) {
          ProviderRegistryServiceGrpc.getRegisterProviderMethod = getRegisterProviderMethod =
              io.grpc.MethodDescriptor.<com.kekwy.unifabric.proto.provider.RegisterProviderRequest, com.kekwy.unifabric.proto.provider.RegisterProviderResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RegisterProvider"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.unifabric.proto.provider.RegisterProviderRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.unifabric.proto.provider.RegisterProviderResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ProviderRegistryServiceMethodDescriptorSupplier("RegisterProvider"))
              .build();
        }
      }
    }
    return getRegisterProviderMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.kekwy.unifabric.proto.provider.ControlEnvelope,
      com.kekwy.unifabric.proto.provider.ControlEnvelope> getControlChannelMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ControlChannel",
      requestType = com.kekwy.unifabric.proto.provider.ControlEnvelope.class,
      responseType = com.kekwy.unifabric.proto.provider.ControlEnvelope.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<com.kekwy.unifabric.proto.provider.ControlEnvelope,
      com.kekwy.unifabric.proto.provider.ControlEnvelope> getControlChannelMethod() {
    io.grpc.MethodDescriptor<com.kekwy.unifabric.proto.provider.ControlEnvelope, com.kekwy.unifabric.proto.provider.ControlEnvelope> getControlChannelMethod;
    if ((getControlChannelMethod = ProviderRegistryServiceGrpc.getControlChannelMethod) == null) {
      synchronized (ProviderRegistryServiceGrpc.class) {
        if ((getControlChannelMethod = ProviderRegistryServiceGrpc.getControlChannelMethod) == null) {
          ProviderRegistryServiceGrpc.getControlChannelMethod = getControlChannelMethod =
              io.grpc.MethodDescriptor.<com.kekwy.unifabric.proto.provider.ControlEnvelope, com.kekwy.unifabric.proto.provider.ControlEnvelope>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ControlChannel"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.unifabric.proto.provider.ControlEnvelope.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.unifabric.proto.provider.ControlEnvelope.getDefaultInstance()))
              .setSchemaDescriptor(new ProviderRegistryServiceMethodDescriptorSupplier("ControlChannel"))
              .build();
        }
      }
    }
    return getControlChannelMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.kekwy.unifabric.proto.provider.DeploymentEnvelope,
      com.kekwy.unifabric.proto.provider.DeploymentEnvelope> getDeploymentChannelMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "DeploymentChannel",
      requestType = com.kekwy.unifabric.proto.provider.DeploymentEnvelope.class,
      responseType = com.kekwy.unifabric.proto.provider.DeploymentEnvelope.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<com.kekwy.unifabric.proto.provider.DeploymentEnvelope,
      com.kekwy.unifabric.proto.provider.DeploymentEnvelope> getDeploymentChannelMethod() {
    io.grpc.MethodDescriptor<com.kekwy.unifabric.proto.provider.DeploymentEnvelope, com.kekwy.unifabric.proto.provider.DeploymentEnvelope> getDeploymentChannelMethod;
    if ((getDeploymentChannelMethod = ProviderRegistryServiceGrpc.getDeploymentChannelMethod) == null) {
      synchronized (ProviderRegistryServiceGrpc.class) {
        if ((getDeploymentChannelMethod = ProviderRegistryServiceGrpc.getDeploymentChannelMethod) == null) {
          ProviderRegistryServiceGrpc.getDeploymentChannelMethod = getDeploymentChannelMethod =
              io.grpc.MethodDescriptor.<com.kekwy.unifabric.proto.provider.DeploymentEnvelope, com.kekwy.unifabric.proto.provider.DeploymentEnvelope>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "DeploymentChannel"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.unifabric.proto.provider.DeploymentEnvelope.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.unifabric.proto.provider.DeploymentEnvelope.getDefaultInstance()))
              .setSchemaDescriptor(new ProviderRegistryServiceMethodDescriptorSupplier("DeploymentChannel"))
              .build();
        }
      }
    }
    return getDeploymentChannelMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.kekwy.unifabric.proto.provider.SignalingEnvelope,
      com.kekwy.unifabric.proto.provider.SignalingEnvelope> getSignalingChannelMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SignalingChannel",
      requestType = com.kekwy.unifabric.proto.provider.SignalingEnvelope.class,
      responseType = com.kekwy.unifabric.proto.provider.SignalingEnvelope.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<com.kekwy.unifabric.proto.provider.SignalingEnvelope,
      com.kekwy.unifabric.proto.provider.SignalingEnvelope> getSignalingChannelMethod() {
    io.grpc.MethodDescriptor<com.kekwy.unifabric.proto.provider.SignalingEnvelope, com.kekwy.unifabric.proto.provider.SignalingEnvelope> getSignalingChannelMethod;
    if ((getSignalingChannelMethod = ProviderRegistryServiceGrpc.getSignalingChannelMethod) == null) {
      synchronized (ProviderRegistryServiceGrpc.class) {
        if ((getSignalingChannelMethod = ProviderRegistryServiceGrpc.getSignalingChannelMethod) == null) {
          ProviderRegistryServiceGrpc.getSignalingChannelMethod = getSignalingChannelMethod =
              io.grpc.MethodDescriptor.<com.kekwy.unifabric.proto.provider.SignalingEnvelope, com.kekwy.unifabric.proto.provider.SignalingEnvelope>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SignalingChannel"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.unifabric.proto.provider.SignalingEnvelope.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.unifabric.proto.provider.SignalingEnvelope.getDefaultInstance()))
              .setSchemaDescriptor(new ProviderRegistryServiceMethodDescriptorSupplier("SignalingChannel"))
              .build();
        }
      }
    }
    return getSignalingChannelMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ProviderRegistryServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ProviderRegistryServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ProviderRegistryServiceStub>() {
        @java.lang.Override
        public ProviderRegistryServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ProviderRegistryServiceStub(channel, callOptions);
        }
      };
    return ProviderRegistryServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ProviderRegistryServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ProviderRegistryServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ProviderRegistryServiceBlockingStub>() {
        @java.lang.Override
        public ProviderRegistryServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ProviderRegistryServiceBlockingStub(channel, callOptions);
        }
      };
    return ProviderRegistryServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ProviderRegistryServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ProviderRegistryServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ProviderRegistryServiceFutureStub>() {
        @java.lang.Override
        public ProviderRegistryServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ProviderRegistryServiceFutureStub(channel, callOptions);
        }
      };
    return ProviderRegistryServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void registerProvider(com.kekwy.unifabric.proto.provider.RegisterProviderRequest request,
        io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.provider.RegisterProviderResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRegisterProviderMethod(), responseObserver);
    }

    /**
     */
    default io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.provider.ControlEnvelope> controlChannel(
        io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.provider.ControlEnvelope> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getControlChannelMethod(), responseObserver);
    }

    /**
     */
    default io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.provider.DeploymentEnvelope> deploymentChannel(
        io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.provider.DeploymentEnvelope> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getDeploymentChannelMethod(), responseObserver);
    }

    /**
     */
    default io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.provider.SignalingEnvelope> signalingChannel(
        io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.provider.SignalingEnvelope> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getSignalingChannelMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service ProviderRegistryService.
   */
  public static abstract class ProviderRegistryServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return ProviderRegistryServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service ProviderRegistryService.
   */
  public static final class ProviderRegistryServiceStub
      extends io.grpc.stub.AbstractAsyncStub<ProviderRegistryServiceStub> {
    private ProviderRegistryServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ProviderRegistryServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ProviderRegistryServiceStub(channel, callOptions);
    }

    /**
     */
    public void registerProvider(com.kekwy.unifabric.proto.provider.RegisterProviderRequest request,
        io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.provider.RegisterProviderResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRegisterProviderMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.provider.ControlEnvelope> controlChannel(
        io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.provider.ControlEnvelope> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getControlChannelMethod(), getCallOptions()), responseObserver);
    }

    /**
     */
    public io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.provider.DeploymentEnvelope> deploymentChannel(
        io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.provider.DeploymentEnvelope> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getDeploymentChannelMethod(), getCallOptions()), responseObserver);
    }

    /**
     */
    public io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.provider.SignalingEnvelope> signalingChannel(
        io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.provider.SignalingEnvelope> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getSignalingChannelMethod(), getCallOptions()), responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service ProviderRegistryService.
   */
  public static final class ProviderRegistryServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<ProviderRegistryServiceBlockingStub> {
    private ProviderRegistryServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ProviderRegistryServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ProviderRegistryServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.kekwy.unifabric.proto.provider.RegisterProviderResponse registerProvider(com.kekwy.unifabric.proto.provider.RegisterProviderRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRegisterProviderMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service ProviderRegistryService.
   */
  public static final class ProviderRegistryServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<ProviderRegistryServiceFutureStub> {
    private ProviderRegistryServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ProviderRegistryServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ProviderRegistryServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.kekwy.unifabric.proto.provider.RegisterProviderResponse> registerProvider(
        com.kekwy.unifabric.proto.provider.RegisterProviderRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRegisterProviderMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_REGISTER_PROVIDER = 0;
  private static final int METHODID_CONTROL_CHANNEL = 1;
  private static final int METHODID_DEPLOYMENT_CHANNEL = 2;
  private static final int METHODID_SIGNALING_CHANNEL = 3;

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
        case METHODID_REGISTER_PROVIDER:
          serviceImpl.registerProvider((com.kekwy.unifabric.proto.provider.RegisterProviderRequest) request,
              (io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.provider.RegisterProviderResponse>) responseObserver);
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
        case METHODID_CONTROL_CHANNEL:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.controlChannel(
              (io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.provider.ControlEnvelope>) responseObserver);
        case METHODID_DEPLOYMENT_CHANNEL:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.deploymentChannel(
              (io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.provider.DeploymentEnvelope>) responseObserver);
        case METHODID_SIGNALING_CHANNEL:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.signalingChannel(
              (io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.provider.SignalingEnvelope>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getRegisterProviderMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.kekwy.unifabric.proto.provider.RegisterProviderRequest,
              com.kekwy.unifabric.proto.provider.RegisterProviderResponse>(
                service, METHODID_REGISTER_PROVIDER)))
        .addMethod(
          getControlChannelMethod(),
          io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
            new MethodHandlers<
              com.kekwy.unifabric.proto.provider.ControlEnvelope,
              com.kekwy.unifabric.proto.provider.ControlEnvelope>(
                service, METHODID_CONTROL_CHANNEL)))
        .addMethod(
          getDeploymentChannelMethod(),
          io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
            new MethodHandlers<
              com.kekwy.unifabric.proto.provider.DeploymentEnvelope,
              com.kekwy.unifabric.proto.provider.DeploymentEnvelope>(
                service, METHODID_DEPLOYMENT_CHANNEL)))
        .addMethod(
          getSignalingChannelMethod(),
          io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
            new MethodHandlers<
              com.kekwy.unifabric.proto.provider.SignalingEnvelope,
              com.kekwy.unifabric.proto.provider.SignalingEnvelope>(
                service, METHODID_SIGNALING_CHANNEL)))
        .build();
  }

  private static abstract class ProviderRegistryServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ProviderRegistryServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.kekwy.unifabric.proto.fabric.ProviderRegistration.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ProviderRegistryService");
    }
  }

  private static final class ProviderRegistryServiceFileDescriptorSupplier
      extends ProviderRegistryServiceBaseDescriptorSupplier {
    ProviderRegistryServiceFileDescriptorSupplier() {}
  }

  private static final class ProviderRegistryServiceMethodDescriptorSupplier
      extends ProviderRegistryServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    ProviderRegistryServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (ProviderRegistryServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ProviderRegistryServiceFileDescriptorSupplier())
              .addMethod(getRegisterProviderMethod())
              .addMethod(getControlChannelMethod())
              .addMethod(getDeploymentChannelMethod())
              .addMethod(getSignalingChannelMethod())
              .build();
        }
      }
    }
    return result;
  }
}
