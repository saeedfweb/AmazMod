package com.amazmod.service.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.amazmod.service.Constants;
import com.amazmod.service.MainService;
import com.amazmod.service.R;
import com.amazmod.service.settings.SettingsManager;
import com.amazmod.service.springboard.SpringboardItem;
import com.amazmod.service.springboard.settings.BaseSetting;
import com.amazmod.service.springboard.settings.HeaderSetting;
import com.amazmod.service.springboard.settings.SpringboardSetting;
import com.amazmod.service.springboard.settings.SpringboardWidgetAdapter;
import com.amazmod.service.springboard.settings.TextSetting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class WidgetsUtil {

    private static ArrayList<BaseSetting> settingList;
    //Countdown timer to prevent saving too often
    private static CountDownTimer countDownTimer;

    public static void loadSettings(final Context context) {

        SettingsManager settingsManager = new SettingsManager(context);
        boolean amazModFirstWidget = settingsManager.getBoolean(Constants.PREF_AMAZMOD_FIRST_WIDGET, true);
        String savedSpringboardOder = settingsManager.getString(Constants.PREF_SPRINGBOARD_ORDER, "");
        String springboard_widget_order_out;
        String springboard_widget_order_in;

        final SpringboardItem amazmodWidget = new SpringboardItem("com.amazmod.service",
                "com.amazmod.service.springboard.AmazModLauncher", true);
        boolean isAmazmodWidgetMissing = true;

        //Get in and out settings.
        // In is the main setting, which defines the order and state of a page, but does not always contain them all.
        if (savedSpringboardOder.isEmpty())
            springboard_widget_order_in = Settings.System.getString(context.getContentResolver(), "springboard_widget_order_in");
        else
            springboard_widget_order_in = savedSpringboardOder;
        Log.d(Constants.TAG, "WidgetsUtil loadSettings widget_order_in  : " + springboard_widget_order_in);

        //Out contains them all, but no ordering. Use saved settings if it exists;
        springboard_widget_order_out = Settings.System.getString(context.getContentResolver(), "springboard_widget_order_out");

        Log.d(Constants.TAG, "WidgetsUtil loadSettings widget_order_out : " + springboard_widget_order_out);

        //Create empty list
        settingList = new ArrayList<>();
        try {
            //Parse JSON
            JSONObject root = new JSONObject(springboard_widget_order_in);
            JSONArray data = root.getJSONArray("data");
            List<String> addedComponents = new ArrayList<>();
            //Data array contains all the elements
            for (int x = 0; x < data.length(); x++) {
                //Get item
                JSONObject item = data.getJSONObject(x);

                //Check if AmazMod widget already exists
                if (item.getString("pkg").equals("com.amazmod.service"))
                    isAmazmodWidgetMissing = false;

                //srl is the position, stored as a string for some reason
                int srl = Integer.parseInt(item.getString("srl"));
                //State is stored as an integer when it would be better as a boolean so convert it
                boolean enable = item.getInt("enable") == 1;
                //Create springboard item with the package name, class name and state
                final SpringboardItem springboardItem = new SpringboardItem(item.getString("pkg"), item.getString("cls"), enable);
                //Create a setting (extending switch) with the relevant data and a callback
                SpringboardSetting springboardSetting = new SpringboardSetting(null, getTitle(springboardItem.getPackageName(), context),
                        formatComponentName(springboardItem.getClassName()), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                        Log.d(Constants.TAG, "WidgetsUtil loadSettings.onCheckedChanged b: " + b);
                        //Ignore on create to reduce load
                        if (!compoundButton.isPressed()) return;
                        //Update state
                        springboardItem.setEnabled(b);
                        //Save
                        save(context);
                    }
                }, springboardItem.isEnable(), springboardItem);
                //Store component name for later
                addedComponents.add(springboardItem.getClassName());
                try {
                    //Attempt to add at position, may cause exception
                    settingList.add(srl, springboardSetting);
                } catch (IndexOutOfBoundsException e) {
                    //Add at top as position won't work
                    settingList.add(springboardSetting);
                }
            }
            //Parse JSON
            JSONObject rootOut = new JSONObject(springboard_widget_order_out);
            JSONArray dataOut = rootOut.getJSONArray("data");
            //Loop through main data array
            for (int x = 0; x < data.length(); x++) {
                //Get item
                //Checks if item exists at out array
                if (!dataOut.isNull(x)) {
                    JSONObject item = dataOut.getJSONObject(x);
                    //Get component name to check list
                    String componentName = item.getString("cls");
                    if (!addedComponents.contains(componentName)) {
                        //Get if item is enabled, this time stored as a string (why?)
                        boolean enable = item.getString("enable").equals("true");
                        //Create item with the package name, class name and state
                        SpringboardItem springboardItem = new SpringboardItem(item.getString("pkg"), item.getString("cls"), enable);
                        //Add class name to list to prevent it being adding more than once
                        addedComponents.add(springboardItem.getClassName());

                        //Always show amazmod as first when swiping left (index 2, second item) if its defined in preferences
                        if (item.getString("pkg").equals("com.amazmod.service") && amazModFirstWidget) {
                            //Make sure it is enabled
                            springboardItem.setEnabled(true);
                            //Create setting with all the relevant data
                            SpringboardSetting springboardSetting = addSpringboardSetting(context, springboardItem);
                            //Add amazmod as first one
                            settingList.add(1, springboardSetting);
                            isAmazmodWidgetMissing = false;
                        } else {
                            //Create setting with all the relevant data
                            SpringboardSetting springboardSetting = addSpringboardSetting(context, springboardItem);
                            //Add setting to main list
                            settingList.add(springboardSetting);
                        }
                    }
                }
            }

            if (isAmazmodWidgetMissing && amazModFirstWidget) {
                SpringboardSetting amazmodSetting = addSpringboardSetting(context, amazmodWidget);
                settingList.add(1, amazmodSetting);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
        //Empty settings list can be confusing to the user, and is quite common, so we'll add the FAQ to save them having to read the OP (oh the horror)
        if (settingList.size() == 0) {
            //Add error message
            settingList.add(new TextSetting(context.getString(R.string.error_loading), null));
        } else
            //Add main header to top (pos 0)
            settingList.add(0, new HeaderSetting("Widgets", new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Log.d(Constants.TAG, "WidgetsUtil loadSettings.onLongClick");
                MainService.setWasSpringboardSaved(true);
                save(context, true, true);
                return true;
            }
        }));

        MainService.setWasSpringboardSaved(true);
        //Save initial config (to keep amazmod in first position)
        save(context, false, false);
    }

    private static SpringboardSetting addSpringboardSetting(final Context context, final SpringboardItem springboardItem) {

        return new SpringboardSetting(null, getTitle(springboardItem.getPackageName(), context),
                formatComponentName(springboardItem.getClassName()), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                Log.d(Constants.TAG, "WidgetsUtil addSpringboardSetting.onCheckedChanged b: " + b);
                if (!compoundButton.isPressed()) return;
                springboardItem.setEnabled(b);
                save(context);
            }
        }, springboardItem.isEnable(), springboardItem);

    }

    public static SpringboardWidgetAdapter getAdapter(final Context context) {
        return new SpringboardWidgetAdapter(context, settingList, new SpringboardWidgetAdapter.ChangeListener() {
            @Override
            public void onChange() {
                Log.d(Constants.TAG, "WidgetsUtil getAdapter.onChange");
                checkSave(context);
            }
        });
    }

    //Get an app name from the package name
    private static String getTitle(String pkg, Context context) {
        PackageManager packageManager = context.getPackageManager();
        try {
            return String.valueOf(packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return context.getString(R.string.unknown);
        }
    }

    //Get last part of component name
    private static String formatComponentName(String componentName) {
        //Ignore if no . and just return component name
        if (!componentName.contains(".")) return componentName;
        //Return just the last section of component name
        return componentName.substring(componentName.lastIndexOf(".") + 1);
    }

    private static void checkSave(final Context context) {

        Log.d(Constants.TAG, "WidgetsUtil checkSave");

        //Create timer if not already, for 2 seconds. Call save after completion
        if (countDownTimer == null) countDownTimer = new CountDownTimer(2000, 2000) {
            @Override
            public void onTick(long l) {
            }

            @Override
            public void onFinish() {
                save(context);
            }
        };
        //Cancel and start timer. This means that this method must be called ONCE in 2 seconds before save will be called, it prevents save from being called more than once every 2 seconds (buffers moving)
        countDownTimer.cancel();
        countDownTimer.start();
    }

    private static void save(Context context) {
        MainService.setWasSpringboardSaved(true);
        save(context, true, false);
    }

    private static void save(Context context, boolean showToast, boolean saveLocal) {

        Log.d(Constants.TAG, "WidgetsUtil save showToast: " + showToast);

        //Create a blank array
        JSONArray data = new JSONArray();
        //Hold position for use as srl
        int pos = 0;

        for (BaseSetting springboardSetting : settingList) {
            //Ignore if not a springboard setting
            if (!(springboardSetting instanceof SpringboardSetting)) continue;
            //Get item
            SpringboardItem springboardItem = ((SpringboardSetting) springboardSetting).getSpringboardItem();
            JSONObject item = new JSONObject();
            //Setup item with data from the item
            try {
                item.put("pkg", springboardItem.getPackageName());
                item.put("cls", springboardItem.getClassName());
                item.put("srl", String.valueOf(pos));
                item.put("enable", springboardItem.isEnable() ? "1" : "0");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            //Add to list and increment position
            data.put(item);
            pos++;
        }
        //Add to root object
        JSONObject root = new JSONObject();
        try {
            root.put("data", data);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        //Save setting
        if (saveLocal) {
            SettingsManager settingsManager = new SettingsManager(context);
            settingsManager.putString(Constants.PREF_SPRINGBOARD_ORDER, root.toString());
            Log.d(Constants.TAG, "WidgetsUtil save PREF_SPRINGBOARD_ORDER: " + root.toString());
        } else {
            Settings.System.putString(context.getContentResolver(), "springboard_widget_order_in", root.toString());
            Log.d(Constants.TAG, "WidgetsUtil save widget_order_in: " + root.toString());
        }
        //Notify user
        if (showToast) {
            if (saveLocal)
                Toast.makeText(context, "List Saved", Toast.LENGTH_SHORT).show();
            else
                Toast.makeText(context, context.getString(R.string.saved), Toast.LENGTH_SHORT).show();
        }
    }

}
