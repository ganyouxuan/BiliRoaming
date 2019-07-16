package me.iacn.biliroaming.hooker;

import android.app.Activity;
import android.app.AndroidAppHelper;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import me.iacn.biliroaming.BiliBiliPackage;
import me.iacn.biliroaming.ColorChooseDialog;
import me.iacn.biliroaming.XposedInit;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.getStaticObjectField;
import static de.robv.android.xposed.XposedHelpers.setBooleanField;
import static de.robv.android.xposed.XposedHelpers.setIntField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;
import static me.iacn.biliroaming.Constant.CUSTOM_COLOR_KEY;
import static me.iacn.biliroaming.Constant.DEFAULT_CUSTOM_COLOR;
import static me.iacn.biliroaming.Constant.TAG;

/**
 * Created by iAcn on 2019/7/14
 * Email i@iacn.me
 */
public class CustomThemeHook extends BaseHook {

    private static final int CUSTOM_THEME_ID = 114514;  // ん？

    public CustomThemeHook(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public void startHook() {
        if (!XposedInit.sPrefs.getBoolean("custom_theme", false)) return;
        Log.d(TAG, "startHook: CustomTheme");

        BiliBiliPackage instance = BiliBiliPackage.getInstance();
        Class<?> helperClass = instance.themeHelper();
        SparseArray<int[]> colorArray = (SparseArray) getStaticObjectField(helperClass, instance.colorArray());

        int mainColor = getCustomColor();
        colorArray.put(CUSTOM_THEME_ID, generateColorArray(mainColor));

        findAndHookMethod("tv.danmaku.bili.ui.theme.ThemeStoreActivity", mClassLoader, "a",
                "tv.danmaku.bili.ui.theme.api.BiliSkinList", boolean.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object biliSkinList = param.args[0];
                        List mList = (List) getObjectField(biliSkinList, "mList");

                        if (mList == null) return;

                        Class<?> biliSkinClass = findClass("tv.danmaku.bili.ui.theme.api.BiliSkin", mClassLoader);
                        Object biliSkin = biliSkinClass.newInstance();

                        setIntField(biliSkin, "mId", CUSTOM_THEME_ID);
                        setObjectField(biliSkin, "mName", "自选颜色");
                        setBooleanField(biliSkin, "mIsFree", true);
                        // Under the night mode item
                        mList.add(3, biliSkin);

                        Log.d(TAG, "Add a theme item: size = " + mList.size());
                    }
                });

        findAndHookMethod(instance.themeListClickListener(), mClassLoader, "onClick", View.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                View view = (View) param.args[0];
                String idName = view.getResources().getResourceEntryName(view.getId());

                if (!"list_item".equals(idName)) return;

                Object biliSkin = view.getTag();

                if (biliSkin == null) return;

                int mId = getIntField(biliSkin, "mId");
                // Make colors updated immediately
                if (mId == CUSTOM_THEME_ID || mId == -1) {
                    Log.d(TAG, "Custom theme item has been clicked");
                    Log.d(TAG, "New theme: mId = " + mId);

                    ColorChooseDialog colorDialog = new ColorChooseDialog(view.getContext(), getCustomColor());
                    colorDialog.setPositiveButton("确定", (dialog, which) -> {
                        int color = colorDialog.getColor();
                        Log.d(TAG, "Chose color: " + color);

                        BiliBiliPackage instance = BiliBiliPackage.getInstance();
                        Class<?> helperClass = instance.themeHelper();
                        SparseArray<int[]> colorArray = (SparseArray) getStaticObjectField(helperClass, instance.colorArray());

                        int[] colors = generateColorArray(color);
                        colorArray.put(CUSTOM_THEME_ID, colors);
                        colorArray.put(-1, colors);  // Add a new color id but it won't be saved

                        // If it is currently use the custom theme, it will use temporary id
                        // To make the theme color take effect immediately
                        setIntField(biliSkin, "mId", mId == CUSTOM_THEME_ID ? -1 : CUSTOM_THEME_ID);

                        putCustomColor(color);
                        try {
                            XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }

                    });
                    colorDialog.show();

                    // Stop executing the original method
                    param.setResult(null);
                }
            }
        });

        findAndHookMethod(helperClass, "a", Context.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                int currentThemeKey = (int) param.args[1];
                if (currentThemeKey == -1)
                    param.args[1] = CUSTOM_THEME_ID;
            }
        });

        // Make sure that not invalidate when user not logging in
        findAndHookMethod(helperClass, "a", Activity.class, XC_MethodReplacement.DO_NOTHING);
    }

    private int[] generateColorArray(int mainColor) {
        return new int[]{mainColor, Color.GREEN, Color.BLUE, Color.BLACK};
    }

    private int getCustomColor() {
        return getBiliPrefs().getInt(CUSTOM_COLOR_KEY, DEFAULT_CUSTOM_COLOR);
    }

    private void putCustomColor(int color) {
        getBiliPrefs().edit().putInt(CUSTOM_COLOR_KEY, color).apply();
    }

    private SharedPreferences getBiliPrefs() {
        return AndroidAppHelper.currentApplication().getSharedPreferences("bili_preference", Context.MODE_PRIVATE);
    }
}