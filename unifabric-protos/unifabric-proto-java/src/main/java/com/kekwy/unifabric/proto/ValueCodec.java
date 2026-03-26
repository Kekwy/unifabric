package com.kekwy.unifabric.proto;

import com.google.protobuf.ByteString;
import com.kekwy.unifabric.proto.common.*;

import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Java 对象 ↔ {@link Value} 的编解码器。
 *
 * <h3>编码（encode）</h3>
 * <pre>{@code
 * Value v = ValueCodec.encode("hello");                          // 自动推断 Type
 * Value v = ValueCodec.encode(42);                               // INT32
 * Value v = ValueCodec.encode(myPojo);                           // POJO → STRUCT
 * Value v = ValueCodec.encode(list, Types.array(Types.INT32));   // 显式指定 Type
 * }</pre>
 *
 * <h3>解码（decode）</h3>
 * <pre>{@code
 * String s   = ValueCodec.decode(v, String.class);
 * MyPojo obj = ValueCodec.decode(v, MyPojo.class);   // STRUCT → POJO
 * Object raw = ValueCodec.decode(v);                 // STRUCT 解码为 Map<String,Object>
 * }</pre>
 */
public final class ValueCodec {

    private ValueCodec() {}

    /* ================================================================
     *  Encode
     * ================================================================ */

    /**
     * 编码 Java 对象，自动推断 {@link Type}。
     * <p>
     * 支持 {@code String / Integer / Long / Float / Double / Boolean / byte[] / List / Map}
     * 以及 POJO（映射为 STRUCT）。
     * {@code Short / Byte} 提升为 INT32。
     */
    public static Value encode(Object value) {
        if (value == null) {
            return Value.newBuilder()
                    .setNullValue(NullValue.getDefaultInstance()).build();
        }
        return encode(value, inferType(value));
    }

    /**
     * 使用显式 {@link Type} 编码 Java 对象。
     */
    public static Value encode(Object value, Type type) {
        if (value == null) {
            return Value.newBuilder()
                    .setNullValue(NullValue.getDefaultInstance()).build();
        }
        Value.Builder b = Value.newBuilder();
        switch (type.getKind()) {
            case TYPE_KIND_STRING  -> b.setStringValue((String) value);
            case TYPE_KIND_INT32   -> b.setInt32Value(((Number) value).intValue());
            case TYPE_KIND_INT64   -> b.setInt64Value(((Number) value).longValue());
            case TYPE_KIND_FLOAT   -> b.setFloatValue(((Number) value).floatValue());
            case TYPE_KIND_DOUBLE  -> b.setDoubleValue(((Number) value).doubleValue());
            case TYPE_KIND_BOOLEAN -> b.setBoolValue((Boolean) value);
            case TYPE_KIND_BYTES   -> b.setBytesValue(
                    value instanceof byte[] arr
                            ? ByteString.copyFrom(arr)
                            : (ByteString) value);
            case TYPE_KIND_NULL    -> b.setNullValue(NullValue.getDefaultInstance());
            case TYPE_KIND_ARRAY   -> b.setArrayValue(encodeArray((List<?>) value, type));
            case TYPE_KIND_MAP     -> b.setMapValue(encodeMap((Map<?, ?>) value, type));
            case TYPE_KIND_STRUCT  -> b.setStructValue(encodeStruct(value, type));
            default -> throw new IllegalArgumentException(
                    "Unsupported TypeKind: " + type.getKind());
        }
        return b.build();
    }

    private static ArrayValue encodeArray(List<?> list, Type type) {
        Type elemType = type.getArrayDetail().getElementType();
        ArrayValue.Builder b = ArrayValue.newBuilder();
        for (Object elem : list) {
            b.addElements(encode(elem, elemType));
        }
        return b.build();
    }

