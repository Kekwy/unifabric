package com.kekwy.unifabric.actor;

import com.kekwy.unifabric.proto.ValueCodec;
import com.kekwy.unifabric.proto.common.FunctionDescriptor;
import com.kekwy.unifabric.proto.common.TypeKind;
import com.kekwy.unifabric.proto.common.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 根据 FunctionDescriptor 反序列化用户函数，并按类型（Input/Task/Output/Combine）反射调用。
 * 不依赖 SDK 接口，仅通过元数据推断类型。
 */
public final class FunctionInvoker {

    private static final String OPTIONAL_VALUE_CLASS = "com.kekwy.unifabric.sdk.type.OptionalValue";

    private static final Logger log = LoggerFactory.getLogger(FunctionInvoker.class);

    private final FunctionDescriptor descriptor;
    private final Object function;
    private final Kind kind;
    private final UserJarLoader jarLoader;

    // 启动时从 Method 解析并缓存入参/返回值对应的 Java 类型，调用时直接使用
    private final Method inputNext;
    private final Method optionalIsEmpty;
    private final Method optionalGet;
    private final Method taskApply;
    private final Class<?> taskInputClass;
    private final Method outputAccept;
    private final Class<?> outputInputClass;
    private final Class<?> combineOptionalValueClass;
    private final Method combineOfNullable;
    private final Method combineEmpty;
    private final Method combineCombine;

    public enum Kind {
        INPUT,   // 0 inputs, has output -> next()  Deprecated
        TASK,    // 1 input, has output -> apply(I)
        OUTPUT,  // 1 input, no output -> accept(I)
        COMBINE  // 2 inputs, has output -> combine(OptionalValue, OptionalValue)
    }

    /**
     * 使用环境变量或描述符推断的节点类型创建。
     *
     * @param descriptor   函数描述符
     * @param jarLoader    用户 JAR ClassLoader
     * @param explicitKind 若非 null 则直接使用（如来自 IARNET_NODE_KIND），否则根据 descriptor 推断
     */
    public FunctionInvoker(FunctionDescriptor descriptor, UserJarLoader jarLoader, Kind explicitKind)
            throws IOException, ClassNotFoundException, NoSuchMethodException {
        this.descriptor = descriptor;
        this.jarLoader = jarLoader;
        if (!descriptor.getSerializedFunction().isEmpty()) {
            this.function = jarLoader.deserialize(descriptor.getSerializedFunction().toByteArray());
        } else {
            throw new IllegalArgumentException("FunctionDescriptor 缺少 serialized_function");
        }
        this.kind = explicitKind != null ? explicitKind : inferKind(descriptor);
        Method inputNext0 = null, optionalIsEmpty0 = null, optionalGet0 = null;
        Method taskApply0 = null;
        Class<?> taskInputClass0 = null;
        Method outputAccept0 = null;
        Class<?> outputInputClass0 = null;
        Class<?> combineOptionalValueClass0 = null;
        Method combineOfNullable0 = null, combineEmpty0 = null, combineCombine0 = null;
        Class<?> fnClass = this.function.getClass();
        switch (this.kind) {
            case INPUT -> {
                inputNext0 = fnClass.getMethod("next");
                setAccessible(inputNext0);
                optionalIsEmpty0 = Optional.class.getMethod("isEmpty");
                optionalGet0 = Optional.class.getMethod("get");
            }
            case TASK -> {
                taskApply0 = fnClass.getMethod("apply", Object.class);
                setAccessible(taskApply0);
                taskInputClass0 = resolveActualParamClass(fnClass, "apply", 1, 0);
            }
            case OUTPUT -> {
                outputAccept0 = fnClass.getMethod("accept", Object.class);
                setAccessible(outputAccept0);
                outputInputClass0 = resolveActualParamClass(fnClass, "accept", 1, 0);
            }
            case COMBINE -> {
                ClassLoader cl = fnClass.getClassLoader();
                combineOptionalValueClass0 = cl.loadClass(OPTIONAL_VALUE_CLASS);
                combineOfNullable0 = combineOptionalValueClass0.getMethod("ofNullable", Object.class);
                combineEmpty0 = combineOptionalValueClass0.getMethod("empty");
                combineCombine0 = fnClass.getMethod("combine", combineOptionalValueClass0, combineOptionalValueClass0);
                setAccessible(combineCombine0);
            }
        }
        this.inputNext = inputNext0;
        this.optionalIsEmpty = optionalIsEmpty0;
        this.optionalGet = optionalGet0;
        this.taskApply = taskApply0;
        this.taskInputClass = taskInputClass0;
        this.outputAccept = outputAccept0;
        this.outputInputClass = outputInputClass0;
        this.combineOptionalValueClass = combineOptionalValueClass0;
        this.combineOfNullable = combineOfNullable0;
        this.combineEmpty = combineEmpty0;
        this.combineCombine = combineCombine0;
        log.debug("FunctionInvoker 已创建: kind={}, identifier={}", kind, descriptor.getFunctionIdentifier());
    }

