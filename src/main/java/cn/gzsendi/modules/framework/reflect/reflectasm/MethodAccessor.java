package cn.gzsendi.modules.framework.reflect.reflectasm;

import static org.springframework.asm.Opcodes.AALOAD;
import static org.springframework.asm.Opcodes.ACC_PUBLIC;
import static org.springframework.asm.Opcodes.ACC_SUPER;
import static org.springframework.asm.Opcodes.ACC_VARARGS;
import static org.springframework.asm.Opcodes.ACONST_NULL;
import static org.springframework.asm.Opcodes.ALOAD;
import static org.springframework.asm.Opcodes.ARETURN;
import static org.springframework.asm.Opcodes.ASTORE;
import static org.springframework.asm.Opcodes.ATHROW;
import static org.springframework.asm.Opcodes.BIPUSH;
import static org.springframework.asm.Opcodes.CHECKCAST;
import static org.springframework.asm.Opcodes.DUP;
import static org.springframework.asm.Opcodes.F_APPEND;
import static org.springframework.asm.Opcodes.F_SAME;
import static org.springframework.asm.Opcodes.ILOAD;
import static org.springframework.asm.Opcodes.INVOKEINTERFACE;
import static org.springframework.asm.Opcodes.INVOKESPECIAL;
import static org.springframework.asm.Opcodes.INVOKESTATIC;
import static org.springframework.asm.Opcodes.INVOKEVIRTUAL;
import static org.springframework.asm.Opcodes.NEW;
import static org.springframework.asm.Opcodes.RETURN;
import static org.springframework.asm.Opcodes.V1_8;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.asm.ClassWriter;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Type;
import org.springframework.util.StringUtils;

import cn.gzsendi.modules.framework.reflect.Reflector;
import cn.gzsendi.modules.framework.utils.JsonUtil;
import cn.gzsendi.modules.framework.utils.ParameterUtils;

/**
 * asm方法操作器.
 *
 * <pre>
 * 创建asm方法操作器是一个非常耗时的过程，
 * 创建后尽量请把它缓存起来重复利用再用，以提高反射效率。
 *
 * ASM框架是一个致力于字节码操作和分析的框架，它可以用来修改一个已存在的类或者动态产生一个新的类。
 * ASM提供了一些通用的字节码转换和分析算法，通过这些算法可以定制更复杂的工具。
 * ASM提供了其它字节码工具相同的功能，但是它更关注执行效率，它被设计的更小更快，它被用于以下项目：
 *
 * OpenJDK，实现lambda表达式调用，Nashorn编译器；
 * Groovy和Kotlin编译器；
 * Cobertura 和Jacoco，测量代码范围；
 * CGLIB动态代理类；
 * Gradle, 运行时生成Class类。
 *
 * ASM框架的maven仓库引用地址：
 * &lt;dependency&gt;
 * &nbsp;&nbsp;&lt;groupId&gt;org.ow2.asm&lt;/groupId&gt;
 * &nbsp;&nbsp;&lt;artifactId&gt;asm&lt;/artifactId&gt;
 * &nbsp;&nbsp;&lt;version&gt;对应版本&lt;/version&gt;
 * &lt;/dependency&gt;
 *
 * 这里为了不在pom.xml中显式引用ASM框架，
 * 使用了{@link org.springframework.asm }包下的相关字节码操作类，
 * 它是Spring对ASM框架的重新包装，带有Spring特有的补丁，
 * 这种重新打包技术避免了与应用程序级或第三方库/框架对ASM的依赖之间的任何潜在冲突。
 *
 * 如果了解其所生成的字节码内容，可发现用其来实现方法的反射调用的效率比jdk自带的反射效率高很多
 * </pre>
 *
 * @author Mr.XiHui
 * @date 2018/09/01
 */
public abstract class MethodAccessor {

    //private final Class<?> clazz;
    private final String className;
    private final String[] methodNames;
    private final Class[][] parameterTypes;
    private final Class[] returnTypes;
    /*** 构造时的Class所持有的非private方法的入参泛型Type列表. */
    private final java.lang.reflect.Type[][] genericParameterTypes;
    /*** 构造时的Class所持有的非private方法的返回值泛型Type列表. */
    private final java.lang.reflect.Type[] genericReturnTypes;
    /**
     * MethodAccessor使用索引调用方法执行比直接通过方法名调用方法执行更快.
     *
     * <pre>
     *
     * key:
     * 1、第一个clazz.getName() + "." + method.getName()，仅供查找getter和setter的索引使用
     * 2、method.toString().subString(method.toString().indexOf(clazz.getName()))
     *
     * value:
     * 以下数组的index （它们的大小都一样，一一对应一个Method）
     * {@link #methodNames}
     * {@link #parameterTypes}
     * {@link #returnTypes}
     * {@link #returnTypes}
     * {@link #genericParameterTypes}
     * {@link #genericReturnTypes}
     *
     * </pre>
     */
    private final Map<String, Integer> methodNameIndexMap;
    /**
     * 构造时的Class的变量名数组.
     * <p>
     * 注意：fieldNames的大小与以上数组极有可能不同，且非一一对应
     * </p>
     */
    private final String[] fieldNames;

