package com.memo.bus.bus

/**
 * title:
 * describe:
 *
 * @author zhou
 * @date 2019-04-22 14:36
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Subscribe(
    //线程
    val threadMode: ThreadMode = ThreadMode.MAIN,
    //是否粘性
    val isSticky: Boolean = false
)
