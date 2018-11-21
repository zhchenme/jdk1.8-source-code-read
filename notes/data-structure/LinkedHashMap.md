一张图看懂 `LinkedHashMap`...

![](http://phtpnyqb4.bkt.clouddn.com/linkedhashmap)<br>

### 一、与 HashMap 中的比较

1.判断是否包含指定的 value 
- `HashMap` 遍历哈希表中的所有桶，然后遍历桶位置上的所有节点
- `LinkedHashMap` 从头节点遍历，一直遍历到尾节点

`HashMap` 中代码如下
``` java
    public boolean containsValue(Object value) {
        Node<K,V>[] tab; V v;
        if ((tab = table) != null && size > 0) {
            // 遍历哈希表
            for (int i = 0; i < tab.length; ++i) {
                // 遍历当前桶上的所有节点
                // TODO 为什么没有对节点类型进行判断，分别走对应的查找？
                for (Node<K,V> e = tab[i]; e != null; e = e.next) {
                    if ((v = e.value) == value ||
                        (value != null && value.equals(v)))
                        return true;
                }
            }
        }
        return false;
    }
```

`LinkedHashMap` 中代码如下
``` java
    public boolean containsValue(Object value) {
        /**
         * 遍历双向链表，判断 value，与 HashMap 完全不同
         */
        for (LinkedHashMap.Entry<K,V> e = head; e != null; e = e.after) {
            V v = e.value;
            if (v == value || (value != null && value.equals(v)))
                return true;
        }
        return false;
    }
```

种 BUG，找 BUG，种 BUG，找 BUG，种 BUG，找 BUG ...