package com.memo.bus.ui

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.memo.bus.R
import com.memo.bus.bus.EventBus
import com.memo.bus.bus.Subscribe
import com.memo.bus.bus.ThreadMode
import kotlinx.android.synthetic.main.activity_post.*
import java.util.*
import java.util.concurrent.Executors

class PostActivity : AppCompatActivity() {

    private var isMain: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post)
        EventBus.getDefault().register(this)
        mBtnPost.setOnClickListener {
            if (isMain) {
                Log.i("Bus", "Post MAIN--> thread = ${Thread.currentThread().name}")
                EventBus.getDefault().post(Event("这是普通消息${Random().nextInt(100)}"))
            } else {
                Executors.newSingleThreadExecutor().submit {
                    Log.i("Bus", "Post BACKGROUND--> thread = ${Thread.currentThread().name}")
                    EventBus.getDefault().post(Event("这是普通消息${Random().nextInt(100)}"))
                }
            }
            isMain = !isMain
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND, isSticky = true)
    fun onEvent(event: StickyEvent) {
        Log.i(
            "Bus",
            "Subscribe Sticky BACKGROUND --> thread = ${Thread.currentThread().name} message = ${event.message}"
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unRegister(this)
        EventBus.getDefault().removeSticky(StickyEvent::class.java)
    }
}
