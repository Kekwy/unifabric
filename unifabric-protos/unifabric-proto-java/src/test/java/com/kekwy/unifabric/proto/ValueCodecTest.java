package com.kekwy.unifabric.proto;

import com.kekwy.unifabric.proto.common.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"unchecked", "DataFlowIssue"})
class ValueCodecTest {

    /* ================================================================
     *  Primitive round-trip
     * ================================================================ */

    @Nested
    class PrimitiveRoundTrip {

        @Test
        void stringRoundTrip() {
            Value v = ValueCodec.encode("hello");
            assertEquals(Value.KindCase.STRING_VALUE, v.getKindCase());
            assertEquals("hello", ValueCodec.decode(v));
        }

        @Test
        void emptyStringRoundTrip() {
            Value v = ValueCodec.encode("");
            assertEquals("", ValueCodec.decode(v));
        }

        @Test
        void int32RoundTrip() {
            Value v = ValueCodec.encode(42);
            assertEquals(Value.KindCase.INT32_VALUE, v.getKindCase());
            assertEquals(42, ValueCodec.decode(v));
        }

        @Test
        void int32NegativeRoundTrip() {
            Value v = ValueCodec.encode(-1);
            assertEquals(-1, ValueCodec.decode(v));
        }

        @Test
        void int64RoundTrip() {
            Value v = ValueCodec.encode(Long.MAX_VALUE);
            assertEquals(Value.KindCase.INT64_VALUE, v.getKindCase());
            assertEquals(Long.MAX_VALUE, ValueCodec.decode(v));
        }

        @Test
        void floatRoundTrip() {
            Value v = ValueCodec.encode(3.14f);
            assertEquals(Value.KindCase.FLOAT_VALUE, v.getKindCase());
            assertEquals(3.14f, (float) ValueCodec.decode(v), 1e-6f);
        }

        @Test
        void doubleRoundTrip() {
            Value v = ValueCodec.encode(2.718281828);
            assertEquals(Value.KindCase.DOUBLE_VALUE, v.getKindCase());
            assertEquals(2.718281828, (double) ValueCodec.decode(v), 1e-9);
        }

        @Test
        void booleanRoundTrip() {
            Value vTrue = ValueCodec.encode(true);
            Value vFalse = ValueCodec.encode(false);
            assertEquals(Value.KindCase.BOOL_VALUE, vTrue.getKindCase());
            assertEquals(true, ValueCodec.decode(vTrue));
            assertEquals(false, ValueCodec.decode(vFalse));
        }

        @Test
        void bytesRoundTrip() {
            byte[] data = "binary".getBytes(StandardCharsets.UTF_8);
            Value v = ValueCodec.encode(data);
            assertEquals(Value.KindCase.BYTES_VALUE, v.getKindCase());
            assertArrayEquals(data, (byte[]) ValueCodec.decode(v));
        }

        @Test
        void nullEncode() {
            Value v = ValueCodec.encode(null);
            assertEquals(Value.KindCase.NULL_VALUE, v.getKindCase());
            assertNull(ValueCodec.decode(v));
        }
    }

    /* ================================================================
     *  Number promotion
     * ================================================================ */

    @Nested
    class NumberPromotion {

        @Test
        void shortPromotedToInt32() {
            Value v = ValueCodec.encode((short) 123);
            assertEquals(Value.KindCase.INT32_VALUE, v.getKindCase());
            assertEquals(123, ValueCodec.decode(v));
        }

        @Test
        void bytePromotedToInt32() {
            Value v = ValueCodec.encode((byte) 7);
            assertEquals(Value.KindCase.INT32_VALUE, v.getKindCase());
            assertEquals(7, ValueCodec.decode(v));
        }
    }

    /* ================================================================
     *  Array
     * ================================================================ */

    @Nested
    class ArrayRoundTrip {

        @Test
        void intArrayRoundTrip() {
            List<Integer> input = List.of(1, 2, 3);
            Value v = ValueCodec.encode(input);
            assertEquals(Value.KindCase.ARRAY_VALUE, v.getKindCase());
            assertEquals(3, v.getArrayValue().getElementsCount());

            List<Object> decoded = (List<Object>) ValueCodec.decode(v);
            assertEquals(List.of(1, 2, 3), decoded);
        }

        @Test
        void stringArrayRoundTrip() {
            List<String> input = List.of("a", "b", "c");
            Value v = ValueCodec.encode(input);
            List<Object> decoded = (List<Object>) ValueCodec.decode(v);
            assertEquals(List.of("a", "b", "c"), decoded);
        }

