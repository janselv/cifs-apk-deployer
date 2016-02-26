package com.jvra.cifs;


import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;

/**
 * Created by Jansel Valentin on 12/11/2015.
 */
public class Deployer {

    private static final int MAX_THREAD = 10;

    private static ExecutorService exec = Executors.newFixedThreadPool(MAX_THREAD);

    public static final void main(String... args) throws Exception {
        if (null == args || 0 >= args.length || 3 != args.length) {
            System.out.println("Usage: java -jar cifs-apk-deployer <shared-dir> <android-file> <package-name>");
            return;
        }

        final String sharedDir = args[0];
        final String androidApk = args[1];
        final String packageName = args[2];

        File appDir = new File(sharedDir);
        exec.execute(new AndroidAppDeployerWatcher(appDir, androidApk, new AndroidAppDeployerWatcher.OnAndroidAppDeployListener() {
            public void onAndroidAppDeploy(File androidApp) {
                try {
                    AdbDeployer.installAndLaunch(androidApp,packageName);

                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }));
    }

    private static final class AndroidAppDeployerWatcher implements Runnable {
        private File apkDir;
        private String fileName;
        private OnAndroidAppDeployListener deployListener;


        public AndroidAppDeployerWatcher(File apkDir, String fileName, OnAndroidAppDeployListener deployListener) {
            this.apkDir = apkDir;
            this.fileName = fileName;
            this.deployListener = deployListener;
        }

        public void run() {
            System.out.println("Listening deploy changes on dir " + apkDir + "...");
            try {
                for (; ; ) {
                    final WatchService watchService = FileSystems.getDefault().newWatchService();
                    final WatchKey watchKey = apkDir.toPath().register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
                    while (true) {
                        final WatchKey wk = watchService.take();
                        for (WatchEvent<?> event : wk.pollEvents()) {

                            //we only register "ENTRY_MODIFY" so the context is always a Path.
                            final Path changed = (Path) event.context();
                            final File fileChanged = new File(apkDir, changed.toString());

                            System.out.println("Reporting deploy changes on file " + changed + " on event " + event.kind());
                            if (null != deployListener && fileChanged.getName().equalsIgnoreCase(fileName))
                                deployListener.onAndroidAppDeploy(fileChanged);
                            else
                                System.out.println("Bypassing this file, nothing to proccess");

                        }
                        boolean valid = wk.reset();
                        if (!valid) {
                            System.out.println("Key has been unregistered");
                        }
                    }
                }
            } catch (Exception ex) {
                System.out.println("Could not attach jar change watcher for class loader");
                ex.printStackTrace();
            }
        }

        private interface OnAndroidAppDeployListener {
            void onAndroidAppDeploy(File androidApp);
        }
    }

    private static final class AdbDeployer {

        public static void install(File file) throws Exception {
            System.out.println("Installing android app: " + file + "...");

            if (null == file)
                throw new IllegalArgumentException("AndroidApk must not be null");

            String command = "adb install -r " + file.getAbsolutePath();
            executeComamnd(command);
        }

        public static void installAndLaunch( File file,String packageName ) throws Exception{
            install(file);
            System.out.println("Launching android app: " + file + "...");

            if (null == file)
                throw new IllegalArgumentException("AndroidApk must not be null");

            String command = "adb shell monkey -p "+packageName+" -c android.intent.category.LAUNCHER 1";
            executeComamnd(command);
        }

        private static void executeComamnd( String command ) throws Exception{
            System.out.println( command );

            Process proc = null;
            try {
                proc = Runtime.getRuntime().exec(command);

                Future<String> errorTask = exec.submit(new ErrorReader(proc.getErrorStream()));
                Future<String> inputTask = exec.submit(new InputReader(proc.getInputStream()));

                try {
                    proc.waitFor();

                    String error = errorTask.get();
                    if (!"".equals(error)) {
                        System.out.println(error);
                        System.out.println( inputTask.get() );
                    } else {
                        System.out.println( inputTask.get() );
                    }
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }

            } catch (IOException ioe) {
                ioe.printStackTrace();
            } finally {
                if (null != proc)
                    proc.destroy();
            }
        }

        private static class InputReader implements Callable<String> {
            private InputStream stream;

            public InputReader(InputStream stream) {
                this.stream = stream;
            }

            public String call() throws Exception {
                if (null == stream)
                    return null;

                StringBuilder builder = new StringBuilder();

                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                    String line;
                    while (null != (line = reader.readLine())) {
                        builder.append( line ).append( "\n" );
                    }
                }catch ( Exception ex ){
                    ex.printStackTrace();
                }
                return builder.toString();
            }
        }

        private static class ErrorReader implements Callable<String> {
            private InputStream stream;

            public ErrorReader(InputStream stream) {
                this.stream = stream;
            }

            public String call() throws Exception {
                if (null == stream)
                    return null;
                StringBuilder sb = new StringBuilder();
                String line;
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                    while (null != (line = reader.readLine())) {
                        sb.append(line).append(" ");
                    }
                }catch (Exception ex){
                    ex.printStackTrace();
                }
                return sb.toString();
            }
        }
    }
}