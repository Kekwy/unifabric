package com.kekwy.unifabric.adapter.registry;

import io.grpc.stub.StreamObserver;

/**
 * 延迟绑定的 StreamObserver 代理：构造时 delegate 为空，后续通过 setDelegate 注入，
 * 用于 gRPC 双向流中 Handler 需要持有「发送端」但发送端需由 stub.xxxChannel(receiver) 返回的场景。
 */
public class DelegatingObserver<T> implements StreamObserver<T> {

    private volatile StreamObserver<T> delegate;

    public void setDelegate(StreamObserver<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onNext(T value) {
        if (delegate != null) delegate.onNext(value);
    }

    @Override
    public void onError(Throwable t) {
        if (delegate != null) delegate.onError(t);
    }

    @Override
    public void onCompleted() {
        if (delegate != null) delegate.onCompleted();
    }
}