    /** 使反射可调用 lambda/合成类方法，避免 IllegalAccessException。 */
    private static void setAccessible(AccessibleObject accessible) {
        try {
            accessible.setAccessible(true);
        } catch (Exception e) {
            log.debug("setAccessible 被拒绝: {}", e.getMessage());
        }
    }

    /**
     * 解析函数方法的真实参数类型，绕过泛型擦除导致的 Object.class。
     * <p>策略：
     * <ol>
     *   <li>扫描非桥接方法：匿名类 / 具名类会生成带真实参数类型的重载（非 bridge）</li>
     *   <li>SerializedLambda：lambda 可通过 writeReplace 获取 instantiatedMethodType 解析真实类型</li>
     *   <li>兜底返回 Object.class</li>
     * </ol>
     */
    private Class<?> resolveActualParamClass(Class<?> fnClass, String methodName,
                                             int expectedParamCount, int paramIndex) {
        for (Method m : fnClass.getDeclaredMethods()) {
            if (methodName.equals(m.getName())
                    && m.getParameterCount() == expectedParamCount
                    && !m.isBridge() && !m.isSynthetic()
                    && m.getParameterTypes()[paramIndex] != Object.class) {
                Class<?> resolved = m.getParameterTypes()[paramIndex];
                log.debug("通过非桥接方法解析到参数类型: {}.{}[{}] -> {}",
                        fnClass.getSimpleName(), methodName, paramIndex, resolved.getName());
                return resolved;
            }
        }
        try {
            Method writeReplace = fnClass.getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            SerializedLambda sl = (SerializedLambda) writeReplace.invoke(function);
            String methodType = sl.getInstantiatedMethodType();
            List<String> paramClassNames = parseMethodDescriptorParams(methodType);
            if (paramIndex < paramClassNames.size()) {
                String className = paramClassNames.get(paramIndex);
                Class<?> resolved = jarLoader.getClassLoader().loadClass(className);
                log.debug("通过 SerializedLambda 解析到参数类型: {}.{}[{}] -> {}",
                        fnClass.getSimpleName(), methodName, paramIndex, resolved.getName());
                return resolved;
            }
        } catch (Exception e) {
            log.debug("SerializedLambda 解析失败: {}", e.getMessage());
        }
        return Object.class;
    }

    /** 解析 JVM 方法描述符的参数列表，如 "(Lcom/example/Foo;I)V" → ["com.example.Foo", ...] */
    private static List<String> parseMethodDescriptorParams(String desc) {
        List<String> params = new ArrayList<>();
        if (desc == null || !desc.startsWith("(")) return params;
        int i = 1;
        while (i < desc.length() && desc.charAt(i) != ')') {
            char c = desc.charAt(i);
            if (c == 'L') {
                int end = desc.indexOf(';', i);
                if (end < 0) break;
                params.add(desc.substring(i + 1, end).replace('/', '.'));
                i = end + 1;
            } else if (c == '[') {
                i++;
            } else {
                params.add(Object.class.getName());
                i++;
            }
        }
        return params;
    }

