package com.memo.bus.bus

import android.os.Handler
import android.os.Looper
import java.lang.reflect.Method
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * title:
 * describe:
 *
 * @author zhou
 * @date 2019-04-22 14:29
 */
class EventBus private constructor() {

    private val mCacheMap: HashMap<Any, HashSet<SubscribeMethod>> by lazy { HashMap<Any, HashSet<SubscribeMethod>>() }

    private val mStickyMap: HashMap<Class<*>, Any> by lazy { HashMap<Class<*>, Any>() }

    private val mHandler: Handler by lazy { Handler() }

    private val mExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }

    companion object {
        private val instance: EventBus by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            EventBus()
        }

        fun getDefault() = instance
    }

    fun register(subscribe: Any) {
        //从CacheMap中找寻 不存在那么就从subscribe中找 存在就是sticky
        var methodSet: HashSet<SubscribeMethod>? = mCacheMap[subscribe]
        if (methodSet == null) {
            methodSet = findFromSubscribe(subscribe)
            mCacheMap[subscribe] = methodSet
        }
    }


    /**
     * 从Subscribe中找到方法集合
     */
    private fun findFromSubscribe(subscribe: Any): HashSet<SubscribeMethod> {
        val methodSet: HashSet<SubscribeMethod> = HashSet()
        var clazz: Class<*>? = subscribe.javaClass
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
                    invokeSticky(method, subscribe, paramType[0], annotation.threadMode)
                }
                //加入集合
                methodSet.add(SubscribeMethod(method, annotation.threadMode, paramType[0]))
            }
            //获取父类
            clazz = clazz.superclass
        }
        return methodSet
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

    /**
     * 判断是否是系统级别的父类
     */
    private fun isSystemParent(className: String): Boolean =
        className.startsWith("java.") ||
                className.startsWith("javax.") ||
                className.startsWith("android.")

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

    /**
     * 传递Sticky
     */
    fun postSticky(event: Any) {
        mStickyMap[event.javaClass] = event
    }

    /**
     * 利用反射调用方法
     */
    private fun invoke(method: Method, subscribe: Any, event: Any) {
        try {
            method.invoke(subscribe, event)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 解除注册
     */
    fun unRegister(subscribe: Any) {
        mCacheMap.remove(subscribe)
    }


    /**
     * 移除Sticky
     */
    fun removeSticky(clazz: Class<*>) {
        mStickyMap.remove(clazz)
    }

}