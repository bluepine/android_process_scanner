import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;

import java.lang.Thread;

public class APScan {
	static AndroidDebugBridge adb;

	static private boolean wait_for_connection() {
		int i;
		for (i = 0; i < 5; i++) {
			if (adb.isConnected()) {
				return true;
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return false;
	}

	public static void main(String[] args) {
		String adb_path = "/home/song/android_sdk/android-sdk-linux_86/platform-tools/adb";
		IDevice dl[];
		Log.d("APScan", "Initializing");
		AndroidDebugBridge.init(true);
		System.out.println("AndroidDebugBridge initialized\n");
		adb = AndroidDebugBridge.createBridge(adb_path, true);
		// adb = AndroidDebugBridge.createBridge();
		if (adb == null) {
			System.out.println("createBridge() failed\n");
		} else {
			System.out.println("createBridge() succeeded\n");

			if (wait_for_connection()) {
				System.out.println("adb is connected\n");
				dl = adb.getDevices();
				for (IDevice d : dl) {
					System.out.println(d.toString());
				}
			} else {
				System.out.println("adb is not connected\n");
			}
		}
		// AndroidDebugBridge.disconnectBridge();
		AndroidDebugBridge.terminate();
		System.out.println("AndroidDebugBridge terminated\n");
	}
}
