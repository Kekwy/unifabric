package com.kekwy.unifabric.proto.fabric;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * 跨域调度：远程管理节点在本域执行与本地相同的筛选—评分—部署流程（论文 3.4.2）
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.64.0)",
    comments = "Source: unifabric/fabric/scheduling_service.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class SchedulingServiceGrpc {

  private SchedulingServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "unifabric.fabric.SchedulingService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.kekwy.unifabric.proto.fabric.ScheduleDeployRequest,
      com.kekwy.unifabric.proto.fabric.ScheduleDeployResponse> getScheduleDeployMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ScheduleDeploy",
      requestType = com.kekwy.unifabric.proto.fabric.ScheduleDeployRequest.class,
      responseType = com.kekwy.unifabric.proto.fabric.ScheduleDeployResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.kekwy.unifabric.proto.fabric.ScheduleDeployRequest,
      com.kekwy.unifabric.proto.fabric.ScheduleDeployResponse> getScheduleDeployMethod() {
    io.grpc.MethodDescriptor<com.kekwy.unifabric.proto.fabric.ScheduleDeployRequest, com.kekwy.unifabric.proto.fabric.ScheduleDeployResponse> getScheduleDeployMethod;
    if ((getScheduleDeployMethod = SchedulingServiceGrpc.getScheduleDeployMethod) == null) {
      synchronized (SchedulingServiceGrpc.class) {
        if ((getScheduleDeployMethod = SchedulingServiceGrpc.getScheduleDeployMethod) == null) {
          SchedulingServiceGrpc.getScheduleDeployMethod = getScheduleDeployMethod =
              io.grpc.MethodDescriptor.<com.kekwy.unifabric.proto.fabric.ScheduleDeployRequest, com.kekwy.unifabric.proto.fabric.ScheduleDeployResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ScheduleDeploy"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.unifabric.proto.fabric.ScheduleDeployRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.unifabric.proto.fabric.ScheduleDeployResponse.getDefaultInstance()))
              .setSchemaDescriptor(new SchedulingServiceMethodDescriptorSupplier("ScheduleDeploy"))
              .build();
        }
      }
    }
    return getScheduleDeployMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static SchedulingServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SchedulingServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SchedulingServiceStub>() {
        @java.lang.Override
        public SchedulingServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SchedulingServiceStub(channel, callOptions);
        }
      };
    return SchedulingServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static SchedulingServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SchedulingServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SchedulingServiceBlockingStub>() {
        @java.lang.Override
        public SchedulingServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SchedulingServiceBlockingStub(channel, callOptions);
        }
      };
    return SchedulingServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static SchedulingServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<SchedulingServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<SchedulingServiceFutureStub>() {
        @java.lang.Override
        public SchedulingServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new SchedulingServiceFutureStub(channel, callOptions);
        }
      };
    return SchedulingServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * 跨域调度：远程管理节点在本域执行与本地相同的筛选—评分—部署流程（论文 3.4.2）
   * </pre>
   */
  public interface AsyncService {

    /**
     */
    default void scheduleDeploy(com.kekwy.unifabric.proto.fabric.ScheduleDeployRequest request,
        io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.fabric.ScheduleDeployResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getScheduleDeployMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service SchedulingService.
   * <pre>
   * 跨域调度：远程管理节点在本域执行与本地相同的筛选—评分—部署流程（论文 3.4.2）
   * </pre>
   */
  public static abstract class SchedulingServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return SchedulingServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service SchedulingService.
   * <pre>
   * 跨域调度：远程管理节点在本域执行与本地相同的筛选—评分—部署流程（论文 3.4.2）
   * </pre>
   */
  public static final class SchedulingServiceStub
      extends io.grpc.stub.AbstractAsyncStub<SchedulingServiceStub> {
    private SchedulingServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SchedulingServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SchedulingServiceStub(channel, callOptions);
    }

    /**
     */
    public void scheduleDeploy(com.kekwy.unifabric.proto.fabric.ScheduleDeployRequest request,
        io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.fabric.ScheduleDeployResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getScheduleDeployMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service SchedulingService.
   * <pre>
   * 跨域调度：远程管理节点在本域执行与本地相同的筛选—评分—部署流程（论文 3.4.2）
   * </pre>
   */
  public static final class SchedulingServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<SchedulingServiceBlockingStub> {
    private SchedulingServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SchedulingServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SchedulingServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.kekwy.unifabric.proto.fabric.ScheduleDeployResponse scheduleDeploy(com.kekwy.unifabric.proto.fabric.ScheduleDeployRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getScheduleDeployMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service SchedulingService.
   * <pre>
   * 跨域调度：远程管理节点在本域执行与本地相同的筛选—评分—部署流程（论文 3.4.2）
   * </pre>
   */
  public static final class SchedulingServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<SchedulingServiceFutureStub> {
    private SchedulingServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected SchedulingServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new SchedulingServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.kekwy.unifabric.proto.fabric.ScheduleDeployResponse> scheduleDeploy(
        com.kekwy.unifabric.proto.fabric.ScheduleDeployRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getScheduleDeployMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_SCHEDULE_DEPLOY = 0;

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
        case METHODID_SCHEDULE_DEPLOY:
          serviceImpl.scheduleDeploy((com.kekwy.unifabric.proto.fabric.ScheduleDeployRequest) request,
              (io.grpc.stub.StreamObserver<com.kekwy.unifabric.proto.fabric.ScheduleDeployResponse>) responseObserver);
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
          getScheduleDeployMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.kekwy.unifabric.proto.fabric.ScheduleDeployRequest,
              com.kekwy.unifabric.proto.fabric.ScheduleDeployResponse>(
                service, METHODID_SCHEDULE_DEPLOY)))
        .build();
  }

  private static abstract class SchedulingServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    SchedulingServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.kekwy.unifabric.proto.fabric.SchedulingServiceOuterClass.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("SchedulingService");
    }
  }

  private static final class SchedulingServiceFileDescriptorSupplier
      extends SchedulingServiceBaseDescriptorSupplier {
    SchedulingServiceFileDescriptorSupplier() {}
  }

  private static final class SchedulingServiceMethodDescriptorSupplier
      extends SchedulingServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    SchedulingServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (SchedulingServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new SchedulingServiceFileDescriptorSupplier())
              .addMethod(getScheduleDeployMethod())
              .build();
        }
      }
    }
    return result;
  }
}
