package com.lixg.zmdialect.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import com.google.gson.Gson
import com.lixg.commonlibrary.utils.GlideUtils
import com.lixg.zmdialect.BuildConfig
import com.lixg.zmdialect.CalendarApp
import com.lixg.zmdialect.R
import com.lixg.zmdialect.base.BaseActivity
import com.lixg.zmdialect.common.CommonApi
import com.lixg.zmdialect.common.IntentDataDef
import com.lixg.zmdialect.common.SpDef
import com.lixg.zmdialect.data.AccessManager
import com.lixg.zmdialect.data.common.SplashBean
import com.lixg.zmdialect.network.retrofit.callback.HttpOnNextListener
import com.lixg.zmdialect.network.retrofit.exception.ApiErrorModel
import com.lixg.zmdialect.network.retrofit.http.HttpManager
import com.lixg.zmdialect.network.retrofit.http.RequestOption
import com.lixg.zmdialect.network.service.common.CommonService
import com.lixg.zmdialect.utils.FastClickUtils
import com.lixg.zmdialect.utils.SPUtils
import com.lixg.zmdialect.utils.rx.timer.RxTimer
import com.lixg.zmdialect.utils.rx.timer.TimerListener
import com.lixg.zmdialect.widget.dialog.CommonDialog
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import kotlinx.android.synthetic.main.activity_splash.*
import org.jetbrains.anko.intentFor

/**
 * 工程的入口
 */
class SplashActivity : BaseActivity(), View.OnClickListener {

    private var rxTimer: RxTimer? = null
    override fun resLayout(): Int = R.layout.activity_splash

    private var commonDialog: CommonDialog? = null

    @SuppressLint("CheckResult", "SetTextI18n")
    override fun init() {

        appStatistics("0")

        CommonApi.getConfig(this,null)

        CalendarApp.FORCE_KILL_CODE = 1

        tvVersion.text = "v${BuildConfig.VERSION_NAME}"

        getLocation()
    }

    private fun getLocation() {
        isLocation(object : ResultCallback {
            override fun onPermissionSuccess() {
                timer()
            }

            override fun onPermissionError() {
                commonDialog = CommonDialog.Builder(this@SplashActivity)
                    .setTitle("定位失败了")
                    .setMessage("请您打开应用的定位权限。点击权限，开启位置信息即可")
                    .setCancelBtn("暂不开启", View.OnClickListener {
                        timer()
                    })
                    .setConfirmBtn("去开启", View.OnClickListener {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        val uri = Uri.fromParts("package", packageName, null)
                        intent.data = uri
                        startActivity(intent)
                    }).build()
                commonDialog!!.show()
            }

            override fun onCancelBtnBack() {
            }
        })
    }

    override fun logic() {
        ivImage.setOnClickListener(this)
        skipView.setOnClickListener(this)
    }

    override fun onClick(v: View?) {

        when (v!!.id) {
            R.id.ivImage -> {
                if (FastClickUtils.isAllowClick()) {
                    rxTimer?.finish()
                    val position = if (AccessManager.getVideoStatus()) {
                        2
                    } else {
                        1
                    }
                    startActivity(
                        this@SplashActivity.intentFor<MainActivity>(
                            IntentDataDef.INTENT_MAIN_ACTIVITY_TAB_INDEX to position
                        )
                    )
                    finish()
                }
            }

            R.id.skipView -> {
                if (FastClickUtils.isAllowClick()) {
                    next()
                }
            }
        }
    }


    private fun next() {
        startActivity(intentFor<MainActivity>())
        finish()
    }

    private fun timer() {
        if (rxTimer == null) {
            rxTimer = RxTimer()
        }
        rxTimer?.countDown(4, object : TimerListener() {

            @SuppressLint("SetTextI18n")
            override fun onSuccess(value: Long) {
                skipView.text = "跳过 $value"
            }

            override fun onCompleted() {
                next()
            }
        })
    }

    override fun onDestroy() {
        rxTimer?.finish()
        commonDialog?.dismiss()
        commonDialog = null
        super.onDestroy()
    }

    //屏蔽返回键
    override fun onBackPressed() {
        // super.onBackPressed();
    }


    /**
     * 开屏页
     *
     * @param type 返回的图片组类型 0是开屏
     */
    private fun appStatistics(type: String) {

        HttpManager.instance().apply {
            setOption(RequestOption().apply {
                isShowProgress = false
            })
            doHttpDeal(
                this@SplashActivity,
                createService(CommonService::class.java).appSpalsh(type).bindToLifecycle(this@SplashActivity),
                object : HttpOnNextListener() {
                    override fun onNext(json: String) {
                        Gson().let {
                            val resultEntity = it.fromJson(json, SplashBean::class.java)

                            //正常加载
                            if (resultEntity != null && resultEntity.state == 1 && resultEntity.data != null) {
                                //表示要展示的下标
                                val currentIndex = AccessManager.getSplashType()
                                if (resultEntity.data.size > 0) {
                                    if (currentIndex == 0 || currentIndex > resultEntity.data.size - 1) {
                                        setLocalImage()
                                        SPUtils.setObject(SpDef.SPLASH_TYPE, 1)
                                        return@let
                                    }

                                    GlideUtils.instance.loadSplashImage(
                                        ivImage,
                                        resultEntity.data[currentIndex].pageImage
                                    )
                                    SPUtils.setObject(SpDef.SPLASH_TYPE, currentIndex + 1)
                                } else {
                                    setLocalImage()
                                }
                            } else {
                                setLocalImage()
                            }
                        }
                    }

                    override fun onError(statusCode: Int, apiErrorModel: ApiErrorModel?) {
                        super.onError(statusCode, apiErrorModel)
                        setLocalImage()
                    }
                }
            )
        }
    }

    private fun setLocalImage() {
        ivImage.scaleType = ImageView.ScaleType.FIT_CENTER
    }
}
