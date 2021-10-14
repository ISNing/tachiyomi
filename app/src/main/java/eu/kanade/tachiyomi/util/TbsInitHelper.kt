package eu.kanade.tachiyomi.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build.SUPPORTED_ABIS
import androidx.preference.PreferenceManager
import com.tencent.smtt.export.external.TbsCoreSettings
import com.tencent.smtt.sdk.QbSdk
import com.tencent.smtt.sdk.QbSdk.PreInitCallback
import com.tencent.smtt.sdk.TbsDownloader
import com.tencent.smtt.sdk.TbsListener
import timber.log.Timber
import java.util.*

class TbsInitHelper(ctx: Context) {
    private var mInitialized: Boolean
        get() = PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean(Companion.TBS_INIT_KEY, false)
        set(value) = PreferenceManager.getDefaultSharedPreferences(mContext).edit().putBoolean(TBS_INIT_KEY, value).apply()
    private var mContext: Context = ctx
    private var mUseWebView: Boolean
        get() = (
            !(
                // Tbs only supports these two architectures
                SUPPORTED_ABIS.contains("armeabi-v7a") ||
                    SUPPORTED_ABIS.contains("arm64-v8a")
                ) ||
                mContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)
            )
        private set(value) = Timber.e("mUseWebView cannot be modified : $value")

    init {
        Timber.tag(TAG)
    }

    fun init() {
        initSettings()
        QbSdk.setTbsListener(object : TbsListener {
            override fun onDownloadFinish(i: Int) {
                if (i == 0 || i == 100) Timber.d("Tbs Kernel successfully downloaded $i")
                else {
                    Timber.d("Tbs Kernel onDownloadFinish ->$i")
                    reInitializeIfNot()
                }
            }

            override fun onInstallFinish(i: Int) {
                if (i == 200) Timber.d("Tbs Kernel successfully installed")
                else {
                    Timber.d("Tbs Kernel install failed -> $i")
                    reInitializeIfNot()
                }
            }

            override fun onDownloadProgress(i: Int) {
                Timber.d("Tbs Kernel onDownloadProgress ->$i")
            }
        })
        QbSdk.initX5Environment(
            mContext,
            object : PreInitCallback {
                override fun onCoreInitFinished() {
                    Timber.d("Tbs Core initialize finished")
                }

                override fun onViewInitFinished(b: Boolean) {
                    mInitialized = b
                    Timber.d("Tbs view initialize finshed: $b")
                    if (!mInitialized && TbsDownloader.needDownload(mContext, false) && !TbsDownloader.isDownloading()) {
                        reInitializeIfNot()
                    }
                }
            }
        )
    }

    private fun resetSdk() {
        QbSdk.reset(mContext)
    }

    private fun initSettings() {
        for (i in SUPPORTED_ABIS) Timber.d(i)
        Timber.d(mUseWebView.toString())
        if (mUseWebView) QbSdk.forceSysWebView()
        else {
            val map = HashMap<String, Any>()
            map[TbsCoreSettings.TBS_SETTINGS_USE_SPEEDY_CLASSLOADER] = true
            map[TbsCoreSettings.TBS_SETTINGS_USE_DEXLOADER_SERVICE] = true
            QbSdk.initTbsSettings(map)
            QbSdk.setDownloadWithoutWifi(true)
        }
    }

    fun reInitializeIfNot(): Boolean {
        if (mInitialized) {
            Timber.d("Tbs initialized")
            return mInitialized
        }
        // Re-initialize sdk when not downloading & have never initialized before
        if (!mInitialized && !TbsDownloader.isDownloading() && !mUseWebView) {
            Timber.d("Tbs not initialized, resetting and reinitializing")
            resetSdk()
            initSettings()
            TbsDownloader.startDownload(mContext)
        }
        return false
    }

    fun isInitialized(): Boolean {
        return mInitialized
    }

    companion object {
        const val TAG = "TbsInitHelper"

        private const val TBS_INIT_KEY = "tbs_init_key"
    }
}
