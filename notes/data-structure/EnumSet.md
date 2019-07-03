#### 一、EnumSet 概述
 
 `EnumSet` 是一个用于存储枚举类型的 set 集合，一般的 set 集合底层都是使用对应的 map 实现的，但 `EnumSet` 是个特例，它底层使用一个 `long` 类型的整数或者数组实现。
 
 下面我们先来看下它的使用方式：
 
 ```java
 public class EnumSetTest {
  
     public static void main(String[] args) {
         EnumSet<Season> e1 = EnumSet.allOf(Season.class);
         System.out.println(e1);
 
         EnumSet<Season> e2 = EnumSet.noneOf(Season.class);
         e2.add(Season.SPRING);
         System.out.println(e2);
 
         EnumSet<Season> e3 = EnumSet.of(Season.SPRING, Season.FAIL);
         System.out.println(e3);
 
         EnumSet<Season> e4 = EnumSet.range(Season.SPRING, Season.FAIL);
         System.out.println(e4);
     }
 }
 
 enum Season {
     SPRING,
     SUMMER,
     FAIL,
     WINTER
 }
 ```
 
 输出结果：
 
 [SPRING, SUMMER, FAIL, WINTER] <br>
 [SPRING] <br>
 [SPRING, FAIL] <br>
 [SPRING, SUMMER, FAIL] <br>
 [WINTER] <br>
 
 一般我们不使用构造函数来直接定义 `EnumSet`，`EnumSet` 提供了很灵活的方法可以供使用者有选择的进行初始化。`EnumSet` 本身是一个抽象类，对于增删改查的方法都放在了子类中去实现，这两个子类分别是：`RegularEnumSet` 与 `JumboEnumSet`。他们之间的主要区别是如果枚举类型中的元素大于 64 时，则创建 `JumboEnumSet` 对象，内部使用 `long` 类型的数组实现，反之创建 `RegularEnumSet`，内部使用 `long` 类型整数实现。
 
 ### 二、源码分析
 
 **2.1 初始化**
 
 我们以 `noneOf` 方法为例，来看下 `EnumSet` 是如何初始化的。
 
 ```java
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
 
 在 `noneOf` 方法中首先根据枚举类型获取所有的枚举值，接着判断然后根据枚举类型的元素个数初始化对应的 `EnumSet` 对象。
 
 `getUniverse` 方法如下：
 
 ```java
     private static <E extends Enum<E>> E[] getUniverse(Class<E> elementType) {
         return SharedSecrets.getJavaLangAccess()
                                         .getEnumConstantsShared(elementType);
     }
 
 ```
 
 关于 `SharedSecrets`,[Java 官方文档](http://www.docjar.com/docs/api/sun/misc/SharedSecrets.html)是这么描述的：
 
 >A repository of "shared secrets", which are a mechanism for calling implementation-private methods in another package without using reflection. 
 
 意思是在不使用反射的情况下在另外的包中调用实现私有方法的机制。而 `SharedSecrets.getJavaLangAccess().getEnumConstantsShared` 方法用于获取指定类型的枚举元素数组。
 
 **2.2 add 方法**
 
 下面我们以 `RegularEnumSet` 为例来看下具体源码的实现细节。
 
 
 ```java
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
 
 `add` 方法的内部实现就区区的四行代码，下面我们来详细分析下。首先进行枚举类型检查，接着用一个 `long` 类型的整数值接收 `elements`，下面是 `elements` 在 `RegularEnumSet` 中的定义：
 
 ```java
     /**
      * Bit vector representation of this set.  The 2^k bit indicates the
      * presence of universe[k] in this set.
      *
      * 该数的二进制如果比特位为 1，则表示有枚举类型元素
      */
     private long elements = 0L;
 ```
 
 在 `RegularEnumSet` 中使用的是一个 `long` 整数（二进制）来表示是否添加或者删除了某个枚举值。添加枚举元素的关键代码是这句：`elements |= (1L << ((Enum<?>)e).ordinal());`，下面我们以一个例子来说明是如何把枚举元素添加到集合的。
 
 我们还以上面的 `Season` 枚举为例，假设此时没有添加任何元素则 `elements` 的值为 0，现在往集合里添加元素 `FAIL`，`e.ordinal()` 的值为 2，1 右移 2 位值为 4，换成二进制表示为 `0000 0100`。如果 `elements` 用二进制表示且某下标（index）的值为 1，则表示该位上有枚举元素，则枚举元素就是 `e.ordinal() = index` 位置上的。
 
 
 再举个例子验证一下，假设集合中已经有了 `FAIL` 元素则 `elements` 的值为 4（0000 0100），现在添加元素 `WINTER`，`e.ordinal()` 的值为 3，1 右移 3 位值为 8，换成二进制表示为 `0000 1000`，与 `elements` 进行或运算后是（`0000 1100`）。
 
 
 **2.3 remove 方法**
 
 ```java
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
 
 我们已经知道了添加元素的过程，那移除元素无非就是把对应位置上的 1 替换为 0，`elements &= ~(1L << ((Enum<?>)e).ordinal());` 这行代码执行的就是这个逻辑，我们还以一个例子来说明下问题。
 
 假设现在集合中有 `FAIL` 与 `WINTER`，则 `elements` 的值为 12（`0000 1100`），现在我们要移除 `FAIL`，`(1L << ((Enum<?>)e).ordinal())` 运算过后值为 4（`0000 0100`），取反过后为 `1111 1011`，与 `0000 1100` 进行与运算之后为 `0000 1000`，最后的结果可以看出，`FAIL` 对应位置上的 1 经过运算之后变成了 0。
 
 `RegularEnumSet` 内部的其他方法也是基于 `elements` 变量的二进制进行操作的，有兴趣的可以自行查看。