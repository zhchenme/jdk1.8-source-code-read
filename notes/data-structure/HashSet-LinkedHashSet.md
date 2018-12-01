### 一、HashSet & LinkedHashSet

#### 1.1 相关知识点

- `HashSet` 底层的数据结构是一个 `HashMap`，`HashSet` 的值是 `HashMap` 的 key，其对应的 value 是一个 `static final Object` 对象
- `HashSet` 所有的增删该查方法调用的都是对应的 `HashMap` 的方法，因此你只需要理解 `HashMap` 就可以了
- `LinkedHashSet` 继承自 `HashSet`，`LinkedHashSet` 除了有几个构造函数外没有一个方法实现...

#### 1.2 为什么 LinkedHashSet 没有方法实现也可以维护元素值的顺序呢？

因为 `LinkedHashSet` 继承自 `HashSet`，而在 `HashSet` 中提供了一个构造函数，该构造函数中创建了一个 `LinkedHashMap`，具体方法如下：

``` java
    HashSet(int initialCapacity, float loadFactor, boolean dummy) {
        map = new LinkedHashMap<>(initialCapacity, loadFactor);
    }
```
 
`LinkedHashSet` 的所有构造函数都调用了上面的构造函数，因为 `LinkedHashMap` 底层维护着一个双端链表，因此 `LinkedHashSet` 能保证插入的元素顺序。

