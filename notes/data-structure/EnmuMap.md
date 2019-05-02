### 一、EnumMap 概述

`EnumMap` 是一个用于存储 key 为枚举类型的 map，底层使用数组实现（K，V 双数组）。下面是其继承结构：

``` java
public class EnumMap<K extends Enum<K>, V> extends AbstractMap<K, V>
    implements java.io.Serializable, Cloneable
```

从上面的继承结构上可以看出 `EnumMap` 的 key 必须是一个枚举类型，而 value 没有限制。

**1.1 内部属性**

``` java
    // key 类型
    private final Class<K> keyType;

    // key 数组
    private transient K[] keyUniverse;

    // value 数组
    private transient Object[] vals;

    // 键值对个数
    private transient int size = 0;

    // value 为 null 时对应的值
    private static final Object NULL = new Object() {
        public int hashCode() {
            return 0;
        }

        public String toString() {
            return "java.util.EnumMap.NULL";
        }
    };
```

与其他类型 map 不同的是 `EnumMap` 底层使用双数组来存储 key 与 value，key 数组会在构造函数中根据 `keyType` 进行初始化，下面我们会看到。当 `EnmumMap` 的 value 为 null 时会特殊处理为一个 `Object` 对象。

**1.2 构造函数**

`EnumMap` 共提供了 3 个构造函数，如下：

