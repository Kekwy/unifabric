package com.kekwy.unifabric.actor;

import com.kekwy.unifabric.proto.common.FunctionDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Java Actor 运行时入口：从环境变量加载配置，反序列化函数，连接 Provider 并处理消息。
 */
public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            ActorConfig config = ActorConfig.fromEnvironment();
            log.info("Actor 启动: actorId={}, registry={}", config.getActorId(), config.getRegistryAddr());

            FunctionDescriptor mainFd = FunctionDescriptorLoader.loadMain(config.getFunctionFile());
            Map<Integer, FunctionDescriptor> conditions = config.hasConditionFunctions()
                    ? FunctionDescriptorLoader.loadConditions(config.getConditionFunctionsDir())
                    : Map.of();

            UserJarLoader jarLoader = UserJarLoader.create(config.getArtifactPath());
            FunctionInvoker invoker = new FunctionInvoker(mainFd, jarLoader, config.getNodeKind());
            ConditionEvaluator conditionEvaluator = ConditionEvaluator.fromDescriptors(conditions, jarLoader);

            ActorChannelClient client = new ActorChannelClient(
                    config.getRegistryAddr(),
                    config.getActorId(),
                    invoker,
                    conditionEvaluator,
                    config.getCombineTimeoutMs()
            );

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("收到关闭信号，正在关闭 Actor...");
                client.shutdown();
            }));

            client.start();
            client.awaitClosed();
            log.info("Actor 已退出");
        } catch (IllegalArgumentException e) {
            log.error("配置错误: {}", e.getMessage());
            System.exit(1);
        } catch (IOException | ClassNotFoundException e) {
            log.error("加载函数或条件失败", e);
            System.exit(1);
        } catch (Throwable t) {
            log.error("Actor 异常退出", t);
            System.exit(1);
        }
    }
}
