package com.kekwy.unifabric.fabric.messaging;

import com.kekwy.unifabric.fabric.actor.ActorMessage;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ActorMessageInbox {

    private final BlockingQueue<ActorMessage> queue = new LinkedBlockingQueue<>();

    public ActorMessage get() {
        try {
            return queue.poll(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public void put(ActorMessage message) {
        queue.add(message);
    }

}
