package panes.slim.core;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.ArrayMap;
import android.util.Log;
import android.view.ContextThemeWrapper;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

import panes.slim.SlimBundle;
import panes.slim.SlimConfig;

/**
 * Created by panes.
 */
public class Inject {
    private static Object _mLoadedApk;
    private static Object _sActivityThread;

    static class ActivityThreadGetter implements Runnable {
        ActivityThreadGetter() {

        }

        public void run() {
            try {
                _sActivityThread = Runtime.ActivityThread_currentActivityThread.invoke(Runtime.ActivityThread.getmClass(), new Object[0]);
            } catch (Exception e) {
                e.printStackTrace();
            }
            synchronized (Runtime.ActivityThread_currentActivityThread) {
                Runtime.ActivityThread_currentActivityThread.notify();
            }
        }
    }

    static {
        _sActivityThread = null;
        _mLoadedApk = null;

        try {
            Runtime.ActivityThread = QuickReflection.into("android.app.ActivityThread");
            Runtime.ActivityThread_currentActivityThread = Runtime.ActivityThread.method("currentActivityThread", new Class[0]);
            Runtime.ActivityThread_mPackages = Runtime.ActivityThread.field("mPackages");
            Runtime.ActivityThread_mResourcePackages = Runtime.ActivityThread.field("mResourcePackages");
            // android.app.LoadedApk or android.app.ActivityThread$PackageInfo;
            Runtime.LoadedApk = QuickReflection.into("android.app.LoadedApk");
            Runtime.LoadedApk_mResDir = Runtime.LoadedApk.field("mResDir");
            Runtime.LoadedApk_mResources = Runtime.LoadedApk.field("mResources");
            Runtime.ContextImpl = QuickReflection.into("android.app.ContextImpl");
            Runtime.ContextImpl_mResources = Runtime.ContextImpl.field("mResources");
        } catch (QuickReflection.QrException e) {
            try {
                Runtime.LoadedApk = QuickReflection.into("android.app.ActivityThread$PackageInfo");
            } catch (QuickReflection.QrException e1) {
                e1.printStackTrace();
            }
        }

    }


    public static Object getActivityThread() throws Exception {
        if (_sActivityThread == null) {
            if (Thread.currentThread().getId() == Looper.getMainLooper().getThread().getId()) {
                _sActivityThread = Runtime.ActivityThread_currentActivityThread.invoke(null, new Object[0]);
            } else {
                Handler handler = new Handler(Looper.getMainLooper());
                synchronized (Runtime.ActivityThread_currentActivityThread) {
                    handler.post(new ActivityThreadGetter());
                    Runtime.ActivityThread_currentActivityThread.wait();
                }
            }
        }
        return _sActivityThread;
    }

    public static Object getLoadedApk(Object obj, String str) throws Exception {
        if (_mLoadedApk == null) {
            WeakReference weakReference = (WeakReference) ((Map) Runtime.ActivityThread_mPackages.get(obj)).get(str);
            if (weakReference != null) {
                _mLoadedApk = weakReference.get();
            }
        }
        return _mLoadedApk;
    }