        @Test
        void emptyArrayRoundTrip() {
            Value v = ValueCodec.encode(List.of());
            assertEquals(Value.KindCase.ARRAY_VALUE, v.getKindCase());
            List<Object> decoded = (List<Object>) ValueCodec.decode(v);
            assertTrue(decoded.isEmpty());
        }

        @Test
        void nestedArrayRoundTrip() {
            Type type = Types.array(Types.array(Types.INT32));
            List<List<Integer>> input = List.of(
                    List.of(1, 2),
                    List.of(3, 4));
            Value v = ValueCodec.encode(input, type);
            List<Object> decoded = (List<Object>) ValueCodec.decode(v);
            assertEquals(2, decoded.size());
            assertEquals(List.of(1, 2), decoded.get(0));
            assertEquals(List.of(3, 4), decoded.get(1));
        }
    }

    /* ================================================================
     *  Map
     * ================================================================ */

    @Nested
    class MapRoundTrip {

        @Test
        void stringKeyMapRoundTrip() {
            Map<String, Integer> input = new LinkedHashMap<>();
            input.put("a", 1);
            input.put("b", 2);
            Value v = ValueCodec.encode(input);
            assertEquals(Value.KindCase.MAP_VALUE, v.getKindCase());

            Map<Object, Object> decoded = (Map<Object, Object>) ValueCodec.decode(v);
            assertEquals(2, decoded.size());
            assertEquals(1, decoded.get("a"));
            assertEquals(2, decoded.get("b"));
        }

        @Test
        void intKeyMapRoundTrip() {
            Type type = Types.map(MapKeyKind.MAP_KEY_KIND_INT32, Types.STRING);
            Map<Integer, String> input = new LinkedHashMap<>();
            input.put(1, "one");
            input.put(2, "two");
            Value v = ValueCodec.encode(input, type);

            Map<Object, Object> decoded = (Map<Object, Object>) ValueCodec.decode(v);
            assertEquals("one", decoded.get(1));
            assertEquals("two", decoded.get(2));
        }

        @Test
        void longKeyMapRoundTrip() {
            Type type = Types.map(MapKeyKind.MAP_KEY_KIND_INT64, Types.DOUBLE);
            Map<Long, Double> input = new LinkedHashMap<>();
            input.put(100L, 1.1);
            input.put(200L, 2.2);
            Value v = ValueCodec.encode(input, type);

            Map<Object, Object> decoded = (Map<Object, Object>) ValueCodec.decode(v);
            assertEquals(1.1, (double) decoded.get(100L), 1e-9);
            assertEquals(2.2, (double) decoded.get(200L), 1e-9);
        }

        @Test
        void boolKeyMapRoundTrip() {
            Type type = Types.map(MapKeyKind.MAP_KEY_KIND_BOOLEAN, Types.STRING);
            Map<Boolean, String> input = new LinkedHashMap<>();
            input.put(true, "yes");
            input.put(false, "no");
            Value v = ValueCodec.encode(input, type);

            Map<Object, Object> decoded = (Map<Object, Object>) ValueCodec.decode(v);
            assertEquals("yes", decoded.get(true));
            assertEquals("no", decoded.get(false));
        }

        @Test
        void emptyMapRoundTrip() {
            Value v = ValueCodec.encode(Map.of());
            assertEquals(Value.KindCase.MAP_VALUE, v.getKindCase());
            Map<Object, Object> decoded = (Map<Object, Object>) ValueCodec.decode(v);
            assertTrue(decoded.isEmpty());
        }
    }

    /* ================================================================
     *  Struct (Map-based)
     * ================================================================ */

    @Nested
    class StructMapRoundTrip {

        @Test
        void structFromMapRoundTrip() {
            Type type = Types.struct(
                    Types.field("name", Types.STRING),
                    Types.field("age", Types.INT32));

            Map<String, Object> input = new LinkedHashMap<>();
            input.put("name", "Alice");
            input.put("age", 30);

            Value v = ValueCodec.encode(input, type);
            assertEquals(Value.KindCase.STRUCT_VALUE, v.getKindCase());

            Map<String, Object> decoded = (Map<String, Object>) ValueCodec.decode(v);
            assertEquals("Alice", decoded.get("name"));
            assertEquals(30, decoded.get("age"));
        }

