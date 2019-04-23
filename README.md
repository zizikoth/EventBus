# EventBus
🏀理解EventBus的原理，手写简单的EventBus实现

首先看下EventBus是如何使用的    
```
EventBus.getDefault().register(this);

@Subscribe(threadMode = ThreadMode.MAIN)  
public void onMessageEvent(MessageEvent event) {/* Do something */};

EventBus.getDefault().unregister(this);

EventBus.getDefault().post(new MessageEvent());
```
可以看到，我们在ActivityA中进行注册和解除EventBus，并且使用@Subscribe进行注解需要接收Event的方法。在ActivityB中发送数据Event，通过Event的类型来查找使用的方法，来进行数据的交互。通过简单的了解了一下EventBus，我们来尝试自己写一下EventBus。    

EventBus其实就是一个数据集合，通过key和value的键值对存储数据，在合适的时机进行读取，存储和移除。key就是register()方法中传入的this，或者使用this.hashCode()都可以，只要保证唯一就行，value就是在注册体中使用@Subscribe注解的方法，由于可以有多个方法，所以使用HashSet来进行存储。可是注解的是方法，不是一个实体，无法直接调用这个方法，可以想到使用反射来解决这个问题，然后发现方法注解内部还有一个线程的使用，表示该方法在什么线程中进行调用，线程？Handler，Thread。所以与value的集合需要进行一个封装，需要三个参数class(方法体中传入的参数用于匹配post发送的参数)，method(使用@Subscribe进行注解的方法)，threadMode(线程调度的模式)。对于粘性sticky，只要将两者的方案调换，post进行存储，register进行调用。好的基本思路就是这样

