package com.kyunghoon.eversolohistory;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;

public final class StorageLocator {
    private static final String VOLUME = "E6B9-EC04";

    private StorageLocator() {}

    public static File managerDir(Context context) {
        File[] candidates = new File[] {
                new File("/storage/" + VOLUME),
                new File("/mnt/media_rw/" + VOLUME)
        };

        for (File root : candidates) {
            File found = tryManager(root);
            if (found != null) return found;
        }

        File found = scanRoots(new File("/storage"));
        if (found != null) return found;

        found = scanRoots(new File("/mnt/media_rw"));
        if (found != null) return found;

        File[] appDirs = context.getExternalFilesDirs(null);
        if (appDirs != null) {
            for (File appDir : appDirs) {
                File root = volumeRootFromExternalFiles(appDir);
                found = tryManager(root);
                if (found != null) return found;
            }
        }

        File fallbackBase = context.getExternalFilesDir(null);
        if (fallbackBase == null) fallbackBase = context.getFilesDir();
        File fallback = new File(fallbackBase, "EversoloManager");
        if (!fallback.exists()) fallback.mkdirs();
        return fallback;
    }

    private static File scanRoots(File base) {
        File[] children = base.listFiles();
        if (children == null) return null;
        for (File root : children) {
            if (!root.isDirectory()) continue;
            String n = root.getName();
            if ("emulated".equalsIgnoreCase(n) || "self".equalsIgnoreCase(n)) continue;
            if (!new File(root, "Music").exists()) continue;
            File found = tryManager(root);
            if (found != null) return found;
        }
        return null;
    }

    private static File volumeRootFromExternalFiles(File appDir) {
        if (appDir == null) return null;
        File p = appDir;
        for (int i = 0; i < 4 && p != null; i++) p = p.getParentFile();
        return p;
    }

    private static File tryManager(File volumeRoot) {
        if (volumeRoot == null || !volumeRoot.exists() || !volumeRoot.isDirectory()) return null;
        File manager = new File(volumeRoot, ".EversoloManager");
        try {
            if (!manager.exists() && !manager.mkdirs()) return null;
            File test = new File(manager, ".write_test");
            FileWriter w = new FileWriter(test, false);
            w.write("ok");
            w.flush();
            w.close();
            if (!test.delete()) test.deleteOnExit();
            return manager;
        } catch (Exception ignored) {
            return null;
        }
    }
}
