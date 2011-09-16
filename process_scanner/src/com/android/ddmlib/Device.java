/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ddmlib;

import com.android.ddmlib.log.LogReceiver;

import java.io.File;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * A Device. It can be a physical device or an emulator.
 */
final class Device implements IDevice {

    private final static int INSTALL_TIMEOUT = 2*60*1000; //2min

    /** Emulator Serial Number regexp. */
    final static String RE_EMULATOR_SN = "emulator-(\\d+)"; //$NON-NLS-1$

    /** Serial number of the device */
    private String mSerialNumber = null;

    /** Name of the AVD */
    private String mAvdName = null;

    /** State of the device. */
    private DeviceState mState = null;

    /** Device properties. */
    private final Map<String, String> mProperties = new HashMap<String, String>();
    private final Map<String, String> mMountPoints = new HashMap<String, String>();

    private final ArrayList<Client> mClients = new ArrayList<Client>();
    private DeviceMonitor mMonitor;

    private static final String LOG_TAG = "Device";

    /**
     * Socket for the connection monitoring client connection/disconnection.
     */
    private SocketChannel mSocketChannel;

    private boolean mArePropertiesSet = false;

    /**
     * Output receiver for "pm install package.apk" command line.
     */
    private static final class InstallReceiver extends MultiLineReceiver {

        private static final String SUCCESS_OUTPUT = "Success"; //$NON-NLS-1$
        private static final Pattern FAILURE_PATTERN = Pattern.compile("Failure\\s+\\[(.*)\\]"); //$NON-NLS-1$

        private String mErrorMessage = null;

        public InstallReceiver() {
        }

        @Override
        public void processNewLines(String[] lines) {
            for (String line : lines) {
                if (line.length() > 0) {
                    if (line.startsWith(SUCCESS_OUTPUT)) {
                        mErrorMessage = null;
                    } else {
                        Matcher m = FAILURE_PATTERN.matcher(line);
                        if (m.matches()) {
                            mErrorMessage = m.group(1);
                        }
                    }
                }
            }
        }

        public boolean isCancelled() {
            return false;
        }

        public String getErrorMessage() {
            return mErrorMessage;
        }
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getSerialNumber()
     */
    public String getSerialNumber() {
        return mSerialNumber;
    }

    /** {@inheritDoc} */
    public String getAvdName() {
        return mAvdName;
    }

