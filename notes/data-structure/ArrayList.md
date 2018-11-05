### 一、ArrayList 扩容机制
1：调用 `add` 方法<br>
``` java
    public boolean add(E e) {
        ensureCapacityInternal(size + 1);  // Increments modCount!!
        elementData[size++] = e;
        return true;
    }
```

2：调用 `ensureCapacityInternal` 方法<br>
``` java
    private void ensureCapacityInternal(int minCapacity) {
        // 判空
        if (elementData == DEFAULTCAPACITY_EMPTY_ELEMENTDATA) {
            minCapacity = Math.max(DEFAULT_CAPACITY, minCapacity);
        }

        ensureExplicitCapacity(minCapacity);
    }
```

3：调用 `ensureExplicitCapacity` 方法<br>
``` java
    private void ensureExplicitCapacity(int minCapacity) {
        modCount++;

        // 当数组存不下新元素时，对数组进行扩容
        if (minCapacity - elementData.length > 0)
            grow(minCapacity);
    }
```

4：调用 `grow` 方法<br>
``` java
    private void grow(int minCapacity) {
        // overflow-conscious code
        int oldCapacity = elementData.length;
        // 数组容量被扩大为原来的 1.5 倍
        int newCapacity = oldCapacity + (oldCapacity >> 1);
        if (newCapacity - minCapacity < 0)
            newCapacity = minCapacity;
        // MAX_ARRAY_SIZE = 2<sup>31</sup>-1-8
        if (newCapacity - MAX_ARRAY_SIZE > 0)
            newCapacity = hugeCapacity(minCapacity);
        // minCapacity is usually close to size, so this is a win:
        elementData = Arrays.copyOf(elementData, newCapacity);
    }
```

5：调用 `Arrays.copyOf` 方法<br>
``` java
    public static <T,U> T[] copyOf(U[] original, int newLength, Class<? extends T[]> newType) {
        @SuppressWarnings("unchecked")
        T[] copy = ((Object)newType == (Object)Object[].class)
            ? (T[]) new Object[newLength]
            : (T[]) Array.newInstance(newType.getComponentType(), newLength);
        System.arraycopy(original, 0, copy, 0,
                         Math.min(original.length, newLength));
        return copy;
    }
```