        @Test
        void structWithNestedArray() {
            Type type = Types.struct(
                    Types.field("tags", Types.array(Types.STRING)),
                    Types.field("score", Types.DOUBLE));

            Map<String, Object> input = new LinkedHashMap<>();
            input.put("tags", List.of("java", "proto"));
            input.put("score", 9.5);

            Value v = ValueCodec.encode(input, type);
            Map<String, Object> decoded = (Map<String, Object>) ValueCodec.decode(v);
            assertEquals(List.of("java", "proto"), decoded.get("tags"));
            assertEquals(9.5, (double) decoded.get("score"), 1e-9);
        }

        @Test
        void structWithNullField() {
            Type type = Types.struct(
                    Types.field("name", Types.STRING),
                    Types.field("bio", Types.STRING));

            Map<String, Object> input = new LinkedHashMap<>();
            input.put("name", "Bob");
            input.put("bio", null);

            Value v = ValueCodec.encode(input, type);
            Map<String, Object> decoded = (Map<String, Object>) ValueCodec.decode(v);
            assertEquals("Bob", decoded.get("name"));
            assertNull(decoded.get("bio"));
        }
    }

    /* ================================================================
     *  Struct (POJO)
     * ================================================================ */

    public static class Person {
        public String name;
        public int age;
        public Person() {}
        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    public static class Address {
        public String city;
        public String zip;
        public Address() {}
        public Address(String city, String zip) {
            this.city = city;
            this.zip = zip;
        }
    }

    public static class Employee {
        public String name;
        public int age;
        public Address address;
        public Employee() {}
        public Employee(String name, int age, Address address) {
            this.name = name;
            this.age = age;
            this.address = address;
        }
    }

    public static class Department {
        public String deptName;
        public Employee manager;
        public List<Employee> members;
        public Department() {}
        public Department(String deptName, Employee manager, List<Employee> members) {
            this.deptName = deptName;
            this.manager = manager;
            this.members = members;
        }
    }

    public static class WithList {
        public String label;
        public List<Integer> numbers;
        public WithList() {}
        public WithList(String label, List<Integer> numbers) {
            this.label = label;
            this.numbers = numbers;
        }
    }

    @Nested
    class StructPojoRoundTrip {

        @Test
        void pojoEncodeAndDecodeAsMap() {
            Person p = new Person("Alice", 25);
            Value v = ValueCodec.encode(p);
            assertEquals(Value.KindCase.STRUCT_VALUE, v.getKindCase());

            Map<String, Object> decoded = (Map<String, Object>) ValueCodec.decode(v);
            assertEquals("Alice", decoded.get("name"));
            assertEquals(25, decoded.get("age"));
        }

        @Test
        void pojoEncodeAndDecodeAsPojo() {
            Type type = Types.struct(
                    Types.field("name", Types.STRING),
                    Types.field("age", Types.INT32));

            Person input = new Person("Bob", 30);
            Value v = ValueCodec.encode(input, type);

            Person output = ValueCodec.decode(v, Person.class);
            assertEquals("Bob", output.name);
            assertEquals(30, output.age);
        }

        @Test
        void pojoWithListField() {
            WithList input = new WithList("nums", List.of(10, 20, 30));
            Value v = ValueCodec.encode(input);

            Map<String, Object> decoded = (Map<String, Object>) ValueCodec.decode(v);
            assertEquals("nums", decoded.get("label"));
            assertEquals(List.of(10, 20, 30), decoded.get("numbers"));
        }

        @Test
        void nestedPojoEncodeAndDecodeAsMap() {
            Employee emp = new Employee("Alice", 30,
                    new Address("Beijing", "100000"));
            Value v = ValueCodec.encode(emp);
            assertEquals(Value.KindCase.STRUCT_VALUE, v.getKindCase());

            Map<String, Object> decoded = (Map<String, Object>) ValueCodec.decode(v);
            assertEquals("Alice", decoded.get("name"));
            assertEquals(30, decoded.get("age"));

            Map<String, Object> addr = (Map<String, Object>) decoded.get("address");
            assertNotNull(addr);
            assertEquals("Beijing", addr.get("city"));
            assertEquals("100000", addr.get("zip"));
        }