![在这里插入图片描述](https://img-blog.csdnimg.cn/20190502163722665.png)

下面我们只来看其中一个指定类型的构造函数。

``` java
    public EnumMap(Class<K> keyType) {
        this.keyType = keyType;
        // 初始化 key 数组，getKeyUniverse 方法会计算出枚举元素的总数并初始化 key 数组
        keyUniverse = getKeyUniverse(keyType);
        // 初始化 value 数组大小
        vals = new Object[keyUniverse.length];
    }
```

在使用上述构造函数初始化 `EnumMap` 的时候必须指定枚举类型，上面我们已经说过，`EnumMap` 会在构造函数中初始化 key 数组，这个初始化动作是在 `getKeyUniverse(keyType)` 中完成的。

``` java
    private static <K extends Enum<K>> K[] getKeyUniverse(Class<K> keyType) {
        return SharedSecrets.getJavaLangAccess()
                                        .getEnumConstantsShared(keyType);
    }
```

一开始看上面的代码可能有点懵，这怎么就初始化了 key 数组呢？在 Java 中我们可以通过 `JavaLangAccess` 和 `SharedSecrets` 来获取 JVM 中对象实例，具体是怎么实现的，有兴趣的可以查相关的资料了解下。

我们以 debug 形式来验证下 key 数组是否会在构造函数中被初始化与赋值：

首先来声明一个枚举类型：

``` java
enum Season {
    SPRING("春天"), SUMMER("夏天"), FALL("秋天"), WINTER("冬天");

    private final String name;

    Season(String name) {
        this.name = name;
    }
}
```

测试类：

``` java
    public static void main(String[] args) throws Exception {
        EnumMap<Season, String> map = new EnumMap<>(Season.class);
    }
```

我们把断点打在其构造函数上就会看到 `keyUniverse` 数组被初始化了，且数组的元素顺序与在枚举类型中定义的顺序一致。如下图：

![在这里插入图片描述](https://img-blog.csdnimg.cn/201905021637476.png)

**1.3 使用方式**

``` java
    public static void main(String[] args) throws Exception {
        EnumMap<Season, String> map = new EnumMap<>(Season.class);
        map.put(Season.FALL, "硕果累累的秋天");
        map.put(Season.WINTER, "寒风凛冽的冬天");
        System.out.println(map.get(Season.FALL));
    }
```

### 二、相关源码分析

**2.1 put 方法**

``` java
    public V put(K key, V value) {
        // key 类型检查
        typeCheck(key);

        // 获得该 key 对应的位置
        int index = key.ordinal();
        // 在 vals 数组中获取 key 角标对应的 value
        Object oldValue = vals[index];
        // 覆盖或设置 value
        vals[index] = maskNull(value);
        // 如果 key 对应的位置 value 为 null，则表示新插入了键值对，size++，反之表示值覆盖 size 不变
        if (oldValue == null)
            size++;
        return unmaskNull(oldValue);
    }
```

在添加键值对的时候会先检查 key 的类型，如果 key 的类型不一致会抛出异常。

``` java
    private void typeCheck(K key) {
        Class<?> keyClass = key.getClass();
        if (keyClass != keyType && keyClass.getSuperclass() != keyType)
            throw new ClassCastException(keyClass + " != " + keyType);
    }
```

PS： `keyType` 在构造函数中已经被初始化了。

`EnumMap` 存储键值对时并不会根据 key 获取对应的哈希值，`enum` 本身已经提供了一个 `ordinal()` 方法，该方法会返回具体枚举元素在枚举类中的位置（从 0 开始），因此一个枚举元素从创建就已经有了一个唯一索引与其对应，这样就不存在哈希冲突的问题了。

如果添加的 value 为 null 会通过 `maskNull` 方法特殊处理，存储一个 `Object` 对象。

``` java
    private Object maskNull(Object value) {
        return (value == null ? NULL : value);
    }
```

如果值覆盖的话，`put` 方法会返回旧的 value 值，并特殊处理 value 为 null 的情况：

``` java
    private V unmaskNull(Object value) {
        return (V)(value == NULL ? null : value);
    }
```

`EnmuMap` 添加键值对并没有扩容操作，因为一个枚举类型到底有多少元素在代码运行阶段是确定的，在构造函数中已经对 key 数组进行了初始化与赋值，value 数组的大小也已经被确定。还有一个需要注意的问题，在上面的 `put`
 方法中只对 value 进行了处理，并没有处理 key，原因就是 key 数组在构造函数中已经被赋值了。
 
 **2.2 remove 方法**
 
``` java
     public V remove(Object key) {
        // key 类型错误的时候直接返回 null
        if (!isValidKey(key))
            return null;
        // 根据 key 计算出其在枚举中位置
        int index = ((Enum<?>)key).ordinal();
        // 获取对应的 value
        Object oldValue = vals[index];
        // value 置 null，下次 GC 回收
        vals[index] = null;
        // 如果对应的 value 不为 null，如果添加键值对的时候 value 为 null，则存储的是 NULL（Object）
        if (oldValue != null)
            size--;
        return unmaskNull(oldValue);
    }
```

在移除键值对的时候会先调用 `isValidKey` 方法对 key 进行一次检查：

``` java
    private boolean isValidKey(Object key) {
        // key 为 null 直接返回 false
        if (key == null)
            return false;

        // Cheaper than instanceof Enum followed by getDeclaringClass
        Class<?> keyClass = key.getClass();
        // key 类型检查
        return keyClass == keyType || keyClass.getSuperclass() == keyType;
    }
```

`remove` 方法相对来说比较简单，这里就不总结了。

**2.3 a question**

从上面的源码分析中我们知道，key 数组自从在构造函数中完成初始化之后就没有执行过增删改的操作，是不是意味着我们根据枚举类型创建一个 `EnumMap` 之后，就算不添加任何键值对，也能根据其迭代器获取所有的 key，因为 key 在构造函数中已经被赋值了。看下面的代码：

``` java
    public static void main(String[] args) throws Exception {
        EnumMap<Season, String> map = new EnumMap<>(Season.class);
        // 获取迭代器对象
        Iterator<Map.Entry<Season, String>> iterator = map.entrySet().iterator();
        
        while (iterator.hasNext()) {
            System.out.println(iterator.next().getKey());
        }
    }
```

结果是上面的代码并不会输出任何 key，原因就在于 `EnumMap` 的 `hasNext()` 方法中对 value 做了非空判断，如下：

``` java
        public boolean hasNext() {
            // 循环中会略过 value 数组中为 null 的情况
            while (index < vals.length && vals[index] == null)
                index++;
            return index != vals.length;
        }
```

尽管在构造函数中 key 数组已经被初始化，但是如果对应的 value 为 null，在迭代的时候也会被过滤掉。

`EnumMap` 相对来说比较简单，关于源码就介绍到这里。
