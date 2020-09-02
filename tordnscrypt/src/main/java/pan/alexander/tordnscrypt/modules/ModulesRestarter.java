package pan.alexander.tordnscrypt.modules;

/*
    This file is part of InviZible Pro.

    InviZible Pro is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    InviZible Pro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with InviZible Pro.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.File;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class ModulesRestarter {
    public static void restartDNSCrypt(Context context) {
        sendIntent(context, ModulesService.actionRestartDnsCrypt);
    }

    public static void restartTor(Context context) {
        boolean useDefaultBridges = new PrefManager(context).getBoolPref("useDefaultBridges");
        boolean useOwnBridges = new PrefManager(context).getBoolPref("useOwnBridges");

        if (useDefaultBridges || useOwnBridges) {
            restartTorFull(context);
        } else {
            sendIntent(context, ModulesService.actionRestartTor);
        }

    }

    public static void restartTorFull(Context context) {
        sendIntent(context, ModulesService.actionRestartTorFull);
    }

    public static void restartITPD(Context context) {
        sendIntent(context, ModulesService.actionRestartITPD);
    }

    private static void sendIntent(Context context, String action) {
        Intent intent = new Intent(context, ModulesService.class);
        intent.setAction(action);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra("showNotification", true);
            context.startForegroundService(intent);
        } else {
            intent.putExtra("showNotification", isShowNotification(context));
            context.startService(intent);
        }
    }

    private static boolean isShowNotification(Context context) {
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        return shPref.getBoolean("swShowNotification", true);
    }

    Runnable getTorRestarterRunnable(Context context) {
        boolean useModulesWithRoot = ModulesStatus.getInstance().isUseModulesWithRoot();
        PathVars pathVars = PathVars.getInstance(context);
        String torPid = readPidFile(context, pathVars.getAppDataDir() + "/tor.pid");

        return () -> restartModule(pathVars, pathVars.getTorPath(), torPid, useModulesWithRoot);

    }

    private String readPidFile(Context context, String path) {
        String pid = "";

        File file = new File(path);
        if (file.isFile()) {
            List<String> lines = FileOperations.readTextFileSynchronous(context, path);

            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    pid = line.trim();
                    break;
                }
            }
        }
        return pid;
    }

    private synchronized void restartModule(PathVars pathVars, String module, String pid, boolean killWithRoot) {
        if (module.contains("/")) {
            module = module.substring(module.lastIndexOf("/"));
        }

        String[] preparedCommands = prepareRestartCommand (pathVars, module, pid, killWithRoot);

        if (ModulesStatus.getInstance().isRootAvailable() && killWithRoot) {
            killWithSU(module, preparedCommands);
        } else if (!pid.isEmpty()){
            killWithPid(pid);
        } else {
            killWithSH(module, preparedCommands);
        }
    }

    private void killWithPid(String pid) {
        try {
            android.os.Process.sendSignal(Integer.parseInt(pid), 1);
        } catch (Exception e) {
            Log.e(LOG_TAG, "ModulesRestarter killWithPid exception " + e.getMessage() + " " + e.getCause());
        }
    }

    @SuppressWarnings("deprecation")
    private void killWithSU(String module, String[] commands) {
        try {
            Shell.SU.run(commands);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Restart " + module + " with root exception " + e.getMessage() + " " + e.getCause());
        }
    }

    @SuppressWarnings("deprecation")
    private void killWithSH(String module, String[] commands) {
        try {
            Shell.SH.run(commands);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Restart " + module + " without root exception " + e.getMessage() + " " + e.getCause());
        }
    }

    private String[] prepareRestartCommand(PathVars pathVars, String module, String pid, boolean killWithRoot) {
        String[] result;

        if (pid.isEmpty() || killWithRoot) {
            String killStringBusybox = pathVars.getBusyboxPath() + "pkill -SIGHUP" + " " + module;
            //String killAllStringBusybox = pathVars.busyboxPath + "kill -s SIGHUP" + " $(pgrep " + module + ")";
            String killStringToyBox = "toybox pkill -SIGHUP" + " " + module;
            //String killString = "pkill -" + "SIGHUP" + " " + module;

            String killString = killStringBusybox;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !killWithRoot) {
                killString = killStringToyBox;
            }

            result = new String[]{
                    killString
            };
        } else {
            //String killStringBusyBox = pathVars.busyboxPath + "kill -s SIGHUP" + " " + pid;
            //String killAllStringToolBox = "toolbox kill -s SIGHUP" + " " + pid;
            //String killStringToyBox = "toybox kill -s SIGHUP" + " " + pid;
            String killString = "kill -s SIGHUP" + " " + pid;

            result = new String[]{
                    //killStringBusyBox,
                    //killAllStringToolBox,
                    //killStringToyBox,
                    killString
            };
        }

        return result;
    }

}
