package cn.gzsendi.modules.framework.utils;

import cn.gzsendi.modules.framework.reflect.Reflector;
import cn.gzsendi.modules.framework.reflect.reflectasm.MethodAccessor;

import javax.servlet.ServletRequest;

import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;


/**
 * 参数集中校验及一些参数处理的辅助工具类.
 *
 * @author Mr.XiHui
 * @date 2017/9/22
 * @see org.springframework.util.ObjectUtils
 * @see org.springframework.util.CollectionUtils
 * @see org.springframework.util.StringUtils
 * @see org.springframework.util
 */
public abstract class ParameterUtils {

    /*** 2的最大幂int值. */
    private static final int MAX_POWER_OF_TWO = 1 << (Integer.SIZE - 2);

    /*** 默认编码. */
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    /*** 十六进制数的字符数组. */
    private static final char[] HEX_CHARS_FOR_TRANS_TO_DEC = "0123456789abcdef".toCharArray();

    /**
     * 十六进制字符转十进制数时使用的辅助整数数组. <br>
     * 此数组的索引为 ('十六进制字符' - '0')字符相减的结果： <br>
     * <p>
     * 如 DEC_INTS_FOR_TRANS_FROM_HEX['9' - '0'] = DEC_INTS_FOR_TRANS_FROM_HEX[9] <br>
     * 因为 '9'的ascii码数值为57，而'0'的为48，所以57-48=9
     * <p>
     * 又如 DEC_INTS_FOR_TRANS_FROM_HEX['A' - '0'] = DEC_INTS_FOR_TRANS_FROM_HEX[17] <br>
     * 因为 'A'的ascii码数值为65，而'0'的为48，所以65-48=17
     * <p>
     * 又如 DEC_INTS_FOR_TRANS_FROM_HEX['f' - '0'] = DEC_INTS_FOR_TRANS_FROM_HEX[54] <br>
     * 因为 'f'的ascii码数值为102，而'0'的为48，所以102-48=54
     * <p>
     * 我们发现索引在[10,16]和[23,50]这一段范围内因为没有对应的十六进制字符对应，所以都以值-1填充
     * <p>
     * 综上所述： <br>
     * 十六进制字符['0','9']对应索引范围[0,9] <br>
     * 十六进制字符['A','F']对应索引范围[17,22] <br>
     * 十六进制字符['a','f']对应索引范围[51,64] <br>
     */
    private static final int[] DEC_INTS_FOR_TRANS_FROM_HEX = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -1, -1, -1, -1, -1,
            -1, 10, 11, 12, 13, 14, 15, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, 10, 11, 12, 13, 14, 15,
    };

    /**
     * 检查并删除空元素后判断Collection是否为空. <br>
     * 使用前确保你传进来的集合支持遍历过程中删除多个元素 <br>
     * 调用Arrays.asList得到的List不支持删除元素
     *
     * @param collection Collection集合
     * @param <E>        元素类型
     * @return boolean
     */
    public static <E> boolean isEmpAfterRmEmpElems(Collection<E> collection) {

        if (collection == null) {
            return true;
        }

        rmEmptyElement(collection);

        return collection.isEmpty();
    }

    /**
     * 检查集合中是否有空元素.
     *
     * @param iterable 待检查的集合对象
     * @param <E>      集合的元素类型
     * @return boolean 只要有一个元素为空则返回true
     */
    public static <E> boolean hasEmptyElement(Iterable<E> iterable) {
        if (iterable == null) {
            return true;
        }
        //LinkedList用get(index)方法就是个坑，所以对随机访问支持友好的List才用index遍历
        if (iterable instanceof RandomAccess && iterable instanceof List) {
            List<E> list = (List<E>) iterable;
            int size = list.size();
            for (int i = 0; i < size; i++) {
                if (isEmpty(list.get(i))) {
                    return true;
                }
            }
        } else {
            //Collection的foreach编译后也是iterator...
            for (E e : iterable) {
                if (isEmpty(e)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 检查入参列表中是否有空的对象.
     *
     * @param objects 待检查的入参列表
     * @return boolean 只要有一个对象为空(is empty)则返回true
     */
    public static boolean hasAnyEmpty(Object... objects) {
        if (objects == null) {
            return true;
        }
        for (int i = 0; i < objects.length; i++) {
            if (isEmpty(objects[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查入参列表中是否全是有空的对象.
     *
     * @param objects 待检查的入参列表
     * @return boolean 只要有一个对象非空(not empty)则返回false
     */
    public static boolean areAllEmpty(Object... objects) {
        if (objects == null) {
            return true;
        }
        for (int i = 0; i < objects.length; i++) {
            if (!isEmpty(objects[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检验对象是否为空. <br>
     * 将方法将做以下判断： <br>
     * 1、是否为null <br>
     * 2、Collection和Map的size是否为0 <br>
     * 3、数组和字符序列的length是否为0 <br>
     * 4、字符序列是否空白 <br>
     * 5、其他可遍历对象的元素数量是否为0
     *
     * @param obj 待检验的对象，可以是Collection/Map/CharSequence/数组/Iterable
     * @param <O> 待检验的对象类型
     * @return boolean
     */
    public static <O> boolean isEmpty(O obj) {
        if (obj == null) {
            return true;
        } else if (obj instanceof Collection) {
            return ((Collection) obj).isEmpty();
        } else if (obj instanceof Map) {
            return ((Map) obj).isEmpty();
        } else if (obj instanceof CharSequence) {
            return isEmpty((CharSequence) obj);
        } else if (obj instanceof Object[]) {
            return Array.getLength(obj) == 0;
        } else if (obj instanceof Iterable) {
            //非Collection的Iterable放最后
            return !((Iterable) obj).iterator().hasNext();
        }
        return false;
    }

    /**
     * 检验数组是否为空. <br>
     * 将方法将做以下判断： <br>
     * 1、是否为null <br>
     * 2、length是否为0
     *
     * @param array 待检验的数组
     * @param <O>   待检验的数组元素类型
     * @return boolean
     */
    public static <O> boolean isEmpty(O[] array) {
        return array == null || array.length == 0;
    }

    /**
     * 检验Map是否为空. <br>
     * 将方法将做以下判断： <br>
     * 1、是否为null <br>
     * 2、size是否为0
     *
     * @param map 待检验的Map
     * @return boolean
     */
    public static boolean isEmpty(Map<?, ?> map) {
        if (map == null) {
            return true;
        }
        return map.isEmpty();
    }

    /**
     * 检验CharSequence是否为空. <br>
     * 将方法将做以下判断： <br>
     * 1、是否为null <br>
     * 2、length是否为0 <br>
     * 3、是否全是空白字符 <br>
     *
     * @param charSequence 待检验的CharSequence
     * @return boolean
     */
    public static boolean isEmpty(CharSequence charSequence) {
        if (charSequence == null) {
            return true;
        }
        int length = charSequence.length();
        if (length == 0) {
            return true;
        }
        boolean hasText = false;
        for (int i = 0; i < length; i++) {
            if (!Character.isWhitespace(charSequence.charAt(i))) {
                hasText = true;
                break;
            }
        }
        if (hasText) {
            return false;
        }
        return true;
    }

    /**
     * 检验Collection是否为空. <br>
     * 将方法将做以下判断： <br>
     * 1、是否为null <br>
     * 2、元素数量是否为0
     *
     * @param collection 待检验的Collection
     * @return boolean
     */
    public static boolean isEmpty(Collection<?> collection) {
        if (collection == null) {
            return true;
        }
        return collection.isEmpty();
    }

    /**
     * 检验Iterable是否为空. <br>
     * 将方法将做以下判断： <br>
     * 1、是否为null <br>
     * 2、元素数量是否为0
     *
     * @param iterable 待检验的Iterable
     * @return boolean
     */
    public static boolean isEmpty(Iterable<?> iterable) {
        if (iterable == null) {
            return true;
        }
        return !iterable.iterator().hasNext();
    }

    /**
     * 移除collection中的空值，使用前确保你传进来的集合支持遍历过程中删除多个元素. <br>
     * 调用Arrays.asList得到的List不支持删除元素 <br>
     * 如果传进来的集合没有重写下面这些方法，那么就不能使用此方法 <br>
     * {@link AbstractList#remove(int)} <br>
     * {@link AbstractList#remove(Object)} <br>
     * {@link AbstractCollection#remove(Object)} <br>
     *
     * @param collection 集合对象
     * @param <E>        集合中的元素泛型类型
     */
    public static <E> void rmEmptyElement(Collection<E> collection) {
        if (collection == null) {
            return;
        }
        try {
            collection.removeIf(ParameterUtils::isEmpty);
        } catch (UnsupportedOperationException e) {
            throw new UnsupportedOperationException("你传进来的集合不支持remove操作或不支持遍历过程中删除多个元素，" +
                    "另外：调用Arrays.asList得到的List不支持删除元素", e);
        }
    }

    /**
     * 把字符串切割成有内容的List.
     *
     * @param string 待切割的字符串
     * @param regex  切割表达式
     * @return 内容的字符串List
     */
    public static List<String> split2HasTextLs(String string, String regex) {
        if (string == null) {
            return null;
        }
        String[] split = string.split(regex);
        //调用Arrays.asList得到的List不支持删除元素，所以用ArrayList代替
        List<String> strings = new ArrayList<>(split.length);
        for (int i = 0; i < split.length; i++) {
            String str = split[i].trim();
            if (isEmpty(str)) {
                continue;
            }
            strings.add(str);
        }
        return strings;
    }

    /**
     * 把字符串切割成段后去除空白段再重新拼接.
     *
     * @param string 待切割去空的字符串
     * @param regex  切割表达式
     * @return 去空后的字符串
     */
    public static String trimAndRemoveDuplicates(String string, String regex) {
        if (string == null) {
            return null;
        }
        String[] split = string.split(regex);
        Set<String> strings = new LinkedHashSet<>();
        for (int i = 0; i < split.length; i++) {
            String str = split[i].trim();
            if (!isEmpty(str)) {
                strings.add(str);
            }
        }
        return String.join(regex, strings);
    }

    /**
     * 数组转成可编辑的List. <br>
     * 因为Arrays.asList得到的List是不支持增删的， <br>
     * 所以另外写了一个数组转List的实现
     *
     * @param t   元素对象
     * @param <T> 元素类型
     * @return 可操作的java.util.ArrayList，非{@link Arrays}中的ArrayList内部类
     */
    @SafeVarargs
    public static <T> List<T> arrayAsList(T... t) {
        return new ArrayList<>(Arrays.asList(t));
    }

    /**
     * 集合转数组.
     *
     * @param collection    集合
     * @param componentType 数组中的元素Cass
     * @param <T>           数组中的元素类型
     * @return 数组
     */
    @SuppressWarnings("all")
    public static <T> T[] toArray(Collection<?> collection, Class<T> componentType) {
        if (isEmpty(collection)) {
            return newArray(componentType, 0);
        }
        return collection.toArray(newArray(componentType, collection.size()));
    }

    /**
     * 根据给定的元素类型和长度初始化一个空数组.
     *
     * @param componentType 元素Type
     * @param length        长度
     * @param <T>           元素类型
     * @return 空数组
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] newArray(Class<T> componentType, int length) {
        return (T[]) Array.newInstance(componentType, length);
    }

    /**
     * 善用lambda表达式遍历. <br>
     * 如果是集合或数组，则给{@link BiConsumer#accept}方法传递的是元素和当前遍历到的元素位置 <br>
     * 如果是Map，则给{@link BiConsumer#accept}方法传递的是entry和当前遍历到的entry位置 <br>
     * 如果是普通对象，则给{@link BiConsumer#accept}方法传递的是本身和0
     *
     * @param obj      集合或数组或Map或普通对象
     * @param consumer 函数式接口，使用lambda自定义其{BiConsumer#accept(E elem, Integer index)}方法
     * @param <E>      如果是集合/数组：元素类型，如果是Map: Entry，如果是普通对象: 普通对象本身类型
     */
    @SuppressWarnings("unchecked")
    public static <E> void iterateObj(Object obj, BiConsumer<E, Integer> consumer) {
        if (consumer == null) {
            return;
        }
        if (obj instanceof RandomAccess && obj instanceof List) {
            List<?> list = (List<?>) obj;
            int size = list.size();
            for (int i = 0; i < size; i++) {
                consumer.accept((E) list.get(i), i);
            }
        } else if (obj instanceof Iterable) {
            //Collection的foreach编译后也是iterator...
            int i = 0;
            for (Object o : (Iterable<?>) obj) {
                consumer.accept((E) o, i);
                i++;
            }
        } else if (obj instanceof Object[]) {
            Object[] objArr = (Object[]) obj;
            for (int i = 0; i < objArr.length; i++) {
                consumer.accept((E) objArr[i], i);
            }
        } else if (obj instanceof Map) {
            int i = 0;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                consumer.accept((E) entry, i);
                i++;
            }
        } else {
            consumer.accept((E) obj, 0);
        }
    }

    /**
     * 善用lambda表达式遍历(含有break功能). <br>
     * 此方法提供break功能，即{@link BiFunction#apply}的返回值是true则执行break跳出循环 <br>
     * 如果是集合或数组，则给{@link BiFunction#apply}方法传递的是元素和当前遍历到的元素位置 <br>
     * 如果是Map，则给{@link BiFunction#apply}方法传递的是entry和当前遍历到的entry位置 <br>
     * 如果是普通对象，则给{@link BiFunction#apply}方法传递的是本身和0
     *
     * @param obj      集合或数组或Map或普通对象
     * @param function 函数式接口，使用lambda自定义其{BiFunction#apply(E elem, Integer index)}方法
     *                 apply的返回值如果是true，则跳出循环
     * @param <E>      如果是集合/数组：元素类型，如果是Map: Entry，如果是普通对象: 普通对象本身类型
     */
    @SuppressWarnings("unchecked")
    public static <E> void iterateObj(Object obj, BiFunction<E, Integer, Boolean> function) {
        if (function == null) {
            return;
        }
        if (obj instanceof RandomAccess && obj instanceof List) {
            List<?> list = (List<?>) obj;
            int size = list.size();
            for (int i = 0; i < size; i++) {
                Boolean doBreak = function.apply((E) list.get(i), i);
                if (doBreak) {
                    break;
                }
            }
        } else if (obj instanceof Iterable) {
            //Collection的foreach编译后也是iterator...
            int i = 0;
            for (Object o : (Iterable<?>) obj) {
                Boolean doBreak = function.apply((E) o, i);
                if (doBreak) {
                    break;
                }
                i++;
            }
        } else if (obj instanceof Object[]) {
            Object[] objArr = (Object[]) obj;
            for (int i = 0; i < objArr.length; i++) {
                Boolean doBreak = function.apply((E) objArr[i], i);
                if (doBreak) {
                    break;
                }
            }
        } else if (obj instanceof Map) {
            int i = 0;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                Boolean doBreak = function.apply((E) entry, i);
                if (doBreak) {
                    break;
                }
                i++;
            }
        } else {
            function.apply((E) obj, 0);
        }
    }

    /**
     * 判断两集合是否有交集. <br>
     * 此方法会判断source是否Set集合，如果不是，则将source的数据复制到HashSet中再进行交集判断
     *
     * @param source     进行contains操作的集合
     * @param candidates 被遍历被contains其中元素的集合
     * @return 是否有交集
     */
    @SuppressWarnings("unchecked")
    public static boolean containsAny(Iterable<?> source, Iterable<?> candidates) {
        if (hasAnyEmpty(source, candidates)) {
            return false;
        }

        Collection srcCollection;
        if (source instanceof Set) {
            srcCollection = (Set) source;
        } else {
            srcCollection = new HashSet<>();
            iterateObj(source, (BiConsumer<Object, Integer>) (obj, i) -> srcCollection.add(obj));
        }

        for (Object candidate : candidates) {
            if (srcCollection.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取 集合/数组/Map/其他Iterable 的大小，即元素个数.
     *
     * @param obj 集合/数组/Map/其他Iterable
     * @return 元素个数
     */
    public static int getElementSize(Object obj) {
        int size = 0;
        if (obj instanceof Collection) {
            size = ((Collection) obj).size();
        } else if (obj instanceof Object[]) {
            size = ((Object[]) obj).length;
        } else if (obj instanceof Map) {
            size = ((Map) obj).size();
        } else if (obj instanceof Iterable) {
            //非Collection的Iterable放最后
            for (Object ignored : ((Iterable) obj)) {
                size++;
            }
        }
        return size;
    }

    /**
     * 把target集合拆分成多个ArrayList.SubList的集合. <br>
     * 拆分后除最后一个ArrayList.SubList外，每个ArrayList.SubList的大小为eachSize， <br>
     * 最后一个ArrayList.SubList的大小 &lt;= eachSize，具体看target的大小能否被eachSize整除
     *
     * @param target   待拆分的集合
     * @param eachSize 拆分后每个子集合元素个数
     * @param <T>      元素类型
     * @return List&lt;List&lt;T&gt;&gt;
     */
    public static <T> List<List<T>> splitList(List<T> target, int eachSize) {
        if (isEmpty(target) || eachSize == 0) {
            return new ArrayList<>(0);
        }
        //获取能拆分的集合个数
        int targetSize = target.size();
        int splitSize = targetSize % eachSize == 0 ? targetSize / eachSize : (targetSize / eachSize) + 1;
        List<List<T>> result = new ArrayList<>(splitSize);
        for (int i = 0; i < splitSize; i++) {
            //把指定索引数据放入到result中
            int currentSize = eachSize * i;
            int nextSize = currentSize + eachSize;
            if (targetSize < nextSize) {
                nextSize = targetSize;
            }
            result.add(target.subList(currentSize, nextSize));
        }
        return result;
    }

    /**
     * 检查obj对象中的值是否全都是null或空.
     *
     * @param obj                待检查的对象
     * @param ignoreKeysOrFields 键或变量的可变长度实例
     * @param <K>                键或变量的类型
     * @return boolean
     */
    @SafeVarargs
    public static <K> boolean areValuesAllEmpty(Object obj, K... ignoreKeysOrFields) {

        boolean[] allEmpty = {true};
        Set<?> ignoreSet = new HashSet<>(Arrays.asList(ignoreKeysOrFields));

        iterateObj(obj, (o, index) -> {

            //如果是null则不用处理
            if (o == null) {
                return false;
            }

            Class<?> clazz = o.getClass();
            if (Reflector.isPrimitiveOrWrapper(clazz)) {
                allEmpty[0] = false;
                return true;
            }

            if (o instanceof CharSequence) {
                if (!isEmpty((CharSequence) o)) {
                    allEmpty[0] = false;
                    return true;
                }
                return false;
            }

            Object value;
            if (o instanceof Map.Entry) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
                if (ignoreSet.contains(entry.getKey())) {
                    return false;
                }
                value = entry.getValue();
                if (!isEmpty(value)) {
                    allEmpty[0] = false;
                    return true;
                }
            } else if (!(o instanceof Iterable) && !(o instanceof Object[])) {
                MethodAccessor methodAccessor = Reflector.getMethodAccessor(clazz);
                String[] fieldNames = methodAccessor.getFieldNames();
                for (int i = 0; i < fieldNames.length; i++) {
                    String fieldName = fieldNames[i];
                    if (ignoreSet.contains(fieldName)) {
                        continue;
                    }
                    value = methodAccessor.getFieldValue(o, fieldName);
                    if (!isEmpty(value)) {
                        allEmpty[0] = false;
                        return true;
                    }
                }
            }
            return false;
        });
        return allEmpty[0];
    }

    /**
     * 将obj对象的字符串成员变量或value做非空判断，如果为空则替换成null. <br>
     * 如果obj是集合或数组或map，只操作其第一层元素或value的String
     *
     * @param obj 需要替换空字符串成员变量为null的对象
     */
    public static void replaceBlankToNull(Object obj) {

        boolean isList = obj instanceof List;

        iterateObj(obj, (o, index) -> {
            //如果是null则不用处理
            if (o == null) {
                return;
            }

            Class<?> clazz = o.getClass();
            if (Reflector.isPrimitiveOrWrapper(clazz)) {
                return;
            }

            if (o instanceof String) {
                //如果obj是List<String>，则尝试替换
                if (isList && isEmpty((String) o)) {
                    //可能会抛出UnsupportedOperationException异常，说明这个List不支持操作，请自行处理
                    ((List<?>) obj).set(index, null);
                }
                //如果obj本身是String或obj是其他Iterable<String>也不处理
                return;
            }

            Object value;
            if (o instanceof Map.Entry) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
                value = entry.getValue();
                if (value instanceof String && isEmpty(value)) {
                    entry.setValue(null);
                }
            } else if (!(o instanceof Iterable) && !(o instanceof Object[])) {
                MethodAccessor methodAccessor = Reflector.getMethodAccessor(clazz);
                String[] fieldNames = methodAccessor.getFieldNames();
                for (int i = 0; i < fieldNames.length; i++) {
                    String fieldName = fieldNames[i];
                    value = methodAccessor.getFieldValue(o, fieldName);
                    if (value instanceof String && isEmpty(value)) {
                        methodAccessor.setFieldValue(o, fieldName, null);
                    }
                }
            }
        });
    }

    /**
     * 保留Map中指定的Key. <br>
     * 此方法会判断retainKeys是否Set集合，如果不是，则将retainKeys的数据复制到HashSet中再进行交集判断
     *
     * @param map        待操作的Map
     * @param retainKeys 待保留的key列表
     * @param <K>        键类型
     * @param <V>        值类型
     */
    public static <K, V> void retainMapKeys(Map<K, V> map, Collection<K> retainKeys) {
        if (!isEmpty(map)) {
            if (isEmpty(retainKeys)) {
                map.clear();
            } else {
                if (!(retainKeys instanceof Set)) {
                    retainKeys = new HashSet<>(retainKeys);
                }
                //因为Map不能在遍历过程中对自身进行remove操作
                //所以通过新的
                List<K> ks = new ArrayList<>(map.keySet());
                int size = ks.size();
                for (int i = 0; i < size; i++) {
                    K key = ks.get(i);
                    if (!retainKeys.contains(key)) {
                        map.remove(key);
                    }
                }
            }
        }
    }

    /**
     * 把pojo指定(include)的或所有的fields的值设置成null.
     *
     * @param obj           pojo对象
     * @param includeFields 被包含成员变量可变长度实例
     */
    public static void setPojoFieldsToNull(Object obj, String... includeFields) {
        if (obj == null) {
            return;
        }
        if (!(obj instanceof Map) && !(obj instanceof Iterable) && !(obj instanceof Object[])) {

            MethodAccessor methodAccessor = Reflector.getMethodAccessor(obj.getClass());

            //如果未指定pojo的field列表, 则默认将所有fields的值都设置成null
            if (isEmpty(includeFields)) {
                includeFields = methodAccessor.getFieldNames();
            }
            for (int i = 0; i < includeFields.length; i++) {
                String fieldName = includeFields[i];
                methodAccessor.setFieldValue(obj, fieldName, null);
            }
        }
    }

    /**
     * 把pojo指定之外(exclude)的或所有的fields的值设置成null.
     *
     * @param obj           pojo对象
     * @param excludeFields 被排除的成员变量可变长度实例
     */
    public static void setPojoFieldsToNullEx(Object obj, String... excludeFields) {
        if (obj == null) {
            return;
        }
        if (!(obj instanceof Map) && !(obj instanceof Iterable) && !(obj instanceof Object[])) {

            MethodAccessor methodAccessor = Reflector.getMethodAccessor(obj.getClass());

            //如果未指定pojo的field列表, 则默认将所有fields的值都设置成null
            Set<String> fieldNameSet = new HashSet<>(Arrays.asList(methodAccessor.getFieldNames()));
            if (!isEmpty(excludeFields)) {
                for (int i = 0; i < excludeFields.length; i++) {
                    fieldNameSet.remove(excludeFields[i]);
                }
            }

            for (String fieldName : fieldNameSet) {
                methodAccessor.setFieldValue(obj, fieldName, null);
            }
        }
    }

    /**
     * 计算Map的容量. <br>
     * 只要它的增长不超过预期的大小，并且负载因子&gt;=它的默认值(0.75)， <br>
     * 则返回足以防止进行Map大小调整的容量
     *
     * @param expectedSize 期望大小
     * @return Map容量
     */
    public static int calcMapCapacity(int expectedSize) {
        if (expectedSize < 3) {
            if (expectedSize < 0) {
                throw new IllegalArgumentException("Map的期望大小不能是负数: " + expectedSize);
            }
            return expectedSize + 1;
        }
        if (expectedSize < MAX_POWER_OF_TWO) {
            //这是和HashMap的putAll方法一样的重计大小的方法
            return (int) ((float) expectedSize / 0.75F + 1.0F);
        }
        return Integer.MAX_VALUE;
    }

    /**
     * 蛇型(下划线小写型)字符串转成驼峰式字符串. <br>
     * 如 the_google -&gt; theGoogle
     * <p>
     * 此方法性能上应该比大部分其他封装的方法要好
     *
     * @param snakeCase 蛇型(下划线小写型)字符串
     * @return 驼峰式字符串
     * @see com.google.common.base.CaseFormat
     */
    public static String snakeCaseToCamelCase(String snakeCase) {

        //数字（ascii码区间[48,57]对应[0,9]）
        //小写字母（ascii码区间[97,122]对应[a,z]）
        //大写字母（ascii码区间[65,90]对应[A,Z]）
        //SpringBuilder，Matcher的replaceAll等方法会进行更多的方法调用和数组复制，这里不用
        char[] camelCaseValue = snakeCase.toCharArray();
        //用于记录转化后的字符计数
        int count = 0;
        //用于记录遍历到的最新的单词首字母位置，初始化为-1
        int initialIndex = -1;
        for (int i = 0; i < camelCaseValue.length; i++) {
            char c = camelCaseValue[i];
            //if (c == '_') {
            if (c == 95) {
                //记录遍历到的最新的单词首字母位置
                initialIndex = i + 1;
                //跳过'_'的拼接
                continue;
            }
            //如果是单词首字母
            if (initialIndex == i) {
                //如果单词首字母是小写字母，转成大写字母
                //if (c >= 'a' && c <= 'z') {
                if (c >= 97 && c <= 122) {
                    c -= 32;
                }
            }
            //如果单词的其他字母是大写字母，转成小写字母
            //else if (c >= 'A' && c <= 'Z') {
            else if (c >= 65 && c <= 90) {
                c += 32;
            }
            camelCaseValue[count++] = c;
        }

        return new String(camelCaseValue, 0, count);
    }

    /**
     * 驼峰式字符串转成蛇型(下划线小写型)字符串. <br>
     * 如 theGoogle -&gt; the_google
     * <p>
     * 此方法性能上应该比大部分其他封装的方法要好
     * </p>
     *
     * @param camelCase 驼峰式字符串
     * @return 蛇型(下划线小写型)字符串
     * @see com.google.common.base.CaseFormat
     */
    public static String camelCaseToSnakeCase(String camelCase) {

        //数字（ascii码区间[48,57]对应[0,9]）
        //小写字母（ascii码区间[97,122]对应[a,z]）
        //大写字母（ascii码区间[65,90]对应[A,Z]）
        int length = camelCase.length();
        if (length == 0) {
            return camelCase;
        }
        //SpringBuilder，Matcher的replaceAll等方法会进行更多的方法调用和数组复制，这里不用
        //因为在转化的过程中字符串长度会增加，所以一次性预足两倍的数组空间来存字符
        char[] snakeCaseValue = new char[(length << 1) - 1];
        //用于记录转化后的字符计数
        int count = 0;
        for (int i = 0; i < length; i++) {
            char c = camelCase.charAt(i);
            //如果是大写字母，加下划线再转成小写字母
            //if (c >= 'A' && c <= 'Z') {
            if (c >= 65 && c <= 90) {
                snakeCaseValue[count++] = '_';
                //c += ' ';
                c += 32;
            }
            snakeCaseValue[count++] = c;
        }

        return new String(snakeCaseValue, 0, count);
    }

    /**
     * 去除头尾空字符，并在字符串的每个字符间插入指定字符.
     *
     * @param str        字符串
     * @param c          待插入的字符
     * @param wrapByChar 是否使用指定字符包围
     * @return 去除头尾空字符插入指定字符后的字符串
     */
    public static String trimAndInsertChar(String str, char c, boolean wrapByChar) {
        if (str == null) {
            return null;
        }

        //去除头尾空字符，然后在每个字符间加上指定字符
        //如将字段值处理一下，在每个字符间加上百分号'%'用于sql模糊查询
        int length = str.length();
        if (length == 0) {
            return str;
        }

        int originalTextIndex = 0;

        //从前面住后遍历，找出头部空字符，以将originalTextIndex加上头部空字符数
        for (int i = 0; i < length; i++) {
            if (str.charAt(i) <= ' ') {
                originalTextIndex++;
            } else {
                break;
            }
        }

        if (originalTextIndex != length) {
            //从后面住前遍历，找出尾部的空字符，以将length减去尾部空字符数
            for (int i = length - 1; i > originalTextIndex; i--) {
                if (str.charAt(i) <= ' ') {
                    length--;
                } else {
                    break;
                }
            }
        } else {
            return "";
        }

        //在每个字符间加上指定字符
        char[] chars;
        int textCount = 0;
        if (wrapByChar) {
            chars = new char[(length << 1) + 1];
            chars[textCount++] = c;
        } else {
            chars = new char[(length << 1) - 1];
        }
        for (int i = originalTextIndex; i < length - 1; i++) {
            chars[textCount++] = str.charAt(i);
            chars[textCount++] = c;
        }
        chars[textCount++] = str.charAt(length - 1);
        if (wrapByChar) {
            chars[textCount++] = c;
        }
        return new String(chars, 0, textCount);
    }

    /**
     * 将字节数组转成Base64编码的字符串.
     *
     * @param bytes 字节数组
     * @return Base64编码的字符串
     */
    public static String encodeToBase64Str(byte[] bytes) {
        if (bytes.length == 0) {
            return "";
        } else {
            return new String(Base64.getEncoder().encode(bytes), DEFAULT_CHARSET);
        }
    }

    /**
     * 将Base64编码的字符串解码成字节数组.
     *
     * @param src Base64编码的字符串
     * @return 字节数组
     */
    public static byte[] decodeFromBase64Str(String src) {
        return Base64.getDecoder().decode(src.getBytes(DEFAULT_CHARSET));
    }

    /**
     * 将字节数组转成十六进制字符串.
     *
     * @param bytes 字节数组，一般是包含不可见字节的数组
     * @return 十六进制字符串
     */
    public static String toHexString(byte[] bytes) {
        //我们知道位运算比较高效，在某些情况下，当b为2的n次方时，有如下替换公式：
        //a % b = a & (b-1) 假设(b=2n)即：a % 2n = a & (2n-1)
        char[] buf = new char[bytes.length << 1];
        int index = 0;
        // 利用位运算进行转换
        for (byte b : bytes) {
            //偶数位设置成"商"，相当于HEX_CHARS[(b < 0 ? 256 + b : b) / 16]
            buf[index++] = HEX_CHARS_FOR_TRANS_TO_DEC[b >>> 4 & 0xf];
            //奇数位设置成"余数"，相当于HEX_CHARS[(b < 0 ? 256 + b : b) % 16]
            buf[index++] = HEX_CHARS_FOR_TRANS_TO_DEC[b & 0xf];
        }
        return new String(buf);
    }

    /**
     * 将十六进制字符串转成字节数组.
     *
     * @param hexStr 十六进制字符串
     * @return 字节数组
     */
    public static byte[] fromHexString(String hexStr) {
        if (hexStr == null) {
            return null;
        }

        int hexStrLength = hexStr.length();
        if ((hexStrLength & 1) == 1) {
            throw new IllegalArgumentException("待转换的字符串非十六进制字符串，原因：字符串长度非偶数");
        }

        char[] hexChars = hexStr.toCharArray();
        byte[] bytes = new byte[hexStrLength >> 1];
        String errorMsg = "待转换的字符串非十六进制字符串，原因：字符串中含有非十六进制字符";

        try {
            for (int i = 0; i < bytes.length; i++) {
                int charIndex = i << 1;
                //取出"商"，参考上面的toHexString(byte[] bytes)方法
                int quotient = DEC_INTS_FOR_TRANS_FROM_HEX[hexChars[charIndex] - '0'];
                //取出"余数"，参考上面的toHexString(byte[] bytes)方法
                int remainder = DEC_INTS_FOR_TRANS_FROM_HEX[hexChars[charIndex + 1] - '0'];
                if (quotient == -1 || remainder == -1) {
                    //如果输入字符串中的字符超出了0123456789abcdefABCDEF的范围，就有可能取到-1
                    throw new IllegalArgumentException(errorMsg);
                }
                //(商 * 16 + 余数)就还原了原来的字节
                bytes[i] = (byte) ((quotient << 4) + remainder);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            //如果输入字符串中的字符超出了0123456789abcdefABCDEF的范围，就有可能抛出指针越界异常
            throw new IllegalArgumentException(errorMsg, e);
        }
        return bytes;
    }

    /* ********************************************************************************************** */
    /* **************是否抛异常分隔线（上：boolean，下：RuntimeException）************** */
    /* ********************************************************************************************** */


    /**
     * 指定成员变量列表对pojo的值进行非null/空数据校验，检验不通过抛出异常.
     *
     * @param pojo       需要进行校验的POJO  (Model/Entity)
     * @param filedNames 需要进行非null/空校验的成员变量列表
     * @param <T>        POJO类型
     */
    public static <T> void assertPojoFields(T pojo, String... filedNames) {

        if (pojo == null) {
            throw new IllegalArgumentException("传入的POJO不能为null");
        }
        if (isEmpty(filedNames)) {
            throw new IllegalArgumentException("成员变量名列表不能为空");
        }

        MethodAccessor methodAccessor = Reflector.getMethodAccessor(pojo.getClass());
        for (int i = 0; i < filedNames.length; i++) {
            String fieldName = filedNames[i];
            if (isEmpty(fieldName)) {
                throw new IllegalArgumentException("传入的第" + (i + 1) + "个成员变量名不能为null/空");
            }
            assertValue(fieldName, methodAccessor.getFieldValue(pojo, fieldName));
        }

    }

    /**
     * 对pojo的所有成员变量值进行非null/空数据校验，检验不通过抛出异常.
     *
     * @param pojo 需要进行校验的POJO  (Model/Entity)
     * @param <T>  POJO类型
     */
    public static <T> void assertPojoAllFields(T pojo) {

        if (pojo == null) {
            throw new IllegalArgumentException("传入的POJO不能为null");
        }

        MethodAccessor methodAccessor = Reflector.getMethodAccessor(pojo.getClass());
        String[] fieldNames = methodAccessor.getFieldNames();
        for (int i = 0; i < fieldNames.length; i++) {
            String fieldName = fieldNames[i];
            assertValue(fieldName, methodAccessor.getFieldValue(pojo, fieldName));
        }

    }

    /**
     * 指定key列表对request相应的值进行非null/空数据校验，检验不通过抛出异常.
     *
     * @param request 需要进行校验的ServletRequest对象
     * @param keys    需要进行非null/空校验的key列表
     */
    public static void assertRequestParams(ServletRequest request, String... keys) {

        if (isEmpty(request.getParameterMap())) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        if (isEmpty(keys)) {
            throw new IllegalArgumentException("key列表不能为空");
        }

        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            assertValue(key, request.getParameter(key));
        }
    }

    /**
     * 指定key列表对Map相应的值进行非null/空数据校验，检验不通过抛出异常.
     *
     * @param params 需要进行校验的Map对象
     * @param keys   需要进行非null/空校验的key列表
     * @param <K>    键类型
     * @param <V>    值类型
     */
    @SafeVarargs
    public static <K, V> void assertMapValues(Map<K, V> params, K... keys) {

        if (isEmpty(params)) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        if (isEmpty(keys)) {
            throw new IllegalArgumentException("key列表不能为空");
        }

        for (int i = 0; i < keys.length; i++) {
            K key = keys[i];
            assertValue(key, params.get(key));
        }
    }

    /**
     * 对Map所有值进行非null/空数据校验，检验不通过抛出异常.
     *
     * @param params 需要进行校验的Map对象
     * @param <K>    键类型
     * @param <V>    值类型
     */
    public static <K, V> void assertMapAllValues(Map<K, V> params) {

        if (isEmpty(params)) {
            throw new IllegalArgumentException("请求参数不能为空");
        }

        for (Map.Entry<K, V> entry : params.entrySet()) {
            assertValue(entry.getKey(), entry.getValue());
        }
        //params.forEach(RequestUtils::assertValue);//java8新供用法，目前比java7的foreach慢
    }

    /**
     * 校验值是否为空.
     *
     * @param keyOrFieldName 键或变量实例
     * @param value          值实例
     * @param <K>            键或变量泛型
     * @param <V>            值类型
     */
    public static <K, V> void assertValue(K keyOrFieldName, V value) {
        if (isEmpty(value)) {
            throw new IllegalArgumentException("参数“" + keyOrFieldName + "”的值不能为null/空");
        }
    }
}