    /** 等价于 {@code new FunctionInvoker(descriptor, jarLoader, null)}，完全由描述符推断类型。 */
    public FunctionInvoker(FunctionDescriptor descriptor, UserJarLoader jarLoader)
            throws IOException, ClassNotFoundException, NoSuchMethodException {
        this(descriptor, jarLoader, null);
    }

    private static Kind inferKind(FunctionDescriptor fd) {
        int inputCount = fd.getInputsTypeCount();
        boolean hasOutput = fd.hasOutputType() && fd.getOutputType().getKind() != com.kekwy.unifabric.proto.common.TypeKind.TYPE_KIND_UNSPECIFIED;

        if (inputCount == 0 && hasOutput) {
            return Kind.INPUT;
        }
        if (inputCount == 1 && hasOutput) {
            return Kind.TASK;
        }
        //noinspection ConstantValue
        if (inputCount == 1 && !hasOutput) {
            return Kind.OUTPUT;
        }
        if (inputCount == 2 && hasOutput) {
            return Kind.COMBINE;
        }
        throw new IllegalArgumentException("无法推断函数类型: inputs=" + inputCount + ", hasOutput=" + hasOutput);
    }

    public Kind getKind() {
        return kind;
    }


    /**
     * Task 函数：单输入单输出，解码 input 后调用 apply，编码结果返回。
     * 优先使用 descriptor.inputs_type[0] 的 class_name 解码，否则用反射推断的类型。
     */
    public Object runTask(Value input) throws Throwable {
        if (kind != Kind.TASK) {
            throw new IllegalStateException("非 Task 函数");
        }
        Object decoded = decodeInput(input, 0, taskInputClass);
        return taskApply.invoke(function, decoded);
    }

    /**
     * Output（Sink）函数：解码 input 后调用 accept，无返回值。
     */
    public void runOutput(Value input) throws Throwable {
        if (kind != Kind.OUTPUT) {
            throw new IllegalStateException("非 Output 函数");
        }
        Object decoded = decodeInput(input, 0, outputInputClass);
        outputAccept.invoke(function, decoded);
    }

    /**
     * Combine 函数：两路输入（可为空），解码后包装为 OptionalValue 调用 combine。
     * 使用 descriptor.inputs_type 的 class_name 解码，不依赖反射推断。
     */
    public Object runCombine(Value input1, Value input2) throws Throwable {
        if (kind != Kind.COMBINE) {
            throw new IllegalStateException("非 Combine 函数");
        }
        // 无 row / 空 Value（含上游条件分支发的空响应）视为该路为空，转为 OptionalValue.empty()，立即完成汇聚
        Object left = input1 == null || input1.getKindCase() == Value.KindCase.KIND_NOT_SET
                ? null
                : ValueCodec.decode(input1, descriptor.getInputsType(0), jarLoader.getClassLoader());
        Object right = input2 == null || input2.getKindCase() == Value.KindCase.KIND_NOT_SET
                ? null
                : ValueCodec.decode(input2, descriptor.getInputsType(1), jarLoader.getClassLoader());
        Object optLeft = left == null ? combineEmpty.invoke(null) : combineOfNullable.invoke(null, left);
        Object optRight = right == null ? combineEmpty.invoke(null) : combineOfNullable.invoke(null, right);
        return combineCombine.invoke(function, optLeft, optRight);
    }

    /** 单输入解码：优先用 descriptor 的 Type+class_name，否则用 fallbackClass。 */
    private Object decodeInput(Value input, int inputIndex, Class<?> fallbackClass) {
        if (descriptor.getInputsTypeCount() > inputIndex) {
            var type = descriptor.getInputsType(inputIndex);
            if (type.getKind() == TypeKind.TYPE_KIND_STRUCT && type.hasStructDetail()
                    && !type.getStructDetail().getClassName().isEmpty()) {
                return ValueCodec.decode(input, type, jarLoader.getClassLoader());
            }
        }
        return ValueCodec.decode(input, fallbackClass);
    }

}