    /**
     * Sets the name of the AVD
     */
    void setAvdName(String avdName) {
        if (isEmulator() == false) {
            throw new IllegalArgumentException(
                    "Cannot set the AVD name of the device is not an emulator");
        }

        mAvdName = avdName;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getState()
     */
    public DeviceState getState() {
        return mState;
    }

    /**
     * Changes the state of the device.
     */
    void setState(DeviceState state) {
        mState = state;
    }


    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getProperties()
     */
    public Map<String, String> getProperties() {
        return Collections.unmodifiableMap(mProperties);
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getPropertyCount()
     */
    public int getPropertyCount() {
        return mProperties.size();
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getProperty(java.lang.String)
     */
    public String getProperty(String name) {
        return mProperties.get(name);
    }

    /**
     * {@inheritDoc}
     */
    public boolean arePropertiesSet() {
        return mArePropertiesSet;
    }

    /**
     * {@inheritDoc}
     */
    public String getPropertyCacheOrSync(String name) throws TimeoutException,
            AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
        if (mArePropertiesSet) {
            return getProperty(name);
        } else {
            return getPropertySync(name);
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getPropertySync(String name) throws TimeoutException,
            AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        executeShellCommand(String.format("getprop '%s'", name), receiver);
        String value = receiver.getOutput().trim();
        if (value.isEmpty()) {
            return null;
        }
        return value;
    }

    public String getMountPoint(String name) {
        return mMountPoints.get(name);
    }


    @Override
    public String toString() {
        return mSerialNumber;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#isOnline()
     */
    public boolean isOnline() {
        return mState == DeviceState.ONLINE;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#isEmulator()
     */
    public boolean isEmulator() {
        return mSerialNumber.matches(RE_EMULATOR_SN);
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#isOffline()
     */
    public boolean isOffline() {
        return mState == DeviceState.OFFLINE;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#isBootLoader()
     */
    public boolean isBootLoader() {
        return mState == DeviceState.BOOTLOADER;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#hasClients()
     */
    public boolean hasClients() {
        return mClients.size() > 0;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getClients()
     */
    public Client[] getClients() {
        synchronized (mClients) {
            return mClients.toArray(new Client[mClients.size()]);
        }
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getClient(java.lang.String)
     */
    public Client getClient(String applicationName) {
        synchronized (mClients) {
            for (Client c : mClients) {
                if (applicationName.equals(c.getClientData().getClientDescription())) {
                    return c;
                }
            }

        }

        return null;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getSyncService()
     */
    public SyncService getSyncService()
            throws TimeoutException, AdbCommandRejectedException, IOException {
        SyncService syncService = new SyncService(AndroidDebugBridge.getSocketAddress(), this);
        if (syncService.openSync()) {
            return syncService;
         }

        return null;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getFileListingService()
     */
    public FileListingService getFileListingService() {
        return new FileListingService(this);
    }

    public RawImage getScreenshot()
            throws TimeoutException, AdbCommandRejectedException, IOException {
        return AdbHelper.getFrameBuffer(AndroidDebugBridge.getSocketAddress(), this);
    }

    public void executeShellCommand(String command, IShellOutputReceiver receiver)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
            IOException {
        AdbHelper.executeRemoteCommand(AndroidDebugBridge.getSocketAddress(), command, this,
                receiver, DdmPreferences.getTimeOut());
    }

    public void executeShellCommand(String command, IShellOutputReceiver receiver,
            int maxTimeToOutputResponse)
            throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException,
            IOException {
        AdbHelper.executeRemoteCommand(AndroidDebugBridge.getSocketAddress(), command, this,
                receiver, maxTimeToOutputResponse);
    }

    public void runEventLogService(LogReceiver receiver)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.runEventLogService(AndroidDebugBridge.getSocketAddress(), this, receiver);
    }

    public void runLogService(String logname, LogReceiver receiver)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.runLogService(AndroidDebugBridge.getSocketAddress(), this, logname, receiver);
    }

    public void createForward(int localPort, int remotePort)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.createForward(AndroidDebugBridge.getSocketAddress(), this, localPort, remotePort);
    }

    public void removeForward(int localPort, int remotePort)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.removeForward(AndroidDebugBridge.getSocketAddress(), this, localPort, remotePort);
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#getClientName(int)
     */
    public String getClientName(int pid) {
        synchronized (mClients) {
            for (Client c : mClients) {
                if (c.getClientData().getPid() == pid) {
                    return c.getClientData().getClientDescription();
                }
            }
        }

        return null;
    }


    Device(DeviceMonitor monitor, String serialNumber, DeviceState deviceState) {
        mMonitor = monitor;
        mSerialNumber = serialNumber;
        mState = deviceState;
    }

    DeviceMonitor getMonitor() {
        return mMonitor;
    }

    void addClient(Client client) {
        synchronized (mClients) {
            mClients.add(client);
        }
    }

    List<Client> getClientList() {
        return mClients;
    }

    boolean hasClient(int pid) {
        synchronized (mClients) {
            for (Client client : mClients) {
                if (client.getClientData().getPid() == pid) {
                    return true;
                }
            }
        }

        return false;
    }

    void clearClientList() {
        synchronized (mClients) {
            mClients.clear();
        }
    }

    /**
     * Sets the client monitoring socket.
     * @param socketChannel the sockets
     */
    void setClientMonitoringSocket(SocketChannel socketChannel) {
        mSocketChannel = socketChannel;
    }

    /**
     * Returns the client monitoring socket.
     */
    SocketChannel getClientMonitoringSocket() {
        return mSocketChannel;
    }

    /**
     * Removes a {@link Client} from the list.
     * @param client the client to remove.
     * @param notify Whether or not to notify the listeners of a change.
     */
    void removeClient(Client client, boolean notify) {
        mMonitor.addPortToAvailableList(client.getDebuggerListenPort());
        synchronized (mClients) {
            mClients.remove(client);
        }
        if (notify) {
            mMonitor.getServer().deviceChanged(this, CHANGE_CLIENT_LIST);
        }
    }

    void update(int changeMask) {
        if ((changeMask & CHANGE_BUILD_INFO) != 0) {
            mArePropertiesSet = true;
        }
        mMonitor.getServer().deviceChanged(this, changeMask);
    }

    void update(Client client, int changeMask) {
        mMonitor.getServer().clientChanged(client, changeMask);
    }

    void addProperty(String label, String value) {
        mProperties.put(label, value);
    }

    void setMountingPoint(String name, String value) {
        mMountPoints.put(name, value);
    }

    public void pushFile(String local, String remote)
            throws IOException, AdbCommandRejectedException, TimeoutException, SyncException {
        try {
            String targetFileName = getFileName(local);

            Log.d(targetFileName, String.format("Uploading %1$s onto device '%2$s'",
                    targetFileName, getSerialNumber()));

            SyncService sync = getSyncService();
            if (sync != null) {
                String message = String.format("Uploading file onto device '%1$s'",
                        getSerialNumber());
                Log.d(LOG_TAG, message);
                sync.pushFile(local, remote, SyncService.getNullProgressMonitor());
            } else {
                throw new IOException("Unable to open sync connection!");
            }
        } catch (TimeoutException e) {
            Log.e(LOG_TAG, "Error during Sync: timeout.");
            throw e;

        } catch (SyncException e) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1$s", e.getMessage()));
            throw e;

        } catch (IOException e) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1$s", e.getMessage()));
            throw e;

        }
    }

    public void pullFile(String remote, String local)
            throws IOException, AdbCommandRejectedException, TimeoutException, SyncException {
        try {
            String targetFileName = getFileName(remote);

            Log.d(targetFileName, String.format("Downloading %1$s from device '%2$s'",
                    targetFileName, getSerialNumber()));

            SyncService sync = getSyncService();
            if (sync != null) {
                String message = String.format("Downloding file from device '%1$s'",
                        getSerialNumber());
                Log.d(LOG_TAG, message);
                sync.pullFile(remote, local, SyncService.getNullProgressMonitor());
            } else {
                throw new IOException("Unable to open sync connection!");
            }
        } catch (TimeoutException e) {
            Log.e(LOG_TAG, "Error during Sync: timeout.");
            throw e;

        } catch (SyncException e) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1$s", e.getMessage()));
            throw e;

        } catch (IOException e) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1$s", e.getMessage()));
            throw e;

        }
    }

    public String installPackage(String packageFilePath, boolean reinstall, String... extraArgs)
            throws InstallException {
        try {
            String remoteFilePath = syncPackageToDevice(packageFilePath);
            String result = installRemotePackage(remoteFilePath, reinstall, extraArgs);
            removeRemotePackage(remoteFilePath);
            return result;
        } catch (IOException e) {
            throw new InstallException(e);
        } catch (AdbCommandRejectedException e) {
            throw new InstallException(e);
        } catch (TimeoutException e) {
            throw new InstallException(e);
        } catch (SyncException e) {
            throw new InstallException(e);
        }
    }

    public String syncPackageToDevice(String localFilePath)
            throws IOException, AdbCommandRejectedException, TimeoutException, SyncException {
        try {
            String packageFileName = getFileName(localFilePath);
            String remoteFilePath = String.format("/data/local/tmp/%1$s", packageFileName); //$NON-NLS-1$

            Log.d(packageFileName, String.format("Uploading %1$s onto device '%2$s'",
                    packageFileName, getSerialNumber()));

            SyncService sync = getSyncService();
            if (sync != null) {
                String message = String.format("Uploading file onto device '%1$s'",
                        getSerialNumber());
                Log.d(LOG_TAG, message);
                sync.pushFile(localFilePath, remoteFilePath, SyncService.getNullProgressMonitor());
            } else {
                throw new IOException("Unable to open sync connection!");
            }
            return remoteFilePath;
        } catch (TimeoutException e) {
            Log.e(LOG_TAG, "Error during Sync: timeout.");
            throw e;

        } catch (SyncException e) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1$s", e.getMessage()));
            throw e;

        } catch (IOException e) {
            Log.e(LOG_TAG, String.format("Error during Sync: %1$s", e.getMessage()));
            throw e;

        }
    }

    /**
     * Helper method to retrieve the file name given a local file path
     * @param filePath full directory path to file
     * @return {@link String} file name
     */
    private String getFileName(String filePath) {
        return new File(filePath).getName();
    }

    public String installRemotePackage(String remoteFilePath, boolean reinstall,
            String... extraArgs) throws InstallException {
        try {
            InstallReceiver receiver = new InstallReceiver();
            StringBuilder optionString = new StringBuilder();
            if (reinstall) {
                optionString.append("-r ");
            }
            for (String arg : extraArgs) {
                optionString.append(arg);
                optionString.append(' ');
            }
            String cmd = String.format("pm install %1$s \"%2$s\"", optionString.toString(),
                    remoteFilePath);
            executeShellCommand(cmd, receiver, INSTALL_TIMEOUT);
            return receiver.getErrorMessage();
        } catch (TimeoutException e) {
            throw new InstallException(e);
        } catch (AdbCommandRejectedException e) {
            throw new InstallException(e);
        } catch (ShellCommandUnresponsiveException e) {
            throw new InstallException(e);
        } catch (IOException e) {
            throw new InstallException(e);
        }
    }

    public void removeRemotePackage(String remoteFilePath) throws InstallException {
        try {
            executeShellCommand("rm " + remoteFilePath, new NullOutputReceiver(), INSTALL_TIMEOUT);
        } catch (IOException e) {
            throw new InstallException(e);
        } catch (TimeoutException e) {
            throw new InstallException(e);
        } catch (AdbCommandRejectedException e) {
            throw new InstallException(e);
        } catch (ShellCommandUnresponsiveException e) {
            throw new InstallException(e);
        }
    }

    public String uninstallPackage(String packageName) throws InstallException {
        try {
            InstallReceiver receiver = new InstallReceiver();
            executeShellCommand("pm uninstall " + packageName, receiver, INSTALL_TIMEOUT);
            return receiver.getErrorMessage();
        } catch (TimeoutException e) {
            throw new InstallException(e);
        } catch (AdbCommandRejectedException e) {
            throw new InstallException(e);
        } catch (ShellCommandUnresponsiveException e) {
            throw new InstallException(e);
        } catch (IOException e) {
            throw new InstallException(e);
        }
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IDevice#reboot()
     */
    public void reboot(String into)
            throws TimeoutException, AdbCommandRejectedException, IOException {
        AdbHelper.reboot(into, AndroidDebugBridge.getSocketAddress(), this);
    }
}