        @Test
        void nestedPojoEncodeAndDecodeAsPojo() {
            Type addrType = Types.struct(
                    Types.field("city", Types.STRING),
                    Types.field("zip", Types.STRING));
            Type empType = Types.struct(
                    Types.field("name", Types.STRING),
                    Types.field("age", Types.INT32),
                    Types.field("address", addrType));

            Employee input = new Employee("Bob", 25,
                    new Address("Shanghai", "200000"));
            Value v = ValueCodec.encode(input, empType);

            // 解码为 Map 后验证嵌套结构
            Map<String, Object> decoded = (Map<String, Object>) ValueCodec.decode(v);
            assertEquals("Bob", decoded.get("name"));
            assertEquals(25, decoded.get("age"));
            Map<String, Object> addr = (Map<String, Object>) decoded.get("address");
            assertEquals("Shanghai", addr.get("city"));
            assertEquals("200000", addr.get("zip"));
        }

        @Test
        void triplyNestedPojoEncodeAsMap() {
            Department dept = new Department(
                    "Engineering",
                    new Employee("Manager", 40, new Address("Shenzhen", "518000")),
                    List.of(
                            new Employee("Dev1", 28, new Address("Guangzhou", "510000")),
                            new Employee("Dev2", 32, new Address("Hangzhou", "310000"))
                    ));
            Value v = ValueCodec.encode(dept);

            Map<String, Object> decoded = (Map<String, Object>) ValueCodec.decode(v);
            assertEquals("Engineering", decoded.get("deptName"));

            Map<String, Object> mgr = (Map<String, Object>) decoded.get("manager");
            assertEquals("Manager", mgr.get("name"));
            assertEquals(40, mgr.get("age"));
            Map<String, Object> mgrAddr = (Map<String, Object>) mgr.get("address");
            assertEquals("Shenzhen", mgrAddr.get("city"));

            List<Object> members = (List<Object>) decoded.get("members");
            assertEquals(2, members.size());
            Map<String, Object> dev1 = (Map<String, Object>) members.get(0);
            assertEquals("Dev1", dev1.get("name"));
            Map<String, Object> dev1Addr = (Map<String, Object>) dev1.get("address");
            assertEquals("Guangzhou", dev1Addr.get("city"));
        }

        @Test
        void nestedPojoDecodeAsPojo() {
            Type addrType = Types.struct(
                    Types.field("city", Types.STRING),
                    Types.field("zip", Types.STRING));
            Type empType = Types.struct(
                    Types.field("name", Types.STRING),
                    Types.field("age", Types.INT32),
                    Types.field("address", addrType));

            Employee input = new Employee("Alice", 28,
                    new Address("Beijing", "100000"));
            Value v = ValueCodec.encode(input, empType);

            Employee output = ValueCodec.decode(v, Employee.class);
            assertEquals("Alice", output.name);
            assertEquals(28, output.age);
            assertNotNull(output.address);
            assertEquals("Beijing", output.address.city);
            assertEquals("100000", output.address.zip);
        }

        @Test
        void nestedPojoDecodeAsPojoAutoInfer() {
            Employee input = new Employee("Bob", 32,
                    new Address("Shanghai", "200000"));
            Value v = ValueCodec.encode(input);

            Employee output = ValueCodec.decode(v, Employee.class);
            assertEquals("Bob", output.name);
            assertEquals(32, output.age);
            assertNotNull(output.address);
            assertEquals("Shanghai", output.address.city);
            assertEquals("200000", output.address.zip);
        }

        @Test
        void triplyNestedPojoDecodeAsPojo() {
            Department input = new Department(
                    "Engineering",
                    new Employee("Manager", 40, new Address("Shenzhen", "518000")),
                    List.of(
                            new Employee("Dev1", 28, new Address("Guangzhou", "510000")),
                            new Employee("Dev2", 32, new Address("Hangzhou", "310000"))
                    ));
            Value v = ValueCodec.encode(input);

            Department output = ValueCodec.decode(v, Department.class);
            assertEquals("Engineering", output.deptName);

            assertNotNull(output.manager);
            assertEquals("Manager", output.manager.name);
            assertEquals(40, output.manager.age);
            assertNotNull(output.manager.address);
            assertEquals("Shenzhen", output.manager.address.city);
            assertEquals("518000", output.manager.address.zip);
        }

        @Test
        void nestedPojoDecodeWithNullInner() {
            Employee input = new Employee("Charlie", 35, null);
            Value v = ValueCodec.encode(input);

            Employee output = ValueCodec.decode(v, Employee.class);
            assertEquals("Charlie", output.name);
            assertEquals(35, output.age);
            assertNull(output.address);
        }

