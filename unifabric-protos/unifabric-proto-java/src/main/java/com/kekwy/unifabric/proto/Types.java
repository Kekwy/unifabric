package com.kekwy.unifabric.proto;

import com.kekwy.unifabric.proto.common.*;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;

/**
 * 便捷工具：构建 {@link Type} 实例以及从 Java 类型推断 {@link Type}。
 *
 * <pre>{@code
 * Type type = Types.struct(
 *     Types.field("name", Types.STRING),
 *     Types.field("age",  Types.INT32),
 *     Types.field("tags", Types.array(Types.STRING))
 * );
 * }</pre>
 */
public final class Types {

    public static final Type STRING  = primitive(TypeKind.TYPE_KIND_STRING);
    public static final Type INT32   = primitive(TypeKind.TYPE_KIND_INT32);
    public static final Type INT64   = primitive(TypeKind.TYPE_KIND_INT64);
    public static final Type FLOAT   = primitive(TypeKind.TYPE_KIND_FLOAT);
    public static final Type DOUBLE  = primitive(TypeKind.TYPE_KIND_DOUBLE);
    public static final Type BOOLEAN = primitive(TypeKind.TYPE_KIND_BOOLEAN);
    public static final Type BYTES   = primitive(TypeKind.TYPE_KIND_BYTES);
    public static final Type NULL    = primitive(TypeKind.TYPE_KIND_NULL);

    private Types() {}

    /* ---------- composite builders ---------- */

    public static Type array(Type elementType) {
        return Type.newBuilder()
                .setKind(TypeKind.TYPE_KIND_ARRAY)
                .setArrayDetail(ArrayTypeDetail.newBuilder()
                        .setElementType(elementType))
                .build();
    }

    public static Type map(MapKeyKind keyKind, Type valueType) {
        return Type.newBuilder()
                .setKind(TypeKind.TYPE_KIND_MAP)
                .setMapDetail(MapTypeDetail.newBuilder()
                        .setKeyKind(keyKind)
                        .setValueType(valueType))
                .build();
    }

    public static Type struct(StructField... fields) {
        StructTypeDetail.Builder detail = StructTypeDetail.newBuilder();
        for (StructField f : fields) {
            detail.addFields(f);
        }
        return Type.newBuilder()
                .setKind(TypeKind.TYPE_KIND_STRUCT)
                .setStructDetail(detail)
                .build();
    }

    public static StructField field(String name, Type type) {
        return StructField.newBuilder()
                .setName(name)
                .setType(type)
                .build();
    }

    /* ---------- Java 反射推断 ---------- */

    /**
     * 从 Java {@link Class} 推断 {@link Type}。
     * <p>
     * 基本类型直接映射；{@code short/byte} 提升为 INT32。
     * 不支持裸 {@code List}/{@code Map}（无法推断泛型参数），请使用
     * {@link #fromType(java.lang.reflect.Type)} 或手动构建。
     */
    public static Type fromClass(Class<?> clazz) {
        if (clazz == String.class)                                        return STRING;
        if (clazz == int.class    || clazz == Integer.class)              return INT32;
        if (clazz == short.class  || clazz == Short.class)                return INT32;
        if (clazz == byte.class   || clazz == Byte.class)                 return INT32;
        if (clazz == long.class   || clazz == Long.class)                 return INT64;
        if (clazz == float.class  || clazz == Float.class)                return FLOAT;
        if (clazz == double.class || clazz == Double.class)               return DOUBLE;
        if (clazz == boolean.class || clazz == Boolean.class)             return BOOLEAN;
        if (clazz == byte[].class)                                        return BYTES;
        if (java.util.List.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException(
                    "Cannot infer element type from raw List; use Types.array() or fromType()");
        }
        if (java.util.Map.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException(
                    "Cannot infer key/value types from raw Map; use Types.map() or fromType()");
        }
        return structFromClass(clazz);
    }

    /**
     * 从 Java {@link java.lang.reflect.Type}（含泛型信息）推断 {@link Type}。
     * <p>
     * 对于 {@code List<String>}、{@code Map<String, Integer>} 等参数化类型，
     * 会递归推断其泛型参数。
     */
    public static Type fromType(java.lang.reflect.Type type) {
        if (type instanceof Class<?> clazz) {
            return fromClass(clazz);
        }
        if (type instanceof ParameterizedType pt) {
            Class<?> raw = (Class<?>) pt.getRawType();
            java.lang.reflect.Type[] args = pt.getActualTypeArguments();
            if (java.util.List.class.isAssignableFrom(raw)) {
                return array(fromType(args[0]));
            }
            if (java.util.Map.class.isAssignableFrom(raw)) {
                return map(inferMapKeyKindFromType(args[0]), fromType(args[1]));
            }
        }
        throw new IllegalArgumentException("Cannot infer Type from: " + type);
    }

    /* ---------- internal ---------- */

    private static Type primitive(TypeKind kind) {
        return Type.newBuilder().setKind(kind).build();
    }

    private static Type structFromClass(Class<?> clazz) {
        StructTypeDetail.Builder detail = StructTypeDetail.newBuilder()
                .setClassName(clazz.getName());
        for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())
                    || Modifier.isTransient(f.getModifiers())
                    || f.isSynthetic()) {
                continue;
            }
            detail.addFields(StructField.newBuilder()
                    .setName(f.getName())
                    .setType(fromType(f.getGenericType())));
        }
        return Type.newBuilder()
                .setKind(TypeKind.TYPE_KIND_STRUCT)
                .setStructDetail(detail)
                .build();
    }

    private static MapKeyKind inferMapKeyKindFromType(java.lang.reflect.Type keyType) {
        if (keyType == String.class)                                    return MapKeyKind.MAP_KEY_KIND_STRING;
        if (keyType == Integer.class || keyType == int.class)           return MapKeyKind.MAP_KEY_KIND_INT32;
        if (keyType == Long.class    || keyType == long.class)          return MapKeyKind.MAP_KEY_KIND_INT64;
        if (keyType == Boolean.class || keyType == boolean.class)       return MapKeyKind.MAP_KEY_KIND_BOOLEAN;
        throw new IllegalArgumentException(
                "Unsupported map key type: " + keyType
                        + ". Only String, Integer, Long, Boolean are supported.");
    }
}