    protected MethodAccessor(String className,
                             String[] methodNames,
                             Class[][] parameterTypes,
                             Class[] returnTypes,
                             java.lang.reflect.Type[][] genericParameterTypes,
                             java.lang.reflect.Type[] genericReturnTypes,
                             Map<String, Integer> methodNameIndexMap,
                             String[] fieldNames) {
        this.className = className;
        this.methodNames = methodNames;
        this.parameterTypes = parameterTypes;
        this.returnTypes = returnTypes;
        this.genericParameterTypes = genericParameterTypes;
        this.genericReturnTypes = genericReturnTypes;
        this.methodNameIndexMap = methodNameIndexMap;
        this.fieldNames = fieldNames;
    }

    /**
     * 执行对象相应的方法获取返回值.
     *
     * @param obj         对象
     * @param methodIndex 方法索引
     * @param args        方法参数列表
     * @return Object 具体返回类型因具体被执行的方法而异
     */
    public abstract Object invoke(Object obj, int methodIndex, Object... args);

    /**
     * 创建指定类的MethodAccessor.
     *
     * <pre>
     * 创建MethodAccessor是一个非常耗时的过程，
     * 创建后请尽量把它缓存起来重复利用再用，以提高反射效率
     * {@link Reflector#getMethodAccessor(Class)}有现成的实现，所以请通过Reflector获取MethodAccessor
     * </pre>
     *
     * @param type 不能是Object.class、基本数据类型及void.class
     * @return type对应的MethodAccessor子类
     */
    public static MethodAccessor get(Class<?> type) {

        if (type == null) {
            throw new IllegalArgumentException("The type must not null");
        }

        boolean isInterface = type.isInterface();
        if (!isInterface && type.getSuperclass() == null) {
            throw new IllegalArgumentException("The type must not be the Object class, " +
                    "an interface, a primitive type, or void.");
        }

        List<Method> methods = new ArrayList<>();
        if (!isInterface) {
            Class<?> nextClass = type;
            while (nextClass != Object.class) {
                addNonPrivateMethodsToList(nextClass, methods);
                nextClass = nextClass.getSuperclass();
            }
        } else {
            recursiveAddInterfaceMethodsToList(type, methods);
        }

        String className = type.getName();
        int size = methods.size();
        int halfPlusOne = size >> 1;
        String[] methodNames = new String[size];
        Class[][] parameterTypes = new Class[size][];
        Class[] returnTypes = new Class[size];
        java.lang.reflect.Type[][] genericParameterTypes = new java.lang.reflect.Type[size][];
        java.lang.reflect.Type[] genericReturnTypes = new java.lang.reflect.Type[size];
        Map<String, Integer> methodNameIndexMap = new HashMap<>(ParameterUtils.calcMapCapacity(size + halfPlusOne));
        List<String> fieldNameList = new ArrayList<>(halfPlusOne);

        assignValues(methods, className, methodNames, parameterTypes, returnTypes, genericParameterTypes,
                genericReturnTypes, methodNameIndexMap, fieldNameList);

        //转成构造函数需要的字符串数组
        String[] fieldNames = fieldNameList.toArray(new String[0]);
        //转成不可变Map
        methodNameIndexMap = Collections.unmodifiableMap(methodNameIndexMap);

        String superSimpleName = MethodAccessor.class.getSimpleName();
        String accessorClassName = className + superSimpleName;

        String jdkPackagePrefix = "java.";
        if (accessorClassName.startsWith(jdkPackagePrefix)) {
            accessorClassName = "reflectasm." + accessorClassName;
        }

        Class<?> accessorClass;
        AccessorClassLoader loader = AccessorClassLoader.get(type);
        synchronized (loader) {

            //如果从类路径上加载成功，则直接构造新实例返回
            //如果从类路径上加载不到，则动态生成字节码加载再构造新实现返回
            accessorClass = loader.loadAccessorClass(accessorClassName);
            if (accessorClass == null) {

                String accessorClassNameInternal = accessorClassName.replace('.', '/');
                String classNameInternal = className.replace('.', '/');
                String superName = MethodAccessor.class.getName().replace('.', '/');

                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);

                //声明一个类，使用JDK1.8版本，public的类，父类是java.lang.Object，没有实现任何接口
                cw.visit(V1_8, ACC_PUBLIC + ACC_SUPER, accessorClassNameInternal, null, superName, null);

                insertParametricConstructor(superName, cw);

                //==============================================================================================

                overrideInvokeMethod(isInterface, methods, methodNames, parameterTypes, returnTypes,
                        classNameInternal, cw);

                //==============================================================================================

                cw.visitEnd();

                //==============================================================================================

                byte[] data = cw.toByteArray();

                //try {
                //
                //    //把字节码输出到class文件，然后丢进idea里面就可以看到生成的代码是什么样子的了
                //    //String directory = "D:\\";
                //    String directory = "C:\\Users\\Ryzen5\\Desktop\\";
                //    FileCopyUtils.copy(data, new File(directory + type.getSimpleName() + superSimpleName + ".class"));
                //
                //} catch (IOException e) {
                //    e.printStackTrace();
                //}

                //通过刚生成的字节码数组加载类MethodAccessor子类
                accessorClass = loader.defineAccessorClass(accessorClassName, data);
            }
        }