        @Test
        void nestedPojoWithNullInnerObject() {
            Type addrType = Types.struct(
                    Types.field("city", Types.STRING),
                    Types.field("zip", Types.STRING));
            Type empType = Types.struct(
                    Types.field("name", Types.STRING),
                    Types.field("age", Types.INT32),
                    Types.field("address", addrType));

            Employee input = new Employee("Charlie", 35, null);
            Value v = ValueCodec.encode(input, empType);

            Map<String, Object> decoded = (Map<String, Object>) ValueCodec.decode(v);
            assertEquals("Charlie", decoded.get("name"));
            assertEquals(35, decoded.get("age"));
            assertNull(decoded.get("address"));
        }
    }

    /* ================================================================
     *  Explicit Type encode
     * ================================================================ */

    @Nested
    class ExplicitTypeEncode {

        @Test
        void encodeWithExplicitType() {
            Value v = ValueCodec.encode(42, Types.INT64);
            assertEquals(Value.KindCase.INT64_VALUE, v.getKindCase());
            assertEquals(42L, ValueCodec.decode(v));
        }

        @Test
        void encodeFloatAsDouble() {
            Value v = ValueCodec.encode(3.14f, Types.DOUBLE);
            assertEquals(Value.KindCase.DOUBLE_VALUE, v.getKindCase());
            assertEquals(3.14f, (double) ValueCodec.decode(v), 1e-5);
        }

        @Test
        void encodeNullWithExplicitType() {
            Value v = ValueCodec.encode(null, Types.STRING);
            assertEquals(Value.KindCase.NULL_VALUE, v.getKindCase());
            assertNull(ValueCodec.decode(v));
        }
    }

    /* ================================================================
     *  Typed decode
     * ================================================================ */

    @Nested
    class TypedDecode {

        @Test
        void decodeAsString() {
            Value v = ValueCodec.encode("test");
            String result = ValueCodec.decode(v, String.class);
            assertEquals("test", result);
        }

        @Test
        void decodeAsInteger() {
            Value v = ValueCodec.encode(99);
            Integer result = ValueCodec.decode(v, Integer.class);
            assertEquals(99, result);
        }

        @Test
        void decodeNullReturnsNull() {
            Value v = ValueCodec.encode(null);
            assertNull(ValueCodec.decode(v, String.class));
        }

        @Test
        void decodeTypeMismatchThrows() {
            Value v = ValueCodec.encode("text");
            assertThrows(IllegalArgumentException.class,
                    () -> ValueCodec.decode(v, Integer.class));
        }
    }

    /* ================================================================
     *  inferType
     * ================================================================ */

    @Nested
    class InferType {

        @Test
        void inferPrimitiveTypes() {
            assertEquals(TypeKind.TYPE_KIND_STRING, ValueCodec.inferType("s").getKind());
            assertEquals(TypeKind.TYPE_KIND_INT32, ValueCodec.inferType(1).getKind());
            assertEquals(TypeKind.TYPE_KIND_INT64, ValueCodec.inferType(1L).getKind());
            assertEquals(TypeKind.TYPE_KIND_FLOAT, ValueCodec.inferType(1.0f).getKind());
            assertEquals(TypeKind.TYPE_KIND_DOUBLE, ValueCodec.inferType(1.0).getKind());
            assertEquals(TypeKind.TYPE_KIND_BOOLEAN, ValueCodec.inferType(true).getKind());
            assertEquals(TypeKind.TYPE_KIND_BYTES, ValueCodec.inferType(new byte[]{}).getKind());
            assertEquals(TypeKind.TYPE_KIND_NULL, ValueCodec.inferType(null).getKind());
        }

        @Test
        void inferPromotedTypes() {
            assertEquals(TypeKind.TYPE_KIND_INT32, ValueCodec.inferType((short) 1).getKind());
            assertEquals(TypeKind.TYPE_KIND_INT32, ValueCodec.inferType((byte) 1).getKind());
        }

        @Test
        void inferArrayType() {
            Type t = ValueCodec.inferType(List.of(1, 2, 3));
            assertEquals(TypeKind.TYPE_KIND_ARRAY, t.getKind());
            assertEquals(TypeKind.TYPE_KIND_INT32,
                    t.getArrayDetail().getElementType().getKind());
        }

        @Test
        void inferEmptyArrayDefaultsToString() {
            Type t = ValueCodec.inferType(List.of());
            assertEquals(TypeKind.TYPE_KIND_ARRAY, t.getKind());
            assertEquals(TypeKind.TYPE_KIND_STRING,
                    t.getArrayDetail().getElementType().getKind());
        }

