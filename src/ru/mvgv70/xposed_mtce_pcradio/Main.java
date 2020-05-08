package ru.mvgv70.xposed_mtce_pcradio;

import ru.mvgv70.utils.IniFile;
import ru.mvgv70.utils.Utils;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.microntek.CarManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Main implements IXposedHookLoadPackage
{
  private static final String PACKAGE_NAME = "com.maxxt.pcradio";
  private static String songInfo = "";
  private static String radioTitle = "";
  private static Context radioService = null;
  private static Context radioContext = null;
  private static String radioPackageName = null;
  private static String packageVersion;
  private static boolean playing = false;
  private static boolean stopByMicrontek = false;
  private static String activeClassName = "";
  private static String EXTERNAL_SD = "/mnt/external_sd/";
  private static final String MAIN_SECTION = "settings";
  private static final String TOAST_SECTION = "toast";
  private static final String KEYS_SECTION = "keys";
  private static final String INI_FILE_NAME = "mtce-utils/pcradio.ini";
  private static IniFile props = new IniFile();
  private static CarManager cm = null;
  // настройки
  private static boolean sendTitle = true;
  private static boolean widgetKeys = true;
  private static int play_pause_key = 0;
  private static int next_channel_key = 0;
  private static int prev_channel_key = 0;
  private static boolean toastEnable = false;
  private static int toastSize = 0;
  // old version
  // private final static String ACTION_PREFIX = "com.maxxt.radio";
  // private final static String ACTION_PREFIX_STOP = "";
  // new version
  private final static String ACTION_PREFIX = "com.maxxt.pcradio";
  private final static String ACTION_PREFIX_STOP = ".pcradio";
  private final static String TAG = "xposed-mtce-pcradio";
  
  @Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable 
  {
    
    // RadioService.onCreate()
    XC_MethodHook onCreateService = new XC_MethodHook() {

      @Override
      protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"onCreate");
        radioService = (Context)param.thisObject;
        radioPackageName = radioService.getPackageName();
        activeClassName = radioService.getPackageName();
        cm = new CarManager();
        // показать версию модуля
        try 
        {
          radioContext = radioService.createPackageContext(getClass().getPackage().getName(), Context.CONTEXT_IGNORE_SECURITY);
          String version = radioContext.getString(R.string.app_version_name);
          Log.i(TAG,"version="+version);
        } catch (Exception e) {}
        // версия Android
        Log.i(TAG,"android "+Build.VERSION.RELEASE);
        // версия PcRadio
        packageVersion = radioService.getPackageManager().getPackageInfo(radioPackageName, 0).versionName;
        Log.i(TAG,"PCRadio "+packageVersion);
        // расположение настроечного файла из build.prop
        EXTERNAL_SD = Utils.getModuleSdCard();
        // настройки
        readSettings();
        // create receivers
        createReceivers();
        // обработчик нажатий
        createKeyHandler();
      }
    };
    
    // RadioService.onDestroy()
    XC_MethodHook onDestroyService = new XC_MethodHook() {

      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"onDestroy");
        // выключаем Receivers
        radioService.unregisterReceiver(pcRadioReceiver);
        radioService.unregisterReceiver(tagsQueryReceiver);
        radioService.unregisterReceiver(microntekReceiver);
        radioService.unregisterReceiver(widgetReceiver);
        //
        radioService = null;
        activeClassName = "";
        playing = false;
        stopByMicrontek = false;
      }
    };
    
    // start hooks
    if (!lpparam.packageName.equals(PACKAGE_NAME)) return;
    Log.d(TAG,PACKAGE_NAME);
    Utils.readXposedMap();
    Utils.setTag(TAG);
    Utils.findAndHookMethodCatch("com.maxxt.pcradio.service.RadioService", lpparam.classLoader, "onCreate", onCreateService);
    Utils.findAndHookMethodCatch("com.maxxt.pcradio.service.RadioService", lpparam.classLoader, "onDestroy", onDestroyService);
    Log.d(TAG,PACKAGE_NAME+" hook OK");
  }
  
  // чтение настроек
  private void readSettings()
  {
    try
    {
      Log.d(TAG,"read settings from "+EXTERNAL_SD+INI_FILE_NAME);
      props.clear();
      props.loadFromFile(EXTERNAL_SD+INI_FILE_NAME);
    } 
    catch (Exception e) 
    {
      Log.w(TAG,e.getMessage());
    }
    // настройки
    sendTitle = props.getBoolValue(MAIN_SECTION, "title", true);
    Log.d(TAG,"title="+sendTitle);
    widgetKeys = props.getBoolValue(MAIN_SECTION, "widget.keys", true);
    Log.d(TAG,"widget.keys="+widgetKeys);
    // toast
    toastEnable = props.getBoolValue(TOAST_SECTION, "enable", false);
    Log.d(TAG,"toast.enabled="+toastEnable);
    toastSize = props.getIntValue(TOAST_SECTION, "size", 0);
    Log.d(TAG,"toast.size="+toastSize);
    // клавиши
    play_pause_key = props.getIntValue(KEYS_SECTION, "play_pause", 0);
    Log.d(TAG,"play_pause="+play_pause_key);
    next_channel_key = props.getIntValue(KEYS_SECTION, "next_channel", 0);
    Log.d(TAG,"next_channel="+next_channel_key);
    prev_channel_key = props.getIntValue(KEYS_SECTION, "prev_channel", 0);
    Log.d(TAG,"prev_channel="+prev_channel_key);
  }

  // createReceivers
  private void createReceivers()
  {
    // события PcRadio
    IntentFilter pi = new IntentFilter();
    pi.addAction(ACTION_PREFIX+".EVENT_START_PLAYBACK");
    pi.addAction(ACTION_PREFIX+".EVENT_STOP_PLAYBACK");
    pi.addAction(ACTION_PREFIX+".EVENT_SONG_INFO");
    pi.addAction(ACTION_PREFIX+".EVENT_STATUS");
    radioService.registerReceiver(pcRadioReceiver, pi);
    Log.d(TAG,"pcradio events receiver created");
    // обработчик запросов скринсейвера
    IntentFilter qi = new IntentFilter();
    qi.addAction("hct.music.info");
    radioService.registerReceiver(tagsQueryReceiver, qi);
    Log.d(TAG,"screensaver request receiver created");
    // запуск штатных приложений
    IntentFilter mi = new IntentFilter();
    mi.addAction("com.microntek.bootcheck");
    radioService.registerReceiver(microntekReceiver, mi);
    Log.d(TAG,"bootcheck receiver created");
    // обработчик команд виджета
    IntentFilter wi = new IntentFilter();
    wi.addAction("hct.music.last");
    wi.addAction("hct.music.next");
    wi.addAction("hct.music.playpause");
    radioService.registerReceiver(widgetReceiver, wi);
    Log.d(TAG,"widget command receiver created");
  }
  
  // создание обработчика нажатий
  private void createKeyHandler()
  {
    cm.attach(new KeyHandler(), "KeyDown");
    Log.d(TAG,"KeyHandler created");
  }
  
  // обработчик нажатий
  @SuppressLint("HandlerLeak")
  private class KeyHandler extends Handler
  {
    public void handleMessage(Message msg)
    {
      Bundle data = msg.getData();
      int keyCode = data.getInt("value");
      Log.d(TAG,"keyCode="+keyCode);
      keyHandle(keyCode);
    }
  };
  
  // обработка нажатия кнопки
  private void keyHandle(int keyCode)
  {
    Log.d(TAG,"handle key "+keyCode);
	// кнопки обрабатываются при активном PcRadio или в режиме воспроизведения
    Log.d(TAG,"activeClassName="+activeClassName+", playing="+playing);
    if (radioPackageName.equals(activeClassName) || playing)
    {
      // для работающего PcRadio
      if (keyCode == play_pause_key)
      {
        if (playing)
          stopPlayer();
        else
          startPlayer();
      }
      else if (keyCode == next_channel_key)
      {
        if (playing) nextChannel();
      }
      else if (keyCode == prev_channel_key)
      {
        if (playing) prevChannel();
      }
    }
  }
  
  // отправка информации о воспроизведении
  private void sendNotifyIntent(Context context)
  {
    Log.d(TAG,"sendNotifyIntent");
    // тэги
    Intent intent = new Intent("com.microntek.music.report");
    intent.putExtra("type", "music.tags");
    intent.putExtra(MediaStore.Audio.AudioColumns.TITLE, songInfo);
    if (sendTitle)
      intent.putExtra(MediaStore.Audio.AudioColumns.ARTIST, radioTitle);
    context.sendBroadcast(intent);
    // картинки нет
    Intent pintent = new Intent("com.microntek.music.report");
    pintent.putExtra("type", "music.alumb");
    pintent.putExtra("value", new long[] {-1, -1});
    pintent.putExtra("class", PACKAGE_NAME);
    context.sendBroadcast(pintent);
    // time = 0 of 0
    Intent tintent = new Intent("com.microntek.canbusdisplay");
    tintent.putExtra("type", "music");
    tintent.putExtra("all", 0);
    tintent.putExtra("cur", 0);
    tintent.putExtra("time", 0);
    context.sendBroadcast(tintent);
	// заголовок
    Intent lintent = new Intent("com.microntek.music.report");
    lintent.putExtra("type", "music.title");
    lintent.putExtra("value", songInfo);
    context.sendBroadcast(lintent);
    // позиция = 0 of 0
    Intent mintent = new Intent("com.microntek.music.report");
    mintent.putExtra("type", "music.time");
    mintent.putExtra("value", new int[] {0, 0});
    mintent.putExtra("class", PACKAGE_NAME);
    context.sendBroadcast(mintent);
  }
  
  // показать уведомление о смене трека
  private void showToast()
  {
    Log.d(TAG,"showToast");
    Intent intent = new Intent("com.microntek.music.toast");
    intent.putExtra("toast.size", toastSize);
    intent.putExtra("toast.format", "%title% %song%");
    intent.putExtra("class", PACKAGE_NAME);
    intent.putExtra("song", songInfo);
    intent.putExtra("title", radioTitle);
    radioContext.sendBroadcast(intent);
  }
  
  private void sendMusicOn()
  {
    Log.d(TAG,"sendMusicOn");
    // music-on
    Intent intent = new Intent("com.microntek.canbusdisplay");
    intent.putExtra("type", "music-on");
    radioContext.sendBroadcast(intent);
    // music.state=1
    Intent intentr = new Intent("com.microntek.music.report");
    intentr.putExtra("type", "music.state");
    intentr.putExtra("value", 1);
    intentr.putExtra("class", PACKAGE_NAME);
    radioContext.sendBroadcast(intentr);
  }
  
  private void sendMusicOff()
  {
    Log.d(TAG,"sendMusicOff");
    // music-off
    Intent intent = new Intent("com.microntek.canbusdisplay");
    intent.putExtra("type", "music-off");
    radioContext.sendBroadcast(intent);
    // power=false
    Intent intentr = new Intent("com.microntek.music.report");
    intentr.putExtra("type", "music.state");
    intentr.putExtra("value", 0);
    intentr.putExtra("class", PACKAGE_NAME);
    radioContext.sendBroadcast(intentr);
  }
  
  private void sendCanBusInfo()
  {
    Log.d(TAG,"sendCanBusInfo");
    // music	   
    Intent intent = new Intent("com.microntek.canbusdisplay");
    intent.putExtra("type", "music");
    Log.d(TAG,"sendCanBusInfo");
    radioContext.sendBroadcast(intent);
  }
  
  private void turnMtcAppsOff()
  {
    Log.d(TAG,"turn Microntek apps off");
    Intent intent = new Intent("com.microntek.bootcheck");
    intent.putExtra("class", radioPackageName);
    radioContext.sendBroadcast(intent);
  }
  
  // команда PcRadio
  private void commandPcRadio(String action)
  {
    Log.d(TAG,"API command: "+action);
    Intent intent = new Intent(action);
    intent.setComponent(new ComponentName("com.maxxt.pcradio","com.maxxt.pcradio.service.RadioService"));
    radioContext.startService(intent);  
  }
  
  // остановка проигрывания
  private void stopPlayer()
  {
    commandPcRadio(ACTION_PREFIX+ACTION_PREFIX_STOP+".ACTION_STOP_PLAYBACK");
  }
  
  // начала проигрывания
  private void startPlayer()
  {
    commandPcRadio(ACTION_PREFIX+".ACTION_PLAY_STREAM");
  }
  
  // следующая радиостаниция
  private void nextChannel()
  {
    commandPcRadio(ACTION_PREFIX+".ACTION_PLAY_NEXT_STREAM");
  }
  
  // предыдущая радиостаниция
  private void prevChannel()
  {
    commandPcRadio(ACTION_PREFIX+".ACTION_PLAY_PREV_STREAM");
  }
  
  // обработчик событий PcRadio
  private BroadcastReceiver pcRadioReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context context, Intent intent)
    {
      String action = intent.getAction();
      Log.d(TAG,"PCRadio: "+action);
      if (action.endsWith(".EVENT_START_PLAYBACK"))
      {
        stopByMicrontek = false;
        playing = true;
        radioTitle = intent.getStringExtra("radioTitle");
        // songInfo = intent.getStringExtra("songInfo");
        // информация для скринсейвера
        sendNotifyIntent(context);
        if (toastEnable) showToast();
        // canbus info
        sendMusicOn();
        sendCanBusInfo();
        // разослать интент о закрытии штатных приложений
        turnMtcAppsOff();
      }
      else if (action.endsWith(".EVENT_STOP_PLAYBACK"))
      {
        playing = false;
        // canbus info
        if (stopByMicrontek == false) sendMusicOff();
      }
      else if (action.endsWith(".EVENT_SONG_INFO"))
      {
        songInfo = intent.getStringExtra("songInfo");
        // информация для скринсейвера
        sendNotifyIntent(context);
        // canbus info
        sendCanBusInfo();
      }
      Log.d(TAG,"playing="+playing);
      Log.d(TAG,"radioTitle="+radioTitle+", songInfo="+songInfo);
    }
  };
  
  // обработчик com.android.music.querystate, запросы от скринсейвера
  private BroadcastReceiver tagsQueryReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context context, Intent intent)
    {
      // отправить информацию
      Log.d(TAG,"PCRadio: tags query receiver, playing="+playing);
      if (playing) sendNotifyIntent(context);
    }
  };
  
  // com.microntek.bootcheck
  private BroadcastReceiver microntekReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context context, Intent intent)
    {
      String className = intent.getStringExtra("class");
      activeClassName = className;
      Log.d(TAG,"com.microntek.bootcheck, class="+className);
      if (!className.equals(radioPackageName))
      {
        stopByMicrontek = true;
        Log.d(TAG,"playing="+playing);
        // запускается штатная программа, выключим PCRadio
        if (playing) stopPlayer();
      }
    }
  };
  
  // hct.music.*
  private BroadcastReceiver widgetReceiver = new BroadcastReceiver()
  {
    public void onReceive(Context context, Intent intent)
    {
      String action = intent.getAction();
      Log.d(TAG,"action="+action);
      if (!widgetKeys) return;
      // нажатия обрабатываются при активном PcRadio или в режиме воспроизведения
      if (radioPackageName.equals(activeClassName) || playing)
      {
        if (action.equals("hct.music.playpause"))
        {
          if (playing)
            stopPlayer();
          else
            startPlayer();
        }
        else if (action.equals("hct.music.next"))
        {
          if (playing) nextChannel();
        }
        else if (action.equals("hct.music.last"))
        {
          if (playing) prevChannel();
        }
      }
    }
  };

}
