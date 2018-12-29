### 一、EnumSet 介绍

`EnumSet` 是一种特殊的 `Set` 集合，它存储的是枚举元素，根据枚举元素的 `ordinal()` 方法计算出对应的位置，然后根据该位置通过一个 `long` 类型的二进制数来表示对该枚举元素的增删改查操作。

这种通过二进制位来表示存储信息的状态在 C 语言中被称为位域。位域通过 0 和 1 来表示信息的存储状态，可以节省存储空间。

### 二、相关方法

**2.1 noneOf 方法**

``` java
    public static <E extends Enum<E>> EnumSet<E> noneOf(Class<E> elementType) {
        // 此时 universe 包含所有的枚举值
        Enum<?>[] universe = getUniverse(elementType);
        if (universe == null)
            throw new ClassCastException(elementType + " not an enum");

        // 根据枚举元素的大小判断初始化什么类型的 EnumSet 对象
        if (universe.length <= 64)
            return new RegularEnumSet<>(elementType, universe);
        else
            return new JumboEnumSet<>(elementType, universe);
    }
```

`EnumSet` 只是一个抽象类，在初始化对象时会根据枚举元素的数量分别创建对应的对象，下面以 `RegularEnumSet` 为例，看下对应的方法。

**2.2 add 方法**

``` java
    public boolean add(E e) {
        // 类型检查
        typeCheck(e);

        // 获取存储枚举元素的标记值（比特位存储了对应的枚举元素）
        long oldElements = elements;
        // 通过 | 运算在比特位上追加新元素
        elements |= (1L << ((Enum<?>)e).ordinal());
        // 如果枚举元素已经存在时，返回 false
        return elements != oldElements;
    }
```

**2.3 remove 方法**

``` java
    public boolean remove(Object e) {
        // 如果为 null，直接返回 false
        if (e == null)
            return false;
        Class<?> eClass = e.getClass();
        // 类型检查
        if (eClass != elementType && eClass.getSuperclass() != elementType)
            return false;

        // 获取标记值
        long oldElements = elements;
        // 将 remove 的元素对应的比特位上的 1 置 0
        elements &= ~(1L << ((Enum<?>)e).ordinal());
        return elements != oldElements;
    }
```