        @Test
        void inferMapType() {
            Map<String, Integer> map = Map.of("a", 1);
            Type t = ValueCodec.inferType(map);
            assertEquals(TypeKind.TYPE_KIND_MAP, t.getKind());
            assertEquals(MapKeyKind.MAP_KEY_KIND_STRING, t.getMapDetail().getKeyKind());
            assertEquals(TypeKind.TYPE_KIND_INT32,
                    t.getMapDetail().getValueType().getKind());
        }

        @Test
        void inferMapWithLongKey() {
            Map<Long, String> map = Map.of(1L, "one");
            Type t = ValueCodec.inferType(map);
            assertEquals(MapKeyKind.MAP_KEY_KIND_INT64, t.getMapDetail().getKeyKind());
        }

        @Test
        void inferStructType() {
            Person p = new Person("Alice", 25);
            Type t = ValueCodec.inferType(p);
            assertEquals(TypeKind.TYPE_KIND_STRUCT, t.getKind());
            assertEquals(2, t.getStructDetail().getFieldsCount());
        }

        @Test
        void unsupportedMapKeyThrows() {
            Map<Double, String> map = Map.of(1.0, "val");
            assertThrows(IllegalArgumentException.class,
                    () -> ValueCodec.inferType(map));
        }
    }

    /* ================================================================
     *  Complex nested structures
     * ================================================================ */

    @Nested
    class ComplexNested {

        @Test
        void arrayOfStructs() {
            Type personType = Types.struct(
                    Types.field("name", Types.STRING),
                    Types.field("age", Types.INT32));
            Type type = Types.array(personType);

            List<Map<String, Object>> input = List.of(
                    Map.of("name", "Alice", "age", 25),
                    Map.of("name", "Bob", "age", 30));

            Value v = ValueCodec.encode(input, type);
            List<Object> decoded = (List<Object>) ValueCodec.decode(v);
            assertEquals(2, decoded.size());

            Map<String, Object> first = (Map<String, Object>) decoded.get(0);
            assertEquals("Alice", first.get("name"));
            assertEquals(25, first.get("age"));
        }

        @Test
        void mapWithArrayValue() {
            Type type = Types.map(
                    MapKeyKind.MAP_KEY_KIND_STRING,
                    Types.array(Types.INT32));

            Map<String, List<Integer>> input = new LinkedHashMap<>();
            input.put("odds", List.of(1, 3, 5));
            input.put("evens", List.of(2, 4, 6));

            Value v = ValueCodec.encode(input, type);
            Map<Object, Object> decoded = (Map<Object, Object>) ValueCodec.decode(v);
            assertEquals(List.of(1, 3, 5), decoded.get("odds"));
            assertEquals(List.of(2, 4, 6), decoded.get("evens"));
        }

        @Test
        void structWithNestedMap() {
            Type type = Types.struct(
                    Types.field("id", Types.INT64),
                    Types.field("attrs", Types.map(
                            MapKeyKind.MAP_KEY_KIND_STRING, Types.STRING)));

            Map<String, Object> input = new LinkedHashMap<>();
            input.put("id", 100L);
            input.put("attrs", Map.of("color", "red", "size", "large"));

            Value v = ValueCodec.encode(input, type);
            Map<String, Object> decoded = (Map<String, Object>) ValueCodec.decode(v);
            assertEquals(100L, decoded.get("id"));

            Map<Object, Object> attrs = (Map<Object, Object>) decoded.get("attrs");
            assertEquals("red", attrs.get("color"));
            assertEquals("large", attrs.get("size"));
        }

        @Test
        void deeplyNestedStructure() {
            Type innerStruct = Types.struct(
                    Types.field("value", Types.INT32));
            Type outerStruct = Types.struct(
                    Types.field("label", Types.STRING),
                    Types.field("items", Types.array(innerStruct)));

            Map<String, Object> inner1 = Map.of("value", 1);
            Map<String, Object> inner2 = Map.of("value", 2);
            Map<String, Object> outer = new LinkedHashMap<>();
            outer.put("label", "test");
            outer.put("items", List.of(inner1, inner2));

            Value v = ValueCodec.encode(outer, outerStruct);
            Map<String, Object> decoded = (Map<String, Object>) ValueCodec.decode(v);
            assertEquals("test", decoded.get("label"));

            List<Object> items = (List<Object>) decoded.get("items");
            assertEquals(2, items.size());
            assertEquals(1, ((Map<String, Object>) items.get(0)).get("value"));
            assertEquals(2, ((Map<String, Object>) items.get(1)).get("value"));
        }
    }
}
