package com.tss.alcoholtesting.activities

import BACtrackAPI.API.BACtrackAPI
import BACtrackAPI.API.BACtrackAPI.BACtrackDevice
import BACtrackAPI.API.BACtrackAPICallbacks
import BACtrackAPI.Constants.BACTrackDeviceType
import BACtrackAPI.Constants.BACtrackUnit
import BACtrackAPI.Exceptions.BluetoothLENotSupportedException
import BACtrackAPI.Exceptions.BluetoothNotEnabledException
import BACtrackAPI.Exceptions.LocationServicesNotEnabledException
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.printservice.PrintService
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sunmi.pay.hardware.aidl.AidlConstants
import com.sunmi.pay.hardware.aidlv2.readcard.CheckCardCallbackV2
import com.sunmi.peripheral.printer.InnerResultCallbcak
import com.sunmi.peripheral.printer.SunmiPrinterService
import com.tss.alcoholtesting.*
import com.tss.alcoholtesting.utils.ByteUtil
import kotlinx.android.synthetic.main.activity_main.*
import sunmi.paylib.SunmiPayKernel
import sunmi.paylib.SunmiPayKernel.ConnectCallback
import sunmi.sunmiui.utils.LogUtil
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private val PERMISSIONS_FOR_SCAN: Byte = 100
    internal var baCommandAPDU = byteArrayOf(0x00.toByte(),
                                             0xA4.toByte(),
                                             0x04.toByte(),
                                             0x00.toByte(),
                                             0x08.toByte(),
                                             0xA0.toByte(),
                                             0x00.toByte(),
                                             0x00.toByte(),
                                             0x00.toByte(),
                                             0x54.toByte(),
                                             0x48.toByte(),
                                             0x00.toByte(),
                                             0x01.toByte())

    private val _UTF8_CHARSET = Charset.forName("TIS-620")
    private val _req_version = "80b00000020004"
    private var cardType = AidlConstants.CardType.IC.getValue()
    private var context: Context? = null
    private var mAPI: BACtrackAPI? = null
    private var waitingForBlow = false
    private var mSMPayKernel: SunmiPayKernel? = null
    private var isDisConnectService = true
    private var sunmiPrinterService: SunmiPrinterService? = null
    var algohol = "0 mg%"
    var idCard = "3449302900010"
    var nameTH = "นายคนดี มุ่งการงาน"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        connectPayService()

        this.registerReceiver(mBluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.registerReceiver(mLocationServicesReceiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
        }
//        checkCard()
        load_card_data.setOnClickListener { checkCard() }
        sunmiPrinterService = MyApplication.sunmiPrinterService
        initBacTrackAPI()
        connectNearestClicked()
    }

    private fun checkCard() {
        try {
            //支持M1卡
            val allType = (AidlConstants.CardType.NFC.value or AidlConstants.CardType.IC.value or AidlConstants.CardType.MIFARE.value or AidlConstants.CardType.FELICA.value)
            MyApplication.mReadCardOptV2.checkCard(allType, mReadCardCallback, 60)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val mReadCardCallback: CheckCardCallbackV2.Stub = object : CheckCardCallbackV2.Stub() {
        override fun findMagCard(info: Bundle?) {

        }

        override fun findICCard(atr: String?) {
            cardType = AidlConstants.CardType.IC.getValue()
            sendEdpu()
        }

        override fun findRFCard(uuid: String?) {
        }

        override fun onError(code: Int, message: String?) {
            LogUtil.e(TAG, "onError" + message)
        }

        override fun findICCardEx(info: Bundle?) {
        }

        override fun findRFCardEx(info: Bundle?) {
        }

        override fun onErrorEx(info: Bundle?) {
            LogUtil.e(TAG, "onErrorEx" + info)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(mBluetoothReceiver)
        if (mSMPayKernel != null) {
            mSMPayKernel!!.destroyPaySDK()
        }

        sunmiPrinterService?.let {
            it.clearBuffer()
        }
    }


    protected fun initBacTrackAPI() {
        val apiKey = "8994f5202fea469a903cb731f64cfc" //"a70f68a7e8c54c749842f99de1d89e9c";
        try {
            mAPI = BACtrackAPI(this, mCallbacks, apiKey)
            context = this
        } catch (e: BluetoothLENotSupportedException) {
            e.printStackTrace()
        } catch (e: BluetoothNotEnabledException) {
            e.printStackTrace()
        } catch (e: LocationServicesNotEnabledException) {
            e.printStackTrace()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSIONS_FOR_SCAN.toInt() -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (mAPI == null) initBacTrackAPI()
                    if (mAPI != null) // check for success in case it was null before
                        mAPI!!.connectToNearestBreathalyzer()
                }
            }
        }
    }

    private val mBluetoothReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_OFF -> {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Bluetooth STATE_OFF", Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                    BluetoothAdapter.STATE_ON -> handleBluetoothOn()
                }
            }
        }
    }

    protected fun handleBluetoothOn() {
        if (mAPI == null) {
            initBacTrackAPI()
        } else if (!areLocationServicesAvailable(this)) {
            Toast.makeText(this@MainActivity, "Bluetooth ERR_LOCATIONS_NOT_ENABLED", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this@MainActivity, "Bluetooth DISCONNECTED", Toast.LENGTH_LONG).show()
        }
    }

    private val mLocationServicesReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (!areLocationServicesAvailable(context)) {
                Toast.makeText(this@MainActivity, "BroadcastReceiver ERR_LOCATIONS_NOT_ENABLED", Toast.LENGTH_LONG).show()
            } else if (mAPI == null) {
                initBacTrackAPI()
            } else if (!isBluetoothEnabled()) {
                Toast.makeText(this@MainActivity, "ERR_BT_NOT_ENABLED", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@MainActivity, "DISCONNECTED", Toast.LENGTH_LONG).show()
            }
        }
    }

    protected fun isBluetoothEnabled(): Boolean {
        val bluetoothManager = this@MainActivity.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.adapter.isEnabled
    }


    protected fun areLocationServicesAvailable(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lm = context.getSystemService(LOCATION_SERVICE) as LocationManager
            lm.isLocationEnabled
        } else {
            val mode = Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF)
            mode != Settings.Secure.LOCATION_MODE_OFF
        }
    }

    private fun setStatus(message: String) {
        runOnUiThread {
            Log.d(TAG, message)
            status_message_text_view_id.setText(message)
        }
    }
    fun startBlowProcessClicked(v: View?) {
        var result = false
        if (mAPI != null) {
            result = mAPI!!.startCountdown()
        }
        if (!result) Log.e(TAG, "mAPI.startCountdown() failed") else Log.d(TAG, "Blow process start requested")
    }

    fun getDateTime(): String {
        return try {
            val c: Calendar = Calendar.getInstance()
            val df = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
            df.format(c.getTime())
        } catch (e: Exception) {
            e.toString()
            ""
        }
    }
    fun printData(v: View?) {
        val hHmmss: String = getDateTime()
        setHeight(0x12)
        sunmiPrinterService!!.clearBuffer()
        sunmiPrinterService!!.enterPrinterBuffer(true)
        val logo = BitmapFactory.decodeResource(this@MainActivity.resources, R.drawable.logo3)

        sunmiPrinterService!!.printBitmap(logo, innerResultCallbcak)
//        sunmiPrinterService!!.printText("\n\n เวลา : " + hHmmss + "\n".trimIndent() + "\n " + card_id.text.toString() + "\n " + name_th.text.toString() + "\n " + status_message_text_view_id.text.toString()+"\n    \n   \n",
//                                        innerResultCallbcak)
        sunmiPrinterService!!.printText("ผลการตรวจวัดระดับแอลกอฮอล\uF70E\n" + "ACPS (Alcohol Check Point System)\n" + "ข\uF70Bอมูลจุดตรวจ\n" + "รหัส : CTR091\n" + "หัวหน\uF70Bาชุด :: พ.ต.ต. สมหมาย ใจดี วันที่-เวลาตั้งด\uF70Aาน ::\n" + "2020/11/20 21:00ถึง 2020/11/21 1:00\n" + "ข\uF70Bอมูลผู\uF70Bถูกตรวจวัด\n" + "วัน-เวลาที่ตรวจ :: 2020/11/20 22:30 น. ชื่อ-สกุล::นายคนดี มุ\uF70Aงการงาน เลขบัตรประชาชน :: 3449302900010 ผลการตรวจวดั ::51.2mg%\n" + "ประวัติการตรวจวดั ล\uF70Aาสุด - ไม\uF70Aมี\n" + "คะแนนความประพฤตกิ ารขบั ขี่ :: 11/12\n" + "เซ็นรับทราบข\uF70Bอมูล\n" + "ส.ต.ต. มีชัย เที่ยงตรง เจ\uF70Bาพนักงานผู\uF70Bตรวจวดัด",
                                        innerResultCallbcak)
        sunmiPrinterService!!.printText("\n\n ", innerResultCallbcak)
        sunmiPrinterService!!.printText("\n\n ", innerResultCallbcak)
        sunmiPrinterService!!.commitPrinterBuffer()
    }

    fun printData2(v: View?) {
        val hHmmss: String = getDateTime()
        setHeight(0x11)
        sunmiPrinterService!!.clearBuffer()
        sunmiPrinterService!!.enterPrinterBuffer(true)
        val logo = BitmapFactory.decodeResource(this@MainActivity.resources, R.drawable.logo3)
        val logo2 = BitmapFactory.decodeResource(this@MainActivity.resources, R.drawable.logo5)


        sunmiPrinterService!!.printBitmap(logo2, innerResultCallbcak)
        sunmiPrinterService!!.printText("  ผลการตรวจวัดระดับแอลกอฮอล์\n" +
                                                "ACPS(Alcohol Check Point System)\n" +
                                                "ข้อมูลจุดตรวจ\n" +
                                                "  รหัส : CTR091\n" +
                                                "  หัวหน้าชุด::พ.ต.ต.สมหมาย ใจดี\n" +
                                                "  วันที่-เวลาตั้งด่าน::\n" +
                                                "   2020/11/20 21:00 ถึง \n" +
                                                "   2020/11/21 1:00\n\n" +
                                                "ข้อมูลผู้ถูกตรวจวัด\n" +
                                                "  วัน-เวลาที่ตรวจ ::\n" +
                                                "  2020/11/20 22:30น.\n" +
                                                "  ชื่อ-สกุล::\n" +
                                                "  "+nameTH+"\n" +
                                                "  เลขบัตรประชาชน::\n"+idCard+"\n" +
                                                "  ผลการตรวจวัด::"+algohol+"\n\n" +
                                                "ประวัติการตรวจวัดล่าสุด\n  - ไม่มี\n\n" +
                                                "คะแนนความประพฤติการขับขี่ :: \n" +
                                                "  11/12\n\n" +
                                                "\n\n\n\n\n" +
                                                "     ______________________\n " +
                                                "        เซ็นรับทราบข้อมูล\n\n" +
                                                "      ส.ต.ต. มีชัย เที่ยงตรง\n" +
                                                "       เจ้าพนักงานผู้ตรวจวัด" +
                                                "\n        \n        \n",
                                        innerResultCallbcak)
        sunmiPrinterService!!.printText("\n\n ", innerResultCallbcak)
        sunmiPrinterService!!.printText("\n\n ", innerResultCallbcak)
        sunmiPrinterService!!.commitPrinterBuffer()
    }

    private var `is` = true
    private val innerResultCallbcak: InnerResultCallbcak = object : InnerResultCallbcak() {
        override fun onRunResult(isSuccess: Boolean) {
            LogUtil.e("lxy", "isSuccess:$isSuccess")
            if (`is`) {
                try {
//                    sunmiPrinterService!!.printTextWithFont("""
//    ${SystemDateTime.getHHmmss()}
//
//    """.trimIndent()+ "\n "+card_id.text.toString()+"\n "+name_th.text.toString()+"\n "+ status_message_text_view_id.text.toString(), "", 30f, this)
                    sunmiPrinterService!!.lineWrap(6, this)
                    `is` = false
                } catch (e: RemoteException) {
                    e.printStackTrace()
                }
            }
        }

        override fun onReturnString(result: String) {
            LogUtil.e("lxy", "result:$result")
        }

        override fun onRaiseException(code: Int, msg: String) {
            LogUtil.e("lxy", "code:$code,msg:$msg")
        }

        override fun onPrintResult(code: Int, msg: String) {
            LogUtil.e("lxy", "code:$code,msg:$msg")
        }
    }

    @Throws(RemoteException::class)
    fun setHeight(height: Int) {
        val returnText = ByteArray(3)
        returnText[0] = 0x1B
        returnText[1] = 0x33
        returnText[2] = height.toByte()

        if (sunmiPrinterService == null) {
            sunmiPrinterService = MyApplication.sunmiPrinterService
        }
        sunmiPrinterService?.sendRAWData(returnText, null)
    }

    private fun setStatus(resourceId: Int) {
        this.setStatus(this.resources.getString(resourceId))
    }

    fun connectNearestClicked() {
        setStatus(R.string.TEXT_CONNECTING)
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) !== PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.ACCESS_COARSE_LOCATION) !== PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this@MainActivity,
                                              arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                                              PERMISSIONS_FOR_SCAN.toInt())
        } else if (mAPI != null) {
            /**
             * Permission already granted, start scan.
             */
            mAPI!!.connectToNearestBreathalyzer()
//            connectButton.setEnabled(false)
        }
    }

    fun sendEdpu() {
        val data = sendAPDUkOnClick()
        idCard = data.id.toString()
        nameTH = data.nameTH.toString()
        runOnUiThread {
            card_id.text = "เลขบัตร ปชช : " + data.id
            name_th.text = "ชื่อ : " + data.nameTH
        }
    }

    fun disconnectClicked(v: View?) {
        if (mAPI != null) {
            mAPI!!.disconnect()
        }
    }
    companion object {
        private const val TAG = "MainActivity"
    }

    private fun transmitApduCmd(cmd: String): String {
        val send = ByteUtil.hexStr2Bytes(cmd)
        val recv = ByteArray(260)
        var data = ""
        try {
            MyApplication.mReadCardOptV2.transmitApdu(cardType, baCommandAPDU, ByteArray(260))
            val len = MyApplication.mReadCardOptV2.transmitApdu(cardType, send, recv)
            if (len < 0) {
                LogUtil.e(TAG, "transmitApdu failed,code:$len")
                Toast.makeText(this@MainActivity, "Read data failed", Toast.LENGTH_LONG).show()
            } else {
                LogUtil.e(TAG, "transmitApdu success,recv:" + ByteUtil.bytes2HexStr(*recv))
                data = String(recv, _UTF8_CHARSET)
                LogUtil.e(TAG, "transmitApdu success,recv x :" + data)
            }
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
        return data
    }


    private val mCallbacks: BACtrackAPICallbacks = object : BACtrackAPICallbacks {
        override fun BACtrackAPIKeyDeclined(p0: String?) {
            setStatus("Status: BACtrackAPIKeyDeclined")

        }

        override fun BACtrackAPIKeyAuthorized() {
            setStatus("Status: BACtrackAPIKeyAuthorized")
        }

        override fun BACtrackConnected(p0: BACTrackDeviceType?) {
            setStatus("Status: BACtrackConnected")
        }

        override fun BACtrackDidConnect(p0: String?) {
            setStatus("Status: BACtrackDidConnect")

        }

        override fun BACtrackDisconnected() {
            setStatus("Status: BACtrackDisconnected")
            connectNearestClicked()
        }

        override fun BACtrackConnectionTimeout() {
            setStatus("Status: BACtrackConnectionTimeout")

        }

        override fun BACtrackFoundBreathalyzer(p0: BACtrackDevice?) {
            setStatus("Status: BACtrackFoundBreathalyzer")

        }

        override fun BACtrackCountdown(currentCountdownCount: Int) {
            setStatus("Countdown :" + currentCountdownCount)
        }

        override fun BACtrackStart() {
            waitingForBlow = true
            setStatus("เป่าเลย...")
        }

        override fun BACtrackBlow(breathVolumeRemaining: Float) {
            if (waitingForBlow) setStatus(String.format("Keep Blowing (%d%%)", 100 - (100.0 * breathVolumeRemaining).toInt()))
        }

        override fun BACtrackAnalyzing() {
            waitingForBlow = false
            setStatus("กำลังประมวลผล...")
        }

        override fun BACtrackResults(measuredBac: Float) {
            algohol = "" + measuredBac * 1000 + " mg%"
            setStatus("ผลการทดสอบ : " + algohol)

            printData2(null)
        }

        override fun BACtrackFirmwareVersion(p0: String?) {
            setStatus("BACtrackFirmwareVersion : " + p0)

        }

        override fun BACtrackSerial(p0: String?) {
            setStatus("BACtrackSerial : " + p0)
        }

        override fun BACtrackUseCount(p0: Int) {
            setStatus("BACtrackUseCount : " + p0)

        }

        override fun BACtrackBatteryVoltage(p0: Float) {
            setStatus("BACtrackBatteryVoltage : " + p0)
        }

        override fun BACtrackBatteryLevel(p0: Int) {
            setStatus("BACtrackBatteryLevel : " + p0)
        }

        override fun BACtrackError(p0: Int) {
            waitingForBlow = false
            setStatus("BACtrackError : " + p0)

        }

        override fun BACtrackUnits(units: BACtrackUnit) {}
    }


    fun sendAPDUkOnClick(): UserModel {
        val version = transmitApduCmd(_req_version).trim { it <= ' ' }
        val userModel = UserModel()
        if (version.startsWith("0003")) {
            userModel.id = transmitApduCmd("80b0000402000d").substring(0, 12)
            userModel.nameTH = transmitApduCmd("80b00011020064").replace("#", " ").substring(0, 50)
            userModel.nameEN = transmitApduCmd("80b00075020064").replace("#", " ").substring(0, 50)
            userModel.address = transmitApduCmd("80b015790200A0").replace("#", " ").substring(0, 50)
        } else {
            //cid //offset 4 len:13
            userModel.id = transmitApduCmd("80b1000402000d").substring(0, 12)
            userModel.nameTH = transmitApduCmd("80b10011020064").replace("#", " ").substring(0, 50)
            userModel.nameEN = transmitApduCmd("80b10075020064").replace("#", " ").substring(0, 50)
            userModel.address = transmitApduCmd("80b00004020096").replace("#", " ").substring(0, 50)
        }

        return userModel
    }


    private fun connectPayService() {
        mSMPayKernel = SunmiPayKernel.getInstance()
        mSMPayKernel!!.initPaySDK(this, mConnectCallback)
    }

    private val mConnectCallback: ConnectCallback = object : ConnectCallback {
        override fun onConnectPaySDK() {
            LogUtil.e(Constant.TAG, "onConnectPaySDK")
            try {
                MyApplication.mEMVOptV2 = mSMPayKernel?.mEMVOptV2
                MyApplication.mBasicOptV2 = mSMPayKernel?.mBasicOptV2
                MyApplication.mPinPadOptV2 = mSMPayKernel?.mPinPadOptV2
                MyApplication.mReadCardOptV2 = mSMPayKernel?.mReadCardOptV2
                MyApplication.mSecurityOptV2 = mSMPayKernel?.mSecurityOptV2
                MyApplication.mTaxOptV2 = mSMPayKernel?.mTaxOptV2
                isDisConnectService = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onDisconnectPaySDK() {
            LogUtil.e(Constant.TAG, "onDisconnectPaySDK")
            isDisConnectService = true
            showToast("connect_fail")
        }
    }

    fun showToast(msg: String) {
        showToastOnUI(msg)
    }

    private fun showToastOnUI(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT)
                .show()
        }
    }
}