        return newInstance(accessorClass, className, methodNames, parameterTypes, returnTypes,
                genericParameterTypes, genericReturnTypes, methodNameIndexMap, fieldNames);
    }

    /**
     * 执行指定方法名、指定参数类型及参数值列表对应的方法.
     *
     * @param obj        方法的源对象
     * @param methodName 方法名
     * @param paramTypes 参数类型数组
     * @param args       参数值列表
     * @return Object 方法被执行所得的返回对象
     */
    public Object invoke(Object obj, String methodName, Class[] paramTypes, Object... args) {
        return invoke(obj, getIndex(methodName, paramTypes), args);
    }

    /**
     * 执行指定方法名及参数值列表对应的第一个方法.
     *
     * @param obj        方法的源对象
     * @param methodName 方法名
     * @param args       参数值列表
     * @return Object 方法被执行所得的返回对象
     */
    public Object invoke(Object obj, String methodName, Object... args) {
        return invoke(obj, getIndex(methodName, args == null ? 0 : args.length), args);
    }

    /**
     * 获取指定方法名的第一个方法的索引.
     *
     * @param methodName 方法名
     * @return int {@link #fieldNames}中的方法所对应的索引
     */
    public int getIndex(String methodName) {
        for (int i = 0; i < methodNames.length; i++) {
            if (methodNames[i].equals(methodName)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unable to find non-private method: " + methodName);
    }

    /**
     * 获取指定方法名、指定参数类型及参数值列表对应的第一个方法的索引.
     *
     * @param methodName 方法名
     * @param paramTypes 参数类型列表
     * @return int {@link #fieldNames}中的方法所对应的索引
     */
    public int getIndex(String methodName, Class... paramTypes) {
        for (int i = 0; i < methodNames.length; i++) {
            if (methodNames[i].equals(methodName) && Arrays.equals(paramTypes, parameterTypes[i])) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unable to find non-private method: "
                + methodName + " " + Arrays.toString(paramTypes));
    }

    /**
     * 获取指定方法名、指定参数个数的第一个方法的索引.
     *
     * @param methodName  方法名
     * @param paramsCount 参数个数
     * @return int {@link #fieldNames}中的方法所对应的索引
     */
    public int getIndex(String methodName, int paramsCount) {
        for (int i = 0; i < methodNames.length; i++) {
            if (methodNames[i].equals(methodName) && parameterTypes[i].length == paramsCount) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unable to find non-private method: "
                + methodName + " with " + paramsCount + " params.");
    }

    /**
     * 获取方法名
     *
     * @param index 索引
     * @return 方法名
     */
    public String getMethodName(int index) {
        return methodNames[index];
    }

    /**
     * 获取方法参数类型
     *
     * @param pIndex 一维索引
     * @param sIndex 二维索引
     * @return 方法参数类型
     */
    public Class<?> getParameterType(int pIndex, int sIndex) {
        return parameterTypes[pIndex][sIndex];
    }

    /**
     * 获取方法参数类型数组
     *
     * @param pIndex 一维索引
     * @return 方法参数类型数组
     */
    public Class[] getParameterTypes(int pIndex) {
        return parameterTypes[pIndex].clone();
    }

    /**
     * 获取方法返回值类型
     *
     * @param index 索引
     * @return 方法返回值类型
     */
    public Class<?> getReturnTypes(int index) {
        return returnTypes[index];
    }

    /**
     * 获取方法泛型参数类型
     *
     * @param pIndex 一维索引
     * @param sIndex 二维索引
     * @return 方法泛型参数类型
     */
    public java.lang.reflect.Type getGenericParameterType(int pIndex, int sIndex) {
        return genericParameterTypes[pIndex][sIndex];
    }

    /**
     * 获取方法泛型参数类型数组
     *
     * @param pIndex 一维索引
     * @return 方法泛型参数类型数组
     */
    public java.lang.reflect.Type[] getGenericParameterTypes(int pIndex) {
        return genericParameterTypes[pIndex].clone();
    }

    /**
     * 获取方法泛型返回值类型
     *
     * @param index 索引
     * @return 方法泛型返回值类型
     */
    public java.lang.reflect.Type getGenericReturnType(int index) {
        return genericReturnTypes[index];
    }

    public String[] getMethodNames() {
        return methodNames.clone();
        //String[] dest = new String[methodNames.length];
        //System.arraycopy(methodNames, 0, dest, 0, methodNames.length);
        //return dest;
        //return methodNames;
        //return Arrays.copyOf(methodNames, methodNames.length);
    }

    public Class[][] getParameterTypes() {
        return parameterTypes.clone();
    }

    public Class[] getReturnTypes() {
        return returnTypes.clone();
    }

    public java.lang.reflect.Type[][] getGenericParameterTypes() {
        return genericParameterTypes.clone();
    }

    public java.lang.reflect.Type[] getGenericReturnTypes() {
        return genericReturnTypes.clone();
    }

    public Map<String, Integer> getMethodNameIndexMap() {
        return methodNameIndexMap;
    }

    public int getFieldNamesLength() {
        return fieldNames.length;
    }

    public String[] getFieldNames() {
        return fieldNames.clone();
    }

    /**
     * 从对象obj的变量fieldName取值
     *
     * @param obj       变量的源对象
     * @param fieldName 变量名
     * @return 变量值
     */
    public Object getFieldValue(Object obj, String fieldName) {
        Integer getterIndex = getterIndex(fieldName);
        if (getterIndex == null) {
            return null;
        }
        try {
            return invoke(obj, getterIndex);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    String.format("通过方法(%s.%s())获取值失败",
                            obj.getClass().getName(),
                            methodNames[getterIndex]),
                    e);
        }
    }

    /**
     * 给对象obj的变量fieldName赋值.
     *
     * @param obj       变量的目标对象
     * @param fieldName 变量名
     * @param arg       变量setter方法的入参
     */
    public void setFieldValue(Object obj, String fieldName, Object arg) {
        Integer setterIndex = setterIndex(fieldName);
        if (setterIndex == null) {
            return;
        }
        try {
            invoke(obj, setterIndex, arg);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    String.format("方法(%s.%s(%s))的入参(%s)不匹配",
                            obj.getClass().getName(),
                            methodNames[setterIndex],
                            StringUtils.arrayToDelimitedString(parameterTypes[setterIndex], ","),
                            arg.getClass().getName()),
                    e);
        }
    }

    /**
     * 获取对象obj的getter方法的索引.
     * <p>
     * 返回null表示fieldName无匹配的getter方法
     * </p>
     *
     * @param fieldName getter方法对应的变量名（这里视所有getter方法都对应一个变量）
     * @return getter方法的索引
     */
    public Integer getterIndex(String fieldName) {
        return getPojoMethodIndex(fieldName, true);
    }

    /**
     * 获取对象obj的setter方法的索引.
     * <p>
     * 返回null表示fieldName无匹配的setter方法
     * </p>
     *
     * @param fieldName setter方法对应的变量名（这里视所有setter方法都对应一个变量）
     * @return setter方法的索引
     */
    public Integer setterIndex(String fieldName) {
        return getPojoMethodIndex(fieldName, false);
    }

    /**
     * 通过method.toString字符串去掉类名前面一段得到的部分获取方法索引.
     *
     * <pre>
     * 如public void com.company.project.business.model.po.Contact.setContactId(java.lang.String)
     * 去掉 "public void " 得到com.company.project.business.model.po.Contact.setContactId(java.lang.String)
     * </pre>
     *
     * @param methodStr method.toString().subString(method.toString().indexOf(clazz.getName()))对应的字符串
     * @return 方法对应的索引，null表示无匹配的方法
     */
    public Integer getMethodIndex(String methodStr) {
        return methodNameIndexMap.get(methodStr);
    }

    /**
     * 通过方法名和入参列表获取方法对应的索引.
     *
     * @param methodName 方法名
     * @param args       方法参数列表
     * @return 方法对应的索引，null表示无匹配的方法
     */
    public Integer getMethodIndex(String methodName, Object... args) {
        c:
        for (int i = 0; i < methodNames.length; i++) {
            if (methodNames[i].equals(methodName)) {
                Class[] paramTypes = parameterTypes[i];
                if (paramTypes.length != args.length) {
                    continue;
                }
                for (int j = 0; j < paramTypes.length; j++) {
                    if (!Reflector.isInstance(paramTypes[j], args[j])) {
                        continue c;//不匹配，则跳到下一个外层循环
                    }
                }
                return i;
            }
        }
        return null;
    }

    /**
     * 添加所有clazz中声明的非private方法到methods.
     */
    private static void addNonPrivateMethodsToList(Class<?> type, List<Method> methods) {
        Method[] declaredMethods = type.getDeclaredMethods();
        for (int i = 0, n = declaredMethods.length; i < n; i++) {
            Method method = declaredMethods[i];
            int modifiers = method.getModifiers();
            if (Modifier.isPrivate(modifiers)) {
                continue;
            }
            methods.add(method);
        }
    }

    /**
     * 添加所有interfaceClass中声明的非private方法到methods.
     */
    private static void recursiveAddInterfaceMethodsToList(Class<?> interfaceType, List<Method> methods) {
        addNonPrivateMethodsToList(interfaceType, methods);
        for (Class<?> nextInterface : interfaceType.getInterfaces()) {
            recursiveAddInterfaceMethodsToList(nextInterface, methods);
        }
    }

    /**
     * 给各数组/集合赋值.
     *
     * @param methods               获取到的方法对象集合
     * @param className             类名
     * @param methodNames           待赋值的方法名数组
     * @param parameterTypes        待赋值的方法对象的参数类型二维数组
     * @param returnTypes           待赋值的方法对象的返回值类型数组
     * @param genericParameterTypes 待赋值的方法对象的参数化参数类型二维数组
     * @param genericReturnTypes    待赋值的方法对象的参数化返回值类型数组
     * @param methodNameIndexMap    待赋值的方法名索引Map
     * @param fieldNameList         待赋值的成员变量名List
     */
    private static void assignValues(List<Method> methods, String className, String[] methodNames,
                                     Class[][] parameterTypes, Class[] returnTypes,
                                     java.lang.reflect.Type[][] genericParameterTypes,
                                     java.lang.reflect.Type[] genericReturnTypes,
                                     Map<String, Integer> methodNameIndexMap,
                                     List<String> fieldNameList) {

        for (int i = 0; i < methods.size(); i++) {
            Method method = methods.get(i);
            methodNames[i] = method.getName();
            parameterTypes[i] = method.getParameterTypes();
            returnTypes[i] = method.getReturnType();
            genericParameterTypes[i] = method.getGenericParameterTypes();
            genericReturnTypes[i] = method.getGenericReturnType();

            String methodName = method.getName();

            //-----------------------------------------------------------------
            //特殊处理getter和setter方法的key，然后存入methodNameIndexMap中
            //-----------------------------------------------------------------
            //methodNames中没有Object.class中的final方法(如getClass)，所以这里get开头的不会有getClass
            if (methodName.length() > 3) {
                if (methodName.startsWith(Reflector.GETTER_PREFIX)
                        && method.getParameterCount() == 0) {
                    fieldNameList.add(StringUtils.uncapitalize(methodName.substring(3)));
                    methodNameIndexMap.put(className + Reflector.SEPARATOR + methodName, i);
                } else if (methodName.startsWith(Reflector.SETTER_PREFIX)
                        && method.getParameterCount() == 1) {
                    //如public void com.company.project.business.model.po.Contact.setContactId(java.lang.String)
                    //去掉(String contactId)存入com.company.project.business.model.po.Contact.setContactId为key
                    //且如果有多个匹配com.company.project.business.model.po.Contact.setContactId时只存第一个
                    methodNameIndexMap.putIfAbsent(className + Reflector.SEPARATOR + methodName, i);
                }
            }

            //-----------------------------------------------------------------
            //储存取到的所有Method的key
            //(去除方法toString后的修饰符和返回值部分) = className + methodName + "(参数列表，多个以','分隔)"
            //如HashMap的put方法的toString是"public java.lang.Object java.util.HashMap.put(java.lang.Object,java.lang.Object)"
            //那么key就是"java.util.HashMap.put(java.lang.Object,java.lang.Object)"
            //-----------------------------------------------------------------
            methodNameIndexMap.put(method.toString().replaceFirst(".+ ", ""), i);
        }
    }

    ///**
    // * 写无参的构造函数.
    // *
    // * @param superName 字节码父类名
    // * @param cw        类编辑器
    // */
    //private static void insertNoArgsConstructor(String superName, ClassWriter cw) {
    //
    //    //写无参的构造函数
    //    MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    //    mv.visitCode();
    //    mv.visitVarInsn(ALOAD, 0);
    //    //执行父类的init初始化
    //    mv.visitMethodInsn(INVOKESPECIAL, superName, "<init>", "()V", false);
    //    //从当前方法返回void
    //    mv.visitInsn(RETURN);
    //    mv.visitMaxs(0, 0);
    //    mv.visitEnd();
    //}

    /**
     * 写有参构造函数.
     *
     * @param superName 字节码父类名
     * @param cw        类编辑器
     */
    private static void insertParametricConstructor(String superName, ClassWriter cw) {

        //写有参构造函数
        String parametricConstructorDescriptor = "(" +
                "Ljava/lang/String;" +
                "[Ljava/lang/String;" +
                "[[Ljava/lang/Class;" +
                "[Ljava/lang/Class;" +
                "[[Ljava/lang/reflect/Type;" +
                "[Ljava/lang/reflect/Type;" +
                "Ljava/util/Map;" +
                "[Ljava/lang/String;" +
                ")V";
        MethodVisitor pcmv = cw.visitMethod(ACC_PUBLIC, "<init>", parametricConstructorDescriptor, null, null);
        pcmv.visitCode();
        pcmv.visitVarInsn(ALOAD, 0);
        pcmv.visitVarInsn(ALOAD, 1);
        pcmv.visitVarInsn(ALOAD, 2);
        pcmv.visitVarInsn(ALOAD, 3);
        pcmv.visitVarInsn(ALOAD, 4);
        pcmv.visitVarInsn(ALOAD, 5);
        pcmv.visitVarInsn(ALOAD, 6);
        pcmv.visitVarInsn(ALOAD, 7);
        pcmv.visitVarInsn(ALOAD, 8);
        //添加调用父类的init初始化，即有参构造函数
        pcmv.visitMethodInsn(INVOKESPECIAL, superName, "<init>", parametricConstructorDescriptor, false);
        //从当前方法返回void
        pcmv.visitInsn(RETURN);
        pcmv.visitMaxs(9, 9);
        pcmv.visitEnd();
    }

    /**
     * 重写上面的抽象方法invoke(Object obj, int methodIndex, Object... args).
     *
     * @param isInterface       是否接口
     * @param methods           方法对象的集合
     * @param methodNames       方法名数组
     * @param parameterTypes    方法的参数类型二维数组
     * @param returnTypes       方法的返回值数组
     * @param classNameInternal 字节码类名
     * @param cw                类编辑器
     */
    private static void overrideInvokeMethod(boolean isInterface, List<Method> methods,
                                             String[] methodNames, Class[][] parameterTypes,
                                             Class[] returnTypes, String classNameInternal,
                                             ClassWriter cw) {

        MethodVisitor invokeMV = cw.visitMethod(ACC_PUBLIC + ACC_VARARGS, "invoke",
                "(Ljava/lang/Object;I[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        invokeMV.visitCode();

        //写被传入参数type的其他方法
        if (!methods.isEmpty()) {

            invokeMV.visitVarInsn(ALOAD, 1);
            invokeMV.visitTypeInsn(CHECKCAST, classNameInternal);
            invokeMV.visitVarInsn(ASTORE, 4);
            invokeMV.visitVarInsn(ILOAD, 2);

            int size = methods.size();

            //写字节码tableswitch部分
            Label[] labels = new Label[size];
            for (int i = 0; i < size; i++) {
                labels[i] = new Label();
            }
            Label defaultLabel = new Label();
            invokeMV.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

            String booleanType = "java/lang/Boolean";
            String byteType = "java/lang/Byte";
            String charType = "java/lang/Character";
            String shortType = "java/lang/Short";
            String intType = "java/lang/Integer";
            String floatType = "java/lang/Float";
            String longType = "java/lang/Long";
            String doubleType = "java/lang/Double";
            String valueOf = "valueOf";
            StringBuilder buffer = new StringBuilder(128);

            //写case部分
            for (int i = 0; i < size; i++) {
                Method method = methods.get(i);
                invokeMV.visitLabel(labels[i]);
                if (i == 0) {
                    invokeMV.visitFrame(F_APPEND, 1, new Object[]{classNameInternal}, 0, null);
                } else {
                    invokeMV.visitFrame(F_SAME, 0, null, 0, null);
                }
                invokeMV.visitVarInsn(ALOAD, 4);

                Class[] paramTypes = parameterTypes[i];
                Class<?> returnType = returnTypes[i];

                buffer.setLength(0);
                buffer.append('(');
                for (int paramIndex = 0; paramIndex < paramTypes.length; paramIndex++) {
                    invokeMV.visitVarInsn(ALOAD, 3);
                    invokeMV.visitIntInsn(BIPUSH, paramIndex);
                    invokeMV.visitInsn(AALOAD);
                    Type paramType = Type.getType(paramTypes[paramIndex]);
                    switch (paramType.getSort()) {
                        case Type.BOOLEAN:
                            invokeMV.visitTypeInsn(CHECKCAST, booleanType);
                            invokeMV.visitMethodInsn(INVOKEVIRTUAL, booleanType, "booleanValue", "()Z", false);
                            break;
                        case Type.CHAR:
                            invokeMV.visitTypeInsn(CHECKCAST, charType);
                            invokeMV.visitMethodInsn(INVOKEVIRTUAL, charType, "charValue", "()C", false);
                            break;
                        case Type.BYTE:
                            invokeMV.visitTypeInsn(CHECKCAST, byteType);
                            invokeMV.visitMethodInsn(INVOKEVIRTUAL, byteType, "byteValue", "()B", false);
                            break;
                        case Type.SHORT:
                            invokeMV.visitTypeInsn(CHECKCAST, shortType);
                            invokeMV.visitMethodInsn(INVOKEVIRTUAL, shortType, "shortValue", "()S", false);
                            break;
                        case Type.INT:
                            invokeMV.visitTypeInsn(CHECKCAST, intType);
                            invokeMV.visitMethodInsn(INVOKEVIRTUAL, intType, "intValue", "()I", false);
                            break;
                        case Type.FLOAT:
                            invokeMV.visitTypeInsn(CHECKCAST, floatType);
                            invokeMV.visitMethodInsn(INVOKEVIRTUAL, floatType, "floatValue", "()F", false);
                            break;
                        case Type.LONG:
                            invokeMV.visitTypeInsn(CHECKCAST, longType);
                            invokeMV.visitMethodInsn(INVOKEVIRTUAL, longType, "longValue", "()J", false);
                            break;
                        case Type.DOUBLE:
                            invokeMV.visitTypeInsn(CHECKCAST, doubleType);
                            invokeMV.visitMethodInsn(INVOKEVIRTUAL, doubleType, "doubleValue", "()D", false);
                            break;
                        case Type.ARRAY:
                            invokeMV.visitTypeInsn(CHECKCAST, paramType.getDescriptor());
                            break;
                        case Type.OBJECT:
                            invokeMV.visitTypeInsn(CHECKCAST, paramType.getInternalName());
                            break;
                        default:
                            break;
                    }
                    buffer.append(paramType.getDescriptor());
                }

                buffer.append(')');
                buffer.append(Type.getDescriptor(returnType));

                int invoke;
                if (isInterface) {
                    invoke = INVOKEINTERFACE;
                } else {
                    if (Modifier.isStatic(method.getModifiers())) {
                        invoke = INVOKESTATIC;
                    } else {
                        invoke = INVOKEVIRTUAL;
                    }
                }
                invokeMV.visitMethodInsn(invoke, classNameInternal, methodNames[i], buffer.toString(), false);

                switch (Type.getType(returnType).getSort()) {
                    case Type.VOID:
                        invokeMV.visitInsn(ACONST_NULL);
                        break;
                    case Type.BOOLEAN:
                        invokeMV.visitMethodInsn(INVOKESTATIC, booleanType, valueOf, "(Z)Ljava/lang/Boolean;", false);
                        break;
                    case Type.CHAR:
                        invokeMV.visitMethodInsn(INVOKESTATIC, charType, valueOf, "(C)Ljava/lang/Character;", false);
                        break;
                    case Type.BYTE:
                        invokeMV.visitMethodInsn(INVOKESTATIC, byteType, valueOf, "(B)Ljava/lang/Byte;", false);
                        break;
                    case Type.SHORT:
                        invokeMV.visitMethodInsn(INVOKESTATIC, shortType, valueOf, "(S)Ljava/lang/Short;", false);
                        break;
                    case Type.INT:
                        invokeMV.visitMethodInsn(INVOKESTATIC, intType, valueOf, "(I)Ljava/lang/Integer;", false);
                        break;
                    case Type.FLOAT:
                        invokeMV.visitMethodInsn(INVOKESTATIC, floatType, valueOf, "(F)Ljava/lang/Float;", false);
                        break;
                    case Type.LONG:
                        invokeMV.visitMethodInsn(INVOKESTATIC, longType, valueOf, "(J)Ljava/lang/Long;", false);
                        break;
                    case Type.DOUBLE:
                        invokeMV.visitMethodInsn(INVOKESTATIC, doubleType, valueOf, "(D)Ljava/lang/Double;", false);
                        break;
                    default:
                        break;
                }

                invokeMV.visitInsn(ARETURN);
            }

            //写default部分
            invokeMV.visitLabel(defaultLabel);
            invokeMV.visitFrame(F_SAME, 0, null, 0, null);
        }

        String illExType = "java/lang/IllegalArgumentException";
        String sbType = "java/lang/StringBuilder";

        invokeMV.visitTypeInsn(NEW, illExType);
        invokeMV.visitInsn(DUP);
        invokeMV.visitTypeInsn(NEW, sbType);
        invokeMV.visitInsn(DUP);
        invokeMV.visitLdcInsn("Method not found: ");
        invokeMV.visitMethodInsn(INVOKESPECIAL, sbType, "<init>", "(Ljava/lang/String;)V", false);
        invokeMV.visitVarInsn(ILOAD, 2);
        invokeMV.visitMethodInsn(INVOKEVIRTUAL, sbType, "append", "(I)Ljava/lang/StringBuilder;", false);
        invokeMV.visitMethodInsn(INVOKEVIRTUAL, sbType, "toString", "()Ljava/lang/String;", false);
        invokeMV.visitMethodInsn(INVOKESPECIAL, illExType, "<init>", "(Ljava/lang/String;)V", false);
        invokeMV.visitInsn(ATHROW);
        invokeMV.visitMaxs(0, 0);
        invokeMV.visitEnd();
    }

    /**
     * 通过有参构造创建MethodAccessor子类实例.
     *
     * @param accessorClass         MethodAccessor子类的Class
     * @param className             生成MethodAccessor子类的源Class的类名
     * @param methodNames           已赋值的方法名数组
     * @param parameterTypes        已赋值的方法对象的参数类型二维数组
     * @param returnTypes           已赋值的方法对象的返回值类型数组
     * @param genericParameterTypes 已赋值的方法对象的参数化参数类型二维数组
     * @param genericReturnTypes    已赋值的方法对象的参数化返回值类型数组
     * @param methodNameIndexMap    已赋值的方法名索引Map
     * @param fieldNames            已赋值的成员变量名数组
     * @return MethodAccessor子类实例
     */
    private static MethodAccessor newInstance(Class<?> accessorClass,
                                              String className,
                                              String[] methodNames,
                                              Class[][] parameterTypes,
                                              Class[] returnTypes,
                                              java.lang.reflect.Type[][] genericParameterTypes,
                                              java.lang.reflect.Type[] genericReturnTypes,
                                              Map<String, Integer> methodNameIndexMap,
                                              String[] fieldNames) {
        try {

            //获取有参构造
            Constructor<?> parametricConstructor = accessorClass.getConstructor(
                    String.class,
                    String[].class,
                    Class[][].class,
                    Class[].class,
                    java.lang.reflect.Type[][].class,
                    java.lang.reflect.Type[].class,
                    Map.class,
                    String[].class);

            //执行有参构造得到MethodAccessor子类，然后使用MethodAccessor接收
            return (MethodAccessor) parametricConstructor.newInstance(
                    className,
                    methodNames,
                    parameterTypes,
                    returnTypes,
                    genericParameterTypes,
                    genericReturnTypes,
                    methodNameIndexMap,
                    fieldNames);
        } catch (Throwable t) {
            throw new RuntimeException("Error constructing method accessor class: " + accessorClass.getName(), t);
        }
    }

    /**
     * 获取对象obj的getter或setter方法的索引
     *
     * @param fieldName 变量名
     * @param isGetter  是否Getter方法
     * @return getter或setter方法的索引
     */
    private Integer getPojoMethodIndex(String fieldName, boolean isGetter) {
        if (ParameterUtils.isEmpty(fieldName)) {
            return null;
        }
        String prefix = className + Reflector.SEPARATOR
                + (isGetter ? Reflector.GETTER_PREFIX : Reflector.SETTER_PREFIX);
        Integer index = methodNameIndexMap.get(prefix + StringUtils.capitalize(fieldName));
        //首字母小写，第二字母大写的成员变量（奇行种）
        if (index == null && Reflector.isAlienName(fieldName)) {
            return methodNameIndexMap.get(prefix + fieldName);
        }
        return index;
    }

    public String toString() {
        return JsonUtil.toJSONString(this);
    }
}