    private static MapValue encodeMap(Map<?, ?> map, Type type) {
        MapTypeDetail detail = type.getMapDetail();
        MapKeyKind keyKind = detail.getKeyKind();
        Type valueType = detail.getValueType();
        MapValue.Builder b = MapValue.newBuilder();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            MapEntry.Builder entry = MapEntry.newBuilder();
            setMapKey(entry, e.getKey(), keyKind);
            entry.setValue(encode(e.getValue(), valueType));
            b.addEntries(entry);
        }
        return b.build();
    }

    private static void setMapKey(MapEntry.Builder entry, Object key, MapKeyKind keyKind) {
        switch (keyKind) {
            case MAP_KEY_KIND_STRING  -> entry.setStringKey((String) key);
            case MAP_KEY_KIND_INT32   -> entry.setInt32Key(((Number) key).intValue());
            case MAP_KEY_KIND_INT64   -> entry.setInt64Key(((Number) key).longValue());
            case MAP_KEY_KIND_BOOLEAN -> entry.setBoolKey((Boolean) key);
            default -> throw new IllegalArgumentException(
                    "Unsupported MapKeyKind: " + keyKind);
        }
    }

    private static StructValue encodeStruct(Object value, Type type) {
        StructTypeDetail detail = type.getStructDetail();
        StructValue.Builder b = StructValue.newBuilder();
        if (value instanceof Map<?, ?> map) {
            for (StructField field : detail.getFieldsList()) {
                b.addFields(StructFieldValue.newBuilder()
                        .setName(field.getName())
                        .setValue(encode(map.get(field.getName()), field.getType())));
            }
        } else {
            Class<?> clazz = value.getClass();
            for (StructField field : detail.getFieldsList()) {
                try {
                    java.lang.reflect.Field jf = clazz.getDeclaredField(field.getName());
                    jf.setAccessible(true);
                    b.addFields(StructFieldValue.newBuilder()
                            .setName(field.getName())
                            .setValue(encode(jf.get(value), field.getType())));
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    throw new RuntimeException(
                            "Cannot read field '" + field.getName()
                                    + "' from " + clazz.getName(), e);
                }
            }
        }
        return b.build();
    }

    /* ================================================================
     *  Decode
     * ================================================================ */

    /**
     * 解码 {@link Value} 为 Java 对象。
     * <p>
     * STRUCT 类型解码为 {@code Map<String, Object>}；
     * 如需还原为 POJO，请使用 {@link #decode(Value, Class)}。
     */
    public static Object decode(Value val) {
        Objects.requireNonNull(val, "value must not be null");
        return switch (val.getKindCase()) {
            case STRING_VALUE -> val.getStringValue();
            case INT32_VALUE  -> val.getInt32Value();
            case INT64_VALUE  -> val.getInt64Value();
            case FLOAT_VALUE  -> val.getFloatValue();
            case DOUBLE_VALUE -> val.getDoubleValue();
            case BOOL_VALUE   -> val.getBoolValue();
            case BYTES_VALUE  -> val.getBytesValue().toByteArray();
            case NULL_VALUE   -> null;
            case ARRAY_VALUE  -> decodeArray(val.getArrayValue());
            case MAP_VALUE    -> decodeMap(val.getMapValue());
            case STRUCT_VALUE -> decodeStruct(val.getStructValue());
            case KIND_NOT_SET -> null;
        };
    }

    /**
     * 解码 {@link Value} 为指定 Java 类型。
     * <p>
     * 当 STRUCT 且目标不是 {@code Map} 时，
     * 会通过反射将字段值注入 POJO（要求无参构造器）。
     */
    @SuppressWarnings("unchecked")
    public static <T> T decode(Value val, Class<T> clazz) {
        Objects.requireNonNull(val, "value must not be null");
        Objects.requireNonNull(clazz, "clazz must not be null");
        Object decoded = decode(val);
        if (decoded == null) {
            return null;
        }
        if (clazz.isInstance(decoded)) {
            return clazz.cast(decoded);
        }
        if (val.getKindCase() == Value.KindCase.STRUCT_VALUE
                && decoded instanceof Map) {
            return mapToPojo((Map<String, Object>) decoded, clazz);
        }
        throw new IllegalArgumentException(
                "Cannot convert " + decoded.getClass().getName()
                        + " to " + clazz.getName());
    }

    /**
     * 根据 proto Type 解码 Value。当 type 为 STRUCT 且 struct_detail.class_name 非空时，
     * 使用给定 ClassLoader 加载该类并将 STRUCT 转为 POJO；否则按 {@link #decode(Value)} 解码。
     */
    @SuppressWarnings("unchecked")
    public static Object decode(Value val, Type type, ClassLoader classLoader) {
        Objects.requireNonNull(val, "value must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(classLoader, "classLoader must not be null");
        if (val.getKindCase() == Value.KindCase.KIND_NOT_SET) {
            return null;
        }
        if (type.getKind() == TypeKind.TYPE_KIND_STRUCT && type.hasStructDetail()) {
            StructTypeDetail structDetail = type.getStructDetail();
            if (!structDetail.getClassName().isEmpty()) {
                Object decoded = decode(val);
                if (decoded == null) return null;
                if (decoded instanceof Map) {
                    try {
                        Class<?> clazz = classLoader.loadClass(structDetail.getClassName());
                        return mapToPojo((Map<String, Object>) decoded, clazz);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException("Cannot load class " + structDetail.getClassName(), e);
                    }
                }
            }
        }
        return decode(val);
    }

    private static List<Object> decodeArray(ArrayValue arr) {
        List<Object> list = new ArrayList<>(arr.getElementsCount());
        for (Value elem : arr.getElementsList()) {
            list.add(decode(elem));
        }
        return list;
    }

    private static Map<Object, Object> decodeMap(MapValue map) {
        Map<Object, Object> result = new LinkedHashMap<>(map.getEntriesCount());
        for (MapEntry entry : map.getEntriesList()) {
            result.put(decodeMapKey(entry), decode(entry.getValue()));
        }
        return result;
    }

    private static Object decodeMapKey(MapEntry entry) {
        return switch (entry.getKeyCase()) {
            case STRING_KEY -> entry.getStringKey();
            case INT32_KEY  -> entry.getInt32Key();
            case INT64_KEY  -> entry.getInt64Key();
            case BOOL_KEY   -> entry.getBoolKey();
            case KEY_NOT_SET -> null;
        };
    }

    private static Map<String, Object> decodeStruct(StructValue struct) {
        Map<String, Object> map = new LinkedHashMap<>(struct.getFieldsCount());
        for (StructFieldValue field : struct.getFieldsList()) {
            map.put(field.getName(), decode(field.getValue()));
        }
        return map;
    }

    /* ================================================================
     *  Type inference
     * ================================================================ */

    /**
     * 根据 Java 对象推断 {@link Type}。
     * <p>
     * {@code Short / Byte} 提升为 INT32。
     * 不可识别的对象类型通过反射映射为 STRUCT。
     */
    public static Type inferType(Object value) {
        if (value == null)                                              return Types.NULL;
        if (value instanceof String)                                    return Types.STRING;
        if (value instanceof Integer
                || value instanceof Short
                || value instanceof Byte)                               return Types.INT32;
        if (value instanceof Long)                                      return Types.INT64;
        if (value instanceof Float)                                     return Types.FLOAT;
        if (value instanceof Double)                                    return Types.DOUBLE;
        if (value instanceof Boolean)                                   return Types.BOOLEAN;
        if (value instanceof byte[] || value instanceof ByteString)     return Types.BYTES;

        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return Types.array(Types.STRING);
            }
            return Types.array(inferType(list.get(0)));
        }
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                return Types.map(MapKeyKind.MAP_KEY_KIND_STRING, Types.STRING);
            }
            Map.Entry<?, ?> first = map.entrySet().iterator().next();
            return Types.map(
                    inferMapKeyKind(first.getKey()),
                    inferType(first.getValue()));
        }
        return inferStructType(value);
    }

    private static MapKeyKind inferMapKeyKind(Object key) {
        if (key instanceof String)                                      return MapKeyKind.MAP_KEY_KIND_STRING;
        if (key instanceof Integer
                || key instanceof Short
                || key instanceof Byte)                                 return MapKeyKind.MAP_KEY_KIND_INT32;
        if (key instanceof Long)                                        return MapKeyKind.MAP_KEY_KIND_INT64;
        if (key instanceof Boolean)                                     return MapKeyKind.MAP_KEY_KIND_BOOLEAN;
        throw new IllegalArgumentException(
                "Unsupported map key type: " + key.getClass().getName()
                        + ". Only String, Integer, Long, Boolean are supported.");
    }

    private static Type inferStructType(Object obj) {
        StructTypeDetail.Builder detail = StructTypeDetail.newBuilder();
        for (java.lang.reflect.Field f : obj.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())
                    || Modifier.isTransient(f.getModifiers())
                    || f.isSynthetic()) {
                continue;
            }
            f.setAccessible(true);
            try {
                Object fv = f.get(obj);
                Type ft = (fv != null) ? inferType(fv) : Types.STRING;
                detail.addFields(StructField.newBuilder()
                        .setName(f.getName())
                        .setType(ft));
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Cannot access field: " + f.getName(), e);
            }
        }
        return Type.newBuilder()
                .setKind(TypeKind.TYPE_KIND_STRUCT)
                .setStructDetail(detail)
                .build();
    }

    /* ================================================================
     *  Map → POJO
     * ================================================================ */

    private static <T> T mapToPojo(Map<String, Object> map, Class<T> clazz) {
        try {
            if (clazz.isRecord()) {
                return mapToRecord(map, clazz);
            }
            T instance = clazz.getDeclaredConstructor().newInstance();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                try {
                    java.lang.reflect.Field f = clazz.getDeclaredField(entry.getKey());
                    f.setAccessible(true);
                    f.set(instance, coerce(entry.getValue(), f.getType()));
                } catch (NoSuchFieldException ignored) {
                }
            }
            return instance;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Cannot instantiate " + clazz.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapToRecord(Map<String, Object> map, Class<T> clazz)
            throws ReflectiveOperationException {
        var components = clazz.getRecordComponents();
        Class<?>[] paramTypes = new Class<?>[components.length];
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            paramTypes[i] = components[i].getType();
            Object raw = map.get(components[i].getName());
            args[i] = coerce(raw, paramTypes[i]);
        }
        var ctor = clazz.getDeclaredConstructor(paramTypes);
        ctor.setAccessible(true);
        return (T) ctor.newInstance(args);
    }

    /**
     * 数值类型窄化 / 宽化适配，以及嵌套 Map → POJO 的递归转换。
     */
    @SuppressWarnings("unchecked")
    private static Object coerce(Object value, Class<?> target) {
        if (value == null || target.isInstance(value)) {
            return value;
        }
        if (value instanceof Number num) {
            if (target == int.class     || target == Integer.class)  return num.intValue();
            if (target == long.class    || target == Long.class)     return num.longValue();
            if (target == double.class  || target == Double.class)   return num.doubleValue();
            if (target == float.class   || target == Float.class)    return num.floatValue();
            if (target == short.class   || target == Short.class)    return num.shortValue();
            if (target == byte.class    || target == Byte.class)     return num.byteValue();
        }
        if (value instanceof Map && !Map.class.isAssignableFrom(target)) {
            return mapToPojo((Map<String, Object>) value, target);
        }
        return value;
    }
}
