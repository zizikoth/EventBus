package com.memo.bus.ui

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.memo.bus.R
import com.memo.bus.bus.EventBus
import com.memo.bus.bus.Subscribe
import com.memo.bus.bus.ThreadMode
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        EventBus.getDefault().register(this)
        mBtnIntent.setOnClickListener {
            EventBus.getDefault().postSticky(StickyEvent("这是Sticky消息${Random().nextInt(100)}"))
            startActivity(Intent(this, PostActivity::class.java))
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMainEvent(event: Event) {
        Log.i(
            "Bus",
            "Subscribe MAIN --> thread = ${Thread.currentThread().name} message = ${event.message}"
        )
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onBackgroundEvent(event: Event) {
        Log.i(
            "Bus",
            "Subscribe BACKGROUND --> thread = ${Thread.currentThread().name} message = ${event.message}"
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unRegister(this)
    }
}
