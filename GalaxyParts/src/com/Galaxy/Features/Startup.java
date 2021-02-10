/*
* Copyright (C) 2013 The OmniROM Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package com.Galaxy.Features;

import android.app.ActivityManager;
import android.app.Activity;
import android.provider.Settings;
import android.os.SELinux;
import android.util.Log;
import android.widget.Toast;
import android.text.TextUtils;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import com.Galaxy.Features.Touch.util.Utils;
import com.Galaxy.Features.doze.DozeUtils;
import com.Galaxy.Features.Touch.ScreenOffGesture;
import com.Galaxy.Features.ModeSwitch.GameModeSwitch;
import com.Galaxy.Features.kcal.DisplayCalibration;
import com.Galaxy.Features.DiracUtils;
import com.Galaxy.Features.R;

import java.io.IOException;
import java.util.List;

public class Startup extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";
    private static final String ONE_TIME_TUNABLE_RESTORE = "hardware_tunable_restored";

    private final String HEADPHONE_GAIN_PATH = "/sys/kernel/sound_control/headphone_gain";
    private final String MICROPHONE_GAIN_PATH = "/sys/kernel/sound_control/mic_gain";

    private static final String PREF_SELINUX_MODE = "selinux_mode";

    private Context settingsContext = null;
    private boolean mSetupRunning = false;
    private Context mContext;

    private void restore(String file, boolean enabled) {
        if (file == null) {
            return;
        }
        if (enabled) {
            Utils.writeValue(file, "1");
        }
    }

    private void restore(String file, String value) {
        if (file == null) {
            return;
        }
        Utils.writeValue(file, value);
    }

    @Override
    public void onReceive(final Context context, final Intent bootintent) {
        boolean enabled = false;
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences (context);
        enabled = sharedPrefs.getBoolean (GalaxyParts.KEY_GAME_SWITCH, false);
        restore (GameModeSwitch.getFile ( ), enabled);
        enabled = sharedPrefs.getBoolean(GalaxyParts.PREF_KEY_FPS_INFO, false);
        if (enabled) {
            context.startService(new Intent(context, FPSInfoService.class));
        }
            enableComponent(context, ScreenOffGesture.class.getName());
            SharedPreferences screenOffGestureSharedPreferences = context.getSharedPreferences(
                    Utils.PREFERENCES, Activity.MODE_PRIVATE);
            KernelControl.enableGestures(
                    screenOffGestureSharedPreferences.getBoolean(
                            ScreenOffGesture.PREF_GESTURE_ENABLE, true));
            KernelControl.enableDt2w(
                    screenOffGestureSharedPreferences.getBoolean(
                            ScreenOffGesture.PREF_DT2W_ENABLE, true));
    mContext = context;
    ActivityManager activityManager =
            (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    List<ActivityManager.RunningAppProcessInfo> procInfos =
            activityManager.getRunningAppProcesses();
    for(int i = 0; i < procInfos.size(); i++) {
        if(procInfos.get(i).processName.equals("com.google.android.setupwizard")) {
            mSetupRunning = true;
        }
    }

    if(!mSetupRunning) {
        try {
            settingsContext = context.createPackageContext("com.android.settings", 0);
        } catch (Exception e) {
            Log.e(TAG, "Package not found", e);
        }
        SharedPreferences sharedpreferences = context.getSharedPreferences("selinux_pref",
                Context.MODE_PRIVATE);
        if (sharedpreferences.contains(PREF_SELINUX_MODE)) {
        boolean currentIsSelinuxEnforcing = SELinux.isSELinuxEnforced();
        boolean isSelinuxEnforcing =
                    sharedpreferences.getBoolean(PREF_SELINUX_MODE,
                            currentIsSelinuxEnforcing);
            if (isSelinuxEnforcing) {
               if (!currentIsSelinuxEnforcing) {
                   try {
                        SuShell.runWithSuCheck("setenforce 1");
                        showToast(context.getString(R.string.selinux_enforcing_toast_title),
                                context);
                    } catch (SuShell.SuDeniedException e) {
                        showToast(context.getString(R.string.cannot_get_su), context);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
               }
            } else {
                if (currentIsSelinuxEnforcing) {
                    try {
                        SuShell.runWithSuCheck("setenforce 0");
                        showToast(context.getString(R.string.selinux_permissive_toast_title),
                                context);
                    } catch (SuShell.SuDeniedException e) {
                        showToast(context.getString(R.string.cannot_get_su), context);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

	DozeUtils.checkDozeService(context);
        context.startService (new Intent (context, DisplayCalibration.class));

        int gain = Settings.Secure.getInt (context.getContentResolver ( ),
                GalaxyParts.PREF_HEADPHONE_GAIN, 4);
        FileUtils.setValue (HEADPHONE_GAIN_PATH, gain + " " + gain);
        FileUtils.setValue (MICROPHONE_GAIN_PATH, Settings.Secure.getInt (context.getContentResolver ( ),
                GalaxyParts.PREF_MICROPHONE_GAIN, 0));
        FileUtils.setValue (GalaxyParts.EARPIECE_GAIN_PATH, Settings.Secure.getInt (context.getContentResolver ( ),
                GalaxyParts.PREF_EARPIECE_GAIN, 0));
        context.startService (new Intent (context, DiracService.class));

        enabled = sharedPrefs.getBoolean (GalaxyParts.PREF_KEY_FPS_INFO, false);
        if (enabled) {
            context.startService (new Intent (context, FPSInfoService.class));
            enabled = sharedPrefs.getBoolean (GalaxyParts.KEY_CHARGING_SWITCH, false);
            if (enabled) {
                context.startService (new Intent (context, SmartChargingService.class));
            }
        }

    }

    private void enableComponent(Context context, String name) {
    }
    
    private boolean hasRestoredTunable(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean(ONE_TIME_TUNABLE_RESTORE, false);
    }

    private void setRestoredTunable(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.edit().putBoolean(ONE_TIME_TUNABLE_RESTORE, true).apply();

        }

    private void showToast(String toastString, Context context) {
    Toast.makeText(context, toastString, Toast.LENGTH_SHORT)
            .show();
        }
    }   