![EventBus.png](https://upload-images.jianshu.io/upload_images/4356451-6eaa4ff73f56fecd.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
![Sticky.png](https://upload-images.jianshu.io/upload_images/4356451-88eaf614fd7a1cae.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
## 一、创建封装类
#### SubscribeMethod
```
class SubscribeMethod(
    //需要调用的方法体
    val mMethod: Method,
    //接收的线程
    val mThread: ThreadMode,
    //数据类型
    val mClazz: Class<*>
)
```
#### Subscribe注解
```
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Subscribe(
    //线程
    val threadMode: ThreadMode = ThreadMode.MAIN,
    //是否粘性
    val isSticky: Boolean = false
)
```
#### ThreadMode
```
enum class ThreadMode {
    MAIN,
    BACKGROUND
}
```

## 二、封装EventBus
#### 1.创建单例模式
使用校验锁，主要是为了线程安全，因为EventBus在各种线程总都可以进行通信
```
companion object {
        private val instance: EventBus by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            EventBus()
        }

        fun getDefault() = instance
    }
```
#### 2.创建方法缓存池
key与value的键值对，value是方法封装的集合
```
private val mCacheMap: HashMap<Any, HashSet<SubscribeMethod>> by lazy { HashMap<Any, HashSet<SubscribeMethod>>() }
```
#### 3.register()
防止多次注册
```
fun register(subscriber: Any) {
        //从CacheMap中找寻 不存在那么就从subscriber中找到使用@Subscribe注解的方法
        var methodSet: HashSet<SubscribeMethod>? = mCacheMap[subscriber]
        if (methodSet == null) {
            methodSet = findFromSubscriber(subscriber)
            mCacheMap[subscriber] = methodSet
        }
    }
```
从Subscriber中找
不仅要在自己内部找，还要从继承的方法中找，为了减少查询的数量，每次都只找自己的方法，然后找父类的方法，添加判断是不是系统类型，类名以java. javax. andoird.开头的都是系统类型
方法体内部有且仅有一个参数，如果你想要有多个数据，可以一起封装到一个Event里面
```
/**
     * 从Subscriber中找到方法集合
     */
    private fun findFromSubscriber(subscriber: Any): HashSet<SubscribeMethod> {
        val methodSet: HashSet<SubscribeMethod> = HashSet()
        var clazz: Class<*>? = subscriber.javaClass
        //找到自己内部是否有注册 还要找到父类是否有注册
        while (clazz != null) {
            //如果找到了系统级别的父类了 退出循环
            if (isSystemParent(clazz.name)) {
                break
            }
            //只是找寻当前目标的方法
            val declaredMethods: Array<Method> = clazz.declaredMethods
            for (method: Method in declaredMethods) {
                //找到带有Subscribe注解的方法 如果没有找到 继续循环下一个
                val annotation: Subscribe = method.getAnnotation(Subscribe::class.java) ?: continue
                //判断有且仅有一个参数
                val paramType: Array<Class<*>> = method.parameterTypes
                if (paramType.size != 1) {
                    throw RuntimeException("You can only have one parameter")
                }
                //如果是粘性数据
                if (annotation.isSticky) {
                    invokeSticky(method, subscriber, paramType[0], annotation.threadMode)
                }
                //加入集合
                methodSet.add(SubscribeMethod(method, annotation.threadMode, paramType[0]))
            }
            //获取父类
            clazz = clazz.superclass
        }
        return methodSet
    }
```
#### 4.Post
在post的时候会找到方法池中的已经存放的数据，因为需要进行线程的切换，所以需要Handler和Thread
```
    private val mHandler: Handler by lazy { Handler() }

    private val mExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
```
发送数据，通过post传入的数据类型和方法池中存在数据类型相匹配
```
/**
     * 发送数据
     */
    fun post(event: Any) {
        //从CacheMap中找到对应的方法 并且调用
        mCacheMap.forEach {
            for (subscribeMethod in it.value) {
                //判断两个对象是否相同
                if (subscribeMethod.mClazz == event.javaClass) {
                    when (subscribeMethod.mThread) {
                        ThreadMode.MAIN -> {
                            if (Looper.myLooper() == Looper.getMainLooper()) {
                                //主 -> 主
                                invoke(subscribeMethod.mMethod, it.key, event)
                            } else {
                                //子 -> 主
                                mHandler.post {
                                    invoke(subscribeMethod.mMethod, it.key, event)
                                }
                            }
                        }
                        ThreadMode.BACKGROUND -> {
                            if (Looper.myLooper() == Looper.getMainLooper()) {
                                //主 -> 子
                                mExecutor.submit {
                                    invoke(subscribeMethod.mMethod, it.key, event)
                                }
                            } else {
                                //子 -> 子
                                invoke(subscribeMethod.mMethod, it.key, event)
                            }
                        }
                    }
                }
            }
        }
    }
```
#### 5.解除注册
在合适的时候将方法池中的数据remove掉，在EventBus的官网中他们推荐是在onStop中进行解除，根据国情，很多手机在ActivityA跳转到ActivityB之后，ActivityA会调用onStop方法，所以很遗憾只能在onDestroy中使用。所以需要根据具体情况进行调用
```
    /**
     * 解除注册
     */
    fun unRegister(subscribe: Any) {
        mCacheMap.remove(subscribe)
    }
```

#### 6.Sticky
在上面的代码中已经把包含Sticky的代码贴上了，思路不变，只是将post和register的内容进行对调。首先需要一个实体的存储池，将postStickey的数据放到数据池中

```
private val mStickyMap: HashMap<Class<*>, Any> by lazy { HashMap<Class<*>, Any>() }


    /**
     * 传递Sticky
     */
    fun postSticky(event: Any) {
        mStickyMap[event.javaClass] = event
    }
```

之后在Register中进行调用

```
 //如果是粘性数据
 if (annotation.isSticky) {
      invokeSticky(method, subscriber, paramType[0], annotation.threadMode)
 }

    /**
     * 调用Sticky方法
     */
    private fun invokeSticky(
        method: Method, subscribe: Any, clazz: Class<*>, threadMode: ThreadMode
    ) {
        val data = mStickyMap[clazz] ?: return
        when (threadMode) {
            ThreadMode.MAIN -> {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    //主 -> 主
                    invoke(method, subscribe, data)
                } else {
                    //子 -> 主
                    mHandler.post {
                        invoke(method, subscribe, data)
                    }
                }
            }
            ThreadMode.BACKGROUND -> {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    //主 -> 子
                    mExecutor.submit {
                        invoke(method, subscribe, data)
                    }
                } else {
                    //子 -> 子
                    invoke(method, subscribe, data)
                }
            }
        }
    }

```