    public static void initDynamic(Application application, Context context) {
        try {
            SlimBundle slimBundle = new SlimBundle("panes.slim.bundle", Environment.getExternalStorageDirectory() + File.separator + "slim.bundle.apk", SlimBundle.TYPE_RESOURCES);
            SlimConfig.addResourcesBundle(slimBundle);
            AssetManager assetManager = SysHook.new_AssetManager();
            Method mth = SysHook.method_AssetManager_addAssetPath();
            SlimBundle[] bundles = SlimConfig.getResourcesBundles();
            // need to add application.getResourcesPath
            mth.invoke(assetManager, bundles[0].getPath());
            mth.invoke(assetManager, application.getPackageResourcePath());
            Log.i(SlimConfig.TAG, "invoke");
            QuickReflection.QrClass<Object> ContextImpl = QuickReflection.into("android.app.ContextImpl");
            QuickReflection.QrField<Object, Resources> ContextImpl_mResources = ContextImpl.field("mResources");
            Resources resources = new Resources(assetManager, application.getResources().getDisplayMetrics(), application.getResources().getConfiguration());
            ContextImpl_mResources.set(context, resources);
            Object loadedApk = getLoadedApk(getActivityThread(), application.getPackageName());
            QuickReflection.QrClass<Object> LoadedApk = QuickReflection.into("android.app.LoadedApk");
            QuickReflection.QrField<Object, Resources> LoadedApk_mResources = LoadedApk.field("mResources");
            LoadedApk_mResources.set(loadedApk, resources);
            Log.i(SlimConfig.TAG, "set resources");
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (QuickReflection.QrException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static void injectResources(Application application, Resources resources, AssetManager assetManager) throws Exception {
        Object activityThread = getActivityThread();
        if (activityThread == null) {
            throw new Exception("Failed to get ActivityThread.sCurrentActivityThread");
        }
        Object loadedApk = getLoadedApk(activityThread, application.getPackageName());
        if (loadedApk == null) {
            throw new Exception("Failed to get ActivityThread.mLoadedApk");
        }

        try {
            QuickReflection.QrField<Object, Resources> LoadedApk_mResources = Runtime.LoadedApk.field("mResources");
            QuickReflection.QrClass<Object> ContextImpl = QuickReflection.into("android.app.ContextImpl");
            QuickReflection.QrField<Object, Resources> ContextImpl_mResources = ContextImpl.field("mResources");
            LoadedApk_mResources.set(loadedApk, resources);
            ContextImpl_mResources.set(application.getBaseContext(), resources);

            Method mEnsureStringBlocks = AssetManager.class.getDeclaredMethod("ensureStringBlocks", new Class[0]);
            mEnsureStringBlocks.setAccessible(true);
            mEnsureStringBlocks.invoke(assetManager, new Object[0]);
            QuickReflection.QrClass<Object> QrResourcesManager = QuickReflection.into("android.app.ResourcesManager");
            QuickReflection.QrField<Object, ArrayMap<?, WeakReference<Resources>>> mActiveResources = QrResourcesManager.field("mActiveResources");
            QuickReflection.QrMethod getInstance = QrResourcesManager.staticMethod("getInstance");
            Object ResourcesManager = getInstance.invoke(null);
            ArrayMap<?, WeakReference<Resources>> weakReferenceArrayMap = mActiveResources.get(ResourcesManager);
            Collection<WeakReference<Resources>> references2 = weakReferenceArrayMap.values();
            Log.i(SlimConfig.TAG, "number = " + references2.size());
            for (WeakReference<Resources> reference : references2) {
                Log.i(SlimConfig.TAG, "enter reference");
                Resources resource = reference.get();
                if (resource == null) {
                    Log.i(SlimConfig.TAG, "resource not null");
                } else {
                    Log.i(SlimConfig.TAG, "resource is null");
                }
                try {
                    Field mAssets = resource.getClass().getDeclaredField("mAssets");
                    mAssets.setAccessible(true);
                    mAssets.set(resource, assetManager);
                    Log.i(SlimConfig.TAG, "hook mAssets");
                } catch (NoSuchFieldException e) {
                    // MIUI ?!
                    Field mResourcesImpl = resource.getClass().getDeclaredField("mResourcesImpl");
                    mResourcesImpl.setAccessible(true);
                    Object resourcesImpl = mResourcesImpl.get(resource);
                    Field mAssets1 = resourcesImpl.getClass().getDeclaredField("mAssets");
                    mAssets1.set(resource, assetManager);
                }
                resource.updateConfiguration(resource.getConfiguration(), resource.getDisplayMetrics());

            }
            Log.i(SlimConfig.TAG, "inject resources");
        } catch (QuickReflection.QrException e) {
            e.printStackTrace();
        }
//        Runtime.ContextImpl_mTheme.set(application.getBaseContext(), null);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static void injectTK(Application application, Context context) {
        String packageResourcePath = application.getPackageResourcePath();
        Log.i(SlimConfig.TAG, "packageResourcePath = " + packageResourcePath);
        SlimBundle slimBundle = new SlimBundle("panes.slim.bundle", Environment.getExternalStorageDirectory() + File.separator + "slim.bundle.apk", SlimBundle.TYPE_RESOURCES);
        SlimConfig.addResourcesBundle(slimBundle);
        SlimBundle[] bundles = SlimConfig.getResourcesBundles();
        String bundlePath = bundles[0].getPath();
        // mResDir
//        try {
//            Object activityThread = getActivityThread();
//            Map<String, WeakReference<?>> mPackages = Runtime.ActivityThread_mPackages.get(activityThread);
//            Map<String, WeakReference<?>> mResourcePackages = Runtime.ActivityThread_mResourcePackages.get(activityThread);
//            setLoadedApk_mResDir(mPackages, bundlePath);
//            setLoadedApk_mResDir(mResourcePackages, bundlePath);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        // addAssetPath
        AssetManager assetManager = SysHook.new_AssetManager();
        Runtime.AssetManager = assetManager;
        Method mth = SysHook.method_AssetManager_addAssetPath();
        // need to add application.getResourcesPath
        try {
            Object result3 = mth.invoke(assetManager, packageResourcePath);
            Object result = mth.invoke(assetManager, bundles[0].getPath());
            Log.i(SlimConfig.TAG, "invoke");
            Log.i(SlimConfig.TAG, "invoke package");

            Collection<WeakReference<Resources>> references = null;
            // resources
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                //pre-N
                Class<?> resourcesManagerClass = Class.forName("android.app.ResourcesManager");
                Method mGetInstance = resourcesManagerClass.getDeclaredMethod("getInstance");
                mGetInstance.setAccessible(true);
                Object resourcesManager = mGetInstance.invoke(null);
                try {
                    Field fMActiveResources = resourcesManagerClass.getDeclaredField("mActiveResources");
                    fMActiveResources.setAccessible(true);
                    ArrayMap<?, WeakReference<Resources>> arrayMap =
                            (ArrayMap<?, WeakReference<Resources>>) fMActiveResources.get(resourcesManager);
                    references = arrayMap.values();
                } catch (NoSuchFieldException ignore) {
                    // N moved the resources to mResourceReferences
                    Field mResourceReferences = resourcesManagerClass.getDeclaredField("mResourceReferences");
                    mResourceReferences.setAccessible(true);
                    //noinspection unchecked
                    references = (Collection<WeakReference<Resources>>) mResourceReferences.get(resourcesManager);
                }
            } else {
//                Field fMActiveResources = activityThread.getDeclaredField("mActiveResources");
                QuickReflection.QrClass<Object> QrResourcesManager = QuickReflection.into("android.app.ResourcesManager");
                QuickReflection.QrField<Object, ArrayMap<?, WeakReference<Resources>>> mActiveResources = QrResourcesManager.field("mActiveResources");
                QuickReflection.QrMethod getInstance = QrResourcesManager.staticMethod("getInstance");
                Object ResourcesManager = getInstance.invoke(null);
                ArrayMap<?, WeakReference<Resources>> weakReferenceArrayMap = mActiveResources.get(ResourcesManager);
                references = weakReferenceArrayMap.values();
            }


            Method mEnsureStringBlocks = AssetManager.class.getDeclaredMethod("ensureStringBlocks", new Class[0]);
            mEnsureStringBlocks.setAccessible(true);

            for (WeakReference<Resources> wr : references) {
                Resources resources = wr.get();
                //pre-N
                if (resources != null) {
                    try {
                        Field mAssets = resources.getClass().getDeclaredField("mAssets");
                        mAssets.setAccessible(true);
                        mAssets.set(resources, assetManager);
                        Log.i(SlimConfig.TAG, "hook mAssets");
                    } catch (NoSuchFieldException e) {
                        // MIUI ?!
                        Field mResourcesImpl = resources.getClass().getDeclaredField("mResourcesImpl");
                        mResourcesImpl.setAccessible(true);
                        Object resourcesImpl = mResourcesImpl.get(resources);
                        // ShareReflectUtil --> superclass
                        Field mAssets1 = resourcesImpl.getClass().getDeclaredField("mAssets");
                        mAssets1.set(resources, assetManager);
                    }
                    resources.updateConfiguration(resources.getConfiguration(), resources.getDisplayMetrics());
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } catch (QuickReflection.QrException e) {
            e.printStackTrace();
        }


    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static void injectDirWithResourcesHooked(Application application, Context context) {
        String packageResourcePath = application.getPackageResourcePath();
        Log.i(SlimConfig.TAG, "packageResourcePath = " + packageResourcePath);
        SlimBundle slimBundle = new SlimBundle("panes.slim.bundle", Environment.getExternalStorageDirectory() + File.separator + "slim.bundle.apk", SlimBundle.TYPE_RESOURCES);
        SlimConfig.addResourcesBundle(slimBundle);
        SlimBundle[] bundles = SlimConfig.getResourcesBundles();
        String bundlePath = bundles[0].getPath();
        // mResDir
        try {
            Object activityThread = getActivityThread();
            Map<String, WeakReference<?>> mPackages = Runtime.ActivityThread_mPackages.get(activityThread);
            Map<String, WeakReference<?>> mResourcePackages = Runtime.ActivityThread_mResourcePackages.get(activityThread);
            setLoadedApk_mResDir(mPackages, bundlePath);
            setLoadedApk_mResDir(mResourcePackages, bundlePath);
        } catch (Exception e) {
            e.printStackTrace();
        }


        AssetManager assetManager = SysHook.new_AssetManager();
        Runtime.AssetManager = assetManager;
        Method mth = SysHook.method_AssetManager_addAssetPath();
        // need to add application.getResourcesPath
        try {
            Object result = mth.invoke(assetManager, bundles[0].getPath());
            Object result3 = mth.invoke(assetManager, packageResourcePath);
            Log.i(SlimConfig.TAG, "invoke: " + (Integer)result);
            Log.i(SlimConfig.TAG, "invoke package: "+(Integer) result3);

            Method mEnsureStringBlocks = AssetManager.class.getDeclaredMethod("ensureStringBlocks", new Class[0]);
            mEnsureStringBlocks.setAccessible(true);

            Collection<WeakReference<Resources>> references = null;
            // resources
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                //pre-N
                Class<?> resourcesManagerClass = Class.forName("android.app.ResourcesManager");
                Method mGetInstance = resourcesManagerClass.getDeclaredMethod("getInstance");
                mGetInstance.setAccessible(true);
                Object resourcesManager = mGetInstance.invoke(null);
                try {
                    Field fMActiveResources = resourcesManagerClass.getDeclaredField("mActiveResources");
                    fMActiveResources.setAccessible(true);
                    ArrayMap<?, WeakReference<Resources>> arrayMap =
                            (ArrayMap<?, WeakReference<Resources>>) fMActiveResources.get(resourcesManager);
                    references = arrayMap.values();
                } catch (NoSuchFieldException ignore) {
                    // N moved the resources to mResourceReferences
                    Field mResourceReferences = resourcesManagerClass.getDeclaredField("mResourceReferences");
                    mResourceReferences.setAccessible(true);
                    //noinspection unchecked
                    references = (Collection<WeakReference<Resources>>) mResourceReferences.get(resourcesManager);
                }
            } else {
//                Field fMActiveResources = activityThread.getDeclaredField("mActiveResources");
                QuickReflection.QrClass<Object> QrResourcesManager = QuickReflection.into("android.app.ResourcesManager");
                QuickReflection.QrField<Object, ArrayMap<?, WeakReference<Resources>>> mActiveResources = QrResourcesManager.field("mActiveResources");
                QuickReflection.QrMethod getInstance = QrResourcesManager.staticMethod("getInstance");
                Object ResourcesManager = getInstance.invoke(null);
                ArrayMap<?, WeakReference<Resources>> weakReferenceArrayMap = mActiveResources.get(ResourcesManager);
                references = weakReferenceArrayMap.values();
            }

            for (WeakReference<Resources> wr : references) {
                Resources resources = wr.get();
                //pre-N
                if (resources != null) {
                    try {
                        Field mAssets = resources.getClass().getDeclaredField("mAssets");
                        mAssets.setAccessible(true);
                        mAssets.set(resources, assetManager);
                        Log.i(SlimConfig.TAG, "hook mAssets");
                    } catch (NoSuchFieldException e) {
                        // MIUI ?!
                        Field mResourcesImpl = resources.getClass().getDeclaredField("mResourcesImpl");
                        mResourcesImpl.setAccessible(true);
                        Object resourcesImpl = mResourcesImpl.get(resources);
                        // ShareReflectUtil --> superclass
                        Field mAssets1 = resourcesImpl.getClass().getDeclaredField("mAssets");
                        mAssets1.set(resources, assetManager);
                    }
                    resources.updateConfiguration(resources.getConfiguration(), resources.getDisplayMetrics());
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } catch (QuickReflection.QrException e) {
            e.printStackTrace();
        }


        /**#####################################*/
//        try {
//
//            AssetManager assetManager = SysHook.new_AssetManager();
//            Method mth = SysHook.method_AssetManager_addAssetPath();
//            Object addBundle = mth.invoke(assetManager, bundles[0].getPath());
//            Object addApp= mth.invoke(assetManager, packageResourcePath);
//            Log.i(SlimConfig.TAG, "packageResourcePath = " + packageResourcePath);
//            Log.i(SlimConfig.TAG, "addApp: "+(Integer)addApp+"; addBundle: "+(Integer)addBundle);
//            QuickReflection.QrClass<Object> ContextImpl = null;
//            ContextImpl = QuickReflection.into("android.app.ContextImpl");
//
//            QuickReflection.QrField<Object, Resources> ContextImpl_mResources = ContextImpl.field("mResources");
//            Resources resources = new Resources(assetManager, application.getResources().getDisplayMetrics(), application.getResources().getConfiguration());
//            ContextImpl_mResources.set(context, resources);
//            Object loadedApk = getLoadedApk(getActivityThread(), application.getPackageName());
//            QuickReflection.QrClass<Object> LoadedApk = QuickReflection.into("android.app.LoadedApk");
//            QuickReflection.QrField<Object, Resources> LoadedApk_mResources = LoadedApk.field("mResources");
//            LoadedApk_mResources.set(loadedApk, resources);
//            Log.i(SlimConfig.TAG, "set resources");
//        } catch (QuickReflection.QrException e) {
//            e.printStackTrace();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    public static void getMResDir(Application application) {
        String packageResourcePath = application.getPackageResourcePath();
        Log.i(SlimConfig.TAG, "packageResourcePath = " + packageResourcePath);
        SlimBundle slimBundle = new SlimBundle("panes.slim.bundle", Environment.getExternalStorageDirectory() + File.separator + "slim.bundle.apk", SlimBundle.TYPE_RESOURCES);
        SlimConfig.addResourcesBundle(slimBundle);
        SlimBundle[] bundles = SlimConfig.getResourcesBundles();
        String bundlePath = bundles[0].getPath();
        Object activityThread = null;
        try {
            activityThread = getActivityThread();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Map<String, WeakReference<?>> mPackages = Runtime.ActivityThread_mPackages.get(activityThread);
        Map<String, WeakReference<?>> mResourcePackages = Runtime.ActivityThread_mResourcePackages.get(activityThread);
        for (Map.Entry<String, WeakReference<?>> entry : mPackages.entrySet()) {
            Object loadedApk = entry.getValue().get();
//            Runtime.LoadedApk_mResDir.set(loadedApk, path);
            String path = Runtime.LoadedApk_mResDir.get(loadedApk);
            Log.i(SlimConfig.TAG, "path = " + path);
        }
        for (Map.Entry<String, WeakReference<?>> entry : mResourcePackages.entrySet()) {
            Object loadedApk = entry.getValue().get();
//            Runtime.LoadedApk_mResDir.set(loadedApk, path);
            String path = Runtime.LoadedApk_mResDir.get(loadedApk);
            Log.i(SlimConfig.TAG, "path = " + path);
        }
    }

    public static void injectTheme(Activity activity) {
        Resources.Theme theme = activity.getTheme();
        try {
            try {
                Field ma = Resources.Theme.class.getDeclaredField("mAssets");
                ma.setAccessible(true);
                ma.set(theme, Runtime.AssetManager);
            } catch (NoSuchFieldException ignore) {
                Field themeField = Resources.Theme.class.getDeclaredField("mThemeImpl");
                themeField.setAccessible(true);
                Object impl = themeField.get(theme);
                Field ma = impl.getClass().getDeclaredField("mAssets");
                ma.setAccessible(true);
                ma.set(impl, Runtime.AssetManager);
            }

            Field mt = ContextThemeWrapper.class.getDeclaredField("mTheme");
            mt.setAccessible(true);
            mt.set(activity, null);
            Method mtm = ContextThemeWrapper.class.getDeclaredMethod("initializeTheme",
                    new Class[0]);
            mtm.setAccessible(true);
            mtm.invoke(activity, new Object[0]);

            Method mCreateTheme = AssetManager.class.getDeclaredMethod("createTheme",
                    new Class[0]);
            mCreateTheme.setAccessible(true);
            Object internalTheme = mCreateTheme.invoke(Runtime.AssetManager, new Object[0]);
            Field mTheme = Resources.Theme.class.getDeclaredField("mTheme");
            mTheme.setAccessible(true);
            mTheme.set(theme, internalTheme);
        } catch (Throwable e) {
            Log.e("InstantRun",
                    "Failed to update existing theme for activity " + activity, e);
        }

    }

    private static void setLoadedApk_mResDir(Map<String, WeakReference<?>> map, String path) {
        for (Map.Entry<String, WeakReference<?>> entry : map.entrySet()) {
            Object loadedApk = entry.getValue().get();
            Runtime.LoadedApk_mResDir.set(loadedApk, path);
        }
    }

}
