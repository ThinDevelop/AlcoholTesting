package com.tss.alcoholtesting;

import android.app.Application;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.IBinder;
import android.util.DisplayMetrics;

import com.sunmi.pay.hardware.aidlv2.emv.EMVOptV2;
import com.sunmi.pay.hardware.aidlv2.pinpad.PinPadOptV2;
import com.sunmi.pay.hardware.aidlv2.readcard.ReadCardOptV2;
import com.sunmi.pay.hardware.aidlv2.security.SecurityOptV2;
import com.sunmi.pay.hardware.aidlv2.system.BasicOptV2;
import com.sunmi.pay.hardware.aidlv2.tax.TaxOptV2;
import com.sunmi.peripheral.printer.InnerPrinterCallback;
import com.sunmi.peripheral.printer.InnerPrinterException;
import com.sunmi.peripheral.printer.InnerPrinterManager;
import com.sunmi.peripheral.printer.SunmiPrinterService;
import java.util.Locale;

import sunmi.sunmiui.utils.LogUtil;

public class MyApplication extends Application {

    public static Context context;

    public static BasicOptV2 mBasicOptV2;           // 获取基础操作模块
    public static ReadCardOptV2 mReadCardOptV2;     // 获取读卡模块
    public static PinPadOptV2 mPinPadOptV2;         // 获取PinPad操作模块
    public static SecurityOptV2 mSecurityOptV2;     // 获取安全操作模块
    public static EMVOptV2 mEMVOptV2;               // 获取EMV操作模块
    public static TaxOptV2 mTaxOptV2;               // 获取税控操作模块

    public static SunmiPrinterService sunmiPrinterService;
    public static IScanInterface scanInterface;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        initLocaleLanguage();

        bindPrintService();

        bindScannerService();
    }

    public static void initLocaleLanguage() {
        Resources resources = MyApplication.getContext().getResources();
        DisplayMetrics dm = resources.getDisplayMetrics();
        Configuration config = resources.getConfiguration();
        int showLanguage = CacheHelper.getCurrentLanguage();
        if (showLanguage == Constant.LANGUAGE_AUTO) {
            config.locale = Resources.getSystem().getConfiguration().locale;
            LogUtil.e(Constant.TAG, config.locale.getCountry() + "---这是系统语言");
        } else if (showLanguage == Constant.LANGUAGE_CH_CN) {
            config.locale = Locale.SIMPLIFIED_CHINESE;
            LogUtil.e(Constant.TAG, "这是中文");
        } else {
            LogUtil.e(Constant.TAG, "这是英文");
            config.locale = Locale.ENGLISH;
        }
        resources.updateConfiguration(config, dm);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LogUtil.e(Constant.TAG, "onConfigurationChanged");
    }

    public static Context getContext() {
        return context;
    }

    private void bindPrintService() {
        try {
            InnerPrinterManager.getInstance().bindService(this, innerPrinterCallback);
        } catch (InnerPrinterException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        upbindPrintService();
    }

    private void upbindPrintService() {
        try {
            InnerPrinterManager.getInstance().unBindService(this, innerPrinterCallback);
        } catch (InnerPrinterException e) {
            e.printStackTrace();
        }
    }

    InnerPrinterCallback innerPrinterCallback = new InnerPrinterCallback() {
        @Override
        protected void onConnected(SunmiPrinterService service) {
            MyApplication.sunmiPrinterService = service;
        }

        @Override
        protected void onDisconnected() {
            MyApplication.sunmiPrinterService = null;
        }
    };



    public void bindScannerService() {
        Intent intent = new Intent();
        intent.setPackage("com.sunmi.scanner");
        intent.setAction("com.sunmi.scanner.IScanInterface");
        bindService(intent, scanConn, Service.BIND_AUTO_CREATE);
    }

    private static ServiceConnection scanConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            scanInterface = IScanInterface.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            scanInterface = null;
        }
    };
}
