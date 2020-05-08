package ru.mvgv70.utils;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.content.Context;
import android.microntek.HCTApi;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.text.TextUtils;
import android.util.Log;

//
// version 1.4.2
//

public class Utils 
{
  private final static String INI_FILE_SD_BUILDPROP = "persist.sys.mvgv70.card";
  private final static String INI_FILE_SDNAME_BUILDPROP = "persist.sys.mvgv70.cardname";
  private final static String EXTERNAL_SD = "/mnt/external_sd";
  private final static String XPOSED_MAP_PATH = "/system/etc/mvgv70.xposed.map";
  private final static String CLASS_PARAM = ".class";
  private static IniFile xposedMap = new IniFile();
  private static String TAG = "mvgv70-xposed";
	
  // TAG
  public static void setTag(String newTag)
  {
    TAG = newTag;
  }
  
  // системный параметр из build.prop
  public static String getSystemProperty(String key) 
  {
    String value = null;
    try 
    {
      value = (String)Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, key);
    } 
    catch (Exception e) 
    {
      Log.e(TAG,e.getMessage());
    }
    return value;
  }
  
  // sd-карта для чтения файла настроек
  public static String getModuleSdCard()
  {
    String result = EXTERNAL_SD;
    // смотрим в build.prop и системных насотройках
    String value = getSystemProperty(INI_FILE_SD_BUILDPROP);
    // смотрим в mvgv70.xposed.map
    if (TextUtils.isEmpty(value)) value = getStringSettings("main", INI_FILE_SD_BUILDPROP, null);
    Log.i(TAG,INI_FILE_SD_BUILDPROP+"="+value);
    // если значение задано
    if (!TextUtils.isEmpty(value)) result = value;
    if (!result.endsWith("/")) result = result.concat("/");
    Log.i(TAG,"EXTERNAL_SD="+result);
    return result;
  }
  
  // имя sd-карты для чтения файла настроек
  // TODO: что делать с внутренней памятью
  public static String getModuleSdCardName()
  {
    String result = "";
    // смотрим в build.prop и системных насотройках
    String value = getSystemProperty(INI_FILE_SDNAME_BUILDPROP);
    // смотрим в mvgv70.xposed.map
    if (TextUtils.isEmpty(value)) value = getStringSettings("main", INI_FILE_SDNAME_BUILDPROP, null);
    Log.i(TAG,INI_FILE_SDNAME_BUILDPROP+"="+value);
    // если значение задано
    if (!TextUtils.isEmpty(value)) result = value;
    Log.i(TAG,"EXTERNAL_SD_NAME="+result);
    return result;
  }
  
  // sd-карта для чтения файла настроек с учетом нового параметра cardname
  public static String getModuleSdCardEx(Context context)
  {
    String result = "";
    String cardName = getModuleSdCardName();
    if (cardName != null)
    {
      result = getVolumePath(context, cardName);
      Log.i(TAG,"EXTERNAL_SD="+result);
    }
    else
      result = getModuleSdCard();
    return result;
  }
  
  // проверка подключаемой карты
  public static boolean isExternalCard(String drivePath, String External_Sd)
  {
    return External_Sd.startsWith(drivePath);
  }
  
  // получение пути к карте по имени
  private static String getVolumePath(Context context, String deviceName)
  {
    StorageManager sm = (StorageManager)context.getSystemService(StorageManager.class);
    @SuppressWarnings("unchecked")
	List<VolumeInfo> volumes = (List<VolumeInfo>)callMethod(sm, "getVolumes");
    if (volumes != null)
    {
      Iterator<VolumeInfo> volumes_i = volumes.iterator();
      while (volumes_i.hasNext())
      {
        VolumeInfo volume = (VolumeInfo)volumes_i.next();
        if (volume.getDisk() != null)
        {
          if (deviceName.equalsIgnoreCase(HCTApi.getDeviceName(volume.getDisk().sysPath)))
          {
            String result = volume.path;
            if (!result.endsWith("/")) result = result.concat("/");
            return result;
          }
        }
      } 
    }
    return null;
  }
  
  // чтение карты полей и функций xposed
  public static void readXposedMap()
  {
    xposedMap.clear();
    try 
    {
      Log.d(TAG,"read xposed map from "+XPOSED_MAP_PATH);
      xposedMap.loadFromFile(XPOSED_MAP_PATH);
    } 
    catch (Exception e) 
    {
      Log.w(TAG,e.getMessage());
    }
  }
  
  // отладочный вывод карты полей и функций
  public static void LogXposedMap()
  {
    Log.d(TAG,"");
    Log.d(TAG,XPOSED_MAP_PATH);
    xposedMap.LogProps(TAG);
    Log.d(TAG,"");
  }
  
  // получение обфусцированного имени класса
  public static String getXposedMapClass(String TAG, String className)
  {
    String value = xposedMap.getValue(className,CLASS_PARAM,className);
    return value;
  }
  
  // получение обфусцированного имени функции или поля
  public static String getXposedMapValue(String TAG, String section, String key)
  {
    // если нет строки вернет null
    String value = xposedMap.getValue(section, key, null);
    if (value == null)
      return key;
    else
      return value;
  }
  
  // перехват вызова метода
  public static XC_MethodHook.Unhook findAndHookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) 
  {
    String nameOfMethod = getXposedMapValue(TAG, className, methodName);
    if (!nameOfMethod.isEmpty())
    {
      String nameOfClass = getXposedMapClass(TAG, className);
      Log.d(TAG,"findAndHook "+nameOfClass+"."+nameOfMethod);
      return XposedHelpers.findAndHookMethod(nameOfClass, classLoader, nameOfMethod, parameterTypesAndCallback);
    }
    else
    {
      Log.w(TAG,className+"."+methodName+" not hooked");
      return null;
    }
  }
  
  // перехват вызова метода
  public static XC_MethodHook.Unhook findAndHookMethodCatch(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) 
  {
    try
    {
      return findAndHookMethod(className, classLoader, methodName, parameterTypesAndCallback);
    }
    catch (Error e)
    {
      Log.e(TAG,e.getMessage());
      XposedBridge.log(e.getMessage());
      return null;
    }
  }
  
  // перехват вызова всех методов с заданным именем
  public static Set<XC_MethodHook.Unhook> findAndHookAllMethods(String className, ClassLoader classLoader, String methodName, XC_MethodHook callback) 
  {
    String nameOfMethod = getXposedMapValue(TAG, className, methodName);
    if (!nameOfMethod.isEmpty())
    {
      return XposedBridge.hookAllMethods(findClass(className, classLoader), methodName, callback);
    }
    else
    {
      Log.w(TAG,className+"."+methodName+" not hooked");
      return null;
    }
  }

  // перехват вызова всех методов с заданным именем
  public static Set<XC_MethodHook.Unhook> findAndHookAllMethodsCatch(String className, ClassLoader classLoader, String methodName, XC_MethodHook callback) 
  {
    try
    {
      return findAndHookAllMethods(className, classLoader, methodName, callback);
    }
    catch (Error e)
    {
      Log.e(TAG,e.getMessage());
      XposedBridge.log(e.getMessage());
      return null;
    }
  }
  
  // перехват вызова конструктора
  public static XC_MethodHook.Unhook findAndHookConstructor(String className, ClassLoader classLoader, Object... parameterTypesAndCallback)
  {
    String nameOfClass = getXposedMapClass(TAG, className);
    Log.d(TAG,"findAndHok "+nameOfClass+" constructor");
    return XposedHelpers.findAndHookConstructor(nameOfClass, classLoader, parameterTypesAndCallback);
  }
  
  // перехват вызова конструктора
  public static XC_MethodHook.Unhook findAndHookConstructorCatch(String className, ClassLoader classLoader, Object... parameterTypesAndCallback)
  {
    try
    {
      return findAndHookConstructor(className, classLoader, parameterTypesAndCallback);
    }
    catch (Error e)
    {
      Log.e(TAG,e.getMessage());
      XposedBridge.log(e.getMessage());
      return null;
    }
  }
  
  // перехват всех конструкторов
  public static Set<XC_MethodHook.Unhook> findAndHookAllConstructors(String className, ClassLoader classLoader, XC_MethodHook callback) 
  {
    return XposedBridge.hookAllConstructors(findClass(className, classLoader), callback);
  }

  // перехват всех конструкторов
  public static Set<XC_MethodHook.Unhook> findAndHookAllConstructorsCatch(String className, ClassLoader classLoader, XC_MethodHook callback) 
  {
    try
    {
      return findAndHookAllConstructors(className, classLoader, callback);
    }
    catch (Error e)
    {
      Log.e(TAG,e.getMessage());
      XposedBridge.log(e.getMessage());
      return null;
    }
  }
  
  // поиск класса
  public static Class<?> findClass(String className, ClassLoader classLoader)
  {
    String nameOfClass = getXposedMapClass(TAG, className);
    Log.d(TAG,"find class "+nameOfClass);
    return XposedHelpers.findClass(nameOfClass, classLoader);
  }
  
  // поиск класса
  public static Class<?> findClassCatch(String className, ClassLoader classLoader)
  {
    try
    {
      String nameOfClass = getXposedMapClass(TAG, className);
      Log.d(TAG,"find class "+nameOfClass);
      return XposedHelpers.findClass(nameOfClass, classLoader);
    }
    catch (Error e)
    {
      Log.e(TAG,e.getMessage());
      XposedBridge.log(e.getMessage());
      return null;
    }
  }
  
  // вызов метода
  public static Object callMethod(Object obj, String methodName, Object... args) 
  {
    String className = obj.getClass().getName();
    String nameOfMethod = getXposedMapValue(TAG, className, methodName);
    if (nameOfMethod.isEmpty()) return null;
    try
    {
      return XposedHelpers.callMethod(obj, nameOfMethod, args);
    }
    catch (Error e)
    {
      Log.e(TAG,obj.getClass().getName()+":"+nameOfMethod+" -> "+e.getMessage());
      XposedBridge.log(obj.getClass().getName()+":"+nameOfMethod+" -> "+e.getMessage());
      return null;
    }
  }
  
  // получение объектного поля
  public static Object getObjectField(Object obj, String fieldName)
  {
    String nameOfField = getXposedMapValue(TAG, obj.getClass().getName(), fieldName);
    if (nameOfField.isEmpty()) return null;
    try
    {
      return XposedHelpers.getObjectField(obj, nameOfField);
    }
    catch (Error e)
    {
      Log.e(TAG,obj.getClass().getName()+":"+nameOfField+" -> "+e.getMessage());
      XposedBridge.log(obj.getClass().getName()+":"+nameOfField+" -> "+e.getMessage());
      return null;
    }
  }
  
  // установка объектного поля
  public static void setObjectField(Object obj, String fieldName, Object value)
  {
    String nameOfField = getXposedMapValue(TAG, obj.getClass().getName(), fieldName);
    if (nameOfField.isEmpty()) return;
    try
    {
      XposedHelpers.setObjectField(obj, nameOfField, value);
    }
    catch (Error e)
    {
      Log.e(TAG,obj.getClass().getName()+":"+nameOfField+" -> "+e.getMessage());
      XposedBridge.log(obj.getClass().getName()+":"+nameOfField+" -> "+e.getMessage());
    }
  }
  
  // получение целочисленного поля
  public static int getIntField(Object obj, String fieldName)
  {
    String nameOfField = getXposedMapValue(TAG, obj.getClass().getName(), fieldName);
    if (nameOfField.isEmpty()) return 0;
    try
    {
      return XposedHelpers.getIntField(obj, nameOfField);
    }
    catch (Error e)
    {
      Log.e(TAG,obj.getClass().getName()+":"+nameOfField+" -> "+e.getMessage());
      XposedBridge.log(obj.getClass().getName()+":"+nameOfField+" -> "+e.getMessage());
      return 0;
    }
  }
  
  // установка целочисленного поля
  public static void setIntField(Object obj, String fieldName, int value)
  {
    String nameOfField = getXposedMapValue(TAG, obj.getClass().getName(), fieldName);
    if (nameOfField.isEmpty()) return;
    try
    {
      XposedHelpers.setIntField(obj, nameOfField, value);
    }
    catch (Error e)
    {
      Log.e(TAG,obj.getClass().getName()+":"+nameOfField+" -> "+e.getMessage());
      XposedBridge.log(obj.getClass().getName()+":"+nameOfField+" -> "+e.getMessage());
    }
  }
  
  //получение boolean поля
  public static Boolean getBooleanField(Object obj, String fieldName)
  {
    String nameOfField = getXposedMapValue(TAG, obj.getClass().getName(), fieldName);
    if (nameOfField.isEmpty()) return false;
    try
    {
      return XposedHelpers.getBooleanField(obj, nameOfField);
    }
    catch (Error e)
    {
      Log.e(TAG,obj.getClass().getName()+":"+nameOfField+" -> "+e.getMessage());
      XposedBridge.log(obj.getClass().getName()+":"+nameOfField+" -> "+e.getMessage());
      return false;
    }
  }
  
  // получение поля типа boolean
  public static void setBooleanField(Object obj, String fieldName, boolean value)
  {
    String nameOfField = getXposedMapValue(TAG, obj.getClass().getName(), fieldName);
    if (nameOfField.isEmpty()) return;
    try
    {
      XposedHelpers.setBooleanField(obj, nameOfField, value);
    }
    catch (Error e)
    {
      Log.e(TAG,obj.getClass().getName()+":"+nameOfField+" -> "+e.getMessage());
      XposedBridge.log(obj.getClass().getName()+":"+nameOfField+" -> "+e.getMessage());
    }
  }
  
  // установка статического поля 
  public static void setStaticObjectField(Class<?> cls, String fieldName, String value)
  {
    String nameOfField = getXposedMapValue(TAG, cls.getName(), fieldName);
    if (nameOfField.isEmpty()) return;
    try
    {
      XposedHelpers.setStaticObjectField(cls, nameOfField, value);
    }
    catch (Error e)
    {
      Log.e(TAG,cls.getName()+":"+nameOfField+" -> "+e.getMessage());
      XposedBridge.log(cls.getName()+":"+nameOfField+" -> "+e.getMessage());
    }
  }
  
  // получение статического поля
  public static int getStaticIntField(Class<?> cls, String fieldName)
  {
    String nameOfField = getXposedMapValue(TAG, cls.getName(), fieldName);
    if (nameOfField.isEmpty()) return 0;
    try
    {
      return XposedHelpers.getStaticIntField(cls, nameOfField);
    }
    catch (Error e)
    {
      Log.e(TAG,cls.getName()+":"+nameOfField+" -> "+e.getMessage());
      XposedBridge.log(cls.getName()+":"+nameOfField+" -> "+e.getMessage());
      return 0;
    }
  }
  
  // настройка boolean
  public static boolean getBooleanSettings(String xposedName, String key, boolean defValue)
  {
    return xposedMap.getBoolValue(xposedName+"#settings", key, defValue);
  }
  
  // настройка boolean
  public static boolean getBooleanSettings(Object obj, String key, boolean defValue)
  {
    String xposedName = obj.getClass().getPackage().getName();
    return xposedMap.getBoolValue(xposedName+"#settings", key, defValue);
  }
  
  // настройка int
  public static int getIntSettings(String xposedName, String key, int defValue)
  {
    return xposedMap.getIntValue(xposedName+"#settings", key, defValue);
  }
  
  // настройка String
  public static String getStringSettings(String xposedName, String key, String defValue)
  {
    return xposedMap.getValue(xposedName+"#settings", key, defValue);
  }
  
  // настройка String
  public static String getStringSettings(Object obj, String key, String defValue)
  {
    String xposedName = obj.getClass().getPackage().getName();
    return xposedMap.getValue(xposedName+"#settings", key, defValue);
  }
  
}
