package com.memo.bus.bus

import java.lang.reflect.Method

/**
 * title:
 * describe:
 *
 * @author zhou
 * @date 2019-04-22 14:46
 */
class SubscribeMethod(
    //需要调用的方法体
    val mMethod: Method,
    //接收的线程
    val mThread: ThreadMode,
    //数据类型
    val mClazz: Class<*>
)